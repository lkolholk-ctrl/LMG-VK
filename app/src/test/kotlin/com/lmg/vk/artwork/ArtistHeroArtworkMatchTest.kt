package com.lmg.vk.artwork

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistHeroArtworkMatchTest {
    private val portrait = IntArray(1024) { i ->
        val v = ((i * 7 + i / 32 * 19) % 220) + 15
        (255 shl 24) or (v shl 16) or (v shl 8) or v
    }

    @Test fun acceptsSameArtworkWithCompressionDifferences() {
        val compressed = portrait.map { it + 0x030303 }.toIntArray()
        assertTrue(sameArtistArtwork(portrait, compressed))
    }

    @Test fun rejectsDifferentCoverAndBlankPlaceholders() {
        assertFalse(sameArtistArtwork(portrait, portrait.reversedArray()))
        assertFalse(sameArtistArtwork(IntArray(1024), IntArray(1024)))
        assertFalse(sameArtistArtwork(portrait, IntArray(1024) { -1 }))
    }
}
