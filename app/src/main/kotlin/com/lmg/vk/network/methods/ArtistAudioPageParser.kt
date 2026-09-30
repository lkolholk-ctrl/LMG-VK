package com.lmg.vk.network.methods

import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.VkItems
import com.lmg.vk.network.VkJson
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.dto.music.AudioTrack
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Types

/** Current VK returns count/items; older responses can still contain a bare array. */
internal object ArtistAudioPageParser : VkResponseParser<VkItems<AudioTrack>> {
    private val envelope = MoshiEnvelopeParser<Any>(Any::class.java)
    private val tracks = VkJson.moshi.adapter<List<AudioTrack>>(
        Types.newParameterizedType(List::class.java, AudioTrack::class.java),
    )

    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<VkItems<AudioTrack>> {
        val parsed = envelope.parse(raw)
        val page = parsed.data?.let { payload ->
            val count: Int?
            val items: List<*>
            when (payload) {
                is List<*> -> {
                    count = null
                    items = payload
                }
                is Map<*, *> -> {
                    // A malformed/error payload must never become a successful empty page.
                    items = payload["items"] as? List<*>
                        ?: throw JsonDataException("Artist audio response is missing items")
                    count = (payload["count"] as? Number)?.toInt()?.takeIf { it >= 0 }
                }
                else -> throw JsonDataException("Unexpected artist audio response")
            }
            VkItems(count = count, items = requireNotNull(tracks.fromJsonValue(items)))
        }
        return VkParsedResponse(page, parsed.error, parsed.executeErrors)
    }
}
