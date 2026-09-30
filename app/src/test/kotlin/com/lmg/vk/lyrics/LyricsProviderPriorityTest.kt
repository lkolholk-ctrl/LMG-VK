package com.lmg.vk.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsProviderPriorityTest {
    private val content = LyricsContent.Legacy(LyricsParser.Lyrics(
        listOf(LyricsParser.LyricLine(1000, "Example")), true, null, null))
    private val wordContent = LyricsContent.RawTtml("""<tt xmlns="http://www.w3.org/ns/ttml">
        <body><div><p begin="1s" end="3s"><span begin="1s" end="2s">One </span><span begin="2s" end="3s">two</span></p></div></body></tt>""",
        LyricsSource.LMG_LYRICS_PLUS.id, LyricsSource.LMG_LYRICS_PLUS.title)

    @Test fun appleWordTimingDoesNotCallLowerPriorityProviders() = runBlocking {
        val appleWord = wordContent.copy(sourceId = LyricsSource.APPLE_TTML.id)
        val calls = mutableListOf<LyricsSource>()
        assertSame(appleWord, firstPreferredLyrics(LyricsSource.entries.toSet()) { calls += it; appleWord })
        assertEquals(listOf(LyricsSource.APPLE_TTML), calls)
    }

    @Test fun appleLinesAreUpgradedToLmgWordTiming() = runBlocking {
        val calls = mutableListOf<LyricsSource>()
        val result = firstPreferredLyrics(LyricsSource.entries.toSet()) {
            calls += it
            if (it == LyricsSource.APPLE_TTML) content else wordContent
        }
        assertSame(wordContent, result)
        assertEquals(listOf(LyricsSource.APPLE_TTML, LyricsSource.LMG_LYRICS_PLUS), calls)
    }

    @Test fun rawAppleLineTtmlAlsoAllowsQqWordTiming() = runBlocking {
        val apple = LyricsContent.RawTtml("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="1s" end="3s">Example line</p></div></body></tt>""",
            LyricsSource.APPLE_TTML.id, LyricsSource.APPLE_TTML.title)
        assertFalse(apple.hasWordTiming())
        assertSame(wordContent, firstPreferredLyrics(LyricsSource.entries.toSet()) {
            if (it == LyricsSource.APPLE_TTML) apple else wordContent
        })
    }

    @Test fun missingQqKeepsAppleLinesWithoutCallingUnrelatedProviders() = runBlocking {
        val calls = mutableListOf<LyricsSource>()
        assertSame(content, firstPreferredLyrics(LyricsSource.entries.toSet()) {
            calls += it
            if (it == LyricsSource.APPLE_TTML) content else null
        })
        assertEquals(listOf(LyricsSource.APPLE_TTML, LyricsSource.LMG_LYRICS_PLUS), calls)
    }

    @Test fun qqWithoutWordTimingsDoesNotDisplaceApple() = runBlocking {
        val qqLines = content.copy(lyrics = content.lyrics.copy(source = LyricsSource.LMG_LYRICS_PLUS.id))
        assertSame(content, firstPreferredLyrics(LyricsSource.entries.toSet()) {
            if (it == LyricsSource.APPLE_TTML) content else qqLines
        })
    }

    @Test fun disabledLmgDoesNotDelayAppleLines() = runBlocking {
        val calls = mutableListOf<LyricsSource>()
        assertSame(content, firstPreferredLyrics(LyricsSource.entries.toSet() - LyricsSource.LMG_LYRICS_PLUS) {
            calls += it
            content
        })
        assertEquals(listOf(LyricsSource.APPLE_TTML), calls)
    }

    @Test fun missingSourcesFallThroughInTheRequestedOrder() = runBlocking {
        val expectedOrder = listOf(LyricsSource.APPLE_TTML, LyricsSource.LMG_LYRICS_PLUS, LyricsSource.BINI_LYRICS, LyricsSource.LYRICS_PLUS, LyricsSource.LRCLIB)
        for (winner in expectedOrder.drop(1)) {
            val calls = mutableListOf<LyricsSource>()
            assertSame(content, firstPreferredLyrics(LyricsSource.entries.toSet()) {
                calls += it
                content.takeIf { _ -> it == winner }
            })
            assertEquals(expectedOrder.take(expectedOrder.indexOf(winner) + 1), calls)
        }
    }

    @Test fun disabledSourcesAndOptionalBetterLyricsCannotPreemptThePriorityChain() = runBlocking {
        val calls = mutableListOf<LyricsSource>()
        firstPreferredLyrics(setOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS)) { calls += it; null }
        assertEquals(listOf(LyricsSource.LRCLIB), calls)
    }

    @Test fun lmgCanBeSelectedIndependentlyFromAppleAndPublicLyricsPlus() = runBlocking {
        val calls = mutableListOf<LyricsSource>()
        assertSame(content, firstPreferredLyrics(setOf(LyricsSource.LMG_LYRICS_PLUS)) {
            calls += it
            content
        })
        assertEquals(listOf(LyricsSource.LMG_LYRICS_PLUS), calls)
    }
}
