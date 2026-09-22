package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Prepared DSPGraph -> TimePitch stream. All timeline coordinates are PCM
 * frames already resolved by the host. Construct off the rendering thread and
 * reconstruct for a seek. Does not implement out_gain or choose an EOF policy.
 * Rendering and close serialize; use one rendering owner to avoid contention.
 */
class NativeProcessedTrack(
    sampleRate: Double,
    internal val channels: Int,
    internal val maxFrames: Int,
    firstSourceFrame: Long,
    firstOutputFrame: Double,
    scheduled: Boolean,
    controls: NativeTimePitch.Controls,
    initialParameters: Map<String, Double> = emptyMap(),
    writes: List<NativeTrackEffects.Write> = emptyList(),
    segments: List<NativeTimePitch.Segment>? = null,
) : AutoCloseable {
    private var handle: Long
    init {
        require(channels in 1..2 && maxFrames in 1..16384)
        require(initialParameters.size <= 27 && writes.size <= 16384)
        require(segments == null || segments.size <= 16384)
        fun address(id: String): Int {
            require(id.length == 4 && id.all { it.code in 1..127 }) { "Expected a DSP FourCC" }
            return id.fold(0) { value, char -> (value shl 8) or char.code }
        }
        val initialIds = IntArray(initialParameters.size)
        val initialValues = DoubleArray(initialParameters.size)
        initialParameters.entries.forEachIndexed { i, e ->
            initialIds[i] = address(e.key); initialValues[i] = e.value
        }
        val times = LongArray(writes.size)
        val ids = IntArray(writes.size)
        val values = DoubleArray(writes.size)
        writes.forEachIndexed { i, e ->
            times[i] = e.frame; ids[i] = address(e.parameterId); values[i] = e.value
        }
        val map = segments?.let { list -> DoubleArray(list.size * 5).also { data ->
            list.forEachIndexed { i, s ->
                data[i * 5] = s.startRate; data[i * 5 + 1] = s.endRate
                data[i * 5 + 2] = s.startSourceFrame; data[i * 5 + 3] = s.endSourceFrame
                data[i * 5 + 4] = s.startOutputFrame
            }
        } }
        handle = nativeCreate(sampleRate, channels, maxFrames, firstSourceFrame, firstOutputFrame,
            scheduled, controls.rate, controls.pitch, controls.smoothness, controls.coherence,
            controls.preserveTransients, initialIds, initialValues, times, ids, values, map)
        check(handle != 0L)
    }
    private fun alive() { check(handle != 0L) { "Processed track is closed" } }
    private fun buffer(buffer: ByteBuffer, frames: Int, writable: Boolean) {
        require(buffer.isDirect && buffer.order() == ByteOrder.nativeOrder())
        require(!writable || !buffer.isReadOnly)
        require(buffer.position() % 4 == 0 && buffer.remaining() >= frames * channels * 4)
    }
    /** Returns accepted frames. Advance input by that count only. Buffer positions
     * are unchanged. Null input explicitly supplies zeros; it does not signal EOS.
     * Initial parameters override the recovered time-zero graph defaults.
     */
    @Synchronized fun enqueue(input: ByteBuffer?, frames: Int, silenceFlags: Int = 0): Int {
        alive(); require(frames in 0..maxFrames && silenceFlags and 15 == silenceFlags)
        if (input != null) buffer(input, frames, false)
        return nativeEnqueue(handle, input, input?.position() ?: 0, frames, silenceFlags)
    }
    /** Writes only the returned frame count, retaining output position/limit. */
    @Synchronized fun dequeue(output: ByteBuffer, frames: Int, outputFrame: Double): Int {
        alive(); require(frames in 0..maxFrames); buffer(output, frames, true)
        return nativeDequeue(handle, output, output.position(), frames, outputFrame)
    }
    @Synchronized fun inputPositionFrames(): Long { alive(); return nativePosition(handle) }
    @Synchronized fun pendingFrames(): Int { alive(); return nativePending(handle) }
    @Synchronized override fun close() {
        if (handle != 0L) { nativeDestroy(handle); handle = 0L }
    }
    private external fun nativeCreate(fs: Double, channels: Int, capacity: Int, start: Long,
        output: Double, scheduled: Boolean, rate: Double, pitch: Float, smoothness: Float,
        coherence: Boolean, transients: Boolean, initialIds: IntArray, initialValues: DoubleArray,
        frames: LongArray, ids: IntArray, values: DoubleArray, segments: DoubleArray?): Long
    private external fun nativeEnqueue(handle: Long, input: ByteBuffer?, offset: Int, frames: Int, silence: Int): Int
    private external fun nativeDequeue(handle: Long, output: ByteBuffer, offset: Int, frames: Int, time: Double): Int
    private external fun nativePosition(handle: Long): Long
    private external fun nativePending(handle: Long): Int
    private external fun nativeDestroy(handle: Long)
    companion object { init { System.loadLibrary("lmg_automix_jni") } }
}
