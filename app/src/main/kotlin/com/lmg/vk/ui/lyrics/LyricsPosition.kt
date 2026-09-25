package com.lmg.vk.ui.lyrics

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lmg.vk.engine.PlayerController

/** Samples the audio timeline; the lyrics renderer must never advance its own playback clock. */
@Composable
internal fun rememberLyricsPosition(
    trackKey: Any,
    positionMs: Long,
    playing: Boolean,
    offsetMs: Long = 0L,
    durationMs: Long = 0L,
): () -> Int {
    val clock = remember(trackKey) { mutableLongStateOf(PlayerController.getPlaybackPositionMs()) }
    val offset = rememberUpdatedState(offsetMs)
    val duration = rememberUpdatedState(durationMs)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(trackKey, positionMs, playing) {
        clock.longValue = PlayerController.getPlaybackPositionMs()
    }
    LaunchedEffect(trackKey) {
        PlayerController.positionDiscontinuity.collect {
            clock.longValue = PlayerController.getPlaybackPositionMs()
        }
    }
    LaunchedEffect(trackKey, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            followLyricsPlayback(
                readPosition = PlayerController::getPlaybackPositionMs,
                awaitFrame = { withFrameNanos { } },
                publish = { clock.longValue = it },
            )
        }
    }
    return remember(clock) {
        {
            val elapsed = if (duration.value > 0L) clock.longValue.coerceAtMost(duration.value) else clock.longValue
            (elapsed + offset.value).lyricMillis()
        }
    }
}
