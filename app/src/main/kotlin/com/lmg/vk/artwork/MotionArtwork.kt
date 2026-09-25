package com.lmg.vk.artwork

import com.lmg.vk.engine.lyrics.apple.AppleLyricsConfig
import kotlinx.serialization.json.*
import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class MotionArtwork(
    val mp4: String?,
    val hls: String?,
    val preview: String?,
    val tall: Boolean,
    val background: Long,
) {
    val playbackUrl: String get() = requireNotNull(hls ?: mp4)
    companion object {
        fun requestUrl(query: ArtworkQuery): okhttp3.HttpUrl {
            val canonical = ItunesArtworkMatcher.lookupQuery(query)
            val albumClean = ItunesArtworkMatcher.normalize(ItunesArtworkMatcher.cleanMetadata(query.album))
            return "${AppleLyricsConfig.PROXY_BASE_URL}/v2/motion".toHttpUrl().newBuilder()
                .addQueryParameter("title", canonical.title)
                .addQueryParameter("artist", canonical.artist)
                .addQueryParameter("include_mp4", "0")
                .apply { if (query.durationMs > 0) addQueryParameter("duration", (query.durationMs / 1000.0).toString()) }
                .apply { albumClean.takeIf(String::isNotBlank)?.let { addQueryParameter("album", it) } }
                .build()
        }

        fun parse(body: String, request: ArtworkQuery): MotionArtwork? {
            val root = Json.parseToJsonElement(body).jsonObject
            if (root["has_motion"]?.jsonPrimitive?.booleanOrNull != true) return null
            if (!AppleArtwork.matches(root, request)) return null
            val color = (root["colors"] as? JsonObject)?.get("bg")?.jsonPrimitive?.contentOrNull
                ?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16) ?: 0x202020L
            for (kind in listOf("tall", "square")) {
                val asset = root[kind] as? JsonObject ?: continue
                fun url(key: String) = asset[key]?.jsonPrimitive?.contentOrNull?.takeIf(::isHttps)
                val mp4 = url("mp4")
                val hls = url("m3u8")
                if (mp4 != null || hls != null) {
                    return MotionArtwork(mp4, hls, url("preview"), kind == "tall", 0xFF000000L or color)
                }
            }
            return null
        }

        private fun isHttps(value: String) = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)

    }
}
