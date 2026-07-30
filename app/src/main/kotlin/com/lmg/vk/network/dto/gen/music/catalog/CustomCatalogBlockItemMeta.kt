package com.lmg.vk.network.dto.gen.music.catalog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/catalog/CustomCatalogBlockItemMetaJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class CustomCatalogBlockItemMeta(
    val icon: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
)
