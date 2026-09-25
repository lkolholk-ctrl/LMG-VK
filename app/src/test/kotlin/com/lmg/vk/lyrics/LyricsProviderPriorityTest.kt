package com.lmg.vk.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsProviderPriorityTest {
    private val content = LyricsContent.Legacy(LyricsParser.Lyrics(
        listOf(LyricsParser.LyricLine(1000, "Example")), true, null, null))

    @Test fun primarySuccessDoesNotCallLowerPriorityProviders() = runBlocking {
        val calls = mutableListOf<LyricsSource>()
        assertSame(content, firstPreferredLyrics(LyricsSource.entries.toSet()) { calls += it; content })
        assertEquals(listOf(LyricsSource.APPLE_TTML), calls)
    }

    @Test fun missingSourcesFallThroughInTheRequestedOrder() = runBlocking {
        val expectedOrder = listOf(LyricsSource.APPLE_TTML, LyricsSource.BINI_LYRICS, LyricsSource.LYRICS_PLUS, LyricsSource.LRCLIB)
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
}
