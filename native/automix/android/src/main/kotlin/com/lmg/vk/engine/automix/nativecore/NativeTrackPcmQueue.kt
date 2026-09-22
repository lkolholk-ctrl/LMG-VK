package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Per-track transport between decoded PCM and the native graph. Owns [track].
 * Single rendering owner; prepare and close outside concurrent rendering.
 * Output is interleaved native-order float PCM. The consumer advances [output]'s
 * position only for bytes it accepted; an outstanding output block is never
 * overwritten. This transport does not decide source/output timestamp mapping,
 * EOF padding, transition gain, or whether a track should use AutoMix.
 */
class NativeTrackPcmQueue(
    private val track: NativeProcessedTrack,
    private val channels: Int,
    private val maxFrames: Int,
    firstOutputFrame: Double,
) : AutoCloseable {
    enum class Encoding(val bytesPerSample: Int) { PCM16(2), FLOAT32(4) }
    private val scratch: ByteBuffer
    val output: ByteBuffer
    private var nextOutputFrame = firstOutputFrame
    var outputStartFrame: Double = firstOutputFrame
        private set
    private var closed = false

    init {
        require(channels == track.channels && maxFrames == track.maxFrames)
        require(firstOutputFrame.isFinite() && firstOutputFrame >= 0.0)
        scratch = ByteBuffer.allocateDirect(maxFrames * channels * 4).order(ByteOrder.nativeOrder())
        output = ByteBuffer.allocateDirect(maxFrames * channels * 4).order(ByteOrder.nativeOrder())
        output.limit(0)
    }

    /** Advances only accepted input bytes. Supports decoder buffers at arbitrary
     * byte offsets without changing their byte order or limit. A zero result
     * requires the caller to drain output before retrying the same input.
     */
    fun offer(input: ByteBuffer, encoding: Encoding): Int {
        check(!closed)
        val stride = channels * encoding.bytesPerSample
        require(input.remaining() % stride == 0) { "Incomplete PCM frame" }
        val frames = minOf(maxFrames, input.remaining() / stride)
        if (frames == 0) return 0
        // An unconsumed downstream buffer puts backpressure on the whole path.
        if (output.hasRemaining()) return 0
        val position = input.position()
        scratch.clear()
        val littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        fun word(offset: Int, bytes: Int): Int {
            var value = 0
            for (i in 0 until bytes) {
                val shift = if (littleEndian) i * 8 else (bytes - 1 - i) * 8
                value = value or ((input.get(offset + i).toInt() and 255) shl shift)
            }
            return value
        }
        for (i in 0 until frames * channels) {
            val offset = position + i * encoding.bytesPerSample
            val sample = when (encoding) {
                Encoding.PCM16 -> word(offset, 2).toShort().toFloat() / 32768f
                Encoding.FLOAT32 -> Float.fromBits(word(offset, 4))
            }
            require(sample.isFinite()) { "Nonfinite PCM sample" }
            scratch.putFloat(sample)
        }
        scratch.flip()
        val accepted = track.enqueue(scratch, frames)
        input.position(position + accepted * stride)
        return accepted
    }

    /** Returns the same buffer until completely consumed. Does not supply
     * invented silence when the native processor needs more source input.
     */
    fun pull(): ByteBuffer {
        check(!closed)
        if (output.hasRemaining()) return output
        check(nextOutputFrame <= 0x1FFFFFFFFFFFFFL - maxFrames) { "Output frame precision exhausted" }
        output.clear()
        val frames = try {
            track.dequeue(output, maxFrames, nextOutputFrame)
        } catch (error: Throwable) {
            output.limit(0)
            throw error
        }
        output.limit(frames * channels * 4)
        outputStartFrame = nextOutputFrame
        nextOutputFrame += frames
        return output
    }

    override fun close() {
        if (!closed) {
            closed = true
            output.position(0); output.limit(0)
            track.close()
        }
    }
}
