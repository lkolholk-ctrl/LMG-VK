package com.lmg.vk.lyrics.apple

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleTimingType
import com.lmg.vk.engine.lyrics.apple.LyricsPlusRichAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPlusRichAdapterTest {
    @Test
    fun preservesAdjacentSyllablesAndExplicitWordSpace() {
        val lyrics = LyricsParser.Lyrics(
            lines = listOf(
                LyricsParser.LyricLine(
                    timeMs = 1_000L,
                    text = "enough now",
                    words = listOf(
                        LyricsParser.LyricWord(1_000L, "e", 1_200L, 0, 1),
                        LyricsParser.LyricWord(1_200L, "nough", 1_700L, 1, 6),
                        LyricsParser.LyricWord(1_800L, "now", 2_300L, 7, 10),
                    ),
                    endMs = 2_300L,
                )
            ),
            isSynced = true,
            title = "Example",
            artist = "Artist",
            source = "lyrics_plus",
        )

        val document = LyricsPlusRichAdapter.convert(lyrics)!!
        val pieces = document.allLines.single().main

        assertEquals(AppleTimingType.WORD, document.timing)
        assertEquals("enough now", pieces.joinToString("") { it.text })
        assertEquals(listOf("e", "nough", " ", "now"), pieces.map { it.text })
        assertTrue(pieces[2].isWhitespace)
    }
}
