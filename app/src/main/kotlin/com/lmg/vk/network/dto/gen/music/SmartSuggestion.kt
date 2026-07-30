package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/SmartSuggestionJsonAdapter (5 json keys). */
@JsonClass(generateAdapter = true)
data class SmartSuggestion(
    val title: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val context: String? = null,
    val id: String? = null,
)
