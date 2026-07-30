package com.lmg.vk.network

import kotlinx.serialization.Serializable

/**
 * Восстановлено из `defpackage.C18479e` (@Serializable).
 *
 * Авторизационная сессия VK: хранит access/refresh токены и время истечения.
 * Объект по умолчанию (`EMPTY`) используется до логина.
 *
 * Поля восстановлены по потреблению в `C8221e`:
 *  - [accessToken]  — подставляется в "access_token" / "Authorization: Bearer"
 *  - [exchangeToken] — используется для метода "auth.getExchangeToken"
 *  - [expiresAt]    — при 0 сессия считается требующей проверки (см. getValidToken)
 */
@Serializable
data class VkAuthSession(
    val expiresAt: Long = 0L,
    val accessToken: String = "",
    val refreshToken: String = "",
    val exchangeToken: String = "",
    val userId: Long = 0L,
    val secret: String = "",
    val webviewToken: String = "",
) {
    val isExpired: Boolean
        get() = expiresAt != 0L && expiresAt <= System.currentTimeMillis() / 1000

    companion object {
        val EMPTY = VkAuthSession()
    }
}
