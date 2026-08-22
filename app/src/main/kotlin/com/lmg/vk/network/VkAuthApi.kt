package com.lmg.vk.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json

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
    /** Вложенный `access_token` из восстановленного AuthRefreshTokenDto. */
    @JsonClass(generateAdapter = true)
    data class RefreshAccessToken(
        val token: String,
        @Json(name = "expires_in") val expiresIn: Long,
    )

    @JsonClass(generateAdapter = true)
    data class RefreshTokenItem(
        val index: Int,
        @Json(name = "user_id") val userId: Long,
        val banned: Boolean,
        @Json(name = "access_token") val accessToken: RefreshAccessToken? = null,
    )

    @JsonClass(generateAdapter = true)
    data class RefreshTokensResponse(
        val success: List<RefreshTokenItem> = emptyList(),
        val errors: List<RefreshTokenError> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class RefreshTokenError(
        val index: Int,
        val code: Int,
        val description: String,
    )

    data class RefreshedToken(
        val accessToken: String,
        val expiresInSeconds: Long,
    )

    suspend fun refreshTokens(): Boolean {
        val session = sessionStore.session

        val method = VkMethod("auth.refreshTokens", RefreshTokensParser).apply {
            param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID.toLong())
            param("client_secret", RecoveredServiceConfig.VK_ANDROID_CLIENT_SECRET)
            param("exchange_tokens", listOf(session.exchangeToken).joinToString(","))
            param("active_index", 0)
            param("scope", "all")
            param("initiator", "expired_token")
            omitAppUserAgent = true
        }

        return when (val result = client.execute(method)) {
            is VkResult.Success -> {
                val token = result.data.firstOrNull() ?: return false
                // Account could be switched while refresh was in flight. Never
                // reactivate the old session with a late response.
                if (sessionStore.session.userId != session.userId) return false
                val nowSeconds = System.currentTimeMillis() / 1000
                sessionStore.session = session.copy(
                    accessToken = token.accessToken,
                    // Как и OAuth /token, refresh может вернуть expires_in=0.
                    // Храним 0 как "срок не указан", а не как "истёк сейчас".
                    expiresAt = token.expiresInSeconds
                        .takeIf { it > 0L }
                        ?.let { nowSeconds + it }
                        ?: 0L,
                )
                true
            }
            is VkResult.Error -> false
        }
    }

    private object RefreshTokensParser : VkResponseParser<List<RefreshedToken>> {
        private val delegate = MoshiEnvelopeParser<RefreshTokensResponse>(
            RefreshTokensResponse::class.java,
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<RefreshedToken>> {
            val parsed = delegate.parse(raw)
            return VkParsedResponse(
                data = parsed.data?.success.orEmpty().mapNotNull { item ->
                    item.accessToken?.let { RefreshedToken(it.token, it.expiresIn) }
                },
                error = parsed.error,
            )
        }
    }
}
