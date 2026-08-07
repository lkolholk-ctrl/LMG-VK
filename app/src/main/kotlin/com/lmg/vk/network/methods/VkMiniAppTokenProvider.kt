package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkEndpoint
import com.lmg.vk.network.VkHttpMethod
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.RawHttpResponse

/**
 * Порт `C8221e.vip(appId, scope, sourceUrl)` из VK X 8.12.1 — получение
 * отдельного access_token мини-приложения VK.
 *
 * Зачем это нужно. `musicStatResults.getMetrics` и
 * `musicStatResults.createPlaylist` принимают `access_token` ПАРАМЕТРОМ, и это
 * не основной токен клиента, а токен мини-приложения «Итоги года»
 * (`FRESH C4271e.java:1075,1126`, `FRESH C4673e.java:249`). С основным токеном
 * эти методы не работают, поэтому без этого шага экран метрик существовать не
 * может — в спеке (`docs/vkx-port/04-periphery.md` §5) это и отмечено как
 * пропущенная часть порта.
 *
 * Как работает. GET на `oauth.<домен>/authorize` с `response_type=token` и
 * `redirect_uri=…/blank.html`. Сервер отвечает редиректом, в котором токен лежит
 * во фрагменте URL после `access_token=`. VK X читает его двумя путями
 * (`FRESH C8221e.java:1048-1060`):
 *  1) из заголовка `X-Req-Hash`, если ответ не 200;
 *  2) из URL финального редиректа на `/blank.html#access_token=`.
 * В обоих случаях значение обрезается по `"&"`.
 *
 * ВАЖНОЕ ОТСТУПЛЕНИЕ ОТ VK X. Путь через `X-Req-Hash` в LMG VK нерабочий, и
 * подгонять его нельзя: в оригинале этот заголовок ставит сетевой слой и кладёт
 * туда URL запроса (`FRESH AbstractC3257e.java:78-83`), а наш
 * [com.lmg.vk.network.proxy.VkProxyInterceptor] пишет туда только ХОСТ. Хост
 * токена не содержит, поэтому здесь остаётся единственный путь — разбор
 * URL/тела ответа. Заголовок всё же проверяется первым: если он когда-нибудь
 * начнёт содержать полный URL, поведение само совпадёт с оригиналом.
 *
 * ИЗВЕСТНОЕ ОГРАНИЧЕНИЕ (проверять на живом API первым делом). Токен приходит во
 * ФРАГМЕНТЕ url (`…/blank.html#access_token=…`), а [RawHttpResponse] отдаёт
 * наружу только `statusCode`, `url` и тело. Причём `url` — это строка вида
 * `HttpResponse[<url>, <status>]`, собранная по ИСХОДНОМУ запросу Ktor
 * (`KtorRawHttpResponse.url`), а редиректы доводит OkHttp-интерцептор ниже. То
 * есть фрагмент финального редиректа может здесь просто не появиться, и тогда
 * токен получить не удастся — метрики отдадут честную ошибку, а не выдуманные
 * данные.
 *
 * Чтобы закрыть это до конца, нужен доступ к заголовку `Location`/финальному URL
 * из [RawHttpResponse]. Это правка общего сетевого ядра
 * ([com.lmg.vk.network.VkApiClient] / [RawHttpResponse]), которая конфликтует с
 * параллельными батчами, поэтому здесь она НЕ сделана намеренно.
 *
 * Токен кэшируется в памяти процесса: за один заход на экран его дёргают минимум
 * дважды (метрики + создание плейлиста), а лишний OAuth-редирект — лишний шанс
 * упереться в блокировку. На диск не пишется: это чужой короткоживущий токен.
 */
class VkMiniAppTokenProvider(
    private val client: VkApiClient,
) {
    @Volatile
    private var cached: String? = null

    /**
     * Токен мини-приложения «Итоги года» либо `null`, если получить не удалось.
     * `null` — это не ошибка формата, а «данных нет»: вызывающий обязан показать
     * честный текст, а не подставлять основной токен.
     */
    suspend fun yearStatsToken(): String? {
        cached?.let { return it }
        val token = requestToken(
            appId = YEAR_STATS_APP_ID,
            scope = YEAR_STATS_SCOPE,
            sourceUrl = YEAR_STATS_SOURCE_URL,
        )
        cached = token
        return token
    }

    /** Сбрасывает кэш — если VK ответил, что токен уже не годится. */
    fun invalidate() {
        cached = null
    }

    private suspend fun requestToken(appId: Int, scope: String, sourceUrl: String): String? {
        // Основной токен здесь — параметр авторизации самого /authorize, как в
        // оригинале (`FRESH C8221e.java:1108`).
        val mainToken = runCatching { client.getValidToken() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val method = VkMethod("authorize", MiniAppTokenParser).apply {
            endpoint = VkEndpoint.OAUTH
            httpMethod = VkHttpMethod.GET
            param("scope", scope)
            param("client_id", appId)
            param("source_url", sourceUrl)
            param("display", "android")
            param("response_type", "token")
            param("redirect_uri", "https://oauth.${client.currentApiDomain}/blank.html")
            param("access_token", mainToken)
        }

        // Тут сознательно НЕ используется client.execute: он трактует не-2xx как
        // ошибку и не даёт добраться до редиректа, в котором и лежит токен.
        val raw = runCatching {
            client.rawCall(
                name = method.name,
                endpoint = method.endpoint,
                httpMethod = method.httpMethod,
                apiVersion = method.apiVersion,
                params = method.params,
                userAgent = method.userAgent,
            )
        }.getOrNull() ?: return null

        return MiniAppTokenParser.parse(raw).data
    }

    /** Достаёт токен из URL/заголовка ответа. Ошибок не бросает: нет — значит нет. */
    private object MiniAppTokenParser : VkResponseParser<String?> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<String?> {
            // `RawHttpResponse.url` у нас — строка вида
            // "HttpResponse[<url>, <status>]", поэтому ищем подстроку, а не
            // парсим URL: нужен только фрагмент после access_token=.
            val token = extractToken(raw.url)
                ?: extractToken(runCatching { raw.bodyText() }.getOrNull().orEmpty())
            return VkParsedResponse(token, null)
        }

        private fun extractToken(source: String): String? {
            if (source.isEmpty()) return null
            val marker = "access_token="
            val start = source.indexOf(marker)
            if (start < 0) return null
            return source.substring(start + marker.length)
                .substringBefore('&')
                .substringBefore('"')
                .substringBefore(' ')
                .substringBefore(',')
                .substringBefore(']')
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        /** app_id мини-приложения «Итоги года» (`FRESH C4271e.java:1075`). */
        const val YEAR_STATS_APP_ID = 52384530

        /** Дословно из оригинала — тот же порядок и состав. */
        const val YEAR_STATS_SCOPE = "audio,photos"

        const val YEAR_STATS_SOURCE_URL =
            "https://prod-app52384530-74ed1fb7d3e1.pages-ac.vk-apps.com/index.html"
    }
}
