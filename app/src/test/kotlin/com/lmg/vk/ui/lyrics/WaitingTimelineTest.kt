package com.lmg.vk.ui.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.WaitingInterval
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.waitingFrame
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.waitingIntervals
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.waitingPulseScale
import org.junit.Assert.*
import org.junit.Test

class WaitingTimelineTest {
    private fun line(start: Int, end: Int) = SyncedLine("test", null, start, end)

    @Test fun overlappingVocalsDoNotProduceAFalseInstrumentalGap() {
        val windows = waitingIntervals(listOf(line(0, 20_000), line(1_000, 2_000), line(15_000, 21_000), line(30_000, 32_000)))
        assertEquals(listOf(WaitingInterval(21_000, 30_000, 3)), windows)
    }

    @Test fun nestedBackgroundVocalsCoverBothSidesOfTheMainLine() {
        val bg = listOf(
            KaraokeLine.AccompanimentKaraokeLine(emptyList(), null, KaraokeAlignment.Start, 5_000, 11_000),
            KaraokeLine.AccompanimentKaraokeLine(emptyList(), null, KaraokeAlignment.Start, 11_000, 18_000)
        )
        val main = KaraokeLine.MainKaraokeLine(emptyList(), null, KaraokeAlignment.Start, 10_000, 12_000, accompanimentLines = bg)
        val windows = waitingIntervals(listOf(main, line(25_000, 26_000)))
        assertEquals(listOf(WaitingInterval(18_000, 25_000, 1)), windows)
    }

    @Test fun introAndGapsUseHalfOpenIntervalsAndNeverCreateAnOutro() {
        val windows = waitingIntervals(listOf(line(8_000, 10_000), line(16_000, 20_000)))
        assertEquals(listOf(WaitingInterval(0, 8_000, 0), WaitingInterval(10_000, 16_000, 1)), windows)
        assertTrue(windows[0].contains(0))
        assertFalse(windows[0].contains(-1))
        assertFalse(windows[0].contains(8_000))
        assertFalse(windows.any { it.contains(20_000) })
    }

    @Test fun unsortedInputKeepsTheCorrectNextLineOwner() {
        assertEquals(
            listOf(WaitingInterval(0, 7_000, 1), WaitingInterval(8_000, 20_000, 0)),
            waitingIntervals(listOf(line(20_000, 25_000), line(7_000, 8_000)))
        )
        assertTrue(waitingIntervals(emptyList()).isEmpty())
        assertTrue(waitingIntervals(listOf(line(0, 0))).isEmpty())
    }

    @Test fun dotsFreezeOnPauseAndReconstructAfterTrackChangesAndSeeks() {
        val initial = waitingFrame(4_000, 0, 12_000)
        repeat(120) { assertEquals(initial, waitingFrame(4_000, 0, 12_000)) }
        waitingFrame(11_900, 0, 12_000)
        assertEquals(initial, waitingFrame(4_000, 0, 12_000))
        assertEquals(initial, waitingFrame(104_000, 100_000, 112_000))
        assertEquals(listOf(0f, 0f, 0f), waitingFrame(0, 0, 12_000).dotAlphas)
    }

    @Test fun dotsDisappearExactlyAtTheVocalBoundary() {
        for (time in listOf(-1, 12_000, 12_001, Int.MAX_VALUE)) {
            val frame = waitingFrame(time, 0, 12_000)
            assertEquals(listOf(0f, 0f, 0f), frame.dotAlphas)
        }
        val lastVisible = waitingFrame(11_999, 0, 12_000).dotAlphas.max()
        assertTrue(lastVisible < waitingFrame(11_990, 0, 12_000).dotAlphas.max())
        // The recovered exit curve accelerates sharply near its endpoint.
        assertTrue(lastVisible < 0.15f)
    }

    @Test fun dotsAppearAndFillInSequence() {
        val entering = waitingFrame(60, 0, 12_000).dotAlphas
        assertTrue(entering[0] > entering[1] && entering[1] > entering[2])
        assertEquals(0f, entering[2], 0f)
        val middle = waitingFrame(6_000, 0, 12_000).dotAlphas
        assertTrue(middle[0] > middle[1] && middle[1] > middle[2])
        assertEquals(46f / 255f, middle[2], 0.0001f)
        val end = waitingFrame(11_749, 0, 12_000).dotAlphas
        assertEquals(end[0], end[2], 0.002f)
    }

    @Test fun pulseAndExitHaveContinuousJoins() {
        assertEquals(1f, waitingPulseScale(0f), 0f)
        assertEquals(1.2f, waitingPulseScale(0.5f), 0f)
        assertEquals(1f, waitingPulseScale(1f), 0f)
        for (boundary in listOf(5_500, 11_000, 11_750)) {
            val before = waitingFrame(boundary - 1, 0, 12_000)
            val after = waitingFrame(boundary + 1, 0, 12_000)
            assertEquals(before.scale, after.scale, 0.005f)
            before.dotAlphas.zip(after.dotAlphas).forEach { (a, b) -> assertEquals(a, b, 0.01f) }
        }
    }

    @Test fun shortAndInvalidWindowsHaveNoNanOrNegativeGeometry() {
        for (end in listOf(-1, 0, 1, 20, 250, 1_000, 5_001, 20_000)) {
            for (time in -1..end.coerceAtLeast(0) + 1 step 7) {
                val frame = waitingFrame(time, 0, end)
                assertTrue(frame.scale.isFinite() && frame.scale in 0.5f..1.2f)
                assertTrue(frame.dotAlphas.all { it.isFinite() && it in 0f..1f })
            }
        }
    }
}
