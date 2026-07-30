package com.lmg.vk.network

import com.squareup.moshi.JsonClass
import kotlin.time.Duration.Companion.seconds

/**
 * Восстановлено из `defpackage.C18301e` (корутина refresh) +
 * `ua.lmg.vkapi2.methods.auth.RefreshToken$RTToken`.
 *
 * Протокол обновления токена VK:
 *   POST auth.refreshTokens
 *     client_id=2274003
 *     client_secret=hHbZxrka2uZ6jB1inYsH        (прямым текстом в коде!)
 *     exchange_tokens=<session.exchangeToken>
 *     active_index=0
 *     scope=all
 *     initiator=expired_token
 * Ответ: список токенов; берём первый, обновляем сессию (token + now+expiresIn).
 */
class VkAuthApi(
    private val client: VkApiClient,
    private val sessionStore: VkSessionStore,
) {
    @JsonClass(generateAdapter = true)
    data class RTToken(val token: String)

    /** Ответ auth.refreshTokens: список выданных токенов (берём первый). */
    data class RefreshedToken(
        val accessToken: String,
        val expiresInSeconds: Long,
    )

    suspend fun refreshTokens(): Boolean {
        val session = sessionStore.session

        val method = VkMethod("auth.refreshTokens", RefreshTokensParser).apply {
            param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID.toLong())
            param("client_secret", "hHbZxrka2uZ6jB1inYsH")
            param("exchange_tokens", listOf(session.exchangeToken).joinToString(","))
            param("active_index", 0)
            param("scope", "all")
            param("initiator", "expired_token")
        }

        return when (val result = client.execute(method)) {
            is VkResult.Success -> {
                val token = result.data.firstOrNull() ?: return false
                sessionStore.session = session.copy(
                    accessToken = token.accessToken,
                    expiresAt = (System.currentTimeMillis() / 1000) +
                        token.expiresInSeconds.seconds.inWholeSeconds,
                )
                true
            }
            is VkResult.Error -> false
        }
    }

    private object RefreshTokensParser : VkResponseParser<List<RefreshedToken>> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<RefreshedToken>> {
            TODO("Moshi: VKResponse<List<RefreshTokenItem>> -> map { RefreshedToken(it.token, it.expires_in) }")
        }
    }
}
