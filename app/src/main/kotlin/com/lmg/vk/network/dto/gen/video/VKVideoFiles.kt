package com.lmg.vk.network.dto.gen.video

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/video/VKVideoFilesJsonAdapter (8 json keys). */
@JsonClass(generateAdapter = true)
data class VKVideoFiles(
    @Json(name = "mp4_240") val mp4240: String? = null,
    @Json(name = "mp4_360") val mp4360: String? = null,
    @Json(name = "mp4_480") val mp4480: String? = null,
    @Json(name = "mp4_720") val mp4720: String? = null,
    @Json(name = "mp4_1080") val mp41080: String? = null,
    @Json(name = "mp4_1440") val mp41440: String? = null,
    @Json(name = "mp4_2160") val mp42160: String? = null,
    val hls: String? = null,
)
