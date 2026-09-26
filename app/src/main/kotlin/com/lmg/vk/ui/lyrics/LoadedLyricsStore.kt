package com.lmg.vk.ui.lyrics

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
        // A transient empty provider response must not permanently hide available lyrics.
        isValid = { it.synced.lines.isNotEmpty() },
    )

    fun peek(key: LoadedLyricsKey) = cache.peek(key)
    suspend fun load(key: LoadedLyricsKey, loader: suspend () -> AccompanistLyrics) =
        cache.getOrPrepare(key, loader)
    fun invalidate(key: LoadedLyricsKey) = cache.invalidate(key)
    fun clear() = cache.clear()
}
