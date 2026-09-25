package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class MotionArtworkLifetimeTest {
    @Test
    fun outgoingFrameSurvivesUntilEverySurfaceHasFaded() {
        val lifetime = MotionArtworkLifetime<String>()
        lifetime.attach("player")
        lifetime.attach("airSheet")
        assertTrue(lifetime.canPlay)
        lifetime.retire()
        assertFalse(lifetime.canPlay)
        assertFalse(lifetime.shouldRelease)
        lifetime.detach("player")
        assertFalse(lifetime.shouldRelease)
        lifetime.detach("airSheet")
        assertTrue(lifetime.shouldRelease)
    }

    @Test
    fun closingFullPlayerKeepsCurrentMotionPlaying() {
        val lifetime = MotionArtworkLifetime<String>()
        lifetime.attach("player")
        lifetime.detach("player")
        assertTrue(lifetime.canPlay)
        assertFalse(lifetime.shouldRelease)
        assertTrue(lifetime.attach("playerReopened"))
        assertTrue(lifetime.canPlay)
    }

    @Test
    fun quickSkipsReleaseEachOutgoingTrackIndependently() {
        val first = MotionArtworkLifetime<String>()
        val second = MotionArtworkLifetime<String>()
        val third = MotionArtworkLifetime<String>()
        first.attach("player")
        first.retire()
        second.attach("player")
        second.retire()
        third.attach("player")
        second.detach("player")
        assertTrue(second.shouldRelease)
        assertFalse(first.shouldRelease)
        assertTrue(third.canPlay)
        first.detach("player")
        assertTrue(first.shouldRelease)
    }

    @Test
    fun lateCallbacksAndRepeatedDetachCannotReleaseAnotherSurface() {
        val lifetime = MotionArtworkLifetime<String>()
        assertTrue(lifetime.attach("player"))
        assertFalse(lifetime.attach("player"))
        lifetime.attach("airSheet")
        lifetime.retire()
        lifetime.detach("player")
        lifetime.detach("player")
        assertFalse(lifetime.contains("player"))
        assertFalse(lifetime.shouldRelease)
        lifetime.detach("airSheet")
        assertTrue(lifetime.shouldRelease)
        lifetime.close()
        assertFalse(lifetime.attach("lateView"))
        assertFalse(lifetime.contains("airSheet"))
        assertFalse(lifetime.shouldRelease)
    }

    @Test
    fun skippedTrackWithoutSurfacesCanBeReleasedImmediately() {
        val lifetime = MotionArtworkLifetime<String>()
        lifetime.retire()
        assertTrue(lifetime.shouldRelease)
        assertFalse(lifetime.canPlay)
    }

    @Test
    fun currentMotionStartsBeforeAnyScreenAttaches() {
        val lifetime = MotionArtworkLifetime<String>()
        assertTrue(lifetime.canPlay)
        assertFalse(lifetime.shouldRelease)
        lifetime.attach("fullPlayer")
        assertTrue(lifetime.canPlay)
    }

    @Test
    fun repeatedLyricsAndQueueVisitsKeepMotionRunningWithoutOutputs() {
        val lifetime = MotionArtworkLifetime<String>()
        repeat(4) {
            lifetime.attach("fullPlayer")
            lifetime.detach("fullPlayer")
            assertTrue(lifetime.canPlay)
            assertFalse(lifetime.shouldRelease)
            lifetime.attach("airSheet")
            lifetime.detach("airSheet")
            assertTrue(lifetime.canPlay)
            lifetime.attach("fullPlayerReturned")
            assertTrue(lifetime.canPlay)
            lifetime.detach("fullPlayerReturned")
        }
        lifetime.retire()
        assertFalse(lifetime.canPlay)
        assertTrue(lifetime.shouldRelease)
    }

    @Test
    fun closedMotionCannotRestartWhenAViewReturns() {
        val lifetime = MotionArtworkLifetime<String>()
        lifetime.attach("fullPlayer")
        lifetime.detach("fullPlayer")
        lifetime.close()
        assertFalse(lifetime.canPlay)
        assertFalse(lifetime.attach("fullPlayerReturned"))
        assertFalse(lifetime.canPlay)
    }

}
