package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TTMLVocalTimingTest {
    private fun parse(body: String, metadata: String = "") = TTMLParser().parse(
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"><head><metadata>$metadata</metadata></head>
            <body><div>$body</div></body></tt>"""
    )

    @Test fun plainBackgroundUsesItsOwnIntervalAndPreservesMainWords() {
        val line = parse("""<p begin="1s" end="4s"><span begin="1s" end="3s">Hello</span><span ttm:role="x-bg" begin="2s" end="5s">(e<span>ch</span>o &amp; &#x1F3B5;)</span></p>""")
            .lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(listOf("Hello"), line.syllables.map { it.content })
        assertEquals(1000 to 3000, line.syllables.single().let { it.start to it.end })
        assertEquals(1000 to 4000, line.start to line.end)
        val bg = line.accompanimentLines!!.single()
        assertEquals("echo & 🎵", bg.syllables.single().content)
        assertEquals(2000 to 5000, bg.start to bg.end)
        assertEquals(2000 to 5000, bg.syllables.single().let { it.start to it.end })
    }

    @Test fun plainBackgroundInheritsOnlyMissingBoundsFromParent() {
        val line = parse("""<p begin="1s" end="5s"><span begin="2s" end="4s">Main</span><span ttm:role="x-bg">first</span><span ttm:role="x-bg" begin="3s">second</span><span ttm:role="x-bg" end="4.5s">third</span></p>""")
            .lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(listOf(1000 to 5000, 3000 to 5000, 1000 to 4500),
            line.accompanimentLines!!.map { it.start to it.end })
        assertEquals(listOf("first", "second", "third"),
            line.accompanimentLines!!.map { it.syllables.single().content })
    }

    @Test fun plainMainTextIsNotLostWhenBackgroundIsPresent() {
        val line = parse("""<p begin="1s" end="5s">Main <span>vocal</span><span ttm:role="x-bg" begin="2s" end="4s">echo</span></p>""")
            .lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals("Main vocal", line.syllables.single().content)
        assertEquals(1000 to 5000, line.syllables.single().let { it.start to it.end })
        assertEquals("echo", line.accompanimentLines!!.single().syllables.single().content)
    }

    @Test fun plainBackgroundKeepsTranslationOutsideVocalText() {
        val line = parse("""<p begin="1s" end="5s"><span begin="1s" end="4s">Main</span><span ttm:role="x-bg">（echo）<span ttm:role="x-translation">(эхо)</span><span ttm:role="x-roman">reading</span></span></p>""")
            .lines.single() as KaraokeLine.MainKaraokeLine
        val bg = line.accompanimentLines!!.single()
        assertEquals("echo", bg.syllables.single().content)
        assertEquals("эхо", bg.translation)
    }

    @Test fun backgroundMetadataDoesNotCreateAnEmptyVoice() {
        val line = parse("""<p begin="1s" end="5s">Main<span ttm:role="x-bg"><span ttm:role="x-translation">Translation</span></span><span ttm:role="x-bg">()</span></p>""")
            .lines.single() as SyncedLine
        assertEquals("Main", line.content)
    }

    @Test fun missingParagraphBoundsUseAllWordExtremaWithoutChangingWords() {
        val line = parse("""<p><span begin="3s" end="4s">One</span> <span begin="1s" end="5s">two</span></p>""")
            .lines.single() as KaraokeLine
        assertEquals(1000 to 5000, line.start to line.end)
        assertEquals(listOf(3000 to 4000, 1000 to 5000), line.syllables.map { it.start to it.end })
        assertEquals(listOf("One ", "two"), line.syllables.map { it.content })
    }

    @Test fun onlyMissingParagraphBoundsAreInferredIncludingExplicitZero() {
        val words = """<span begin="2s" end="4s">Word</span>"""
        val lines = parse("""<p begin="0s">$words</p><p end="9s">$words</p><p begin="1s" end="3s">$words</p>""").lines
        assertEquals(listOf(0 to 4000, 1000 to 3000, 2000 to 9000), lines.map { it.start to it.end })
        lines.forEach { assertEquals(2000 to 4000, (it as KaraokeLine).syllables.single().let { word -> word.start to word.end }) }
    }

    @Test fun translationAndRomanizationCannotSupplyVocalBounds() {
        val line = parse("""<p><span ttm:role="x-translation" begin="0s" end="20s">Translation</span><span begin="2s" end="4s">Main</span><span ttm:role="x-roman" begin="0s" end="30s">reading</span></p>""").lines.single()
        assertEquals(2000 to 4000, line.start to line.end)
    }

    @Test fun timedBackgroundCanSupplyMissingParagraphBounds() {
        val line = parse("""<p><span begin="2s" end="4s">Main</span><span ttm:role="x-bg" begin="1s" end="6s">echo</span></p>""")
            .lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(1000 to 6000, line.start to line.end)
        assertEquals(2000 to 4000, line.syllables.single().let { it.start to it.end })
        assertEquals(1000 to 6000, line.accompanimentLines!!.single().let { it.start to it.end })
    }

    @Test fun backgroundWordsTakePrecedenceOverParentFallback() {
        val line = parse("""<p begin="0s" end="9s"><span begin="1s" end="4s">Main</span><span ttm:role="x-bg"><span begin="2s" end="3s">(first </span><span begin="4s" end="6s">second)</span></span></p>""")
            .lines.single() as KaraokeLine.MainKaraokeLine
        val bg = line.accompanimentLines!!.single()
        assertEquals(2000 to 6000, bg.start to bg.end)
        assertEquals(listOf("first ", "second"), bg.syllables.map { it.content })
        assertEquals(listOf(2000 to 3000, 4000 to 6000), bg.syllables.map { it.start to it.end })
    }

    @Test fun fullyUntimedLyricsDoNotGainInventedZeroTimestamps() {
        assertTrue(parse("""<p>Main<span ttm:role="x-bg">echo</span></p>""").lines.isEmpty())
        assertTrue(parse("""<p><span ttm:role="x-translation" begin="1s" end="2s">Translation</span></p>""").lines.isEmpty())
    }

    @Test fun inferredStartsParticipateInSortingBeforeAgentAlignment() {
        val metadata = """<ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/>"""
        val lines = parse("""<p ttm:agent="v1"><span begin="1s" end="2s">First</span></p><p begin="4s" end="5s" ttm:agent="v2"><span begin="4s" end="5s">Last</span></p><p ttm:agent="v2"><span begin="2s" end="3s">Second</span></p>""", metadata).lines.map { it as KaraokeLine }
        assertEquals(listOf(1000, 2000, 4000), lines.map { it.start })
        assertEquals(listOf(KaraokeAlignment.Start, KaraokeAlignment.End, KaraokeAlignment.End), lines.map { it.alignment })
    }
}
