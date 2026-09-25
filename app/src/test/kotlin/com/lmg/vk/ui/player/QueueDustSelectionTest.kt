package com.lmg.vk.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDustSelectionTest {
    @Test fun skipsCurrentAndPlayedTracks() {
        assertEquals(setOf(3, 4), queueDustRemovalIndices(8, 2, (0..4).toList(), 8, false))
    }

    @Test fun skipsAutoplayHeadingAndMapsFollowingRows() {
        assertEquals(setOf(2, 3, 4), queueDustRemovalIndices(8, 1, (0..5).toList(), 3, true))
    }

    @Test fun emptyAutoplayHeadingDoesNotBecomeTrack() {
        assertEquals(emptySet<Int>(), queueDustRemovalIndices(3, 2, listOf(2, 3), 3, true))
    }

    @Test fun clearOnlyAnimatesVisibleRows() {
        assertEquals(setOf(10, 11), queueDustRemovalIndices(100, 0, listOf(10, 11), 100, false))
    }

    @Test fun ignoresInvalidPositionsAndHandlesNoCurrentTrack() {
        assertEquals(setOf(0, 1), queueDustRemovalIndices(2, -1, listOf(-1, 0, 1, 2), 2, false))
    }
}
