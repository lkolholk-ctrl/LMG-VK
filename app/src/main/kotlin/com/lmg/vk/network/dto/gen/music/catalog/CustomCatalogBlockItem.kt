package com.lmg.vk.network.dto.gen.music.catalog

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/catalog/CustomCatalogBlockItemJsonAdapter (6 json keys). */
@JsonClass(generateAdapter = true)
data class CustomCatalogBlockItem(
    val title: String? = null,
    val subtitle: String? = null,
    val image: List<Any?>,
    val url: String? = null,
    val id: String? = null,
    val meta: CustomCatalogBlockItemMeta? = null,
)
