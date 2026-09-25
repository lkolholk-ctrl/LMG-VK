package com.lmg.vk.ui.lyrics

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AccompanistLyricsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun lines() = AccompanistLyricsAdapter.fromLegacy(LyricsParser.Lyrics(
        lines = (1..24).map { index ->
            LyricsParser.LyricLine(index * 3000L, "Verse $index", endMs = (index + 1) * 3000L,
                translations = mapOf("en" to LyricsParser.LyricLayer("Translation $index")))
        }, isSynced = true, title = null, artist = null,
    ))

    @Test fun seekingForwardAndBackwardFollowsTheNewPosition() {
        val position = mutableIntStateOf(3500)
        var clicked = -1
        val lyrics = lines()
        compose.setContent {
            MaterialTheme {
                AccompanistLyricsBody(lyrics, { position.intValue },
                    onSeek = { clicked = it.start }, onShare = {})
            }
        }
        compose.onNodeWithText("Verse 1").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(3000, clicked); position.intValue = 60500 }
        compose.waitForIdle()
        compose.onNodeWithText("Verse 20").assertIsDisplayed()
        compose.runOnIdle { position.intValue = 3500 }
        compose.waitForIdle()
        compose.onNodeWithText("Verse 1").assertIsDisplayed()
    }

    @Test fun translationsCanBeToggledWithoutReloadingTheLyrics() {
        val show = mutableStateOf(true)
        val lyrics = lines()
        compose.setContent {
            MaterialTheme {
                AccompanistLyricsBody(lyrics, { 3500 }, showTranslations = show.value,
                    onSeek = {}, onShare = {})
            }
        }
        compose.onNodeWithText("Translation 1").assertIsDisplayed()
        compose.runOnIdle { show.value = false }
        compose.onNodeWithText("Translation 1").assertDoesNotExist()
    }

    @Test fun fullScreenRendersRawTtmlAndKeepsSyncWithoutTransportButtons() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="1s" end="8s"><span begin="1s" end="3s">A brand </span><span begin="3s" end="8s">new day</span></p>
                <p begin="9s" end="14s"><span begin="9s" end="11s">Let the music </span><span begin="11s" end="14s">play</span></p>
                <p begin="16s" end="22s"><span begin="16s" end="19s">Every word </span><span begin="19s" end="22s">comes alive</span></p>
              </div></body>
            </tt>
        """.trimIndent()
        compose.setContent {
            MaterialTheme {
                LyricsScreen(null, ttml, 4500, trackTitle = "Accompanist", trackArtist = "LMG VK",
                    trackDurationMs = 24000, trackId = "lyrics-instrumentation")
            }
        }
        val syncLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.sync_chip)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(syncLabel).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Закрыть").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (label in listOf(R.string.action_play, R.string.action_pause,
            R.string.lyrics_previous_track, R.string.lyrics_next_track)) {
            compose.onNodeWithContentDescription(context.getString(label)).assertDoesNotExist()
        }
        saveScreenshot("lyrics-portrait.png")
    }

    @Test fun karaokeRendersInANarrowViewport() {
        val lyrics = AccompanistLyricsAdapter.fromLegacy(LyricsParser.Lyrics(
            listOf(LyricsParser.LyricLine(1000, "A long line that wraps across the narrow viewport",
                words = listOf(LyricsParser.LyricWord(1000, "A long line that wraps across the narrow viewport", 8000)),
                endMs = 8000)), true, null, null,
        ))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF243F4D))) {
                    AccompanistLyricsBody(lyrics, { 4500 }, splitMode = true, onSeek = {}, onShare = {},
                        modifier = Modifier.width(260.dp))
                }
            }
        }
        compose.waitForIdle()
        saveScreenshot("lyrics-narrow.png")
    }

    @Test fun androidTtmlCreditsAppearAfterTheLastLyric() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata>
            <songwriter>Marshall Mathers</songwriter><songwriter>Luis Resto</songwriter>
            </metadata></head><body><div><p begin="1s" end="2s">
            <span begin="1s" end="2s">Last line</span></p></div></body></tt>"""
        val lyrics = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(xml, "apple_ttml", "Apple TTML"))
        assertEquals(listOf("Marshall Mathers", "Luis Resto"), lyrics.songwriters)
        compose.setContent {
            MaterialTheme {
                AccompanistLyricsBody(lyrics, { 2500 }, onSeek = {}, onShare = {})
            }
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.lyrics_songwriters, "Marshall Mathers, Luis Resto")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
