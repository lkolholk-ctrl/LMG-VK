package com.lmg.vk.network.dto.gen.newsfeed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/newsfeed/NewsfeedPlaylistsJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class NewsfeedPlaylists(
    val count: Int = 0,
    val items: List<Any?>,
)
