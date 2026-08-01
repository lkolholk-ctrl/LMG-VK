package com.lmg.vk.network.lastfm

import com.lmg.vk.network.RecoveredServiceConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import java.security.MessageDigest
import java.util.SortedMap
import java.util.TreeMap

/**
 * Восстановленный Last.fm transport из AbstractC8561e/C7914e/C13077e.
 * Session key передаётся вызывающей стороной и не смешивается с VK-сессией.
 */
class LastFmScrobbler(
    private val httpClient: HttpClient,
) {
    suspend fun updateNowPlaying(
        sessionKey: String,
        artist: String,
        track: String,
        album: String? = null,
        durationSeconds: Long? = null,
    ): Boolean = postSigned(
        sortedMapOf(
            "method" to "track.updateNowPlaying",
            "api_key" to RecoveredServiceConfig.LAST_FM_API_KEY,
            "sk" to sessionKey,
            "artist" to artist,
            "track" to track,
        ).apply {
            album?.let { put("album", it) }
            durationSeconds?.takeIf { it >= 0 }?.let { put("duration", it.toString()) }
        },
    )

    suspend fun scrobble(
        sessionKey: String,
        artist: String,
        track: String,
        album: String? = null,
        durationSeconds: Long? = null,
        startedAtMs: Long = System.currentTimeMillis(),
    ): Boolean = postSigned(
        sortedMapOf(
            "method" to "track.scrobble",
            "api_key" to RecoveredServiceConfig.LAST_FM_API_KEY,
            "sk" to sessionKey,
            "artist[0]" to artist,
            "track[0]" to track,
            "timestamp[0]" to ((startedAtMs.takeIf { it != 0L }
                ?: System.currentTimeMillis()) / 1000L).toString(),
        ).apply {
            album?.let { put("album[0]", it) }
            // В оригинальном APK ключ именно `duration`, без `[0]`.
            durationSeconds?.takeIf { it >= 0 }?.let { put("duration", it.toString()) }
        },
    )

    private suspend fun postSigned(unsigned: SortedMap<String, String>): Boolean {
        val params = TreeMap(unsigned).apply {
            put("api_sig", signature(unsigned))
            put("format", "json")
        }
        val response = httpClient.post(RecoveredServiceConfig.LAST_FM_ENDPOINT) {
            setBody(FormDataContent(Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            }))
        }
        return response.status.value in 200..299
    }

    /** Last.fm signature: sorted key+value pairs + shared secret, MD5 lower-hex. */
    internal fun signature(params: Map<String, String>): String {
        val source = buildString {
            params.toSortedMap().forEach { (key, value) -> append(key).append(value) }
            append(RecoveredServiceConfig.LAST_FM_SHARED_SECRET)
        }
        return MessageDigest.getInstance("MD5")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
