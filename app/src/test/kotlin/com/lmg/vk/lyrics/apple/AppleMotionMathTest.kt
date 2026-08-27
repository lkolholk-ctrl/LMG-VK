package com.lmg.vk.lyrics.apple

import com.lmg.vk.ui.lyrics.apple.AppleEmphasisEngine
import com.lmg.vk.ui.lyrics.apple.appleLineMoveDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMotionMathTest {

    @Test
    fun lineMovementDurationFormulaMatchesAppleSpecification() {
        // Lower bound clamp (gap <= 200ms -> 480ms)
        assertEquals(480, appleLineMoveDuration(currentEnd = 1000L, nextBegin = 1100L))
        assertEquals(480, appleLineMoveDuration(currentEnd = 1000L, nextBegin = 1200L))

        // Upper bound clamp (gap >= 750ms -> 750ms)
        assertEquals(750, appleLineMoveDuration(currentEnd = 1000L, nextBegin = 1750L))
        assertEquals(750, appleLineMoveDuration(currentEnd = 1000L, nextBegin = 3000L))

        // Mid point: gap = 475ms -> (475 - 200) / 550 = 0.5 -> 480 + (270 * 0.5) = 615ms
        assertEquals(615, appleLineMoveDuration(currentEnd = 1000L, nextBegin = 1475L))
    }

    @Test
    fun targetScaleMatchesAppleFormula() {
        assertEquals(1.00f, AppleEmphasisEngine.calculateTargetScale(1000L), 0.001f)
        assertEquals(1.07f, AppleEmphasisEngine.calculateTargetScale(1500L), 0.001f)
        assertEquals(1.14f, AppleEmphasisEngine.calculateTargetScale(2000L), 0.001f)
        assertEquals(1.14f, AppleEmphasisEngine.calculateTargetScale(5000L), 0.001f)
    }

    @Test
    fun animatorDurationCappedAt3000Ms() {
        assertEquals(1000L, AppleEmphasisEngine.calculateAnimationDurationMs(1000L))
        assertEquals(2500L, AppleEmphasisEngine.calculateAnimationDurationMs(2500L))
        assertEquals(3000L, AppleEmphasisEngine.calculateAnimationDurationMs(3000L))
        assertEquals(3000L, AppleEmphasisEngine.calculateAnimationDurationMs(6000L))
    }

    @Test
    fun staggerFormulaMatchesAppleSpecification() {
        // count = 2, dur = 1000ms -> 0.4 * 1000 / 2 = 200ms
        assertEquals(200L, AppleEmphasisEngine.calculateStaggerMs(1000L, 2))
        // count = 1, dur = 2000ms -> 0.4 * 2000 / 1 = 800ms -> capped at 400ms
        assertEquals(400L, AppleEmphasisEngine.calculateStaggerMs(2000L, 1))
    }

    @Test
    fun cascadeDelayMatchesAppleCurve() {
        assertEquals(25L, AppleEmphasisEngine.calculateCascadeDelayMs(1))
        assertEquals(44L, AppleEmphasisEngine.calculateCascadeDelayMs(2))
        assertEquals(56L, AppleEmphasisEngine.calculateCascadeDelayMs(3))
        assertEquals(63L, AppleEmphasisEngine.calculateCascadeDelayMs(4))
        assertEquals(63L, AppleEmphasisEngine.calculateCascadeDelayMs(5))
        assertEquals(63L, AppleEmphasisEngine.calculateCascadeDelayMs(6))
    }

    @Test
    fun neighborDisplacementKeepsSyllablesCohesive() {
        // Group of 3 pieces with expansion
        val expansions = floatArrayOf(2.0f, 3.0f, 2.0f)
        val translations = AppleEmphasisEngine.calculateAppleNeighborTranslations(expansions, isRtl = false)

        // Center piece (index 1) remains at 0 offset
        assertEquals(0f, translations[1], 0.001f)
        // Left piece moves left (negative X)
        assertTrue(translations[0] < 0f)
        // Right piece moves right (positive X)
        assertTrue(translations[2] > 0f)
        // Symmetric absolute values for symmetric expansions
        assertEquals(translations[2], -translations[0], 0.001f)
    }
}
