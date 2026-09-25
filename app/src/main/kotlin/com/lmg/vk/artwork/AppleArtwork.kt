package com.lmg.vk.artwork

import kotlinx.serialization.json.*
import java.io.IOException
import java.net.URI
import kotlin.math.abs

/** One verified catalog result supplies both the still cover and motion. */
internal data class AppleArtwork(val cover: String?, val motion: MotionArtwork?) {
    companion object {
        fun parse(body: String, query: ArtworkQuery): AppleArtwork? {
            val root = Json.parseToJsonElement(body).jsonObject
            val error = root["error"]?.jsonPrimitive?.contentOrNull
            if (error == "not_found") return null
            if (error != null) throw IOException("Artwork API error: $error")
            if (!matches(root, query)) throw IOException("Artwork catalog identity mismatch")
            val cover = root["static_artwork"]?.jsonPrimitive?.contentOrNull?.takeIf(::isHttps)
            return AppleArtwork(cover, MotionArtwork.parse(body, query))
        }

        fun matches(root: JsonObject, query: ArtworkQuery): Boolean {
            val expected = ItunesArtworkMatcher.lookupQuery(query)
            val actual = ItunesArtworkMatcher.lookupQuery(ArtworkQuery(
                root["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                root["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(), query.durationMs))
            if (expected.title != actual.title || ItunesArtworkMatcher.matchArtists(
                    ItunesArtworkMatcher.artists(expected.artist),
                    ItunesArtworkMatcher.artists(actual.artist)) == null) return false
            val duration = root["duration_ms"]?.jsonPrimitive?.longOrNull
            return duration == null || duration <= 0 || query.durationMs <= 0 ||
                abs(duration - query.durationMs) <= 6_000
        }

        private fun isHttps(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }
}
