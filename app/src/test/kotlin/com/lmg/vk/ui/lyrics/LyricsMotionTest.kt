package com.lmg.vk.ui.lyrics

import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.fillFeatherStops
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.wordLiftDp
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.characterDelayMs
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.characterEmphasis
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.appleCharacterSpread
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.characterEmphasisFraction
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.glyphLiftStartMs
import org.junit.Assert.assertArrayEquals
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.canAnimateCharacters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.criticalLyricsSpring
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.wordGlowAlpha

class LyricsMotionTest {
    @Test fun liftFollowsGlyphPositionWithoutRestartingWithTheEmphasisReturn() {
        assertEquals(1_000L, glyphLiftStartMs(1_000, 3_000, 0f, 100f))
        assertEquals(1_400L, glyphLiftStartMs(1_000, 3_000, 20f, 100f))
        assertEquals(2_500L, glyphLiftStartMs(1_000, 3_000, 75f, 100f))
        assertEquals(2_500L, glyphLiftStartMs(1_000, 3_000, 225f, 300f))
        val start = glyphLiftStartMs(1_000, 3_000, 20f, 100f)
        assertTrue(wordLiftDp(3_200, start, 5_000) < -1.99f)
        assertEquals(1_000L, glyphLiftStartMs(1_000, 1_000, 0f, 0f))
    }
    @Test fun lettersFormARisingWaveAndStayRaisedAfterTheirWordFinishes() {
        val starts = List(5) { characterDelayMs(1_000, it, 5, false) }
        val wave = starts.map { wordLiftDp(600, it, 5_000) }
        assertTrue(wave[0] < wave[1] && wave[1] < wave[2] && wave[2] < wave[3])
        assertEquals(0f, wave[4], 0f)
        starts.forEach { start ->
            assertEquals(-2f, wordLiftDp(4_000, start, 5_000), 0.001f)
            assertEquals(0f, wordLiftDp(9_000, start, 5_000), 0.001f)
        }
    }

    @Test fun emphasisSpreadsAcrossLettersAndReturnsFromItsCurrentScale() {
        val start = 1_000L
        val end = 3_000L
        val count = 5
        val sample = List(count) { characterEmphasis(1_400, start, end, it, count, 6_000) }
        assertTrue(sample[0] > sample[1] && sample[1] > sample[2])
        assertEquals(0f, sample[4], 0f)
        for (index in 0 until count) {
            val release = start + characterDelayMs(end - start, index, count, true) + 2 * (end - start) / count
            val before = characterEmphasis(release - 1, start, end, index, count, 6_000)
            val after = characterEmphasis(release + 1, start, end, index, count, 6_000)
            assertEquals(before, after, 0.003f)
            assertEquals(0f, characterEmphasis(8_000, start, end, index, count, 6_000), 0.00001f)
        }
    }

    @Test fun sustainedWordsReturnGraduallyInsteadOfSnappingBackAfterThreeHundredMs() {
        val release = 960L
        val peak = characterEmphasis(release, 0, 2_000, 0, 5, 6_000)
        val duringReturn = characterEmphasis(release + 600, 0, 2_000, 0, 5, 6_000)
        assertTrue(peak > 0f)
        assertTrue(duringReturn > 0f && duringReturn < peak)
        assertTrue(characterEmphasis(release + 2_000, 0, 2_000, 0, 5, 6_000) > 0f)
        assertEquals(0f, characterEmphasis(release + 6_000, 0, 2_000, 0, 5, 6_000), 0.00001f)
    }

    @Test fun appleSpreadKeepsTheMiddleLetterAnchoredAndPropagatesOutwards() {
        val offsets = FloatArray(5)
        appleCharacterSpread(floatArrayOf(1f, 2f, 3f, 4f, 5f), intArrayOf(0, 1, 2, 3, 4), offsets)
        assertArrayEquals(floatArrayOf(-2.75f, -2.5f, 0f, 3.5f, 6.25f), offsets, 0f)
    }

