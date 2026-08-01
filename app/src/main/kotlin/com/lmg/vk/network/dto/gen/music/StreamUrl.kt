package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Из `AudioPlaylistSnippetEntry.StreamUrlJsonAdapter` (url, clip_from, clip_to). */
@JsonClass(generateAdapter = true)
data class StreamUrl(
    val url: String? = null,
    @Json(name = "clip_from") val clipFrom: Int? = null,
    @Json(name = "clip_to") val clipTo: Int? = null,
)
