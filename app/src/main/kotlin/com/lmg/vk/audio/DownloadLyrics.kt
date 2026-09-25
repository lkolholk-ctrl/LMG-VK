package com.lmg.vk.audio

import android.content.Context
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.lyrics.apple.AppleTtmlCache
import com.lmg.vk.engine.lyrics.apple.DefaultAppleTtmlClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal object DownloadLyrics {
    private val client = DefaultAppleTtmlClient()
    suspend fun fetch(context: Context, track: Track): String? {
        EmbeddedLyrics.validTtml(AppleTtmlCache.read(context, track.title, track.artist, track.durationMs))?.let { return it }
        return try {
            val raw = withTimeoutOrNull(16_000) {
                client.fetch(track.title, track.artist, track.durationMs).getOrNull()
            }
            EmbeddedLyrics.validTtml(raw)?.also {
                AppleTtmlCache.write(context, track.title, track.artist, track.durationMs, rawTtml = it)
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { null }
    }
}
