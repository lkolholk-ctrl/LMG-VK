package com.lmg.vk.network.dto.gen.podcasts

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/podcasts/PodcastCatalogLongreadJsonAdapter (11 json keys). */
@JsonClass(generateAdapter = true)
data class PodcastCatalogLongread(
    val id: Int = 0,
    @Json(name = "owner_id") val ownerId: Long = 0,
    @Json(name = "owner_name") val ownerName: String? = null,
    val photo: PodcastCover? = null,
    @Json(name = "published_date") val publishedDate: Int = 0,
    val subtitle: String? = null,
    val title: String? = null,
    val url: String? = null,
    @Json(name = "view_url") val viewUrl: String? = null,
    val views: Int = 0,
    val shares: Int = 0,
)
