package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class MotionBackdropLayoutTest {
    @Test
    fun portraitKeepsThreeByFourVideoAndDensityScaledOverlap() {
        val layout = MotionBackdropLayout.create(585, 1280, 1.5f)
        assertEquals(780f / 1280f, layout.videoFraction, 0.00001f)
        assertEquals(225f / 1280f, layout.overlapFraction, 0.00001f)
        assertEquals(80, layout.stripWidth)
        assertEquals(6, layout.stripHeight)
        assertEquals(6f / 49f, layout.stripFraction, 0.00001f)
    }

    @Test
    fun pixelDensityDoesNotShiftTheTransitionOnTheSameLogicalScreen() {
        val low = MotionBackdropLayout.create(390, 840, 1f)
        val high = MotionBackdropLayout.create(1170, 2520, 3f)
        assertEquals(low.videoFraction, high.videoFraction, 0.00001f)
        assertEquals(low.overlapFraction, high.overlapFraction, 0.00001f)
    }

    @Test
    fun tallerScreenExtendsTheBackdropWithoutStretchingVideo() {
        val normal = MotionBackdropLayout.create(1080, 1920, 3f)
        val tall = MotionBackdropLayout.create(1080, 2400, 3f)
        assertEquals(normal.videoFraction * 1920, tall.videoFraction * 2400, 0.001f)
        assertEquals(normal.overlapFraction * 1920, tall.overlapFraction * 2400, 0.001f)
        assertEquals(normal.stripHeight, tall.stripHeight)
    }

    @Test
    fun degenerateAndShortSurfacesKeepFiniteNonzeroParameters() {
        for ((width, height) in listOf(0 to 0, 1 to 1, 2400 to 1080, 100 to 200)) {
            val layout = MotionBackdropLayout.create(width, height, 3f)
            assertTrue(layout.videoFraction > 0f && layout.videoFraction <= 1f)
            assertTrue(layout.overlapFraction > 0f && layout.overlapFraction <= layout.videoFraction)
            assertTrue(layout.stripFraction > 0f && layout.stripFraction <= 1f)
            assertTrue(layout.stripWidth >= 2 && layout.stripHeight >= 2)
        }
    }
}
