package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.MappingVkResponseParser
import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.VkItems
import com.lmg.vk.jni.LmgNative
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types

/**
 * Восстановлено из `defpackage.C10943e` — поток "silent authorization".
 *
 * Связка с нативным слоем (liblmg.so, x02):
 *  1. Дёргаем `apps.get(app_id=51931326)` — VK возвращает код подтверждения.
 *  2. Код прогоняем через `LmgNative.x02(code)`:
 *     base64( code XOR "THETRUTHLIES" ) — обфускация транспорта кода.
 *  3. Результат уходит как параметр "code" в запрос silent-авторизации.
 */
class AppsGetSilentAuth(
    private val client: VkApiClient,
) {
    /** Возвращает singletonMap("code", obfuscated) как в оригинале. */
    suspend fun getSilentAuthCode(): Map<String, String> {
        val method = VkMethod("apps.get", AppsGetParser).apply {
            param("app_id", SILENT_AUTH_APP_ID)
        }

        val code: String = when (val result = client.execute(method)) {
            is VkResult.Success -> result.data.webviewUrl ?: ""
            is VkResult.Error -> ""
        }

        // Нативный вызов: BundleNativeClass.ad[0] = base64(xor(code, "THETRUTHLIES"))
        val bundle = LmgNative.getSilentAuthorizationEnvironment(code)
        val obfuscated = bundle.ad[0] as? String
            ?: error("idx 0 is empty") // как в оригинале: "idx 0 ..." проверки

        return mapOf("code" to obfuscated)
    }

    data class AppsGetResponse(val webviewUrl: String?)

    @JsonClass(generateAdapter = true)
    data class AppItem(
        @Json(name = "webview_url") val webviewUrl: String? = null,
    )

    private object AppsGetParser : VkResponseParser<AppsGetResponse> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<VkItems<AppItem>>(
                Types.newParameterizedType(VkItems::class.java, AppItem::class.java),
            ),
        ) { response -> AppsGetResponse(response.items.firstOrNull()?.webviewUrl) }

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<AppsGetResponse> {
            return delegate.parse(raw)
        }
    }

    companion object {
        /** app_id для silent auth (хардкод в оригинале). */
        const val SILENT_AUTH_APP_ID = 51931326L
    }
}