    @Test fun appleSpreadHandlesEvenWordsAndDoesNotMoveUnrelatedGlyphs() {
        val offsets = floatArrayOf(99f, 0f, 0f, 0f, 0f, 77f)
        appleCharacterSpread(floatArrayOf(0f, 1f, 2f, 3f, 4f, 0f), intArrayOf(1, 2, 3, 4), offsets)
        assertArrayEquals(floatArrayOf(99f, -2f, -1f, 1.5f, 4.25f, 77f), offsets, 0f)
        appleCharacterSpread(FloatArray(6), intArrayOf(1, 2, 3, 4), offsets)
        assertArrayEquals(floatArrayOf(99f, 0f, 0f, 0f, 0f, 77f), offsets, 0f)
    }

    @Test fun glowUsesWordTimingAndPreservesContinuityAtRelease() {
        assertEquals(0f, wordGlowAlpha(500, 0, 1_000, 4_000), 0f)
        assertTrue(wordGlowAlpha(100, 0, 2_000, 4_000) > 0f)
        assertEquals(0f, characterEmphasisFraction(100, 0, 2_000, 0, 5, 4_000), 0f)
        assertEquals(wordGlowAlpha(1_999, 0, 2_000, 4_000), wordGlowAlpha(2_001, 0, 2_000, 4_000), 0.001f)
        assertTrue(wordGlowAlpha(2_300, 0, 2_000, 4_000) > wordGlowAlpha(2_600, 0, 2_000, 4_000))
        for (time in -100L..10_000L step 7) {
            assertTrue(wordGlowAlpha(time, 0, 2_000, 4_000) in 0f..0.4f)
        }
    }

    @Test fun appleCharacterDelaysStartAtOneStepAndCapEachStagger() {
        assertEquals(114L, characterDelayMs(2_000, 0, 7, true))
        assertEquals(229L, characterDelayMs(2_000, 1, 7, true))
        assertEquals(343L, characterDelayMs(2_000, 2, 7, true))
        assertEquals(400L, characterDelayMs(10_000, 0, 7, true))
        assertEquals(800L, characterDelayMs(10_000, 1, 7, true))
        assertEquals(2_800L, characterDelayMs(10_000, 6, 7, true))
        assertEquals(0L, characterDelayMs(0, 1, 0, true))
    }

    @Test fun stretchMatchesCriticalPhysicalSpringAndDoesNotStopAtResponseTime() {
        var position = 0.0
        var velocity = 0.0
        val dt = 0.0001
        val omega = 2.0 * Math.PI / 2.0
        for (step in 1..40_000) {
            velocity += (omega * omega * (1.0 - position) - 2 * omega * velocity) * dt
            position += velocity * dt
            if (step % 500 == 0) {
                assertEquals(position, criticalLyricsSpring(step * dt * 1_000, 2_000.0), 0.0002)
            }
        }
        assertTrue(criticalLyricsSpring(2_000.0, 2_000.0) < 1.0)
        assertTrue(criticalLyricsSpring(2_001.0, 2_000.0) > criticalLyricsSpring(2_000.0, 2_000.0))
    }

    @Test fun connectedScriptsAndCombiningSequencesAreNotSplitIntoGlyphs() {
        assertTrue("Love".canAnimateCharacters())
        assertTrue("\u041f\u0440\u0438\u0432\u0435\u0442".canAnimateCharacters())
        for (text in listOf("e\u0301", "\uD83D\uDC69\u200D\uD83D\uDCBB", "\u0644\u0627", "\u0E01\u0E32")) {
            assertTrue(!text.canAnimateCharacters())
        }
    }

    @Test fun emphasisHandlesSeeksLineEndAndShortWordsWithoutOvershoot() {
        for (duration in listOf(0L, 400L, 1_000L, 2_000L, 10_000L)) {
            for (position in 0L..15_000L step 13L) {
                val value = characterEmphasis(position, 0, duration, 2, 5, 3_000)
                assertTrue(value.isFinite() && value in 0f..1f)
                if (duration < 1_000) assertEquals(0f, value, 0f)
                if (position >= 9_000) assertEquals(0f, value, 0.0001f)
            }
        }
        val value = characterEmphasis(900, 0, 2_000, 2, 5, 3_000)
        characterEmphasis(9_000, 0, 2_000, 2, 5, 3_000)
        repeat(60) { assertEquals(value, characterEmphasis(900, 0, 2_000, 2, 5, 3_000), 0f) }
    }

