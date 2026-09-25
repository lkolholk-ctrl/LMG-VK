package com.lmg.vk.ui.lyrics

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreparedLyricsLayoutsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backgroundPreparationPreservesGeometryAndReusesEqualLyricsIncludingBackingVocals() {
        val backing = KaraokeLine.AccompanimentKaraokeLine(
            listOf(KaraokeSyllable("Yeah", 100, 1500)), null, KaraokeAlignment.Start, 100, 1500,
        )
        val main = KaraokeLine.MainKaraokeLine(
            listOf(KaraokeSyllable("Stay ", 0, 2000), KaraokeSyllable("here", 2000, 3500)),
            null, KaraokeAlignment.Start, 0, 3500, accompanimentLines = listOf(backing),
        )
        val rtl = main.copy(
            syllables = listOf(KaraokeSyllable("مرحبا", 4000, 6000), KaraokeSyllable(" ", 6000, 6000)),
            alignment = KaraokeAlignment.End, start = 4000, end = 6000, accompanimentLines = null,
        )
        val phonetic = main.copy(
            syllables = listOf(KaraokeSyllable("歌", 7000, 9000, "うた")),
            start = 7000, end = 9000, accompanimentLines = null,
        )
        val lyrics = SyncedLyrics(listOf(main, rtl, phonetic))
        lateinit var key: LyricsLayoutKey
        lateinit var uiMeasurer: TextMeasurer
        compose.setContent {
            val density = LocalDensity.current
            uiMeasurer = rememberTextMeasurer()
            key = LyricsLayoutKey(
                lyrics, TextStyle(fontSize = 34.sp), TextStyle(fontSize = 20.sp),
                TextStyle(fontSize = 13.sp), density.density, density.fontScale,
                LocalLayoutDirection.current, LocalFontFamilyResolver.current, LocaleList.current,
            )
        }
        compose.waitForIdle()
        val prepared = runBlocking { PreparedLyricsLayouts.prepare(key) }
        assertEquals(4, prepared.size)
        compose.runOnIdle {
            for ((line, actual) in prepared) {
                val isBacking = line is KaraokeLine.AccompanimentKaraokeLine
                val style = (if (isBacking) key.accompanimentStyle else key.normalStyle)
                    .copy(textDirection = TextDirection.Content)
                val syllables = if (line.alignment == KaraokeAlignment.End) {
                    line.syllables.dropLastWhile { it.content.isBlank() }
                } else line.syllables
                val expected = measureSyllablesAndDetermineAnimation(
                    syllables, uiMeasurer, style,
                    if (isBacking) key.phoneticStyle else key.phoneticStyle.copy(textDirection = TextDirection.Content),
                    isBacking,
                    uiMeasurer.measure(" ", style).size.width.toFloat(),
                )
                assertEquals(expected.size, actual.size)
                expected.zip(actual).forEach { (before, after) ->
                    assertEquals(before.syllable, after.syllable)
                    assertEquals(before.width, after.width, 0f)
                    assertEquals(before.firstBaseline, after.firstBaseline, 0f)
                    assertEquals(before.textLayoutResult.size, after.textLayoutResult.size)
                    assertEquals(before.charOriginalBounds, after.charOriginalBounds)
                    assertEquals(before.charLayouts?.map { it.size }, after.charLayouts?.map { it.size })
                    assertEquals(before.phoneticLayoutResult?.size, after.phoneticLayoutResult?.size)
                    assertEquals(before.useAwesomeAnimation, after.useAwesomeAnimation)
                    assertEquals(before.wordId, after.wordId)
                }
            }
        }
        val equivalent = key.copy(lyrics = lyrics.copy(lines = listOf(main.copy(), rtl.copy(), phonetic.copy())))
        assertSame(prepared, PreparedLyricsLayouts.peek(equivalent))
        assertSame(prepared, runBlocking { PreparedLyricsLayouts.prepare(equivalent) })
        assertNull(PreparedLyricsLayouts.peek(key.copy(fontScale = key.fontScale * 1.1f)))
        assertNull(PreparedLyricsLayouts.peek(key.copy(normalStyle = key.normalStyle.copy(fontSize = 38.sp))))
        assertNull(PreparedLyricsLayouts.peek(key.copy(lyrics = lyrics.copy(lines = listOf(main.copy(end = 4000))))))
    }
}
