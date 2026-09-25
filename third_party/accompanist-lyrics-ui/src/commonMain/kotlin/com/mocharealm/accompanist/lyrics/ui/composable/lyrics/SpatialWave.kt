// LMG VK addition: cached spatial smoothing for a continuous lyric baseline.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import kotlin.math.abs
import kotlin.math.exp

internal class SpatialWave(centres: FloatArray, widths: FloatArray, radiusPx: Float) {
    private val variance = radiusPx.coerceAtLeast(1f).let { it * it }
    private class Neighbours(val indices: IntArray, val weights: FloatArray, val distances: FloatArray)
    private val neighbours = Array(centres.size) { index ->
        val indices = centres.indices.filter { abs(centres[it] - centres[index]) <= radiusPx * 3f }
        val weights = FloatArray(indices.size) { offset ->
            val other = indices[offset]
            val distance = centres[other] - centres[index]
            exp(-distance * distance / (2f * variance)) * widths[other].coerceAtLeast(1f)
        }
        val total = weights.sum().coerceAtLeast(1f)
        for (offset in weights.indices) weights[offset] /= total
        Neighbours(indices.toIntArray(), weights, FloatArray(indices.size) { centres[indices[it]] - centres[index] })
    }

    fun sample(values: FloatArray, result: FloatArray, slopes: FloatArray? = null) {
        require(values.size == neighbours.size && result.size == values.size)
        for (index in neighbours.indices) {
            val local = neighbours[index]
            var value = 0f
            for (offset in local.indices.indices) value += local.weights[offset] * values[local.indices[offset]]
            result[index] = value
            if (slopes != null) {
                var slope = 0f
                for (offset in local.indices.indices) {
                    slope += local.weights[offset] * (values[local.indices[offset]] - value) * local.distances[offset]
                }
                slopes[index] = slope / variance
            }
        }
    }
}
