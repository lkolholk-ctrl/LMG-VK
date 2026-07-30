package com.lmg.vk.network.dto.gen.newsfeed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/newsfeed/NewsfeedItemJsonAdapter (16 json keys). */
@JsonClass(generateAdapter = true)
data class NewsfeedItem(
    val type: String? = null,
    @Json(name = "post_type") val postType: String? = null,
    @Json(name = "source_id") val sourceId: Int? = null,
    @Json(name = "from_id") val fromId: Int? = null,
    val title: String? = null,
    val text: String? = null,
    val date: Long? = null,
    val button: Catalog2Button? = null,
    @Json(name = "post_id") val postId: Int? = null,
    val audio: NewsfeedAudios? = null,
    val audios: List<Any?>,
    @Json(name = "audio_playlist") val audioPlaylist: NewsfeedPlaylists? = null,
    @Json(name = "copy_history") val copyHistory: List<Any?>,
    val attachments: List<Any?>,
    val caption: NewsfeedCaption? = null,
    @Json(name = "marked_as_ads") val markedAsAds: Int? = null,
)
