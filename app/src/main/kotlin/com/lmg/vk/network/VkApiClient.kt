package com.lmg.vk.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
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
 *  - выполнение [VkMethod] через Ktor-клиент (POST form-urlencoded)
 *  - резолв и кэш access_token с mutex-защитой (getValidToken / refreshToken)
 *  - обработка ошибок VK: captcha(14), validation(17), token expired(1117)
 *  - языковой резолв (uk->ua, kk->kz, whitelist, иначе en)
 *
 * Эндпоинт: https://api.<domain>/method|oauth/<methodName>
 * (<domain> приходит из нативного окружения — LmgNative.getVkApiData, "vk.ru")
 */
class VkApiClient(
    private val httpClient: HttpClient,
    private val sessionStore: VkSessionStore,
    private val deviceIdProvider: () -> String,
    apiDomain: String = "vk.ru",
) {
    /** Обработчик "validation required" (error 17): возвращает доп. параметры для ретрая. */
    var validationHandler: (suspend (redirectUri: String) -> Map<String, String>?)? = null

    /** Обработчик капчи (error 14): возвращает введённый пользователем текст. */
    var captchaHandler: (suspend (captchaImg: String, captchaSid: String?) -> Map<String, String>?)? = null

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
                useOAuth = method.useOAuth,
                apiVersion = method.apiVersion,
                params = method.params,
                userAgent = null,
            )

            if (raw.statusCode !in 200..299) {
                return VkResult.Error(raw.statusCode, "HTTP ${raw.statusCode}: ${raw.url}")
            }

            if (method.isOneShot) {
                return VkResult.Error(VkErrorCodes.NO_CONTENT, "BH.VkApi - One-Shot methods have no content")
            }

            val parsed = method.parser.parse(raw)

            // Для oauth-методов ошибка лежит прямо в конверте; для обычных —
            // проверяем ещё и data-as-error случаи.
            val error: VKError? = if (method.useOAuth) {
                parsed.error
            } else {
                (parsed.data as? MayCarryVkError)?.carriedError ?: parsed.error
            }

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
                    val img = error.captchaImg
                    val extra = if (!img.isNullOrEmpty()) {
                        captchaHandler?.invoke(img, error.captchaSid)
                    } else null
                    if (extra != null) {
                        method.params.putAll(extra)
                        return execute(method) // ретрай с captcha_key
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
            VkResult.Error(0, e.message ?: "")
        }
    }

    // ------------------------------------------------------------------
    // Сырой HTTP-вызов (в jadx: `appmetrica(name, isOAuth, version, params, ua, cont)`)
    // ------------------------------------------------------------------
    suspend fun rawCall(
        name: String,
        useOAuth: Boolean,
        apiVersion: String,
        params: Map<String, String>,
        userAgent: String?,
    ): RawHttpResponse {
        // --- резолв access_token ---
        val explicit = params["access_token"].takeUnless { it.isNullOrEmpty() }
        val token: String? = when {
            explicit != null -> explicit
            name == "auth.getExchangeToken" -> sessionStore.session.exchangeToken
            !useOAuth && name != "auth.refreshTokens" -> getValidToken().takeIf { it.isNotBlank() }
            else -> null
        }

        val response: HttpResponse = httpClient.post {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.$selectedApiDomain"
                path(if (useOAuth) "oauth" else "method", name)
            }
            header("Content-Type", "application/x-www-form-urlencoded")
            header("X-VK-Android-Client", "new")
            header("X-Screen", "nowhere")
            token?.let { header("Authorization", "Bearer $it") }
            userAgent?.let { header("User-Agent", it) }
            setBody(FormDataContent(Parameters.build {
                params.forEach { (k, v) -> append(k, v) }
                append("v", apiVersion)
                append("https", "1")
                append("api_id", VK_ANDROID_CLIENT_ID)
                append("lang", VkLocales.current())
                append("device_id", deviceIdProvider())
            }))
        }
        return KtorRawHttpResponse(response)
    }

    // ------------------------------------------------------------------
    // Токены (в jadx: `ad(cont)` = getValidToken, `purchase(cont)` = refreshToken)
    // ------------------------------------------------------------------

    /** Возвращает валидный access_token; при истечении — обновляет под mutex. */
    suspend fun getValidToken(): String {
        val current = sessionStore.session
        if (current.expiresAt != 0L && !current.isExpired) {
            return current.accessToken
        }
        return tokenMutex.withLock {
            // double-check после захвата mutex
            val rechecked = sessionStore.session
            if (rechecked.expiresAt != 0L && !rechecked.isExpired) {
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

/** Обёртка Ktor HttpResponse под [RawHttpResponse]. */
class KtorRawHttpResponse(private val response: HttpResponse) : RawHttpResponse {
    override val statusCode: Int get() = response.status.value
    override val url: String get() = "HttpResponse[${response.call.request.url}, ${response.status}]"
    override suspend fun bodyText(): String = response.bodyAsText()
}

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
