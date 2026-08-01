package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.lmg.vk.network.dto.music.AlbumThumb
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioWidgetItemJsonAdapter (5 json keys). */
@JsonClass(generateAdapter = true)
data class AudioWidgetItem(
    val photo: AlbumThumb? = null,
    val title: String? = null,
    val type: String? = null,
    val subtitle: String? = null,
    val url: String? = null,
)
