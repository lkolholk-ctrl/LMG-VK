package com.lmg.vk.ui.lyrics

import android.content.Context
import android.net.Uri
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.lyrics.LyricsSourceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

private data class LyricsPrefetchRequest(val uri: Uri, val key: LoadedLyricsKey)

/** Lives with AudioService, so lookup starts even while the application UI is closed. */
internal fun startLyricsPrefetch(context: Context, scope: CoroutineScope) = scope.launch {
    val app = context.applicationContext
    combine(PlayerController.currentTrack, PlayerController.durationMs) { track, duration ->
        track?.takeIf { it.title.isNotBlank() }?.let {
            LyricsPrefetchRequest(it.uri, LoadedLyricsKey(
                it.id.ifBlank { "${it.uri}|${it.title}|${it.artist}" }, it.title, it.artist,
                it.durationMs.takeIf { value -> value > 0 } ?: duration.coerceAtLeast(0),
                null, LyricsSourceStore.enabled(app).toSet(), Locale.getDefault().toLanguageTag(),
            ))
        }
    }.distinctUntilChanged().collectLatest { request ->
        if (request == null) return@collectLatest
        try {
            DebugLog.add("lyrics prefetch start track=${request.key.trackId}")
            val lyrics = LoadedLyricsStore.load(app, request.uri, request.key)
            DebugLog.add("lyrics prefetch ready track=${request.key.trackId} lines=${lyrics.synced.lines.size}")
        } catch (cancelled: CancellationException) {
            LoadedLyricsStore.cancelPending(request.key)
            throw cancelled
        } catch (error: Exception) {
            DebugLog.add("lyrics prefetch failed track=${request.key.trackId}: ${error.javaClass.simpleName}")
        }
    }
}
