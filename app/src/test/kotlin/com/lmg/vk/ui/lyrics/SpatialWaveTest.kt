package com.lmg.vk.ui.lyrics

import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.SpatialWave
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SpatialWaveTest {
    @Test fun wordBoundaryBecomesAContinuousSlopeInsteadOfATwoDpStep() {
        val centres = FloatArray(20) { it * 12f }
        val input = FloatArray(20) { if (it < 10) -2f else 0f }
        val wave = SpatialWave(centres, FloatArray(20) { 12f }, 28f)
        val heights = FloatArray(20)
        val slopes = FloatArray(20)
        wave.sample(input, heights, slopes)
        for (index in 1 until heights.size) {
            assertTrue(abs(heights[index] - heights[index - 1]) < 0.4f)
            assertTrue(heights[index] >= heights[index - 1] - 0.00001f)
        }
        assertTrue(slopes[9] > 0f && slopes[10] > 0f)
        assertTrue(abs(slopes[9] - slopes[10]) < 0.002f)
        assertTrue(heights[0] < -1.99f && heights.last() > -0.01f)
    }

    @Test fun restingAndFullyLiftedLinesStayFlat() {
        val wave = SpatialWave(floatArrayOf(0f, 8f, 30f, 90f), floatArrayOf(5f, 9f, 30f, 15f), 24f)
        for (level in listOf(0f, -2f, 1f)) {
            val heights = FloatArray(4)
            val slopes = FloatArray(4)
            wave.sample(FloatArray(4) { level }, heights, slopes)
            heights.forEach { assertEquals(level, it, 0.000001f) }
            slopes.forEach { assertEquals(0f, it, 0.000001f) }
        }
    }

    @Test fun emphasisIsSmoothedAcrossWordsWithoutExceedingItsOriginalPeak() {
        val input = floatArrayOf(0f, 0f, 0.7f, 1f, 0.5f, 0f, 0f)
        val result = FloatArray(input.size)
        SpatialWave(FloatArray(input.size) { it * 15f }, FloatArray(input.size) { 15f }, 28f).sample(input, result)
        assertTrue(result[1] > 0f && result[5] > 0f)
        assertTrue(result.all { it in 0f..1f })
        assertTrue(result[3] < input[3])
        for (index in 1 until result.size) assertTrue(abs(result[index] - result[index - 1]) < 0.25f)
    }

    @Test fun densityAndScreenPositionDoNotChangeTheWave() {
        val raw = floatArrayOf(-2f, -1.5f, -0.2f, 0f)
        val baseline = FloatArray(4)
        val baselineSlope = FloatArray(4)
        SpatialWave(floatArrayOf(0f, 16f, 32f, 48f), FloatArray(4) { 16f }, 24f).sample(raw, baseline, baselineSlope)
        for (density in listOf(1f, 2f, 3.5f)) {
            val values = FloatArray(4)
            val slopes = FloatArray(4)
            SpatialWave(FloatArray(4) { 450f + it * 16f * density }, FloatArray(4) { 16f * density }, 24f * density)
                .sample(raw, values, slopes)
            values.indices.forEach { index ->
                assertEquals(baseline[index], values[index], 0.00001f)
                assertEquals(baselineSlope[index], slopes[index] * density, 0.00001f)
            }
        }
    }

    @Test fun reusedBuffersRecoverTheSameShapeAfterABackwardSeek() {
        val wave = SpatialWave(FloatArray(8) { it * 10f }, FloatArray(8) { 10f }, 20f)
        val output = FloatArray(8)
        val first = floatArrayOf(-2f, -1.9f, -1.3f, -0.7f, -0.1f, 0f, 0f, 0f)
        wave.sample(first, output)
        val expected = output.copyOf()
        wave.sample(FloatArray(8) { -2f }, output)
        wave.sample(first, output)
        assertArrayEquals(expected, output, 0f)
    }
}
