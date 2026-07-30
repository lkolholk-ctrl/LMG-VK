package com.lmg.vk.network.dto.gen.message

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/message/ConversationAudioElementJsonAdapter (1 json keys). */
@JsonClass(generateAdapter = true)
data class ConversationAudioElement(
    val attachment: NewsfeedAttachment? = null,
)
