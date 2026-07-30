package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioContentCardJsonAdapter (7 json keys). */
@JsonClass(generateAdapter = true)
data class AudioContentCard(
    @Json(name = "editor_annotation") val editorAnnotation: String? = null,
    @Json(name = "editor_background_image") val editorBackgroundImage: List<Any?>,
    @Json(name = "editor_gradient_image") val editorGradientImage: List<Any?>,
    @Json(name = "editor_tag") val editorTag: String? = null,
    @Json(name = "entity_id") val entityId: String? = null,
    @Json(name = "entity_owner_id") val entityOwnerId: String? = null,
    @Json(name = "entity_type") val entityType: EnumC15939e? = null,
)
