package com.lmg.vk.network.dto.gen.music.catalog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/catalog/CatalogArtistPhotosContainerJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class CatalogArtistPhotosContainer(
    val type: String? = null,
    val photo: List<Any?>,
)
