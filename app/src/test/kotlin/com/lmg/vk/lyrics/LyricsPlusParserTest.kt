package com.lmg.vk.lyrics

import com.lmg.vk.engine.lyrics.*
import com.lmg.vk.ui.lyrics.AccompanistLyricsAdapter
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.*
import org.junit.Test

class LyricsPlusParserTest {
    @Test fun v2KeepsSyllablesBackgroundVocalsAndTranslationsInTheExistingRenderer() {
        val raw = """{"type":"Word","metadata":{"language":"en"},"lyrics":[
          {"time":1000,"duration":3000,"text":"enough now (oh)",
           "element":{"singer":"v1","key":"L1"},
           "syllabus":[{"time":1000,"duration":200,"text":"e"},
             {"time":1200,"duration":600,"text":"nough "},
             {"time":2000,"duration":1500,"text":"now"},
             {"time":2500,"duration":800,"text":"oh","isBackground":true}],
           "translation":{"lang":"en","text":"Example translation"},
           "transliteration":{"lang":"en","text":"Example phonetic"}}]}"""
        val content = LyricsPlusParser.parse(raw, "Example", "Artist", 8000)!!
        val result = AccompanistLyricsAdapter.convert(content, 8000)
        assertEquals("lyrics_plus", result.source)
        val line = result.synced.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals("enough now", line.syllables.joinToString("") { it.content })
        assertEquals(listOf(1000, 1200, 2000), line.syllables.filter { it.content.isNotBlank() }.map { it.start })
        assertEquals("Example translation", line.translation)
        assertEquals("Example phonetic", line.phonetic)
        val bg = line.accompanimentLines!!.single()
        assertEquals("oh", bg.syllables.joinToString("") { it.content })
        assertEquals(2500, bg.start)
        assertEquals(3300, bg.end)
    }

    @Test fun fullLineRestoresWordSpacesAndMissingDurationUsesTheNextBoundary() {
        val raw = """{"type":"Word","lyrics":[{"time":1000,"text":"Hello, world!",
          "syllabus":[{"time":1000,"text":"Hello"},{"time":1800,"text":"world"}]},
          {"time":4000,"duration":1000,"text":"Next"}]}"""
        val result = AccompanistLyricsAdapter.convert(LyricsPlusParser.parse(raw, "", "", 6000)!!)
        val line = result.synced.lines.first() as KaraokeLine.MainKaraokeLine
        assertEquals("Hello, world!", line.syllables.joinToString("") { it.content })
        assertEquals(1800, line.syllables.first().end)
        assertEquals(4000, line.end)
    }

    @Test fun reducedTtmlInfersParagraphTimingAndWordEndsWithoutChangingExplicitTimes() {
        val raw = """<tt><body><div><p><span begin="1s" dur="0.2s">e</span><span begin="1.2s">nough </span><span begin="2s" end="3s">now</span></p>
          <p begin="4s" end="5s">Next</p></div></body></tt>"""
        val content = LyricsPlusParser.parse(raw, "", "", 6000)!!
        assertTrue(content is LyricsContent.RawTtml)
        val result = AccompanistLyricsAdapter.convert(content)
        val line = result.synced.lines.first() as KaraokeLine.MainKaraokeLine
        assertEquals(1000, line.start)
        assertEquals(3000, line.end)
        assertEquals("enough now", line.syllables.joinToString("") { it.content })
        assertEquals(listOf(1200, 2000, 3000), line.syllables.filter { it.content.isNotBlank() }.map { it.end })
    }

    @Test fun plainTextStaysUntimedAndInvalidResponsesCannotWinProviderSelection() {
        val content = LyricsPlusParser.parse("""{"type":"None","lyrics":[{"text":"Untimed words"}]}""", "", "", 0)!!
        assertFalse(AccompanistLyricsAdapter.convert(content).isSynced)
        for (body in listOf("{\"error\":\"unavailable\"}", "<html>error</html>", "{\"lyrics\":[]}",
            "<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///not-a-real-file'>]><tt>&x;</tt>")) {
            assertNull(LyricsPlusParser.parse(body, "", "", 0))
        }
    }

    @Test fun reducedTtmlKeepsOverlappingBackgroundTimingSeparateFromMainWords() {
        val raw = """<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
          <p begin="1s" end="5s"><span begin="1s">Main </span>
            <span ttm:role="x-bg" begin="2s"><span begin="2s" end="3s">back </span><span begin="3s" end="4.5s">voice</span></span>
            <span begin="4s" end="5s">end</span></p>
          </div></body></tt>"""
        val content = LyricsPlusParser.parse(raw, "", "", 6000)!!
        val line = AccompanistLyricsAdapter.convert(content).synced.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(4000, line.syllables.first().end)
        val background = line.accompanimentLines!!.single()
        assertEquals(2000, background.start)
        assertEquals(4500, background.end)
        assertEquals(listOf(3000, 4500), background.syllables.filter { it.content.isNotBlank() }.map { it.end })
    }

    @Test fun reducedTtmlUsesTheNextInferredParagraphStartForMissingEnds() {
        val raw = """<tt><body><div><p><span begin="1s">First</span></p>
          <p><span begin="3s" end="4s">Next</span></p></div></body></tt>"""
        val result = AccompanistLyricsAdapter.convert(LyricsPlusParser.parse(raw, "", "", 6000)!!)
        assertEquals(3000, result.synced.lines.first().end)
    }
}
