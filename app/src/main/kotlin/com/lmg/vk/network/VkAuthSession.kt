package com.lmg.vk.network

import kotlinx.serialization.Serializable

/**
 * Восстановлено из `defpackage.C18479e` (@Serializable).
 *
 * Авторизационная сессия VK (`VkAccount`, восстановлено из `C18479e`).
 * Объект по умолчанию (`EMPTY`) используется до логина.
 *
 * Поля восстановлены по потреблению в `C8221e`:
 * Порядок и назначение всех 11 полей подтверждены `toString()` оригинального
 * класса и сериализатором `C3940e`.
 */
@Serializable
data class VkAuthSession(
    val userId: Long = 0L,
    val accessToken: String = "",
    val expiresAt: Long = 0L,
    val trustedHash: String = "",
    val exchangeToken: String = "",
    val metadataExpiresAt: Long = 0L,
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val avatar: String = "",
    val usesLatestApi: Boolean = true,
) {
    val isExpired: Boolean
        get() = expiresAt != 0L && expiresAt <= System.currentTimeMillis() / 1000

    companion object {
        val EMPTY = VkAuthSession()
    }
}
