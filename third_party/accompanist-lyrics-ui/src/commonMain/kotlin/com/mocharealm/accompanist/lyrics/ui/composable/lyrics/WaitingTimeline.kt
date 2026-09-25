// LMG VK addition: merged vocal coverage and audio-driven instrumental animation.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import kotlin.math.abs

internal data class WaitingInterval(val startMs: Int, val endMs: Int, val nextLineIndex: Int) {
    fun contains(positionMs: Int) = positionMs >= startMs && positionMs < endMs
}

internal fun waitingIntervals(lines: List<ISyncedLine>, minimumGapMs: Int = 5_000): List<WaitingInterval> {
    val vocals = lines.flatMapIndexed { index, line ->
        val nested = (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty()
        (listOf(line) + nested).map { WaitingInterval(it.start.coerceAtLeast(0), it.end, index) }
    }.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
    val result = mutableListOf<WaitingInterval>()
    var coveredUntil = 0
    vocals.forEach { vocal ->
        if (vocal.startMs.toLong() - coveredUntil > minimumGapMs) {
            result += WaitingInterval(coveredUntil, vocal.startMs, vocal.nextLineIndex)
        }
        coveredUntil = maxOf(coveredUntil, vocal.endMs)
    }
    return result
}

private val dotFillEasing = CubicBezierEasing(0f, 0.25f, 1f, 0.58f)
private val dotExpandEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val dotExitEasing = CubicBezierEasing(0.25f, 0f, 1f, 0.2f)

// S8/a timing samples recovered in the user's research; these describe the pulse, not a clock.
private val dotPulseSamples = floatArrayOf(
    0f, .0005f, .002f, .004f, .007f, .01f, .0138f, .02f, .026f, .032f,
    .039f, .048f, .058f, .071f, .086f, .102f, .119f, .135f, .153f, .172f,
    .194f, .215f, .234f, .257f, .279f, .299f, .32f, .34f, .358f, .373f,
    .389f, .404f, .418f, .43f, .44f, .45f, .458f, .466f, .473f, .479f,
    .483f, .4875f, .491f, .494f, .496f, .498f, .499f, .4995f, .5f,
    .5005f, .502f, .504f, .507f, .51f, .5138f, .52f, .526f, .532f, .539f,
    .548f, .558f, .571f, .586f, .602f, .619f, .635f, .653f, .672f, .694f,
    .715f, .734f, .757f, .779f, .799f, .82f, .84f, .858f, .873f, .889f,
    .904f, .918f, .93f, .94f, .95f, .958f, .966f, .973f, .979f, .983f,
    .9875f, .991f, .994f, .996f, .998f, .999f, .9995f, 1f
)

internal fun waitingPulseScale(progress: Float): Float {
    val sample = progress.coerceIn(0f, 1f) * dotPulseSamples.lastIndex
    val index = sample.toInt().coerceAtMost(dotPulseSamples.lastIndex - 1)
    val eased = dotPulseSamples[index] + (dotPulseSamples[index + 1] - dotPulseSamples[index]) * (sample - index)
    return 1f + 0.2f * (1f - abs(2f * eased - 1f))
}

internal data class WaitingFrame(val scale: Float, val dotAlphas: List<Float>)

internal fun waitingFrame(positionMs: Int, startMs: Int, endMs: Int, count: Int = 3): WaitingFrame {
    val number = count.coerceIn(1, 12)
    val duration = endMs.toLong() - startMs
    val elapsed = positionMs.toLong() - startMs
    if (duration <= 0 || elapsed < 0 || elapsed >= duration) {
        return WaitingFrame(1f, List(number) { 0f })
    }
    // Fit the exit inside the gap: dots are already gone when the next vocal starts.
    val factor = (duration / 2_000f).coerceAtMost(1f)
    val expandMs = 750f * factor
    val exitMs = 250f * factor
    val bodyMs = duration - expandMs - exitMs
    val exitStart = duration - exitMs
    val exit = ((elapsed - exitStart) / exitMs).coerceIn(0f, 1f)
    val exitEase = dotExitEasing.transform(exit)
    val scale = when {
        elapsed >= exitStart -> 1.2f - 0.7f * exitEase
        elapsed >= bodyMs -> 1f + 0.2f * dotExpandEasing.transform(((elapsed - bodyMs) / expandMs).coerceIn(0f, 1f))
        else -> {
            val cycles = (bodyMs / 4_000f).toInt().coerceAtLeast(1)
            val period = bodyMs / cycles
            waitingPulseScale((elapsed % period) / period)
        }
    }
    val third = (bodyMs + expandMs) / number
    val alphas = List(number) { index ->
        val enter = ((elapsed - index * 50f * factor) / (750f * factor)).coerceIn(0f, 1f)
        val windowStart = index * third
        val windowDuration = minOf(third, bodyMs - windowStart).coerceAtLeast(1f)
        val fill = if (index == number - 1 && elapsed >= bodyMs) {
            val initial = (windowDuration / third).coerceIn(0f, 1f)
            initial + (1f - initial) * ((elapsed - bodyMs) / expandMs).coerceIn(0f, 1f)
        } else {
            (windowDuration / third).coerceAtMost(1f) * dotFillEasing.transform(
                ((elapsed - windowStart) / windowDuration).coerceIn(0f, 1f)
            )
        }
        val tintAlpha = (46f + (240f - 46f) * fill) / 255f
        tintAlpha * enter * enter * (1f - exitEase)
    }
    return WaitingFrame(scale, alphas)
}
