package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.lmg.vk.ui.lyrics.RetainedLyricsCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class LyricsLayoutKey(
    val lyrics: SyncedLyrics,
    val normalStyle: TextStyle,
    val accompanimentStyle: TextStyle,
    val phoneticStyle: TextStyle,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
    val fontFamilyResolver: FontFamily.Resolver,
    val locales: LocaleList,
)

internal object PreparedLyricsLayouts {
    private val cache = RetainedLyricsCache<LyricsLayoutKey, Map<KaraokeLine, List<SyllableLayout>>>(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        maxEntries = 2,
        maxWeight = 16000,
        weightOf = { lines ->
            lines.values.sumOf { layouts -> layouts.sumOf {
                1 + (it.charLayouts?.size ?: 0) + (it.phoneticLayoutResult?.let { 1 } ?: 0)
            } }
        },
        isValid = { lines -> lines.values.all { layouts -> layouts.all {
            !it.textLayoutResult.multiParagraph.intrinsics.hasStaleResolvedFonts &&
                it.charLayouts.orEmpty().none { char -> char.multiParagraph.intrinsics.hasStaleResolvedFonts } &&
                it.phoneticLayoutResult?.multiParagraph?.intrinsics?.hasStaleResolvedFonts != true
        } } },
    )

    fun peek(key: LyricsLayoutKey) = cache.peek(key)

    suspend fun prepare(key: LyricsLayoutKey): Map<KaraokeLine, List<SyllableLayout>> =
        withContext(Dispatchers.Default) {
            cache.getOrPrepare(key) {
                val measurer = TextMeasurer(
                    defaultFontFamilyResolver = key.fontFamilyResolver,
                    defaultDensity = Density(key.density, key.fontScale),
                    defaultLayoutDirection = key.layoutDirection,
                )
                val normal = key.normalStyle.copy(textDirection = TextDirection.Content)
                val accompaniment = key.accompanimentStyle.copy(textDirection = TextDirection.Content)
                val normalSpace = measurer.measure(" ", normal).size.width.toFloat()
                val accompanimentSpace = measurer.measure(" ", accompaniment).size.width.toFloat()
                val layouts = LinkedHashMap<KaraokeLine, List<SyllableLayout>>()
                for (line in karaokeLinesForPreparation(key.lyrics)) {
                    currentCoroutineContext().ensureActive()
                    val isAccompaniment = line is KaraokeLine.AccompanimentKaraokeLine
                    val syllables = if (line.alignment == KaraokeAlignment.End) {
                        line.syllables.dropLastWhile { it.content.isBlank() }
                    } else line.syllables
                    layouts[line] = measureSyllablesAndDetermineAnimation(
                        syllables = syllables,
                        textMeasurer = measurer,
                        style = if (isAccompaniment) accompaniment else normal,
                        phoneticStyle = if (isAccompaniment) key.phoneticStyle
                            else key.phoneticStyle.copy(textDirection = TextDirection.Content),
                        isAccompanimentLine = isAccompaniment,
                        spaceWidth = if (isAccompaniment) accompanimentSpace else normalSpace,
                    )
                }
                layouts.toMap()
            }
        }
}

internal fun karaokeLinesForPreparation(lyrics: SyncedLyrics): List<KaraokeLine> = buildList {
    for (line in lyrics.lines) {
        if (line is KaraokeLine) {
            add(line)
            if (line is KaraokeLine.MainKaraokeLine) addAll(line.accompanimentLines.orEmpty())
        }
    }
}.distinct()
