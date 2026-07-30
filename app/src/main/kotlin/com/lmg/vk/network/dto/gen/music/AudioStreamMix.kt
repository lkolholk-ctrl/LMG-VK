package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioStreamMixJsonAdapter (6 json keys). */
@JsonClass(generateAdapter = true)
data class AudioStreamMix(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    @Json(name = "stream_mix") val streamMix: Link? = null,
    @Json(name = "is_tunable") val isTunable: Boolean? = null,
    val titles: Titles? = null,
)
