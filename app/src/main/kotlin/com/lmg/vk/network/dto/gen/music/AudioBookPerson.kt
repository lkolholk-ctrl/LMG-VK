package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioBookPersonJsonAdapter (5 json keys). */
@JsonClass(generateAdapter = true)
data class AudioBookPerson(
    val description: String? = null,
    val id: Int? = null,
    val name: String? = null,
    val photo: List<Any?>,
    val roles: List<Any?>,
)
