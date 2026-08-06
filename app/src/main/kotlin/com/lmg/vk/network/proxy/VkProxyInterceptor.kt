package com.lmg.vk.network.proxy

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Перенос `C13079e` / `AbstractC3257e` из VK X — увод запросов к доменам VK на
 * IP-адреса прокси.
 *
 * Как это работает и почему именно так. Хост в URL заменяется на IP, а исходное
 * имя возвращается заголовком `Host`. Смысл не в самой подмене, а в побочном
 * эффекте: для IP-литерала TLS не отправляет SNI, поэтому имя домена нигде не
 * едет открытым текстом — узел выбирает бэкенд по `Host` уже внутри шифрования.
 * Из-за этого нельзя заменить всё это подменой DNS: резолвер оставил бы SNI на
 * месте, и смысл обхода пропал бы.
 *
 * Плата за это — сертификат узла не совпадает с именем хоста, что и разбирают
 * [VkProxyTrustManager] и [VkProxyHostnameVerifier].
 *
 * Отличия от VK X, сделанные намеренно:
 * - VK X всегда берёт первый адрес (`AbstractC13480e.m3591interface` — это
 *   буквально `get(0)`) и при мёртвом узле просто отдаёт ошибку. Здесь адреса
 *   перебираются по порядку, а если не ответил ни один — запрос уходит напрямую.
 *   Прокси задуман как обход блокировки, а не как единственный путь к сети.
 * - `domain_overrides` (в конфиге VK — `override_api_domain`) применяется только
 *   к проксируемым запросам. У VK это отдельный эксперимент с выкаткой на 50%
 *   (`override_domain_part`), и включать зеркало `r.vk.com` на прямом пути,
 *   который и так работает, нет причин.
 */
internal class VkProxyInterceptor(
    private val enabled: () -> Boolean,
    private val state: () -> VkProxyState,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val available = (if (enabled()) state() else null) as? VkProxyState.Available

        // Редиректы у самого OkHttp выключены (см. installVkProxy), поэтому даже
        // непроксируемый запрос надо довести до конца здесь.
        if (available == null || available.ips.isEmpty() || !available.matches(original.url.host)) {
            return withReqHash(execute(chain, original, null, null))
        }

        // Повтор возможен, только если тело можно прочитать заново. Односторонний
        // поток после первой попытки уже вычерпан — тогда работаем как VK X, по
        // первому адресу и без запасных вариантов.
        val retryable = original.body?.isOneShot() != true
        val candidates = if (retryable) available.ips else available.ips.take(1)

        var lastFailure: IOException? = null
        for (ip in candidates) {
            try {
                return withReqHash(execute(chain, original, available, ip))
            } catch (error: IOException) {
                lastFailure = error
            }
        }

        if (!retryable) throw lastFailure ?: IOException("Прокси VK: узел не ответил")
        // Ни один узел не ответил — пробуем напрямую. Если домен и правда
        // заблокирован, здесь прилетит своя ошибка, и это честнее, чем молчать.
        return try {
            withReqHash(execute(chain, original, null, null))
        } catch (direct: IOException) {
            throw lastFailure?.also { it.addSuppressed(direct) } ?: direct
        }
    }

    /**
     * Выполняет запрос через узел [ip], сам доводя редиректы.
     *
     * Логический запрос (с настоящим именем хоста) ведётся отдельно от того, что
     * уходит в сеть: `Location` разрешается относительно домена, а не IP, иначе
     * относительный редирект увёл бы следующий шаг на голый адрес без `Host`.
     */
    private fun execute(
        chain: Interceptor.Chain,
        start: Request,
        available: VkProxyState.Available?,
        ip: String?,
    ): Response {
        var logical = start
        var response = chain.proceed(wire(logical, available, ip))
        var hops = 0
        while (hops < MAX_REDIRECTS) {
            val next = followUp(response, logical) ?: return response
            hops++
            response.close()
            logical = next
            response = chain.proceed(wire(next, available, ip))
        }
        return response
    }

    /**
     * Запрос в том виде, в каком он уйдёт в сеть: хост → [ip], `Host` → исходное
     * имя (или его замена из `domain_overrides`). Домены не из списка остаются
     * как есть — редирект мог увести за пределы VK.
     */
    private fun wire(request: Request, available: VkProxyState.Available?, ip: String?): Request {
        if (available == null || ip == null) return request
        val host = request.url.host
        if (!available.matches(host)) return request
        val target = available.domainOverrides[host] ?: host
        val url = runCatching { request.url.newBuilder().host(ip).build() }.getOrNull()
            ?: return request
        return request.newBuilder()
            .url(url)
            .removeHeader("Host")
            .addHeader("Host", target)
            .build()
    }

    /**
     * Следующий запрос по редиректу — сокращённые правила OkHttp, повторяющие
     * `AbstractC3257e.vip`. [logical] — запрос с настоящим хостом, от него и
     * считается относительный `Location`.
     */
    private fun followUp(response: Response, logical: Request): Request? {
        val method = logical.method
        val allowed = when (response.code) {
            307 -> method == "GET" || method == "HEAD" || method == "POST"
            308 -> method == "GET" || method == "HEAD"
            in 300..303 -> true
            else -> false
        }
        if (!allowed) return null

        val location = response.header("Location") ?: return null
        val url = logical.url.resolve(location) ?: return null

        val builder = logical.newBuilder().url(url).removeHeader("Host")
        if (response.code in 300..303 && method != "GET" && method != "HEAD") {
            // 301/302/303 для не-GET превращаются в GET — тело и его заголовки
            // становятся бессмысленными.
            builder.method("GET", null)
                .removeHeader("Transfer-Encoding")
                .removeHeader("Content-Length")
                .removeHeader("Content-Type")
        }
        if (!logical.url.host.equals(url.host, ignoreCase = true) ||
            logical.url.scheme != url.scheme ||
            logical.url.port != url.port
        ) {
            // Уходим на другой узел — токен туда отдавать нельзя.
            builder.removeHeader("Authorization")
        }
        return builder.build()
    }

    /** Диагностический заголовок VK X: хост, которым запрос реально ушёл. */
    private fun withReqHash(response: Response): Response =
        response.newBuilder()
            .header(HEADER_REQ_HASH, response.request.url.host)
            .build()

    private companion object {
        const val MAX_REDIRECTS = 20
        const val HEADER_REQ_HASH = "X-Req-Hash"
    }
}

/** `host == domain || host.endsWith(".$domain")` — правило совпадения из VK X. */
internal fun VkProxyState.Available.matches(host: String): Boolean =
    allowedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
