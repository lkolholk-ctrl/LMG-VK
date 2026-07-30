package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioFollowingsUpdateInfoJsonAdapter (3 json keys). */
@JsonClass(generateAdapter = true)
data class AudioFollowingsUpdateInfo(
    val id: Long = 0,
    val title: String? = null,
    val covers: List<Any?>,
)
