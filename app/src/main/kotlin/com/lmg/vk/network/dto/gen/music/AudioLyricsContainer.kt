package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.lmg.vk.network.dto.music.AudioLyrics
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioLyricsContainerJsonAdapter (3 json keys). */
@JsonClass(generateAdapter = true)
data class AudioLyricsContainer(
    val md5: String? = null,
    val lyrics: AudioLyrics? = null,
    val credits: String? = null,
)
