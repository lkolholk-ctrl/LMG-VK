package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import kotlin.math.PI

internal data class LyricsLineSpring(val stiffness: Float, val dampingRatio: Float)

internal fun lyricsLineSpring(wordTimed: Boolean, gapMs: Long?): LyricsLineSpring {
    if (!wordTimed || gapMs == null) return LyricsLineSpring(100f, 0.9f)
    val fraction = ((gapMs / 1_000.0 - 0.2) / 0.55).coerceIn(0.0, 1.0)
    val response = fraction * 0.27 + 0.48
    val frequency = 2.0 * PI / response
    return LyricsLineSpring(
        stiffness = (frequency * frequency).toFloat(),
        dampingRatio = ((1.0 - fraction) * 0.12 + 0.78).toFloat()
    )
}

internal fun lyricsLineSpringFor(lines: List<ISyncedLine>, targetIndex: Int): LyricsLineSpring {
    val currentIndex = (targetIndex.coerceAtMost(lines.lastIndex) downTo 0)
        .firstOrNull { lines[it] !is KaraokeLine.AccompanimentKaraokeLine }
        ?: return lyricsLineSpring(false, null)
    val previousIndex = (currentIndex - 1 downTo 0)
        .firstOrNull { lines[it] !is KaraokeLine.AccompanimentKaraokeLine }
    val current = lines[currentIndex]
    val gap = previousIndex?.let { current.start.toLong() - lines[it].end }
    return lyricsLineSpring(current is KaraokeLine.MainKaraokeLine, gap)
}
