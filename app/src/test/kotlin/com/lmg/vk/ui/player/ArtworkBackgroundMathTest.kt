package com.lmg.vk.ui.player

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ArtworkBackgroundMathTest {
    @Test fun threeLayersKeepTheirDirectionsAtTwentyPercentLowerSpeed() {
        assertEquals(-144f, artworkRotation(60_000, 0), 0.001f)
        assertEquals(144f, artworkRotation(45_000, 1), 0.001f)
        assertEquals(144f, artworkRotation(35_000, 2), 0.001f)
        for ((layer, period) in listOf(150_000L, 112_500L, 87_500L).withIndex()) {
            assertEquals(artworkRotation(42, layer), artworkRotation(period + 42, layer), 0f)
            assertEquals(0f, artworkRotation(-1, layer), 0f)
        }
    }

    @Test fun workingSurfaceIsSmallAndPreservesPortraitLandscapeGeometry() {
        assertEquals(59 to 104, backgroundSurfaceSize(1080, 1920, 480))
        assertEquals(104 to 59, backgroundSurfaceSize(1920, 1080, 480))
        val large = backgroundSurfaceSize(4320, 7680, 320)
        assertTrue(large.first <= 180 && large.second <= 180)
        assertEquals(4320f / 7680, large.first.toFloat() / large.second, 0.01f)
        assertEquals(1 to 1, backgroundSurfaceSize(0, 0, 480))
    }

    @Test fun gaussianPreservesUniformColorsIncludingEdgesAndReusedBuffers() {
        val blur = ArtworkBlur(11, 7)
        for (color in listOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff297be1.toInt())) {
            val pixels = IntArray(77) { color }
            blur.apply(pixels)
            assertTrue(pixels.all { it == color })
        }
    }

    @Test fun gaussianSpreadsAnImpulseSymmetricallyWithoutMixingColorChannels() {
        val size = 61
        val pixels = IntArray(size * size) { 0xff000000.toInt() }
        for (y in 27..33) for (x in 27..33) pixels[y * size + x] = 0xffff0000.toInt()
        ArtworkBlur(size, size).apply(pixels)
        for (y in 0 until size) for (x in 0 until size) {
            val color = pixels[y * size + x]
            assertEquals(0, color and 0xffff)
            assertEquals(255, color ushr 24)
            val value = (color ushr 16) and 255
            val mirror = (pixels[(size - 1 - y) * size + size - 1 - x] ushr 16) and 255
            assertTrue(abs(value - mirror) <= 1)
        }
        val centre = (pixels[30 * size + 30] ushr 16) and 255
        val neighbour = (pixels[30 * size + 40] ushr 16) and 255
        assertTrue(centre in 1..254 && neighbour in 1 until centre)
    }

    @Test fun blurNeverCarriesPixelsFromThePreviousArtworkIntoTheNext() {
        val blur = ArtworkBlur(9, 12)
        val first = IntArray(108) { if (it % 3 == 0) 0xffff0000.toInt() else 0xff0000ff.toInt() }
        val expected = first.copyOf()
        blur.apply(expected)
        blur.apply(IntArray(108) { 0xffffffff.toInt() })
        blur.apply(first)
        assertArrayEquals(expected, first)
    }
}
