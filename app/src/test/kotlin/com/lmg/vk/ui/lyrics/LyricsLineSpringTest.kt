package com.lmg.vk.ui.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.lyricsLineSpring
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.lyricsLineSpringFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLineSpringTest {
    @Test fun wordTimedGapsInterpolateResponseAndClampAtBothEnds() {
        val short = lyricsLineSpring(true, 200)
        val middle = lyricsLineSpring(true, 475)
        val long = lyricsLineSpring(true, 750)
        assertEquals(0.9f, short.dampingRatio, 0.00001f)
        assertEquals(0.84f, middle.dampingRatio, 0.00001f)
        assertEquals(0.78f, long.dampingRatio, 0.00001f)
        assertEquals(171.3473f, short.stiffness, 0.001f)
        assertEquals(70.1839f, long.stiffness, 0.001f)
        assertTrue(short.stiffness > middle.stiffness && middle.stiffness > long.stiffness)
        assertEquals(short, lyricsLineSpring(true, -500))
        assertEquals(long, lyricsLineSpring(true, 10_000))
    }

    @Test fun lineTimedAndMissingGapUseDefaultPhysicalSpring() {
        for (gap in listOf(null, -500L, 200L, 750L, 10_000L)) {
            assertEquals(100f, lyricsLineSpring(false, gap).stiffness, 0f)
            assertEquals(0.9f, lyricsLineSpring(false, gap).dampingRatio, 0f)
        }
        assertEquals(lyricsLineSpring(false, null), lyricsLineSpring(true, null))
    }

    @Test fun gapUsesMainLinesAndIgnoresAccompaniment() {
        val first = KaraokeLine.MainKaraokeLine(emptyList(), null, KaraokeAlignment.Start, 0, 2_000)
        val backing = KaraokeLine.AccompanimentKaraokeLine(emptyList(), null, KaraokeAlignment.Start, 1_500, 2_700)
        val next = KaraokeLine.MainKaraokeLine(emptyList(), null, KaraokeAlignment.Start, 2_750, 4_000)
        val lines = listOf(first, backing, next)
        assertEquals(lyricsLineSpring(true, 750), lyricsLineSpringFor(lines, 2))
        assertEquals(lyricsLineSpring(false, null), lyricsLineSpringFor(lines, 0))
        assertEquals(lyricsLineSpring(false, null), lyricsLineSpringFor(lines, 1))
        assertEquals(lyricsLineSpring(false, null), lyricsLineSpringFor(lines, -1))
        assertEquals(lyricsLineSpring(false, null), lyricsLineSpringFor(emptyList(), 0))
    }
}
