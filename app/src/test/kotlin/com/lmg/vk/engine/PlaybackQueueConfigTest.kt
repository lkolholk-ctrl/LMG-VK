package com.lmg.vk.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueConfigTest {
    @Test
    fun regularMusicQueuesAreFinite() {
        val config = PlaybackQueueConfig.MUSIC_CONFIG

        assertTrue(config.loadingAvailable)
        assertTrue(config.shuffleEnabled)
        assertTrue(config.loopEnabled)
        assertFalse(config.endlessListeningEnabled)
        assertFalse(config.loadMoreIfEndOfQueue)
        assertFalse(config.loadTracksDirectly)
    }

    @Test
    fun vkMixLoadsDirectlyAndContinuesAtQueueEnd() {
        val config = PlaybackQueueConfig.VK_MIX_CONFIG

        assertTrue(config.loadingAvailable)
        assertTrue(config.prefetchEnabled)
        assertTrue(config.endlessListeningEnabled)
        assertTrue(config.loadMoreIfEndOfQueue)
        assertTrue(config.loadTracksDirectly)
    }

    @Test
    fun musicWithoutSourceDoesNotInventAContinuation() {
        val config = PlaybackQueueConfig.MUSIC_WITHOUT_SOURCE_CONFIG

        assertFalse(config.loadingAvailable)
        assertTrue(config.shuffleEnabled)
        assertTrue(config.loopEnabled)
        assertFalse(config.endlessListeningEnabled)
        assertFalse(config.loadMoreIfEndOfQueue)
    }
}
