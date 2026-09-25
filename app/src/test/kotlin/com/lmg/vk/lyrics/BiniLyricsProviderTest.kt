package com.lmg.vk.lyrics

import com.lmg.vk.engine.lyrics.*
import com.lmg.vk.ui.lyrics.AccompanistLyricsAdapter
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class BiniLyricsProviderTest {
    private val storage = "https://lyrics-storage.binimum.org/"
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
      <body><div><p begin="1s" end="4s"><span begin="1s" end="2s">Example </span><span begin="2s" end="4s">words</span>
      <span ttm:role="x-bg" begin="2s" end="3s"><span begin="2s" end="3s">Oh</span></span></p></div></body></tt>"""

    private fun row(name: String, duration: Int, file: String, timing: String = "word", artist: String = "Haddaway") =
        """{"track_name":"$name","artist_name":"$artist","duration":$duration,"timing_type":"$timing","lyricsUrl":"$storage$file"}"""

    @Test fun genericTitleSelectsMatchingDurationAndPrefersWordTiming() {
        val json = """{"results":[${row("What Is Love (Club Mix)", 403, "wrong.ttml")},
          ${row("What Is Love (Remastered)", 269, "line.ttml", "line")},
          ${row("What Is Love (7 Mix)", 270, "word.ttml")},
          ${row("What Is Love", 270, "cover.ttml", artist = "Other Artist")}]}"""
        assertEquals(listOf(storage + "word.ttml", storage + "line.ttml"),
            BiniLyricsProvider.candidates(json, "What Is Love", "Haddaway", 270000))
        assertTrue(BiniLyricsProvider.candidates(json, "What Is Love", "Haddaway", 0).isEmpty())
    }

    @Test fun explicitVersionAndUnicodeNamesAreNotFlattenedIntoAnotherSong() {
        val json = """{"results":[${row("Песня", 200, "original.ttml", artist = "Артист")},
          ${row("Песня (Live)", 200, "live.ttml", artist = "Артист")},
          ${row("Другая песня", 200, "other.ttml", artist = "Артист")}]}"""
        assertEquals(listOf(storage + "live.ttml"),
            BiniLyricsProvider.candidates(json, "ПЕСНЯ (Live)", "АРТИСТ", 200000))
    }

    @Test fun downloadedTtmlReachesExistingRendererWithoutTimingChanges() = runBlocking {
        val calls = mutableListOf<String>()
        val result = BiniLyricsProvider.fetch("What Is Love", "Haddaway", 270000) { url ->
            calls += url
            if (url.startsWith(storage)) ttml else """{"results":[${row("What Is Love", 270, "example.ttml") }]}"""
        }!!
        assertEquals(2, calls.size)
        assertEquals(ttml, result.value)
        assertEquals("bini_lyrics", result.sourceId)
        val line = AccompanistLyricsAdapter.convert(result).synced.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(1000, line.start)
        assertEquals(4000, line.end)
        assertEquals(3000, line.accompanimentLines!!.single().end)
    }

    @Test fun unavailableOrInvalidFilesDoNotBlockTheNextCandidateOrProvider() = runBlocking {
        val json = """{"results":[${row("What Is Love", 270, "missing.ttml")},
          ${row("What Is Love", 270, "error.ttml")},${row("What Is Love", 270, "valid.ttml")}]}"""
        val result = BiniLyricsProvider.fetch("What Is Love", "Haddaway", 270000) { url ->
            when {
                url.endsWith("missing.ttml") -> null
                url.endsWith("error.ttml") -> "<html>Forbidden</html>"
                url.endsWith("valid.ttml") -> ttml
                else -> json
            }
        }
        assertNotNull(result)
        assertNull(BiniLyricsProvider.fetch("What Is Love", "Haddaway", 270000) { null })
        assertNull(BiniLyricsProvider.fetch("What Is Love", "Haddaway", 270000) { "<html>Forbidden</html>" })
    }

    @Test fun unknownHostsAndUnrelatedSongsCannotBeSelected() {
        val json = """{"results":[${row("What Is Love", 270, "valid.ttml")},
          ${row("What Is Love Again", 270, "wrong.ttml")}]}"""
        assertEquals(listOf(storage + "valid.ttml"), BiniLyricsProvider.candidates(json, "What Is Love", "Haddaway", 270000))
        assertTrue(BiniLyricsProvider.candidates(json.replace(storage, "https://example.com/"),
            "What Is Love", "Haddaway", 270000).isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun trackChangesCancelTheDownload() = runBlocking<Unit> {
        BiniLyricsProvider.fetch("What Is Love", "Haddaway", 270000) { throw CancellationException() }
    }
}
