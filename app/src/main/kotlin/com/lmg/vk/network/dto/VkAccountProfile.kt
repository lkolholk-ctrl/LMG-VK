package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Минимальный профиль текущего пользователя из `users.get`. */
@JsonClass(generateAdapter = true)
data class VkAccountProfile(
    val id: Long = 0L,
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String = "",
    @Json(name = "photo_100") val photo100: String = "",
    @Json(name = "photo_200") val photo200: String = "",
    @Json(name = "photo_200_orig") val photo200Orig: String = "",
    @Json(name = "photo_400_orig") val photo400Orig: String = "",
    @Json(name = "photo_max_orig") val photoMaxOrig: String = "",
    val domain: String = "",
) {
    /** Самая качественная доступная фотография профиля с безопасными fallback. */
    val bestPhotoUrl: String
        get() = sequenceOf(photoMaxOrig, photo400Orig, photo200Orig, photo200, photo100)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
}
