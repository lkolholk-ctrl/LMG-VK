package com.lmg.vk.network.dto.gen.video

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/video/VKVideoJsonAdapter (12 json keys). */
@JsonClass(generateAdapter = true)
data class VKVideo(
    val id: Int = 0,
    @Json(name = "owner_id") val ownerId: Long? = null,
    val title: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val image: List<Any?>,
    @Json(name = "user_id") val userId: Long? = null,
    val files: VKVideoFiles? = null,
    @Json(name = "main_artists") val mainArtists: List<Any?>,
    val genres: List<Any?>,
    val duration: Int = 0,
    @Json(name = "direct_url") val directUrl: String? = null,
)
