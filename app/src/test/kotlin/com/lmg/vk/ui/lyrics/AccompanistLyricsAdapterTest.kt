package com.lmg.vk.ui.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.engine.lyrics.apple.*
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.*
import org.junit.Test

class AccompanistLyricsAdapterTest {
    private fun legacy(vararg lines: LyricsParser.LyricLine, synced: Boolean = true) =
        LyricsParser.Lyrics(lines.toList(), synced, "Title", "Artist", "vk")

    @Test fun lineTimingsUseNextDistinctStartAndDuration() {
        val result = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(1000, "First"),
            LyricsParser.LyricLine(1000, "Duet"),
            LyricsParser.LyricLine(5000, "Last"),
        ), 9000)
        assertEquals(listOf(5000, 5000, 9000), result.synced.lines.map { it.end })
        assertEquals("vk", result.source)
        assertTrue(result.isSynced)
    }

    @Test fun keepsWordSpacesPunctuationAndExactTiming() {
        val result = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(1000, "Hello,  world!", words = listOf(
                LyricsParser.LyricWord(1000, "Hello", 1600, 0, 5),
                LyricsParser.LyricWord(2000, "world", 2700, 8, 13),
            ), endMs = 3000),
        ))
        val line = result.synced.lines.single() as KaraokeLine
        assertEquals("Hello,  world!", line.lyricText())
        assertEquals(1600, line.syllables.first().end)
        assertEquals(2000, line.syllables.first { it.content == "world" }.start)
    }

    @Test fun findsRepeatedWordsInOrderWithoutCharacterOffsets() {
        val line = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(1000, "go go go", words = listOf(
                LyricsParser.LyricWord(1000, "go"),
                LyricsParser.LyricWord(2000, "go"),
                LyricsParser.LyricWord(3000, "go"),
            ), endMs = 4000),
        )).synced.lines.single() as KaraokeLine
        assertEquals("go go go", line.lyricText())
        assertEquals(listOf(1000, 2000, 3000), line.syllables.filter { it.content == "go" }.map { it.start })
    }

    @Test fun preservesPlainTextOrderWithoutInventingSynchronization() {
        val result = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(-1, "First"), LyricsParser.LyricLine(-1, "Second"), synced = false,
        ))
        assertFalse(result.isSynced)
        assertEquals(listOf("First", "Second"), result.synced.lines.map { it.lyricText() })
    }

    @Test fun backgroundVocalsStayAttachedToTheirSinger() {
        val result = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(1000, "One", agentId = "v1"),
            LyricsParser.LyricLine(2000, "Two", agentId = "v2", endMs = 4000,
                backgroundLayers = listOf(LyricsParser.LyricLayer("echo", listOf(
                    LyricsParser.LyricWord(3500, "echo", 5000),
                )))),
        ))
        val line = result.synced.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.End, line.alignment)
        assertEquals("Two", line.lyricText())
        assertEquals("echo", line.accompanimentLines!!.single().lyricText())
        assertEquals(5000, line.accompanimentLines!!.single().end)
    }

    @Test fun keepsTranslationsAndPhoneticWords() {
        val result = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(1000, "Hello", words = listOf(LyricsParser.LyricWord(1000, "Hello", 2000)),
                translations = mapOf("en" to LyricsParser.LyricLayer("Translation")),
                pronunciations = mapOf("en" to LyricsParser.LyricLayer("helo", listOf(
                    LyricsParser.LyricWord(1000, "helo", 2000),
                )))),
        ))
        val line = result.synced.lines.single() as KaraokeLine
        assertEquals("Translation", line.translation)
        assertEquals("helo", line.phonetic)
        assertEquals("helo", line.syllables.single().phonetic)
    }

    @Test fun richDocumentsKeepSeparateVocalAndTranslationLayers() {
        fun piece(text: String, role: ApplePieceRole, start: Long = 1000, end: Long = 2000) =
            AppleLyricPiece(text, text, start, end, "v1", role, sourceOrder = 0)
        val line = AppleLyricsLine("line", null, 1000, 3000, "v1",
            main = listOf(piece("Hello ", ApplePieceRole.MAIN), piece("world", ApplePieceRole.MAIN, 2000, 3000)),
            background = listOf(piece("echo", ApplePieceRole.BACKGROUND)),
            translation = listOf(piece("Translation", ApplePieceRole.TRANSLATION)),
            pronunciation = listOf(piece("helo", ApplePieceRole.PRONUNCIATION)),
        )
        val doc = AppleLyricsDocument(4000, "en", AppleTimingType.WORD,
            listOf(AppleLyricsSection(1000, 3000, null, listOf(line))), emptyMap())
        val result = AccompanistLyricsAdapter.convert(LyricsContent.Rich(doc, "lyricsplus", "LyricsPlus"))
        val converted = result.synced.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals("Hello world", converted.lyricText())
        assertEquals("Translation", converted.translation)
        assertEquals("echo", converted.accompanimentLines!!.single().lyricText())
    }

    @Test fun parsesRawTtmlWithTheImportedParser() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="1s" end="4s">
                <span begin="1s" end="2s">Hello </span><span begin="2s" end="3s">world</span>
                <span ttm:role="x-bg" begin="2s" end="4s"><span begin="2s" end="4s">echo</span></span>
              </p></div></body>
            </tt>
        """.trimIndent()
        val result = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(ttml, "apple_ttml", "Apple"))
        val line = result.synced.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(1000, line.start)
        assertEquals(4000, line.end)
        assertEquals("Hello world", line.lyricText().trim())
        assertEquals("echo", line.accompanimentLines!!.single().lyricText())
    }

    @Test fun zeroDurationSyllableProducesFiniteProgressAtItsTimestamp() {
        val syllable = KaraokeSyllable("!", 1000, 1000)
        assertEquals(0f, syllable.progress(999), 0f)
        assertEquals(1f, syllable.progress(1000), 0f)
        assertEquals(1f, syllable.progress(1001), 0f)
    }

    @Test fun ttmlSupportsClockAndOffsetTimeNotations() {
        for (start in listOf("1.001s", "1001ms", "00:01.001", "00:00:01.001", "1.001")) {
            val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="$start" end="0.1m">Text</p></div></body></tt>"""
            val line = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(ttml, "embedded", "Embedded"))
                .synced.lines.single()
            assertEquals(start, 1001, line.start)
            assertEquals(start, 6000, line.end)
        }
    }

    @Test fun malformedWordEndIsNormalized() {
        val result = AccompanistLyricsAdapter.fromLegacy(legacy(
            LyricsParser.LyricLine(2000, "Word", words = listOf(LyricsParser.LyricWord(2000, "Word", 1000))),
        ))
        val line = result.synced.lines.single() as KaraokeLine
        assertTrue(line.syllables.single().end > line.syllables.single().start)
    }

    @Test fun emptyLyricsDoNotCreateRows() {
        val result = AccompanistLyricsAdapter.fromLegacy(LyricsParser.Lyrics.EMPTY)
        assertTrue(result.synced.lines.isEmpty())
        assertFalse(result.isSynced)
    }

    @Test fun untimedTtmlRemainsReadable() {
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p>First line</p><p>Second line</p></div></body></tt>"""
        val result = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(ttml, "embedded", "Embedded"))
        assertFalse(result.isSynced)
        assertEquals(listOf("First line", "Second line"), result.synced.lines.map { it.lyricText() })
    }

    @Test fun songwritersReachTheScreenWithoutChangingTimedLines() {
        val head = """<head><metadata><iTunesMetadata><songwriters>
            <songwriter> A. Writer </songwriter><songwriter>B. &amp; C.</songwriter>
            <songwriter>A. Writer</songwriter><songwriter> </songwriter>
            </songwriters></iTunesMetadata></metadata></head>"""
        val body = """<body><div><p begin="1s" end="3s"><span begin="1s" end="3s">Hello</span></p></div></body>"""
        fun parse(metadata: String) = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(
            """<tt xmlns="http://www.w3.org/ns/ttml">$metadata$body</tt>""", "embedded", "Embedded"))
        val original = parse("")
        val credited = parse(head)
        assertEquals(listOf("A. Writer", "B. & C."), credited.songwriters)
        assertEquals(original.synced, credited.synced)
        assertEquals(original.isSynced, credited.isSynced)
        assertTrue(original.songwriters.isEmpty())
    }

    @Test fun songwritersSupportNamespacesAndNumericEntitiesButIgnoreBodyText() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:m="http://www.w3.org/ns/ttml#metadata">
            <head><metadata><m:songwriter>Ren&#233; &#x1F3B5;</m:songwriter>
            <songwriter>B &amp;amp; C</songwriter></metadata></head>
            <body><div><p begin="1s" end="2s">Text</p><songwriter>Not credits</songwriter></div></body></tt>"""
        val result = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(xml, "embedded", "Embedded"))
        assertEquals(listOf("René 🎵", "B &amp; C"), result.songwriters)
    }

    @Test fun plainAndRichAndLegacyLyricsPreserveCredits() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata>
            <songwriter>A. Writer</songwriter></metadata></head><body><div><p>Text</p></div></body></tt>"""
        val raw = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(xml, "embedded", "Embedded"))
        assertFalse(raw.isSynced)
        assertEquals(listOf("A. Writer"), raw.songwriters)
        val doc = requireNotNull(AppleTtmlParser.parse(xml))
        val rich = AccompanistLyricsAdapter.convert(LyricsContent.Rich(doc, "apple", "Apple"))
        assertEquals(raw.songwriters, rich.songwriters)
        val old = legacy(LyricsParser.LyricLine(1000, "Text")).copy(
            songwriters = listOf("A. Writer", "", " A. Writer ", "B.  Writer"))
        assertEquals(listOf("A. Writer", "B. Writer"), AccompanistLyricsAdapter.fromLegacy(old).songwriters)
    }

    @Test fun rawTtmlRetainsWordTimedRowsBetweenExplicitRowsAndTheirCredits() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
            <head><metadata><songwriter>A. Writer</songwriter></metadata></head><body><div>
            <p begin="1s" end="2s"><span begin="1s" end="2s">First</span></p>
            <p><span begin="3s" end="4s">Second</span><span ttm:role="x-bg" begin="4s" end="8s">echo</span></p>
            <p begin="9s" end="10s"><span begin="9s" end="10s">Last</span></p>
            </div></body></tt>"""
        val result = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(xml, "apple_ttml", "Apple TTML"))
        assertTrue(result.isSynced)
        assertEquals(listOf("First", "Second", "Last"), result.synced.lines.map { it.lyricText() })
        assertEquals(listOf(1000, 3000, 9000), result.synced.lines.map { it.start })
        assertEquals(listOf("A. Writer"), result.songwriters)
        val second = result.synced.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals("echo", second.accompanimentLines!!.single().lyricText())
        assertEquals(8000, second.accompanimentLines!!.single().end)
        assertTrue(com.mocharealm.accompanist.lyrics.ui.composable.lyrics.waitingIntervals(result.synced.lines)
            .none { it.startMs < 8000 && it.endMs > 4000 })
    }
}
