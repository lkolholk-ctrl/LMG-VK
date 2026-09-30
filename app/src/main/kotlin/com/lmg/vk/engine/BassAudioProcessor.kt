package com.lmg.vk.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Transparent, per-sink meter. No ML/VAD engine is fed from the AutoMix PCM path. */
@UnstableApi
class BassAudioProcessor(private val sink: SinkAudioState? = null) : BoundedPcmAudioProcessor() {
    private val meter = PcmBandMeter(sink)
    private var width = 0
    private var channelCount = 0
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        width = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount !in 1..2) return AudioProcessor.AudioFormat.NOT_SET
        channelCount = inputAudioFormat.channelCount
        prepareOutputStorage()
        meter.configure(inputAudioFormat.sampleRate, channelCount, width)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val bytes = boundedBytes(inputBuffer, channelCount * width)
        if (AudioReactor.hasListeners) {
            meter.process(inputBuffer, inputBuffer.position(), bytes / (channelCount * width))
            if (sink == null) {
                val levels = meter.packedLevels
                AudioReactor.low = SinkAudioState.band(levels, 0)
                AudioReactor.mid = SinkAudioState.band(levels, 1)
                AudioReactor.high = SinkAudioState.band(levels, 2)
            }
        }
        copy(inputBuffer, replaceOutputBuffer(bytes), bytes)
    }
    override fun onFlush() { meter.reset() }
    override fun onReset() { meter.reset() }
}
