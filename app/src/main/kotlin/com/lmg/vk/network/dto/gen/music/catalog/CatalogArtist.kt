package com.lmg.vk.network.dto.gen.music.catalog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/catalog/CatalogArtistJsonAdapter (9 json keys). */
@JsonClass(generateAdapter = true)
data class CatalogArtist(
    val name: String? = null,
    val id: String? = null,
    val domain: String? = null,
    val photo: List<Any?>,
    val photos: List<Any?>,
    val genres: List<Any?>,
    @Json(name = "is_album_cover") val isAlbumCover: Boolean = false,
    @Json(name = "is_followed") val isFollowed: Boolean = false,
    @Json(name = "can_follow") val canFollow: Boolean = false,
)
