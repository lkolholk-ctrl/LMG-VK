package com.lmg.vk.ui.player

import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun backgroundSurfaceSize(widthPx: Int, heightPx: Int, densityDpi: Int): Pair<Int, Int> {
    val factor = if (densityDpi >= 420) 24f else 16f
    val width = widthPx.coerceAtLeast(1) * 1.3f / factor
    val height = heightPx.coerceAtLeast(1) * 1.3f / factor
    val cap = (maxOf(width, height) / 180f).coerceAtLeast(1f)
    return (width / cap).roundToInt().coerceAtLeast(1) to (height / cap).roundToInt().coerceAtLeast(1)
}

internal fun artworkRotation(elapsedMs: Long, layer: Int): Float {
    val period = when (layer) { 0 -> 150_000L; 1 -> 112_500L; else -> 87_500L }
    return (elapsedMs.coerceAtLeast(0L) % period).toFloat() / period * if (layer == 0) -360f else 360f
}

/** Three sliding box filters approximate the Gaussian in linear time, with no per-frame buffers. */
internal class ArtworkBlur(private val width: Int, private val height: Int, radius: Int = 25) {
    private val scratch = IntArray(width * height)
    private val radii = run {
        val sigma = radius * 0.4 + 0.6
        var lower = sqrt(4 * sigma * sigma + 1).toInt()
        if (lower % 2 == 0) lower--
        val smallerPasses = ((12 * sigma * sigma - 3 * lower * lower - 12 * lower - 9) /
            (-4 * lower - 4)).roundToInt().coerceIn(0, 3)
        IntArray(3) { ((if (it < smallerPasses) lower else lower + 2) - 1) / 2 }
    }

    fun apply(pixels: IntArray) {
        require(pixels.size == width * height)
        for (radius in radii) {
            boxPass(pixels, scratch, width, height, 1, width, radius)
            boxPass(scratch, pixels, height, width, width, 1, radius)
        }
    }

    private fun boxPass(source: IntArray, target: IntArray, length: Int, lines: Int,
                        stride: Int, lineStride: Int, radius: Int) {
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var red = 0; var green = 0; var blue = 0
            for (offset in -radius..radius) {
                val color = source[base + offset.coerceIn(0, length - 1) * stride]
                red += (color ushr 16) and 255
                green += (color ushr 8) and 255
                blue += color and 255
            }
            for (position in 0 until length) {
                target[base + position * stride] = (255 shl 24) or
                    (((red + window / 2) / window) shl 16) or
                    (((green + window / 2) / window) shl 8) or ((blue + window / 2) / window)
                val removed = source[base + (position - radius).coerceAtLeast(0) * stride]
                val added = source[base + (position + radius + 1).coerceAtMost(length - 1) * stride]
                red += ((added ushr 16) and 255) - ((removed ushr 16) and 255)
                green += ((added ushr 8) and 255) - ((removed ushr 8) and 255)
                blue += (added and 255) - (removed and 255)
            }
        }
    }
}
