package com.lmg.vk.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.VkErrorCodes

/**
 * Восстановлено из `defpackage.C8221e` — ядро сетевого слоя LMG VK.
 *
 * Ответственности:
 *  - выполнение [VkMethod] через Ktor-клиент (POST form-urlencoded или GET)
 *  - резолв и кэш access_token с mutex-защитой (getValidToken / refreshToken)
 *  - обработка ошибок VK: captcha(14), validation(17), token expired(1117)
 *  - языковой резолв (uk->ua, kk->kz, whitelist, иначе en)
 *
 * Эндпоинты подтверждены восстановленным VK X и рабочим VK MP3 MOD:
 *  - API:       https://api.<domain>/method/<methodName>
 *  - API OAuth: https://api.<domain>/oauth/<methodName>
 *  - OAuth:     https://oauth.<domain>/<methodName>
 * Домен автоматически выбирается между vk.com и vk.ru.
 */
class VkApiClient(
    private val httpClient: HttpClient,
    private val sessionStore: VkSessionStore,
    private val deviceIdProvider: () -> String,
    apiDomain: String = "vk.ru",
) {
    /** Обработчик "validation required" (error 17): возвращает доп. параметры для ретрая. */
    var validationHandler: (suspend (redirectUri: String) -> Map<String, String>?)? = null

    /** Обработчик капчи (error 14): возвращает полный набор параметров ретрая. */
    var captchaHandler: (suspend (
        captchaImg: String,
        captchaSid: String?,
        captchaTs: Double?,
        captchaAttempt: Int?,
    ) -> Map<String, String>?)? = null

    private val tokenMutex = Mutex()

    @Volatile
    private var selectedApiDomain: String = apiDomain

    val currentApiDomain: String
        get() = selectedApiDomain

    /**
     * C9616e: проверяем хосты строго в исходном порядке — сначала vk.com,
     * затем vk.ru — и сохраняем первый доступный для следующих API-вызовов.
     */
    suspend fun probeAndSelectApiDomain(): VkApiAvailability {
        if (pingApiHost("vk.com")) {
            selectedApiDomain = "vk.com"
            return VkApiAvailability.VK_COM_WORKS
        }
        if (pingApiHost("vk.ru")) {
            selectedApiDomain = "vk.ru"
            return VkApiAvailability.VK_RU_WORKS
        }
        return VkApiAvailability.NOTHING_WORKS
    }

    private suspend fun pingApiHost(domain: String): Boolean {
        return try {
            val response = httpClient.get("https://api.$domain/ping.txt")
            response.bodyAsText()
            response.status.value in 200..299
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Публичный вход: выполнение метода (в jadx: `license(C5577e, cont)`)
    // ------------------------------------------------------------------
    suspend fun <T> execute(method: VkMethod<T>): VkResult<T> {
        return try {
            val raw = rawCall(
                name = method.name,
                endpoint = method.endpoint,
                httpMethod = method.httpMethod,
                apiVersion = method.apiVersion,
                params = method.params,
                userAgent = method.userAgent,
                omitAppUserAgent = method.omitAppUserAgent,
            )

            // OAuth `token` возвращает полезный JSON (2FA/captcha/client_error)
            // и вместе с HTTP 4xx. Его обязан разобрать RequestTokenParser;
            // иначе UI видит только голый "HTTP 401" и теряет следующий шаг.
            val hasStructuredOAuthError = (method.endpoint == VkEndpoint.OAUTH || method.endpoint == VkEndpoint.API_OAUTH) && method.name == "token"
            if (raw.statusCode !in 200..299 && !hasStructuredOAuthError) {
                return VkResult.Error(raw.statusCode, "HTTP ${raw.statusCode}: ${raw.url}")
            }

            if (method.isOneShot) {
                return VkResult.Error(VkErrorCodes.NO_CONTENT, "BH.VkApi - One-Shot methods have no content")
            }

            val parsed = method.parser.parse(raw)

            // Для oauth-методов ошибка лежит прямо в конверте; для обычных —
            // проверяем ещё и data-as-error случаи.
            val error: VKError? = (parsed.data as? MayCarryVkError)?.carriedError ?: parsed.error

            if (error == null) {
                if (parsed.data != null) return VkResult.Success(parsed.data)
                error("[unboxVkResponse] raw as no error but response is null, needs investigating")
            }

            when (error.error_code) {
                VkErrorCodes.VALIDATION_REQUIRED -> {
                    val redirect = error.redirectUri
                    val extra = redirect?.let { validationHandler?.invoke(it) }
                    if (extra != null) {
                        method.params.putAll(extra)
                        return execute(method) // ретрай с параметрами валидации
                    }
                    VkResult.Error(error.error_code, error.error_msg)
                }

                VkErrorCodes.CAPTCHA_REQUIRED -> {
                    val redirect = error.redirectUri
                    val img = error.captchaImg
                    val extra = if (!redirect.isNullOrBlank()) {
                        validationHandler?.invoke(redirect)
                    } else if (!img.isNullOrEmpty()) {
                        captchaHandler?.invoke(
                            img,
                            error.captchaSid,
                            error.captchaTs,
                            error.captchaAttempt,
                        )
                    } else null
                    if (extra != null) {
                        method.params.putAll(extra)
                        return execute(method) // ретрай с captcha_key или SmartCaptcha
                    }
                    VkResult.Error(error.error_code, error.error_msg)
                }

                VkErrorCodes.TOKEN_EXPIRED -> {
                    if (method.name != "auth.refreshTokens" && !method.tokenRefreshRetried) {
                        method.tokenRefreshRetried = true
                        if (refreshToken()) {
                            return execute(method) // один ретрай после refresh
                        }
                    }
                    VkResult.Error(error.error_code, error.error_msg)
                }

                else -> VkResult.Error(error.error_code, error.error_msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            // Код 0 у нас означает «нет сети» и так и показывается пользователю
            // («Нет подключения к интернету»). Но сюда попадало ЛЮБОЕ исключение —
            // ошибка разбора JSON, NPE, битый ответ, — и настоящая причина
            // подменялась ложным диагнозом: пользователь искал проблему в сети,
            // которой нет.
            //
            // Теперь 0 остаётся только за реальными сетевыми сбоями, всё
            // остальное получает свой код и своё сообщение, а тип исключения
            // пишется в лог — по нему видно, что случилось.
            val isNetwork = e is java.net.UnknownHostException ||
                e is java.net.SocketTimeoutException ||
                e is java.net.ConnectException ||
                e is java.net.NoRouteToHostException ||
                e is javax.net.ssl.SSLException ||
                e is java.io.IOException
            val label = e.javaClass.simpleName
            com.lmg.vk.debug.DebugLog.add(
                "API ${method.name} упал: $label: ${e.message ?: "без сообщения"}",
            )
            if (isNetwork) {
                VkResult.Error(0, e.message ?: "Сеть недоступна")
            } else {
                // -1: «локальный сбой обработки ответа», не сетевой.
                VkResult.Error(-1, "$label: ${e.message ?: "неизвестная ошибка"}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Сырой HTTP-вызов (в jadx: `appmetrica(name, isOAuth, version, params, ua, cont)`)
    // ------------------------------------------------------------------
    suspend fun rawCall(
        name: String,
        endpoint: VkEndpoint,
        httpMethod: VkHttpMethod,
        apiVersion: String,
        params: Map<String, String>,
        userAgent: String?,
        omitAppUserAgent: Boolean = false,
    ): RawHttpResponse {
        // access_token API 5.272 передаётся только как Bearer, не дублируется в body.
        val explicit = params["access_token"].takeUnless { it.isNullOrEmpty() }
        val token: String? = if (endpoint != VkEndpoint.API_METHOD) {
            null
        } else when {
            explicit != null -> explicit
            // C8221e отправляет auth.refreshTokens без Authorization: протухший
            // access token не должен мешать обмену сохранённого exchange token.
            name == "auth.refreshTokens" -> null
            else -> getValidToken().takeIf { it.isNotBlank() }
        }

        val requestParams = LinkedHashMap<String, String>().apply {
            // VK X keeps the resolved API token only in Authorization. An
            // explicitly supplied access_token selects the Bearer value, but
            // must not also be duplicated in the form body.
            params.forEach { (key, value) ->
                if (key != "access_token" || endpoint != VkEndpoint.API_METHOD) {
                    put(key, value)
                }
            }
            putIfAbsent("v", apiVersion)
            putIfAbsent("https", "1")
            if (endpoint != VkEndpoint.OAUTH) {
                putIfAbsent("api_id", VK_ANDROID_CLIENT_ID)
            }
            putIfAbsent("lang", VkLocales.current())
            putIfAbsent("device_id", deviceIdProvider())
        }

        val response: HttpResponse = httpClient.request {
            method = if (httpMethod == VkHttpMethod.GET) HttpMethod.Get else HttpMethod.Post
            url {
                protocol = URLProtocol.HTTPS
                when (endpoint) {
                    VkEndpoint.API_METHOD -> {
                        host = "api.$selectedApiDomain"
                        path("method", name)
                    }
                    VkEndpoint.API_OAUTH -> {
                        host = "api.$selectedApiDomain"
                        path("oauth", name)
                    }
                    VkEndpoint.OAUTH -> {
                        host = "oauth.$selectedApiDomain"
                        path(name)
                    }
                }
                if (httpMethod == VkHttpMethod.GET) {
                    requestParams.forEach { (key, value) -> parameters.append(key, value) }
                }
            }
            if (endpoint != VkEndpoint.OAUTH) {
                header("X-VK-Android-Client", "new")
                header("X-Screen", "nowhere")
            }
            token?.let { header("Authorization", "Bearer $it") }
            if (!omitAppUserAgent) {
                header("User-Agent", userAgent ?: VkUserAgents.api)
            }
            if (httpMethod == VkHttpMethod.POST) {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(FormDataContent(Parameters.build {
                    requestParams.forEach { (key, value) -> append(key, value) }
                }))
            }
        }
        return KtorRawHttpResponse(response)
    }

    // ------------------------------------------------------------------
    // Токены (в jadx: `ad(cont)` = getValidToken, `purchase(cont)` = refreshToken)
    // ------------------------------------------------------------------

    /** Возвращает валидный access_token; при истечении — обновляет под mutex. */
    val currentUserId: Long get() = sessionStore.session.userId

    suspend fun getValidToken(): String {
        val current = sessionStore.session
        // VK может вернуть expires_in=0: это означает, что явный срок жизни
        // токена не задан. Такой токен остаётся рабочим до ошибки 1117 и не
        // должен принудительно обновляться перед каждым API-вызовом.
        if (current.accessToken.isNotBlank() && !current.isExpired) {
            return current.accessToken
        }
        return tokenMutex.withLock {
            // double-check после захвата mutex
            val rechecked = sessionStore.session
            if (rechecked.accessToken.isNotBlank() && !rechecked.isExpired) {
                return@withLock rechecked.accessToken
            }
            performTokenRefresh()
            sessionStore.session.accessToken
        }
    }

    /** Принудительный refresh (вызов auth.refreshTokens). Возвращает успех. */
    suspend fun refreshToken(): Boolean {
        return tokenMutex.withLock {
            performTokenRefresh()
        }
    }

    private suspend fun performTokenRefresh(): Boolean {
        val session = sessionStore.session
        if (session.exchangeToken.isBlank()) return false
        return VkAuthApi(this, sessionStore).refreshTokens()
    }

    companion object {
        /** api_id официального Android-клиента VK (хардкод в оригинале). */
        const val VK_ANDROID_CLIENT_ID = RecoveredServiceConfig.VK_ANDROID_CLIENT_ID
    }
}

/** Имена восстановлены без обфускации из EnumC6583e. */
enum class VkApiAvailability {
    VK_COM_WORKS,
    VK_RU_WORKS,
    NOTHING_WORKS,
}

/** Маркер для DTO, которые могут нести ошибку внутри data (в jadx: C15748e). */
interface MayCarryVkError {
    val carriedError: VKError?
}

/** Хранилище сессии (в jadx поля `billing`/`metrica` клиента + C6594e-флоу). */
interface VkSessionStore {
    var session: VkAuthSession
}

/** Optional multi-account capabilities; VkApiClient still consumes one active session. */
interface VkMultiSessionStore : VkSessionStore {
    val sessions: List<VkAuthSession>
    fun activate(userId: Long): VkAuthSession?
    fun remove(userId: Long): VkAuthSession
}

/** Обёртка Ktor HttpResponse под [RawHttpResponse]. */
class KtorRawHttpResponse(private val response: HttpResponse) : RawHttpResponse {
    override val statusCode: Int get() = response.status.value
    override val url: String get() = "HttpResponse[${response.call.request.url}, ${response.status}]"
    override suspend fun bodyText(): String = response.bodyAsText()

    /**
     * `response.headers` — заголовки, уже пропущенные через прокси-интерцептор,
     * поэтому здесь видно и его `X-Req-Hash` с фрагментом финального URL.
     * Ktor сравнивает имена заголовков без учёта регистра, как и требует HTTP.
     */
    override fun header(name: String): String? = response.headers[name]
}

/**
 * Фрагмент URL финального ответа цепочки редиректов, если его удалось узнать.
 *
 * Нужен там, где значимая часть ответа лежит в САМОМ URL, а не в теле: OAuth
 * мини-приложений возвращает токен как `…/blank.html#access_token=…`.
 *
 * Источник — заголовок `X-Req-Hash`, который ставит
 * [com.lmg.vk.network.proxy.VkProxyInterceptor] ровно как VK X: там в него
 * пишется `url.fragment` (`AbstractC3257e`, поле `C15718e.yandex`). Редиректы
 * доводит сам интерцептор, поэтому фрагмент виден именно здесь — выше, в Ktor,
 * `response.call.request` описывает уже исходный запрос.
 *
 * На пути без интерцептора заголовка не будет — тогда `null`, и вызывающий
 * обязан это учесть.
 */
fun RawHttpResponse.urlFragmentOrNull(): String? =
    header("X-Req-Hash")?.takeIf { it.isNotBlank() }

/**
 * Языковой резолв VK (в jadx: `AbstractC4533e`).
 * uk->ua, kk->kz, языки из whitelist — как есть, иначе en. Кэшируется.
 */
object VkLocales {
    private val SUPPORTED = arrayOf("ru", "en", "ua", "kz", "pt") // whitelist[5] из оригинала

    @Volatile
    private var cached: String? = null

    fun current(): String {
        cached?.let { return it }
        val lang = java.util.Locale.getDefault().language
        val resolved = when {
            lang == "uk" -> "ua"
            lang == "kk" -> "kz"
            lang.length >= 2 -> SUPPORTED.firstOrNull { lang.startsWith(it) } ?: "en"
            else -> "en"
        }
        cached = resolved
        return resolved
    }
}
