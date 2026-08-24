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
        val index: Int,
        val userId: Long,
        val accessToken: String,
        val expiresInSeconds: Long,
    )

    suspend fun refreshTokens(): Boolean = refreshTokens(sessionStore.session) != null

    suspend fun refreshTokens(session: VkAuthSession): VkAuthSession? {
        return refreshTokens(listOf(session))[session.userId]
    }

    suspend fun refreshTokens(sessions: List<VkAuthSession>): Map<Long, VkAuthSession> {
        val eligible = sessions
            .filter { it.userId != 0L && it.exchangeToken.isNotBlank() }
            .distinctBy(VkAuthSession::userId)
        if (eligible.isEmpty()) return emptyMap()
        val activeUserId = sessionStore.session.userId
        val activeIndex = eligible.indexOfFirst { it.userId == activeUserId }.coerceAtLeast(0)
        val method = VkMethod("auth.refreshTokens", RefreshTokensParser).apply {
            param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID.toLong())
            param("client_secret", RecoveredServiceConfig.VK_ANDROID_CLIENT_SECRET)
            param("exchange_tokens", eligible.joinToString(",") { it.exchangeToken })
            param("active_index", activeIndex)
            param("scope", "all")
            param("initiator", "expired_token")
            userAgent = VkUserAgents.auth
        }

        return when (val result = client.execute(method)) {
            is VkResult.Success -> {
                val nowSeconds = System.currentTimeMillis() / 1000
                buildMap {
                    result.data.forEach { token ->
                        val expected = eligible.firstOrNull { it.userId == token.userId }
                            ?: eligible.getOrNull(token.index)
                            ?: return@forEach
                        val updated = sessionStore.updateSession(expected) { current ->
                            current.copy(
                                accessToken = token.accessToken,
                                expiresAt = token.expiresInSeconds
                                    .takeIf { it > 0L }
                                    ?.let { nowSeconds + it }
                                    ?: 0L,
                            )
                        }
                        if (updated != null) put(updated.userId, updated)
                    }
                }
            }
            is VkResult.Error -> emptyMap()
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
                    item.accessToken?.let {
                        RefreshedToken(item.index, item.userId, it.token, it.expiresIn)
                    }
                },
                error = parsed.error,
            )
        }
    }
}
