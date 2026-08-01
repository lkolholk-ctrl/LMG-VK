package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Из `AudioStreamMix.TitlesJsonAdapter` (common_state, playing_state). */
@JsonClass(generateAdapter = true)
data class Titles(
    @Json(name = "common_state") val commonState: String? = null,
    @Json(name = "playing_state") val playingState: String? = null,
)
