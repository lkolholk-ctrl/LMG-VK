package com.lmg.vk.ui.navigation

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class WindowCloseStateTest {
    private class FrameClock : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            time += 16_666_667L
            return onFrame(time)
        }
    }

    @Test fun repeatedBackClosesOnlyOnce() = runBlocking(FrameClock()) {
        var closes = 0
        val state = WindowCloseState(this).apply { onBack = { closes++ } }
        val first = launch { state.complete() }
        yield()
        state.complete()
        first.join()
        assertEquals(1, closes)
        assertEquals(1f, state.exit.floatValue, .001f)
    }

    @Test fun navigationWaitsForTheShortExit() = runBlocking(FrameClock()) {
        var closes = 0
        val state = WindowCloseState(this).apply { onBack = { closes++ } }
        val close = launch { state.complete() }
        yield()
        assertEquals(0, closes)
        assertTrue(state.completing)
        close.join()
        assertEquals(1, closes)
    }

    @Test fun reopenedWindowCannotBeClosedByStaleAnimation() = runBlocking(FrameClock()) {
        var closes = 0
        val state = WindowCloseState(this).apply { onBack = { closes++ } }
        val close = launch { state.complete() }
        yield()
        state.reset()
        close.join()
        assertEquals(0, closes)
        assertEquals(0f, state.exit.floatValue, .001f)
        assertFalse(state.completing)
        state.complete()
        assertEquals(1, closes)
    }

    @Test fun cancelledAnimationRestoresWindowAndDoesNotNavigate() = runBlocking(FrameClock()) {
        var closes = 0
        val state = WindowCloseState(this).apply { onBack = { closes++ } }
        val close = launch { state.complete() }
        yield()
        close.cancel()
        close.join()
        assertEquals(0, closes)
        assertFalse(state.completing)
        assertEquals(0f, state.exit.floatValue, .001f)
    }

    @Test fun airSheetStartsDustImmediatelyWithoutFadingItsContent() = runBlocking(FrameClock()) {
        var closes = 0
        val state = WindowCloseState(this).apply {
            fadeOnCommit = false
            onBack = { closes++ }
        }
        state.complete()
        assertEquals(1, closes)
        assertEquals(0f, state.exit.floatValue, .001f)
    }
}
