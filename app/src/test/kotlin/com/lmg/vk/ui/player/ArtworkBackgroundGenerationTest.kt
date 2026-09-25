package com.lmg.vk.ui.player

import org.junit.Assert.*
import org.junit.Test

class ArtworkBackgroundGenerationTest {
    @Test
    fun changingStaticTracksKeepsExistingBlend() {
        val state = ArtworkBackgroundGeneration()
        val first = state.update("first", true)
        assertEquals(first, state.update("second", true))
        assertEquals(first, state.update("second", true))
    }

    @Test
    fun frozenBackgroundOfPreviousTrackIsDiscardedOnResume() {
        val state = ArtworkBackgroundGeneration()
        val first = state.update("first", true)
        assertEquals(first, state.update("first", false))
        val resumed = state.update("second", true)
        assertNotEquals(first, resumed)
        assertEquals(resumed, state.update("second", true))
    }

    @Test
    fun switchingTracksBehindMotionInvalidatesOldFrame() {
        val state = ArtworkBackgroundGeneration()
        val first = state.update("first", false)
        val next = state.update("second", false)
        assertNotEquals(first, next)
        assertEquals(next, state.update("second", true))
    }

    @Test
    fun openingLyricsOrQueueOnSameTrackKeepsTheBackground() {
        val state = ArtworkBackgroundGeneration()
        val first = state.update("track", false)
        assertEquals(first, state.update("track", false))
        assertEquals(first, state.update("track", true))
        assertEquals(first, state.update("track", false))
    }
}
