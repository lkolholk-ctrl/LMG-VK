package com.lmg.vk.ui.lyrics

import android.content.Context
import android.net.Uri
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.engine.lyrics.LyricsRepository
import com.lmg.vk.engine.lyrics.LyricsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal data class LoadedLyricsKey(
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val embedded: String?,
    val sources: Set<LyricsSource>,
    val locale: String,
)

internal object LoadedLyricsStore {
    private val cache = RetainedLyricsCache<LoadedLyricsKey, AccompanistLyrics>(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        maxEntries = 8,
        maxWeight = 200_000,
        weightOf = { lyrics -> lyrics.synced.lines.sumOf { 1 + it.lyricText().length } },
        // Reopening immediately reuses a completed empty lookup; transient misses expire.
        expiresAfterMs = { if (it.synced.lines.isEmpty()) 30_000L else Long.MAX_VALUE },
    )

    fun peek(key: LoadedLyricsKey) = cache.peek(key)
    suspend fun load(key: LoadedLyricsKey, loader: suspend () -> AccompanistLyrics) =
        cache.getOrPrepare(key, loader)
    suspend fun load(context: Context, uri: Uri?, key: LoadedLyricsKey) = load(key) {
        val embedded = key.embedded
        val content = if (!embedded.isNullOrBlank()) {
            val trimmed = embedded.trimStart()
            if (trimmed.startsWith("<tt", true) ||
                (trimmed.startsWith("<?xml", true) && trimmed.contains("<tt", true))) {
                LyricsContent.RawTtml(embedded, "embedded", "Embedded")
            } else LyricsContent.Legacy(LyricsParser.parseLyrics(embedded))
        } else {
            LyricsRepository.load(context.applicationContext, uri, key.title, key.artist,
                key.durationMs, key.trackId, enabledSources = key.sources)
        }
        val trace = com.lmg.vk.debug.PlayerStartupTrace.begin("lyrics_parse")
        try { AccompanistLyricsAdapter.convert(content, key.durationMs) }
        finally { trace?.end() }
    }
    fun cancelPending(key: LoadedLyricsKey) = cache.cancelPending(key)
    fun invalidate(key: LoadedLyricsKey) = cache.invalidate(key)
    fun clear() = cache.clear()
}
