package com.lmg.vk.ui.lyrics

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Shared frame pump, independent of Android so audio timeline transitions can be tested. */
internal suspend fun followLyricsPlayback(
    readPosition: () -> Long,
    awaitFrame: suspend () -> Unit,
    publish: (Long) -> Unit,
) {
    publish(readPosition())
    while (currentCoroutineContext().isActive) {
        awaitFrame()
        publish(readPosition())
    }
}
