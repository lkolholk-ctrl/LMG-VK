package com.lmg.vk.network.dto.gen.message

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/message/ConversationChatPhotoJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class ConversationChatPhoto(
    @Json(name = "photo_100") val photo100: String? = null,
    @Json(name = "photo_200") val photo200: String? = null,
)
