package com.lmg.vk.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.pow

/**
 * Необязательные legacy DJ-эффекты в цепочке одного ExoPlayer.
 * Во время нативного AutoMix mixed-output они строго обходятся:
 *  - FILTER_SWEEP: one-pole LP, срез уезжает экспоненциально 16кГц→150Гц;
 *  - ECHO_OUT: feedback-делэй 375мс (fb 0.42), send растёт с прогрессом;
 *    в фазе TAIL dry замьючен — поверх нового трека звенят только повторы,
 *    гаснущие в последние 0.25с.
 * Эхо post-fader (громкость плеера умножает и его), поэтому wet
 * компенсируется текущей громкостью уходящего (кап 2.5x).
 * Вне перехода полностью прозрачен: ни одного умножения на сэмпл.
 */
@UnstableApi
class DjFxAudioProcessor(private val sink: SinkAudioState? = null) : BoundedPcmAudioProcessor() {
    private var width = 0
    private var streamVersion = Long.MIN_VALUE

    private var sweepLp = FloatArray(2)
    private var echoBuf = FloatArray(0)      // кольцо [pos*2 + channel]
    private var echoLen = 0
    private var echoPos = 0
    private var tailRemaining = 0L
    private var tailArmed = false
    private var dirty = false                // нужен сброс состояний перед новым переходом
    private var sampleRate = 44100
    // State belongs to this sink and resets on an actual output occurrence change.
    private var seenGen = -1L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        width = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount !in 1..2) return AudioProcessor.AudioFormat.NOT_SET
        prepareOutputStorage()
        sampleRate = inputAudioFormat.sampleRate
        echoLen = (sampleRate * 0.375).toInt().coerceAtLeast(1)
        echoBuf = FloatArray(echoLen * 2)
        echoPos = 0
        tailRemaining = 0
        tailArmed = false
        return inputAudioFormat
    }

    private fun resetFxState() {
        sweepLp[0] = 0f; sweepLp[1] = 0f
        echoBuf.fill(0f)
        echoPos = 0
        tailRemaining = 0
        tailArmed = false
        dirty = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val channels = inputAudioFormat.channelCount
        val sizeBytes = boundedBytes(inputBuffer, channels * width)
        val version = sink?.streamVersion ?: 0L
        if (streamVersion != version) { resetFxState(); seenGen = -1L; streamVersion = version }

        val command = DjStreamFx.snapshot()
        val mode = command.mode

        // Unknown routing and native-owned mixed output are always transparent.
        if (mode == DjStreamFx.MODE_NONE || echoLen == 0 || !command.matches(sink)) {
            if (dirty) resetFxState()
            val output = replaceOutputBuffer(sizeBytes)
            copy(inputBuffer, output, sizeBytes)
            return
        }
        // Новый переход на том же процессоре → сбросить хвосты старого.
        val gen = command.generation
        if (gen != seenGen) {
            resetFxState()
            seenGen = gen
        }
        dirty = true

        val inputStart = inputBuffer.position()
        val output = replaceOutputBuffer(sizeBytes)
        val frames = sizeBytes / (width * channels)

        val p = command.progress

        // FILTER_SWEEP: коэффициент на блок (~5-20мс) — для one-pole достаточно.
        var sweepAlpha = 0f
        if (mode == DjStreamFx.MODE_SWEEP) {
            val cutoff = 16000f * (150f / 16000f).pow(p)
            sweepAlpha = 1f - exp(-2f * Math.PI.toFloat() * cutoff / sampleRate)
        }

        // ECHO: send растёт с прогрессом; wet компенсирует post-fader громкость.
        val echoActive = mode == DjStreamFx.MODE_ECHO
        val tailActive = mode == DjStreamFx.MODE_ECHO_TAIL
        if (tailActive && !tailArmed) {
            tailArmed = true
            tailRemaining = (sampleRate * 2.2).toLong()
        }
        val smoothP = p * p * (3f - 2f * p)
        val send = if (echoActive) smoothP * 0.55f else 0f
        // Кап 1.8x: выход процессора — PCM16, клампится ДО громкости плеера;
        // больший буст срезал бы волну эха на жирном материале.
        val volComp = (1f / command.outVolume.coerceAtLeast(0.45f)).coerceAtMost(1.8f)
        val tailFadeSamples = (sampleRate * 0.25f)

        var base = 0
        for (f in 0 until frames) {
            val wet = when {
                echoActive -> volComp
                tailActive && tailRemaining > 0 ->
                    (tailRemaining / tailFadeSamples).coerceAtMost(1f)
                else -> 0f
            }
            for (c in 0 until channels) {
                val at = inputStart + (base + c) * width
                var s = if (width == 2) inputBuffer.getShort(at) / 32768f else inputBuffer.getFloat(at)
                if (!s.isFinite()) s = 0f
                if (c < 2) {
                    if (sweepAlpha > 0f) {
                        sweepLp[c] += sweepAlpha * (s - sweepLp[c])
                        s = sweepLp[c]
                    }
                    if (echoActive || tailActive) {
                        val bi = echoPos * 2 + c
                        val y = echoBuf[bi]
                        echoBuf[bi] = s * send + y * 0.42f
                        // TAIL: dry замьючен — поверх нового трека только повторы.
                        s = if (tailActive) y * wet else s + y * wet
                    }
                }
                // Мягкое колено (только в FX-режиме): dry+эхо в 16-бит цепочке
                // может превысить 1.0 — асимптотический лимитер вместо жёсткого
                // среза. 0.88 + 0.12·x/(x+0.25) < 1.0 всегда.
                val a = if (s >= 0f) s else -s
                if (a > 0.88f) {
                    val x = a - 0.88f
                    val lim = 0.88f + 0.12f * (x / (x + 0.25f))
                    s = if (s >= 0f) lim else -lim
                }
                if (width == 2) {
                    val v = (s * 32768f).coerceIn(-32768f, 32767f)
                    output.putShort((base + c) * width, v.toInt().toShort())
                } else output.putFloat((base + c) * width, s)
            }
            if (echoActive || tailActive) {
                if (++echoPos >= echoLen) echoPos = 0
                if (tailActive && tailRemaining > 0) tailRemaining--
            }
            base += channels
        }

        inputBuffer.position(inputStart + sizeBytes)
        output.position(sizeBytes)
        output.flip()
    }

    override fun onFlush() {
        resetFxState()
    }

    override fun onReset() {
        resetFxState()
    }
}
