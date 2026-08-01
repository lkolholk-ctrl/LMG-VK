package com.lmg.vk.network.dto.gen.newsfeed

import com.squareup.moshi.Json
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/newsfeed/NewsfeedAttachmentJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class NewsfeedAttachment(
    val audio: AudioTrack? = null,
    @Json(name = "audio_playlist") val audioPlaylist: AudioPlaylist? = null,
)
