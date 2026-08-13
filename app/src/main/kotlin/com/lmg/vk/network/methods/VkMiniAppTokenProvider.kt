package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkEndpoint
import com.lmg.vk.network.VkHttpMethod
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.urlFragmentOrNull

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
 * ПОЧЕМУ РАБОТАЕТ ПУТЬ ЧЕРЕЗ `X-Req-Hash`. Заголовок выглядит диагностическим, но
 * сетевой слой VK X кладёт в него не хост, а ФРАГМЕНТ финального URL:
 * `AbstractC3257e.java:123-127` пишет `response.request.url.fragment` (поле
 * `C15718e.yandex` — 8-й аргумент конструктора HttpUrl, в
 * `C15718e.purchase()` оно вычисляется как «всё после `#`»). Во фрагменте
 * `…/blank.html#access_token=…` и лежит токен, поэтому оба «разных» пути в
 * `C8221e` читают одно и то же значение.
 *
 * Поведение оригинала воспроизведено как есть:
 * [com.lmg.vk.network.proxy.VkProxyInterceptor] пишет фрагмент в `X-Req-Hash`, а
 * читается он через [com.lmg.vk.network.urlFragmentOrNull]. Заголовок ставится на
 * ОТВЕТ и в сеть не уходит — его видит только сам клиент внутри процесса.
 *
 * Почему вообще нужен заголовок, а не `RawHttpResponse.url`: редиректы у OkHttp
 * выключены и доводятся интерцептором, поэтому Ktor знает только ИСХОДНЫЙ запрос,
 * и финальный URL до него не доходит.
 *
 * Если токен получить не удалось — возвращается `null`. Подставлять основной токен
 * нельзя: `musicStatResults.*` его не примут, а тихая подмена превратила бы
 * отсутствие данных в необъяснимую ошибку VK.
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
    @Volatile
    private var cachedUserId: Long = 0L

    /**
     * Токен мини-приложения «Итоги года» либо `null`, если получить не удалось.
     * `null` — это не ошибка формата, а «данных нет»: вызывающий обязан показать
     * честный текст, а не подставлять основной токен.
     */
    suspend fun yearStatsToken(): String? {
        val userId = client.currentUserId
        if (cachedUserId == userId) cached?.let { return it }
        cached = null
        val token = requestToken(
            appId = YEAR_STATS_APP_ID,
            scope = YEAR_STATS_SCOPE,
            sourceUrl = YEAR_STATS_SOURCE_URL,
        )
        cached = token
        cachedUserId = userId
        return token
    }

    /** Сбрасывает кэш — если VK ответил, что токен уже не годится. */
    fun invalidate() {
        cached = null
        cachedUserId = 0L
    }

    private suspend fun requestToken(appId: Int, scope: String, sourceUrl: String): String? {
        // Основной токен здесь — параметр авторизации самого /authorize, как в
        // оригинале (`FRESH C8221e.java:1108`).
        val mainToken = runCatching { client.getValidToken() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parser = MiniAppTokenParser(mainToken)
        val method = VkMethod("authorize", parser).apply {
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

        // Парсер знает основной токен, чтобы никогда не выдать его за токен
        // мини-приложения (см. MiniAppTokenParser).
        return parser.parse(raw).data
    }

    /**
     * Достаёт токен из URL/заголовков ответа. Ошибок не бросает: нет — значит нет.
     *
     * [mainToken] — основной токен клиента, который ушёл в ЗАПРОСЕ параметром
     * `access_token`. Из-за этого строка запроса сама содержит `access_token=…`, и
     * наивный поиск подстроки вернул бы основной токен вместо токена
     * мини-приложения: `musicStatResults.*` его не принимают, а подмена выглядела
     * бы как необъяснимая ошибка VK вместо честного «токен не получен». Поэтому
     * ниже два независимых предохранителя: из URL читается только ФРАГМЕНТ (VK
     * кладёт токен после `#`, а основной токен остаётся в query до `#`), и любой
     * кандидат, равный [mainToken], отбрасывается.
     */
    private class MiniAppTokenParser(private val mainToken: String) : VkResponseParser<String?> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<String?> {
            // Штатный источник — тот же, что у VK X: заголовок `X-Req-Hash`, куда
            // прокси-интерцептор кладёт ФРАГМЕНТ финального URL (`AbstractC3257e`
            // пишет туда `url.fragment`). Именно во фрагменте
            // `…/blank.html#access_token=…` и лежит токен.
            //
            // Дальше запасные пути, на случай если интерцептор в цепочке не
            // участвовал (прокси выключен — тогда заголовка нет):
            //  - `Location`, если редирект не был доведён и ответ остался 3xx;
            //  - `RawHttpResponse.url` — строка по ИСХОДНОМУ запросу; фрагмента там
            //    обычно нет, но если Ktor сам увидел финальный URL — сработает.
            val fromFragment = raw.urlFragmentOrNull()?.let(::tokenFrom)
            val fromUrls = fromFragment ?: sequenceOf(
                raw.header("Location"),
                raw.url,
            ).firstNotNullOfOrNull { source -> source?.let(::tokenFromFragment) }

            // Тело — последний шанс: если VK ответит не редиректом, а страницей
            // с токеном. Здесь фрагмента нет, поэтому ищем по всей строке, полагаясь
            // на проверку на mainToken.
            return VkParsedResponse(
                fromUrls ?: tokenFrom(runCatching { raw.bodyText() }.getOrNull().orEmpty()),
                null,
            )
        }

        /** Токен из фрагмента URL (часть после `#`), где его и отдаёт VK. */
        private fun tokenFromFragment(source: String): String? {
            val hash = source.indexOf('#')
            if (hash < 0) return null
            return tokenFrom(source.substring(hash + 1))
        }

        private fun tokenFrom(source: String): String? {
            if (source.isEmpty()) return null
            val marker = "access_token="
            val start = source.indexOf(marker)
            if (start < 0) return null
            return source.substring(start + marker.length)
                // Граница — любой символ, которого в токене быть не может: VK отдаёт
                // безопасный для URL набор, дальше идёт следующий параметр или
                // обрамление лог-строки.
                .takeWhile { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
                .takeIf { it.isNotBlank() && it != mainToken }
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
