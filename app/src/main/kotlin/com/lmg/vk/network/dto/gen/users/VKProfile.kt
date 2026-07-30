package com.lmg.vk.network.dto.gen.users

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/users/VKProfileJsonAdapter (7 json keys). */
@JsonClass(generateAdapter = true)
data class VKProfile(
    val id: Long = 0,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "photo_base") val photoBase: String? = null,
    val name: String? = null,
    @Json(name = "is_followed") val isFollowed: Boolean? = null,
    @Json(name = "can_follow") val canFollow: Boolean? = null,
)
