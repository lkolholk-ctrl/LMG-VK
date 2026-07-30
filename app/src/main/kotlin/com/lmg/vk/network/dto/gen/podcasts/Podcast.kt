package com.lmg.vk.network.dto.gen.podcasts

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/podcasts/PodcastJsonAdapter (6 json keys). */
@JsonClass(generateAdapter = true)
data class Podcast(
    @Json(name = "podcast_title") val podcastTitle: String? = null,
    @Json(name = "owner_id") val ownerId: Long = 0,
    val id: Int = 0,
    @Json(name = "playlist_id") val playlistId: Int = 0,
    val subtitle: String? = null,
    val thumbs: List<Any?>,
)
