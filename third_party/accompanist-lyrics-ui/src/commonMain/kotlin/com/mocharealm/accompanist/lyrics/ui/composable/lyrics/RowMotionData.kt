// LMG VK addition: prepare geometry once, reuse animation buffers while drawing.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult

internal data class GlyphMotionData(
    val text: TextLayoutResult,
    val origin: Offset,
    val pivot: Offset,
    val width: Float,
    val visible: Boolean,
    val startMs: Long,
    val word: WordAnimationInfo,
    val wordId: Int,
    val characterIndex: Int,
    val characterCount: Int,
    val emphasis: Boolean
)

class RowMotionData internal constructor(rowLayouts: List<SyllableLayout>) {
    internal val glyphs = rowLayouts.flatMap { layout ->
        val word = layout.wordAnimInfo
        val texts = layout.charLayouts
        val bounds = layout.charOriginalBounds
        if (word == null || texts == null || bounds == null) emptyList()
        else layout.syllable.content.indices.map { index ->
            val box = bounds[index]
            val text = texts[index]
            val absoluteIndex = layout.charOffsetInWord + index
            val count = word.wordContent.trimEnd().length.coerceAtLeast(1)
            // Lift follows the fill's spatial progression, independently of emphasis start/return.
            val start = glyphLiftStartMs(layout.syllable.start, layout.syllable.end, box.left, layout.width)
            GlyphMotionData(
                text, Offset(layout.position.x + box.left + (box.width - text.size.width) / 2f,
                    layout.position.y + layout.firstBaseline - text.firstBaseline),
                Offset(layout.position.x + box.center.x, layout.position.y + layout.firstBaseline),
                box.width, !layout.syllable.content[index].isWhitespace(), start, word,
                layout.wordId, absoluteIndex, count, layout.useAwesomeAnimation
            )
        }
    }.sortedBy { it.pivot.x }
    private val wordGroups = glyphs.indices.filter { glyphs[it].visible }
        .groupBy { glyphs[it].wordId }.values.map { it.toIntArray() }
    internal val runStarts = LongArray(rowLayouts.size) { index ->
        val layout = rowLayouts[index]
        val driver = if (layout.syllable.content.isBlank() || layout.syllable.content.none { it.isLetterOrDigit() }) {
            rowLayouts.take(index).lastOrNull { it.syllable.content.any { char -> char.isLetterOrDigit() } } ?: layout
        } else layout
        driver.wordAnimInfo?.wordStartTime ?: driver.syllable.start.toLong()
    }
    private val spatialWave = SpatialWave(
        FloatArray(glyphs.size) { glyphs[it].pivot.x },
        FloatArray(glyphs.size) { glyphs[it].width },
        (rowLayouts.maxOfOrNull { it.textLayoutResult.size.height } ?: 1) * 0.6f
    )
    private val rawLifts = FloatArray(glyphs.size)
    internal val lifts = FloatArray(glyphs.size)
    internal val emphasis = FloatArray(glyphs.size)
    internal val shadowAlphas = FloatArray(glyphs.size)
    private val halfGrowth = FloatArray(glyphs.size)
    internal val offsets = FloatArray(glyphs.size)
    private var lastPosition = Long.MIN_VALUE
    private var lastRelease = Long.MIN_VALUE

    internal fun update(positionMs: Long, releaseMs: Long) {
        if (lastPosition == positionMs && lastRelease == releaseMs) return
        lastPosition = positionMs
        lastRelease = releaseMs
        for (index in glyphs.indices) {
            val glyph = glyphs[index]
            rawLifts[index] = wordLiftDp(positionMs, glyph.startMs, releaseMs)
            val fraction = if (glyph.visible && glyph.emphasis) characterEmphasisFraction(
                positionMs, glyph.word.wordStartTime, glyph.word.wordEndTime,
                glyph.characterIndex, glyph.characterCount, releaseMs
            ) else 0f
            emphasis[index] = fraction * (glyph.word.wordDuration / 1_000f - 1f).coerceIn(0f, 1f)
            shadowAlphas[index] = if (glyph.visible && glyph.emphasis) wordGlowAlpha(
                positionMs, glyph.word.wordStartTime, glyph.word.wordEndTime, releaseMs
            ) else 0f
            halfGrowth[index] = glyph.width * 0.07f * emphasis[index]
        }
        spatialWave.sample(rawLifts, lifts)
        // Scale follows the recovered per-character envelope directly; only lift is spatially smoothed.
        // Keep expansion within its word so an emphasized syllable cannot move the entire row.
        for (group in wordGroups) {
            appleCharacterSpread(halfGrowth, group, offsets)
        }
    }
}
