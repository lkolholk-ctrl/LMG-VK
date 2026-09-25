// LMG VK addition: audio-position-based motion adapted from lyrics animation research.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToLong
import kotlin.math.abs
import kotlin.math.PI

private const val WordLiftDp = -2.0
private const val WordSpringStiffness = 14.0
private val WordSpringDampingRatio = 7.0 / (2.0 * sqrt(WordSpringStiffness))
private const val FillFeatherDp = 30f
private val springDecay = WordSpringDampingRatio * sqrt(WordSpringStiffness)
private val springFrequency = sqrt(WordSpringStiffness * (1 - WordSpringDampingRatio * WordSpringDampingRatio))

internal fun lyricAnimationPositionMs(positionMs: Int, startMs: Int, endMs: Int): Int =
    positionMs.coerceIn(startMs, (endMs.toLong() + 4_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

internal fun glyphLiftStartMs(startMs: Int, endMs: Int, leftPx: Float, widthPx: Float): Long =
    startMs.toLong() + ((endMs.toLong() - startMs).coerceAtLeast(0) *
        (leftPx / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)).toLong()

private fun springStep(elapsedMs: Long): Double {
    if (elapsedMs <= 0L) return 0.0
    val seconds = elapsedMs / 1000.0
    val phase = springFrequency * seconds
    return 1 - exp(-springDecay * seconds) *
        (cos(phase) + springDecay / springFrequency * sin(phase))
}

internal fun wordLiftDp(positionMs: Long, startMs: Long, releaseMs: Long): Float {
    if (releaseMs <= startMs || positionMs <= startMs) return 0f
    // Hold through subsequent words; opposite step responses release at line end
    // without a jump in position or velocity.
    // Evaluating the audio timeline directly also makes pause/seek independent of frame history.
    return (WordLiftDp * (springStep(positionMs - startMs) - springStep(positionMs - releaseMs))).toFloat()
}

internal fun criticalLyricsSpring(elapsedMs: Double, responseMs: Double): Double {
    if (elapsedMs <= 0.0 || responseMs <= 0.0) return 0.0
    val phase = 2.0 * PI * elapsedMs / responseMs
    return 1.0 - (1.0 + phase) * exp(-phase)
}

internal fun characterDelayMs(durationMs: Long, index: Int, count: Int, emphasis: Boolean): Long {
    if (durationMs <= 0 || count <= 0) return 0
    val step = if (emphasis) (durationMs * 0.4 / count).coerceAtMost(400.0)
    else durationMs.toDouble() / count
    val delay = step * (index.coerceIn(0, count - 1) + if (emphasis) 1 else 0)
    return if (emphasis) delay.roundToLong() else delay.toLong()
}

internal fun characterEmphasis(
    positionMs: Long, wordStartMs: Long, wordEndMs: Long,
    index: Int, count: Int, lineEndMs: Long
): Float = characterEmphasisFraction(positionMs, wordStartMs, wordEndMs, index, count, lineEndMs) *
    ((wordEndMs - wordStartMs) / 1_000f - 1f).coerceIn(0f, 1f)

internal fun characterEmphasisFraction(
    positionMs: Long, wordStartMs: Long, wordEndMs: Long,
    index: Int, count: Int, lineEndMs: Long
): Float {
    val duration = wordEndMs - wordStartMs
    if (duration < 1_000 || count <= 0) return 0f
    val delay = (duration * 0.4 / count).coerceAtMost(400.0) * (index.coerceIn(0, count - 1) + 1)
    val start = wordStartMs + delay
    if (positionMs <= start || lineEndMs <= start) return 0f
    val response = duration.coerceAtMost(3_000).toDouble()
    val release = minOf(start + 2.0 * duration / count, lineEndMs.toDouble())
    fun rise(time: Double) = criticalLyricsSpring(time - start, response)
    return (if (positionMs < release) rise(positionMs.toDouble())
    else rise(release) * (1.0 - criticalLyricsSpring(positionMs - release, response))).toFloat()
}

internal fun wordGlowAlpha(positionMs: Long, startMs: Long, endMs: Long, lineEndMs: Long): Float {
    val duration = endMs - startMs
    if (duration <= 0 || positionMs <= startMs || lineEndMs <= startMs) return 0f
    val factor = (duration / 1_000.0 - 1.0).coerceIn(0.0, 1.0)
    val release = minOf(endMs, lineEndMs)
    val response = duration.coerceAtMost(3_000).toDouble()
    val value = if (positionMs < release) {
        criticalLyricsSpring((positionMs - startMs).toDouble(), response)
    } else {
        criticalLyricsSpring((release - startMs).toDouble(), response) *
            (1.0 - springStep(positionMs - release))
    }
    return (0.4 * factor * value).coerceIn(0.0, 0.4).toFloat()
}

internal fun appleCharacterSpread(halfGrowth: FloatArray, group: IntArray, offsets: FloatArray) {
    if (group.isEmpty()) return
    val middle = (group.size - 1) / 2f
    if (group.size % 2 == 1) offsets[group[group.size / 2]] = 0f
    // player/g walks outwards from the centre, feeding back half of the inner neighbour's offset.
    for (local in (group.size / 2 - 1) downTo 0) {
        val index = group[local]
        val neighbour = local + 1
        var shift = halfGrowth[index]
        if (neighbour <= middle) shift += halfGrowth[group[neighbour]]
        if (neighbour < middle) shift += abs(offsets[group[neighbour]])
        offsets[index] = -0.5f * shift
    }
    for (local in (group.size + 1) / 2 until group.size) {
        val index = group[local]
        val neighbour = local - 1
        var shift = halfGrowth[index]
        if (neighbour >= middle) shift += halfGrowth[group[neighbour]]
        if (neighbour > middle) shift += abs(offsets[group[neighbour]])
        offsets[index] = 0.5f * shift
    }
}

internal fun fillFeatherStops(
    cursorFraction: Float,
    rowWidthPx: Float,
    density: Float,
    isRtl: Boolean
): Pair<Float, Float> {
    val cursor = cursorFraction.coerceIn(0f, 1f)
    val feather = if (rowWidthPx > 0f) (FillFeatherDp * density / rowWidthPx).coerceIn(0f, 1f) else 0f
    return if (isRtl) cursor to (cursor + feather).coerceAtMost(1f)
    else (cursor - feather).coerceAtLeast(0f) to cursor
}
