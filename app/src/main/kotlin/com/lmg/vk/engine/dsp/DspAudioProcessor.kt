package com.lmg.vk.engine.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** One native instance per sink. Configuration is pending until Media3 flushes;
 * configure may run while the old stream is still draining. */
@UnstableApi
class DspAudioProcessor(
    private val diagnosticProbe: com.lmg.vk.debug.PlaybackAudioDiagnostics.Probe? = null,
) : BaseAudioProcessor() {
    private var pending = AudioFormat.NOT_SET
    private var active = AudioFormat.NOT_SET
    private var handle = 0L
    private var endedAtBoundary = false
    private var discontinuity = true
    private var width = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        pending = if (inputAudioFormat.sampleRate in 44100..192000 &&
            inputAudioFormat.channelCount in 1..2 &&
            inputAudioFormat.encoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT))
            inputAudioFormat else AudioFormat.NOT_SET
        return pending
    }

    fun markDiscontinuity() { discontinuity = true }

    override fun onFlush() {
        if (active != pending) {
            releaseNative()
            active = pending
            if (active != AudioFormat.NOT_SET) {
                width = if (active.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
                if (NativeDsp.available) handle = NativeDsp.nativeCreate(active.sampleRate, active.channelCount)
            }
        } else if (handle != 0L && (discontinuity || !endedAtBoundary)) {
            NativeDsp.nativeReset(handle)
        }
        if (active != AudioFormat.NOT_SET) {
            replaceOutputBuffer(MAX_FRAMES * active.channelCount * width).limit(0)
            // Keep BaseAudioProcessor's empty-output sentinel after preallocation,
            // including EOS on a stream that never submitted a frame.
            getOutput()
        }
        endedAtBoundary = false
        discontinuity = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val frameBytes = active.channelCount * width
        check(frameBytes > 0 && inputBuffer.remaining() % frameBytes == 0)
        val frames = minOf(inputBuffer.remaining() / frameBytes, MAX_FRAMES)
        val bytes = frames * frameBytes
        val offset = inputBuffer.position()
        val output = replaceOutputBuffer(bytes)
        if (handle != 0L && inputBuffer.isDirect &&
            NativeDsp.nativeProcess(handle, inputBuffer, offset, output, frames, width)) {
            inputBuffer.position(offset + bytes)
            output.position(bytes)
        } else {
            // A missing/failed native library must never turn playback into silence.
            val limit = inputBuffer.limit()
            inputBuffer.limit(offset + bytes)
            try { output.put(inputBuffer) } finally { inputBuffer.limit(limit) }
        }
        output.flip()
        diagnosticProbe?.processed(output, width)
    }

    // Zero-lookahead mode has no buffered frames to drain and adds no priming silence.
    override fun onQueueEndOfStream() { endedAtBoundary = true }
    override fun onReset() {
        releaseNative(); active = AudioFormat.NOT_SET; pending = AudioFormat.NOT_SET
        width = 0; endedAtBoundary = false; discontinuity = true
    }
    private fun releaseNative() {
        if (handle != 0L) { NativeDsp.nativeDestroy(handle); handle = 0L }
    }
    private companion object { const val MAX_FRAMES = 4096 }
}
