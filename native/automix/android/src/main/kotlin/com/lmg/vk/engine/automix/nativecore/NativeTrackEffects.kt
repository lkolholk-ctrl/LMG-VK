package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One track's recovered DSP graph, before per-track output volume and summation.
 * Input is native-order interleaved float PCM, already on an explicitly resolved
 * frame timeline. This class does not decode, stretch, choose a transition or
 * replace Media3 crossfade. Construct off the render thread; close after use.
 * [firstFrame] and Write.frame share the caller's resolved PCM clock. Each call
 * consumes its frame count on that clock; ByteBuffer positions are byte offsets only.
 *
 * Process and close serialize on this instance. Use one render-thread owner to
 * avoid monitor contention. A seek/new schedule requires a new instance.
 */
class NativeTrackEffects(
    sampleRate: Double,
    private val channels: Int,
    private val maxFrames: Int,
    firstFrame: Long,
    writes: List<Write> = emptyList(),
) : AutoCloseable {
    data class Write(val frame: Long, val parameterId: String, val value: Double)

    private var handle: Long

    init {
        require(channels in 1..2 && maxFrames in 1..16384)
        require(writes.size <= 16384)
        val frames = LongArray(writes.size)
        val ids = IntArray(writes.size)
        val values = DoubleArray(writes.size)
        writes.forEachIndexed { index, write ->
            require(write.parameterId.length == 4 && write.parameterId.all { it.code in 1..127 }) {
                "Expected a four-character DSP graph parameter; out_gain/ts_rate are separate"
            }
            frames[index] = write.frame
            ids[index] = write.parameterId.fold(0) { id, char -> (id shl 8) or char.code }
            values[index] = write.value
        }
        handle = nativeCreate(sampleRate, channels, maxFrames, firstFrame, frames, ids, values)
        check(handle != 0L) { "Native graph creation failed" }
    }

    /**
     * Processes exactly [frames] without changing buffer positions or limits.
     * Exact in-place processing is supported; partially overlapping views are
     * rejected. Null input drains tails using zero PCM. Silence bits correspond
     * to input/send/dry/wet Gain boxes, not inferred PCM silence.
     */
    @Synchronized
    fun process(input: ByteBuffer?, output: ByteBuffer, frames: Int, silenceFlags: Int = 0) {
        check(handle != 0L) { "Track effects are closed" }
        require(frames in 0..maxFrames && silenceFlags and 15 == silenceFlags)
        val bytes = frames * channels * Float.SIZE_BYTES
        require(output.isDirect && !output.isReadOnly && output.order() == ByteOrder.nativeOrder())
        require(output.position() % Float.SIZE_BYTES == 0 && output.remaining() >= bytes)
        if (input != null) {
            require(input.isDirect && input.order() == ByteOrder.nativeOrder())
            require(input.position() % Float.SIZE_BYTES == 0 && input.remaining() >= bytes)
        }
        nativeProcess(handle, input, input?.position() ?: 0, output, output.position(), frames, silenceFlags)
    }

    @Synchronized
    fun positionFrames(): Long {
        check(handle != 0L) { "Track effects are closed" }
        return nativePosition(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(rate: Double, channels: Int, maxFrames: Int, firstFrame: Long,
        frames: LongArray, ids: IntArray, values: DoubleArray): Long
    private external fun nativeProcess(handle: Long, input: ByteBuffer?, inputOffset: Int,
        output: ByteBuffer, outputOffset: Int, frames: Int, silence: Int)
    private external fun nativePosition(handle: Long): Long
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("lmg_automix_jni") }
    }
}
