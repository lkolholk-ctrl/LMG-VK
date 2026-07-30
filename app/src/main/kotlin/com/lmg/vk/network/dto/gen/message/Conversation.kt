package com.lmg.vk.network.dto.gen.message

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/message/ConversationJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class Conversation(
    val peer: ConversationPeer? = null,
    @Json(name = "chat_settings") val chatSettings: ConversationChatSettings? = null,
)
