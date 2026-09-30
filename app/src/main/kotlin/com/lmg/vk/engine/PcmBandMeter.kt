package com.lmg.vk.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/** Audio-reactive metering only. PCM remains bit-for-bit unchanged. All histories and
 * window accumulators are local to one sink. Analysis windows ignore I/O block boundaries. */
class PcmBandMeter(private val sink: SinkAudioState?) {
    private val low = FloatArray(2)
    private val mid = FloatArray(2)
    private var channels = 0
    private var width = 0
    private var alphaLow = 0f
    private var alphaMid = 0f
    private var decay = 0f
    private var frames = 0
    private var energyLow = 0.0
    private var energyMid = 0.0
    private var energyHigh = 0.0
    private var envLow = 0f
    private var envMid = 0f
    private var envHigh = 0f
    private var streamVersion = Long.MIN_VALUE
    var packedLevels = 0L; private set

    fun configure(sampleRate: Int, channelCount: Int, bytesPerSample: Int) {
        require(sampleRate in 8000..192000 && channelCount in 1..2 && (bytesPerSample == 2 || bytesPerSample == 4))
        channels = channelCount; width = bytesPerSample
        alphaLow = (1.0 - exp(-2.0 * Math.PI * 150.0 / sampleRate)).toFloat()
        alphaMid = (1.0 - exp(-2.0 * Math.PI * 1800.0 / sampleRate)).toFloat()
        decay = (1.0 - exp(-WINDOW.toDouble() / (sampleRate * 0.15))).toFloat()
        reset()
    }
    fun reset() {
        low.fill(0f); mid.fill(0f)
        frames = 0; energyLow = 0.0; energyMid = 0.0; energyHigh = 0.0
        envLow = 0f; envMid = 0f; envHigh = 0f; packedLevels = 0
        sink?.resetLevels()
        streamVersion = sink?.streamVersion ?: 0L
    }
    fun process(buffer: ByteBuffer, offset: Int, countFrames: Int) {
        require(channels != 0 && countFrames >= 0 && offset >= 0 &&
            offset.toLong() + countFrames.toLong() * channels * width <= buffer.limit() &&
            buffer.order() == ByteOrder.LITTLE_ENDIAN)
        if (streamVersion != (sink?.streamVersion ?: 0L)) reset()
        var at = offset
        var f = 0
        while (f++ < countFrames) {
            var c = 0
            while (c < channels) {
                val raw = if (width == 2) buffer.getShort(at).toFloat() / 32768f else buffer.getFloat(at)
                val sample = if (raw.isFinite()) raw else 0f // meter only, never modify PCM
                at += width
                low[c] += alphaLow * (sample - low[c])
                mid[c] += alphaMid * (sample - mid[c])
                val bass = low[c]; val middle = mid[c] - bass; val treble = sample - mid[c]
                energyLow += bass.toDouble() * bass
                energyMid += middle.toDouble() * middle
                energyHigh += treble.toDouble() * treble
                c++
            }
            if (++frames == WINDOW) {
                val divisor = (WINDOW * channels).toDouble()
                envLow = envelope(envLow, (sqrt(energyLow / divisor) * 3.5).toFloat())
                envMid = envelope(envMid, (sqrt(energyMid / divisor) * 4.5).toFloat())
                envHigh = envelope(envHigh, (sqrt(energyHigh / divisor) * 6.0).toFloat())
                sink?.publish(envLow, envMid, envHigh)
                packedLevels = sink?.levels ?: pack(envLow, envMid, envHigh)
                frames = 0; energyLow = 0.0; energyMid = 0.0; energyHigh = 0.0
            }
        }
    }
    private fun envelope(previous: Float, input: Float): Float {
        val value = input.coerceIn(0f, 1f)
        return if (value > previous) value else previous + (value - previous) * decay
    }
    private fun pack(a: Float, b: Float, c: Float): Long {
        fun q(x: Float) = (x * 2097151f).toLong()
        return q(a) or (q(b) shl 21) or (q(c) shl 42)
    }
    companion object { const val WINDOW = 256 }
}