    @Test fun springMatchesIntegrationOfThePhysicalSystemThroughRelease() {
        var position = 0.0
        var velocity = 0.0
        val stepSeconds = 0.0005
        for (step in 1..10_000) {
            val target = if (step <= 1_200) -2.0 else 0.0
            val acceleration = 14.0 * (target - position) - 7.0 * velocity
            velocity += acceleration * stepSeconds
            position += velocity * stepSeconds
            if (step % 100 == 0) {
                assertEquals(position, wordLiftDp(step / 2L, 0, 600).toDouble(), 0.004)
            }
        }
    }

    @Test fun wordStartsAtRestAndSettlesAtTwoDpBeforeReturningToBaseline() {
        assertEquals(0f, wordLiftDp(999, 1_000, 5_000), 0f)
        assertEquals(0f, wordLiftDp(1_000, 1_000, 5_000), 0f)
        assertEquals(-2f, wordLiftDp(4_000, 1_000, 5_000), 0.001f)
        assertEquals(0f, wordLiftDp(9_000, 1_000, 5_000), 0.001f)
    }

    @Test fun releasePreservesPositionAndVelocity() {
        val before = wordLiftDp(599, 0, 600)
        val atEnd = wordLiftDp(600, 0, 600)
        val after = wordLiftDp(601, 0, 600)
        assertTrue(atEnd < -1f)
        assertEquals(atEnd, after, 0.005f)
        // One-sided second-order estimates account for the acceleration change at release.
        val velocityBefore = (3 * atEnd - 4 * before + wordLiftDp(598, 0, 600)) * 500
        val velocityAfter = (-3 * atEnd + 4 * after - wordLiftDp(602, 0, 600)) * 500
        assertEquals(velocityBefore, velocityAfter, 0.002f)
    }

    @Test fun shortAndInvalidDurationsRemainFinite() {
        for (duration in listOf(-1L, 0L, 1L, 20L, 100L)) {
            for (time in -10L..6_000L step 10L) {
                val lift = wordLiftDp(time, 0, duration)
                assertTrue(lift.isFinite())
                assertTrue(lift in -2.01f..0.01f)
                if (duration <= 0L) assertEquals(0f, lift, 0f)
            }
        }
    }

    @Test fun pausingAndSeekingBackReproduceTheSameMotion() {
        val firstPass = wordLiftDp(400, 0, 1_000)
        repeat(120) { assertEquals(firstPass, wordLiftDp(400, 0, 1_000), 0f) }
        wordLiftDp(20_000, 0, 1_000)
        assertEquals(firstPass, wordLiftDp(400, 0, 1_000), 0f)
        assertEquals(firstPass, wordLiftDp(100_400, 100_000, 101_000), 0f)
    }

    @Test fun featherKeepsThirtyDpWidthAcrossDensities() {
        for (density in listOf(1f, 1.5f, 2f, 3.5f)) {
            val widthPx = 300f * density
            val (start, end) = fillFeatherStops(0.5f, widthPx, density, false)
            assertEquals(0.5f, end, 0f)
            assertEquals(30f, (end - start) * widthPx / density, 0.0001f)
        }
    }

    @Test fun rtlFeatherMirrorsLtrAtTheSameSungProgress() {
        for (progress in listOf(0f, 0.05f, 0.5f, 0.95f, 1f)) {
            val ltr = fillFeatherStops(progress, 600f, 2f, false)
            val rtl = fillFeatherStops(1f - progress, 600f, 2f, true)
            assertEquals(1f - ltr.second, rtl.first, 0.0001f)
            assertEquals(1f - ltr.first, rtl.second, 0.0001f)
        }
    }

    @Test fun featherStopsStayOrderedForNarrowAndEmptyRows() {
        for (width in listOf(0f, 1f, 20f, 600f)) {
            for (cursor in listOf(-1f, 0f, 0.01f, 0.5f, 0.99f, 1f, 2f)) {
                for (rtl in listOf(false, true)) {
                    val (start, end) = fillFeatherStops(cursor, width, 3f, rtl)
                    assertTrue(start.isFinite() && end.isFinite())
                    assertTrue(start in 0f..1f && end in start..1f)
                }
            }
        }
    }
}
