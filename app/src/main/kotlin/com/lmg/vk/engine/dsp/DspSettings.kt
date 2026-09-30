package com.lmg.vk.engine.dsp

import kotlinx.serialization.Serializable

@Serializable
data class DspBand(
    val enabled: Boolean = true,
    val type: Int = 0,
    val frequency: Float = 1000f,
    val gain: Float = 0f,
    val q: Float = 0.7071f,
    val slope: Float = 1f,
)

/** One complete immutable control snapshot; no mutable arrays are published to UI. */
@Serializable
data class DspSettings(
    val enabled: Boolean = false,
    val mode: Int = 1,
    val preamp: Float = 0f,
    val headroom: Float = 0f,
    val graphic: List<Float> = List(10) { 0f },
    val bands: List<DspBand> = DEFAULT_FREQUENCIES.map { DspBand(frequency = it) },
    val limiter: Boolean = true,
    val threshold: Float = -1f,
    val ceiling: Float = -0.3f,
    val releaseMs: Float = 80f,
) {
    fun normalized(): DspSettings {
        fun Float.safe(min: Float, max: Float, fallback: Float): Float =
            if (isFinite()) coerceIn(min, max) else fallback
        val safeCeiling = ceiling.safe(-12f, 0f, -0.3f)
        return copy(
            mode = mode.coerceIn(0, 2), preamp = preamp.safe(-36f, 12f, 0f),
            headroom = headroom.safe(0f, 24f, 0f),
            graphic = List(10) { graphic.getOrElse(it) { 0f }.safe(-18f, 18f, 0f) },
            bands = List(8) { i ->
                val b = bands.getOrElse(i) { DspBand(frequency = DEFAULT_FREQUENCIES[i]) }
                b.copy(type = b.type.coerceIn(0, 5), frequency = b.frequency.safe(20f, 20000f, 1000f),
                    gain = b.gain.safe(-24f, 24f, 0f), q = b.q.safe(0.1f, 20f, 0.7071f),
                    slope = b.slope.safe(0.1f, 1f, 1f))
            },
            ceiling = safeCeiling, threshold = threshold.safe(-36f, safeCeiling, -1f).coerceAtMost(safeCeiling),
            releaseMs = releaseMs.safe(5f, 2000f, 80f),
        )
    }

    fun toNativeParameters(): FloatArray = FloatArray(67).also { a ->
        a[0] = if (enabled) 1f else 0f; a[1] = mode.toFloat()
        a[2] = preamp; a[3] = headroom; a[4] = if (limiter) 1f else 0f
        a[5] = threshold; a[6] = ceiling; a[7] = releaseMs; a[8] = 20f
        graphic.forEachIndexed { i, gain -> a[9 + i] = gain }
        bands.forEachIndexed { i, b ->
            val j = 19 + i * 6
            a[j] = if (b.enabled) 1f else 0f; a[j + 1] = b.type.toFloat()
            a[j + 2] = b.frequency; a[j + 3] = b.gain; a[j + 4] = b.q; a[j + 5] = b.slope
        }
    }

    companion object {
        val DEFAULT_FREQUENCIES = listOf(60f, 125f, 250f, 500f, 1000f, 4000f, 8000f, 16000f)
        val GRAPHIC_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        val PRESETS = listOf(
            "Flat" to List(10) { 0f },
            "Bass Boost" to listOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f),
            "Vocal" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
            "Rock" to listOf(5f, 4f, 2f, 0f, -1f, 0f, 2f, 4f, 5f, 5f),
            "Electronic" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f),
        )
    }
}
