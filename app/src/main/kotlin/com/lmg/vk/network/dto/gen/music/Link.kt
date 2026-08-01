package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Из `AudioStreamMix.LinkJsonAdapter` (id, title). */
@JsonClass(generateAdapter = true)
data class Link(
    val id: String? = null,
    val title: String? = null,
)
