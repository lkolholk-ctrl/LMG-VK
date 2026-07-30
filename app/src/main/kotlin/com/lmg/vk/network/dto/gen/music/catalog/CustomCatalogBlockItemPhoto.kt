package com.lmg.vk.network.dto.gen.music.catalog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/catalog/CustomCatalogBlockItemPhotoJsonAdapter (4 json keys). */
@JsonClass(generateAdapter = true)
data class CustomCatalogBlockItemPhoto(
    val height: Int = 0,
    val url: Int = 0,
    val width: String? = null,
    val id: String? = null,
)
