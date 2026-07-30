package com.lmg.vk.network.dto.gen.message

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/message/ConversationPeerJsonAdapter (3 json keys). */
@JsonClass(generateAdapter = true)
data class ConversationPeer(
    val id: String? = null,
    val type: Long = 0,
    @Json(name = "local_id") val localId: Long = 0,
)
