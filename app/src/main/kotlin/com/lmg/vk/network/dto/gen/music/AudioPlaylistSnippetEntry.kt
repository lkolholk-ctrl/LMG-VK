package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioPlaylistSnippetEntryJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class AudioPlaylistSnippetEntry(
    val track: AudioTrack? = null,
    @Json(name = "stream_url") val streamUrl: StreamUrl? = null,
)
