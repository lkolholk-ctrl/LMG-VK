package com.lmg.vk.network

import com.lmg.vk.debug.DebugLog
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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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
    private val refreshFailures = ConcurrentHashMap<RefreshAttemptKey, Int>()
    private val refreshRetryAt = ConcurrentHashMap<RefreshAttemptKey, Long>()

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
            val usesImplicitSession = method.endpoint == VkEndpoint.API_METHOD &&
                method.name != "auth.refreshTokens" &&
                method.authorizationToken.isNullOrBlank() &&
                method.params["access_token"].isNullOrBlank()
            val resolvedAuthorizationToken = if (usesImplicitSession) {
                getValidToken()
            } else {
                method.authorizationToken
            }
            val requestSession = sessionStore.session
            val raw = rawCall(
                name = method.name,
                endpoint = method.endpoint,
                httpMethod = method.httpMethod,
                apiVersion = method.apiVersion,
                params = method.params,
                userAgent = method.userAgent,
                authorizationToken = resolvedAuthorizationToken,
            )

            // OAuth `token` возвращает полезный JSON (2FA/captcha/client_error)
            // и вместе с HTTP 4xx. Его обязан разобрать RequestTokenParser;
            // иначе UI видит только голый "HTTP 401" и теряет следующий шаг.
            val hasStructuredOAuthError = (method.endpoint == VkEndpoint.OAUTH || method.endpoint == VkEndpoint.API_OAUTH) && method.name == "token"
            if (raw.statusCode !in 200..299 && !hasStructuredOAuthError) {
                traceAuthResult(method.name, method.endpoint, raw.statusCode, "http_error")
                return VkResult.Error(raw.statusCode, "HTTP ${raw.statusCode}: ${raw.url}")
            }

            if (method.isOneShot) {
                return VkResult.Error(VkErrorCodes.NO_CONTENT, "BH.VkApi - One-Shot methods have no content")
            }

            val parsed = method.parser.parse(raw)

            // Для oauth-методов ошибка лежит прямо в конверте; для обычных —
            // проверяем ещё и data-as-error случаи.
            val error: VKError? = (parsed.data as? MayCarryVkError)?.carriedError ?: parsed.error
            traceAuthResult(
                method.name,
                method.endpoint,
                raw.statusCode,
                error?.let { "vk_error=${it.error_code}" }
                    ?: "parsed=${(parsed.data as Any?)?.javaClass?.simpleName ?: "empty"}",
            )

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
                    if (usesImplicitSession && !method.tokenRefreshRetried) {
                        method.tokenRefreshRetried = true
                        val refreshed = refreshSession(requestSession, force = true)
                        if (refreshed != null && sessionStore.session.userId == requestSession.userId) {
                            return execute(method)
                        }
                    }
                    VkResult.Error(error.error_code, error.error_msg)
                }

                else -> VkResult.Error(error.error_code, error.error_msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAuthWireMethod(method.name, method.endpoint)) {
                DebugLog.add(
                    "VK AUTH WIRE failure method=${method.name} type=${e.javaClass.simpleName}",
                )
            }
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
        authorizationToken: String? = null,
    ): RawHttpResponse {
        val explicit = params["access_token"].takeUnless { it.isNullOrEmpty() }
        val token: String? = if (endpoint != VkEndpoint.API_METHOD) {
            null
        } else when {
            !authorizationToken.isNullOrBlank() -> authorizationToken
            explicit != null -> explicit
            // C8221e отправляет auth.refreshTokens без Authorization: протухший
            // access token не должен мешать обмену сохранённого exchange token.
            name == "auth.refreshTokens" -> null
            else -> getValidToken().takeIf { it.isNotBlank() }
        }

        val requestParams = LinkedHashMap<String, String>().apply {
            putAll(params)
            putIfAbsent("v", apiVersion)
            putIfAbsent("https", "1")
            if (endpoint != VkEndpoint.OAUTH) {
                putIfAbsent("api_id", VK_ANDROID_CLIENT_ID)
            }
            putIfAbsent("lang", VkLocales.current())
            putIfAbsent("device_id", deviceIdProvider())
        }

        val resolvedUserAgent = userAgent ?: VkUserAgents.api
        if (isAuthWireMethod(name, endpoint)) {
            DebugLog.add(
                "VK AUTH WIRE request method=$name endpoint=${endpoint.name} http=${httpMethod.name} " +
                    "host=${wireHost(endpoint, selectedApiDomain)}",
            )
            DebugLog.add(
                "VK AUTH WIRE headers User-Agent=${wireText(resolvedUserAgent)} " +
                    "X-VK-Android-Client=${if (endpoint != VkEndpoint.OAUTH) "new" else "absent"} " +
                    "X-Screen=${if (endpoint != VkEndpoint.OAUTH) "nowhere" else "absent"} " +
                    "Authorization=${wireFingerprint(token)} " +
                    "Content-Type=${if (httpMethod == VkHttpMethod.POST) "application/x-www-form-urlencoded" else "absent"}",
            )
            DebugLog.add(
                "VK AUTH WIRE params " + requestParams.entries.joinToString("&") { (key, value) ->
                    "$key=${wireParam(key, value)}"
                },
            )
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
            header("User-Agent", resolvedUserAgent)
            if (httpMethod == VkHttpMethod.POST) {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(FormDataContent(Parameters.build {
                    requestParams.forEach { (key, value) -> append(key, value) }
                }))
            }
        }
        if (isAuthWireMethod(name, endpoint)) {
            DebugLog.add("VK AUTH WIRE received method=$name http=${response.status.value}")
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
        if (current.accessToken.isBlank() || !shouldRefreshToken(current)) {
            return current.accessToken
        }
        return tokenMutex.withLock {
            val rechecked = sessionStore.session
            if (rechecked.accessToken.isBlank() || !shouldRefreshToken(rechecked)) {
                return@withLock rechecked.accessToken
            }
            performTokenRefresh(rechecked, force = false)?.accessToken ?: sessionStore.session.accessToken
        }
    }

    /** Принудительный refresh (вызов auth.refreshTokens). Возвращает успех. */
    suspend fun refreshToken(): Boolean {
        return tokenMutex.withLock {
            performTokenRefresh(sessionStore.session, force = true) != null
        }
    }

    suspend fun refreshSession(session: VkAuthSession, force: Boolean = false): VkAuthSession? {
        return tokenMutex.withLock {
            performTokenRefresh(session, force)
        }
    }

    suspend fun refreshSessions(
        sessions: List<VkAuthSession>,
        force: Boolean = false,
    ): Map<Long, VkAuthSession> {
        return tokenMutex.withLock {
            performTokenRefresh(sessions, force)
        }
    }

    fun shouldRefreshToken(
        session: VkAuthSession,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): Boolean {
        if (session.accessToken.isBlank() || session.exchangeToken.isBlank()) return false
        return session.expiresAt > 0L && session.expiresAt <= nowSeconds + TOKEN_REFRESH_MARGIN_SECONDS
    }

    private suspend fun performTokenRefresh(
        session: VkAuthSession,
        force: Boolean,
    ): VkAuthSession? = performTokenRefresh(listOf(session), force)[session.userId]

    private suspend fun performTokenRefresh(
        sessions: List<VkAuthSession>,
        force: Boolean,
    ): Map<Long, VkAuthSession> {
        val now = System.currentTimeMillis()
        val eligible = sessions.filter { session ->
            if (session.userId == 0L || session.exchangeToken.isBlank()) return@filter false
            val key = RefreshAttemptKey(session.userId, session.accessToken, session.exchangeToken)
            force || (refreshRetryAt[key] ?: 0L) <= now
        }
        if (eligible.isEmpty()) return emptyMap()
        val refreshed = VkAuthApi(this, sessionStore).refreshTokens(eligible)
        eligible.forEach { session ->
            val key = RefreshAttemptKey(session.userId, session.accessToken, session.exchangeToken)
            if (refreshed.containsKey(session.userId)) {
                refreshFailures.remove(key)
                refreshRetryAt.remove(key)
            } else {
                val failureCount = refreshFailures.merge(key, 1, Int::plus) ?: 1
                val retryDelay = when (failureCount) {
                    1 -> 60_000L
                    2 -> 5L * 60_000L
                    3 -> 15L * 60_000L
                    4 -> 60L * 60_000L
                    else -> 6L * 60L * 60_000L
                }
                refreshRetryAt[key] = now + retryDelay
            }
        }
        return refreshed
    }

    companion object {
        const val TOKEN_REFRESH_MARGIN_SECONDS = 6L * 60L * 60L

        /** api_id официального Android-клиента VK (хардкод в оригинале). */
        const val VK_ANDROID_CLIENT_ID = RecoveredServiceConfig.VK_ANDROID_CLIENT_ID

        private val authWireNames = setOf(
            "get_anonym_token",
            "auth.validateAccount",
            "ecosystem.sendOtpSms",
            "ecosystem.sendOtpEmail",
            "ecosystem.sendOtpPush",
            "ecosystem.sendOtpCallReset",
            "ecosystem.checkOtp",
            "token",
        )

        private val hiddenWireParams = setOf(
            "login",
            "username",
            "password",
            "code",
            "captcha_key",
            "success_token",
            "client_secret",
        )

        private val fingerprintedWireParams = setOf(
            "sid",
            "anonymous_token",
            "access_token",
            "captcha_sid",
            "device_id",
        )

        private val visibleWireParams = setOf(
            "v",
            "https",
            "api_id",
            "lang",
            "client_id",
            "force_password",
            "passkey_supported",
            "supported_ways",
            "flow_type",
            "sak_version",
            "verification_method",
            "libverify_support",
            "scope",
            "device_trusted_hash_support",
            "grant_type",
            "2fa_supported",
            "captcha_ts",
            "captcha_attempt",
        )

        private fun isAuthWireMethod(name: String, endpoint: VkEndpoint): Boolean =
            name in authWireNames && (endpoint == VkEndpoint.API_METHOD || endpoint == VkEndpoint.API_OAUTH)

        private fun traceAuthResult(name: String, endpoint: VkEndpoint, status: Int, result: String) {
            if (isAuthWireMethod(name, endpoint)) {
                DebugLog.add("VK AUTH WIRE result method=$name http=$status $result")
            }
        }

        private fun wireHost(endpoint: VkEndpoint, domain: String): String = when (endpoint) {
            VkEndpoint.API_METHOD, VkEndpoint.API_OAUTH -> "api.$domain"
            VkEndpoint.OAUTH -> "oauth.$domain"
        }

        private fun wireParam(name: String, value: String): String = when (name) {
            in hiddenWireParams -> if (value.isEmpty()) "empty" else "present"
            in fingerprintedWireParams -> wireFingerprint(value)
            in visibleWireParams -> wireText(value)
            else -> if (value.isEmpty()) "empty" else "present"
        }

        private fun wireFingerprint(value: String?): String {
            if (value.isNullOrEmpty()) return "absent"
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            val shortHash = digest.take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "<len=${value.length},sha256=$shortHash>"
        }

        private fun wireText(value: String): String = value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(240)
    }

    private data class RefreshAttemptKey(
        val userId: Long,
        val accessToken: String,
        val exchangeToken: String,
    )
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

    fun updateSession(
        expected: VkAuthSession,
        transform: (VkAuthSession) -> VkAuthSession,
    ): VkAuthSession? {
        val current = session
        if (current.userId != expected.userId ||
            current.accessToken != expected.accessToken ||
            current.exchangeToken != expected.exchangeToken
        ) {
            return null
        }
        return transform(current).also { session = it }
    }
}

interface VkMultiSessionStore : VkSessionStore {
    val sessions: List<VkAuthSession>
    fun save(session: VkAuthSession, makeActive: Boolean)
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
