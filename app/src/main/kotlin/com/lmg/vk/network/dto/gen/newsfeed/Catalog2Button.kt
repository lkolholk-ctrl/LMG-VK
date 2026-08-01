package com.lmg.vk.network.dto.gen.newsfeed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Из `catalogkit/objects/Catalog2ButtonJsonAdapter`.
 * Используется в NewsfeedItem.button; поля разобраны по адаптеру.
 */
@JsonClass(generateAdapter = true)
data class Catalog2Button(
    val action: Any? = null,
    @Json(name = "section_id") val sectionId: String? = null,
    @Json(name = "owner_id") val ownerId: Long? = null,
    @Json(name = "block_id") val blockId: String? = null,
    @Json(name = "mix_id") val mixId: String? = null,
    @Json(name = "entity_id") val entityId: String? = null,
    val options: List<Any?> = emptyList(),
    val title: String? = null,
    val description: String? = null,
    @Json(name = "is_following") val isFollowing: Boolean? = null,
    @Json(name = "ref_layout_name") val refLayoutName: String? = null,
    @Json(name = "ref_items_count") val refItemsCount: Int? = null,
    @Json(name = "ref_data_type") val refDataType: String? = null,
    val images: List<Any?> = emptyList(),
    @Json(name = "foreground_images") val foregroundImages: List<Any?> = emptyList(),
)
