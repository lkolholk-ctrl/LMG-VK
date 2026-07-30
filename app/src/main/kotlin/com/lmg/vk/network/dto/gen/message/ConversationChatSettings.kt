package com.lmg.vk.network.dto.gen.message

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/message/ConversationChatSettingsJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class ConversationChatSettings(
    val title: String? = null,
    val photo: ConversationChatPhoto? = null,
)
