package com.lmg.vk.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** Optional Sound Check approximation, NOT Apple AutoMix DSP. Fixed 256-frame
 * measurement windows and a 20ms per-frame gain ramp make processing independent
 * of input-block partition. One playback owner per instance. No allocations or
 * locks in process(). Disabled unity operation is bit-transparent (including -0).
 */
internal class PcmNormalizationKernel {
    private var width = 0
    private var streamVersion = Long.MIN_VALUE
    private var appliedGain = 1f
    private var rampTarget = 1f
    private var rampStep = 0f
    private var rampRemaining = 0
    private var smoothingFrames = 1

    private var loudnessEnv = 0f       // огибающая RMS трека (0..32768)
    private var gain = 1f              // текущее усиление
    private var peak = 0f              // максимум модуля сэмпла за замер
    var framesSeen = 0L; private set        // сколько кадров прошло с начала трека
    private var framesToMeasure = 0L   // длительность замера в кадрах
    private var windowEnergy = 0.0
    private var windowFrames = 0
    var frozen = false; private set         // замер окончен, усиление больше не меняем

    private var channels = 0
    fun configure(sampleRate: Int, channelCount: Int, bytesPerSample: Int) {
        require(sampleRate in 8000..192000 && channelCount in 1..2 && (bytesPerSample == 2 || bytesPerSample == 4))
        width=bytesPerSample;channels=channelCount
        framesToMeasure=sampleRate.toLong()*MEASURE_SECONDS
        smoothingFrames=(sampleRate/50).coerceAtLeast(1)
        reset()
    }
    fun process(inputBuffer: ByteBuffer, start: Int, frames: Int, output: ByteBuffer,
        enabled: Boolean, version: Long, nativeOwned: Boolean = false) {
        require(channels>0 && frames>=0 && start>=0 && !output.isReadOnly)
        val bytes=frames.toLong()*channels*width
        require(start.toLong()+bytes<=inputBuffer.limit() && bytes<=output.limit())
        require(inputBuffer.order()==ByteOrder.LITTLE_ENDIAN && output.order()==ByteOrder.LITTLE_ENDIAN)
        if(streamVersion!=version) { reset();streamVersion=version }
        // Already mixed native output must never receive a leftover Sound Check ramp.
        if(nativeOwned && (appliedGain!=1f || rampRemaining!=0))reset()
        val normalize=enabled && !nativeOwned
        if(!normalize && appliedGain==1f) {
            rampRemaining=0;rampTarget=1f
            var i=0;val n=bytes.toInt()
            while(i+8<=n) { output.putLong(i,inputBuffer.getLong(start+i));i+=8 }
            while(i<n) { output.put(i,inputBuffer.get(start+i));i++ }
            return
        }
        if (!normalize) setTarget(1f)
        var f = 0
        while (f < frames) {
            if (normalize && !frozen) {
                var c = 0
                while (c < channels) {
                    val at = start + (f * channels + c) * width
                    val sample = if (width == 2) inputBuffer.getShort(at).toFloat()
                        else inputBuffer.getFloat(at) * 32768f
                    if (sample.isFinite()) {
                        windowEnergy += sample.toDouble() * sample
                        peak = maxOf(peak, abs(sample))
                    }
                    c++
                }
                framesSeen++; windowFrames++
                if (windowFrames == MEASURE_WINDOW || framesSeen >= framesToMeasure) {
                    val rms = sqrt(windowEnergy / (windowFrames * channels)).toFloat()
                    if (rms > NOISE_FLOOR) loudnessEnv = if (loudnessEnv <= 0f) rms
                        else loudnessEnv + ENV_COEF * (rms - loudnessEnv)
                    val ref = if (loudnessEnv > NOISE_FLOOR) loudnessEnv else rms.coerceAtLeast(1f)
                    val target = (TARGET_RMS / ref).coerceIn(MIN_GAIN, MAX_GAIN)
                    gain += GAIN_SMOOTH * (target - gain)
                    if (framesSeen >= framesToMeasure) frozen = true
                    val headroom = if (peak > 1f) PEAK_CEILING / peak else MAX_GAIN
                    setTarget(gain.coerceAtMost(headroom))
                    windowFrames = 0; windowEnergy = 0.0
                }
            } else if (normalize) setTarget(gain.coerceAtMost(if (peak > 1f) PEAK_CEILING / peak else MAX_GAIN))
            if (rampRemaining > 0) {
                appliedGain += rampStep
                if (--rampRemaining == 0) appliedGain = rampTarget
            }
            var c = 0
            while (c < channels) {
                val at = (f * channels + c) * width
                if (width == 2) {
                    val value = inputBuffer.getShort(start + at) * appliedGain
                    output.putShort(at, value.coerceIn(-32768f, 32767f).toInt().toShort())
                } else {
                    val value = inputBuffer.getFloat(start + at) * appliedGain
                    output.putFloat(at, if (value.isFinite()) value else 0f)
                }
                c++
            }
            f++
        }
    }
    private fun setTarget(target: Float) {
        if (target != rampTarget) {
            rampTarget = target; rampRemaining = smoothingFrames
            rampStep = (target - appliedGain) / smoothingFrames
        }
    }

    fun reset() {
        appliedGain = 1f; rampTarget = 1f; rampStep = 0f; rampRemaining = 0
        loudnessEnv = 0f
        gain = 1f
        peak = 0f
        framesSeen = 0L; windowFrames = 0; windowEnergy = 0.0
        frozen = false
    }

    private companion object {
        const val MEASURE_WINDOW = 256
        const val TARGET_RMS = 6500f     // ≈ −14 dBFS RMS для 16-бит
        const val NOISE_FLOOR = 200f     // ниже — тишина/интро, не учитываем
        const val ENV_COEF = 0.04f       // интеграция громкости трека (плавно)
        const val GAIN_SMOOTH = 0.05f    // сглаживание усиления (без «пыхтения»)
        const val MIN_GAIN = 0.4f        // ≈ −8 dB (приглушить громкий мастер)
        const val MAX_GAIN = 2.5f        // ≈ +8 dB (подтянуть тихий трек)

        /** Сколько секунд слушаем трек, прежде чем зафиксировать усиление. */
        const val MEASURE_SECONDS = 20L

        /** Потолок пика после усиления: чуть ниже максимума, с запасом. */
        const val PEAK_CEILING = 32000f
    }
}
