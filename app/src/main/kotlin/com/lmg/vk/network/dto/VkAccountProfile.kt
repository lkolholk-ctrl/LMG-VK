package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Профиль пользователя из `users.get`.
 *
 * Базовые поля `id`, `first_name`, `last_name`, `photo_base`, `name`,
 * `is_followed` и `can_follow` подтверждены восстановленным
 * `ua.itaysonlab.vkapi2.objects.users.VKProfile`.
 */
@JsonClass(generateAdapter = true)
data class VkAccountProfile(
    val id: Long = 0L,
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String = "",
    @Json(name = "photo_base") val photoBase: String = "",
    val name: String = "",
    @Json(name = "is_followed") val isFollowed: Boolean? = null,
    @Json(name = "can_follow") val canFollow: Boolean? = null,
    @Json(name = "photo_100") val photo100: String = "",
    @Json(name = "photo_200") val photo200: String = "",
    @Json(name = "photo_200_orig") val photo200Orig: String = "",
    @Json(name = "photo_400_orig") val photo400Orig: String = "",
    @Json(name = "photo_max_orig") val photoMaxOrig: String = "",
    val domain: String = "",
) {
    val displayName: String
        get() = name.ifBlank {
            listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
        }.ifBlank { domain }

    /** Самая качественная доступная фотография профиля с безопасными fallback. */
    val bestPhotoUrl: String
        get() = sequenceOf(photoMaxOrig, photo400Orig, photo200Orig, photo200, photo100, photoBase)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
}
