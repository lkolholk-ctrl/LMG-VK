package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One track's prepared TimePitch. PCM is native-order interleaved float.
 * Timestamps and map coordinates are PCM frames, not seconds or buffer offsets.
 * Buffers retain their positions/limits; advance them by the returned frame count.
 * One serialized render owner; construct/configure off the render thread.
 * EOF padding/trimming belongs to the host and is not guessed by this bridge.
 */
class NativeTimePitch(
    sampleRate: Double, private val channels: Int, private val maxFrames: Int,
    scheduled: Boolean, controls: Controls,
) : AutoCloseable {
    data class Controls(val rate: Double, val pitch: Float, val smoothness: Float,
        val coherence: Boolean, val preserveTransients: Boolean)
    data class Segment(val startRate: Double, val endRate: Double,
        val startSourceFrame: Double, val endSourceFrame: Double, val startOutputFrame: Double)
    private var handle: Long
    init {
        require(channels in 1..2 && maxFrames in 1..16384)
        handle = nativeCreate(sampleRate, channels, maxFrames, scheduled, controls.rate,
            controls.pitch, controls.smoothness, controls.coherence, controls.preserveTransients)
        check(handle != 0L)
    }
    private fun alive() { check(handle != 0L) { "TimePitch is closed" } }
    private fun buffer(buffer: ByteBuffer, frames: Int, writable: Boolean) {
        require(frames in 0..maxFrames)
        require(buffer.isDirect && buffer.order() == ByteOrder.nativeOrder())
        require(!writable || !buffer.isReadOnly)
        require(buffer.position() % 4 == 0 && buffer.remaining() >= frames * channels * 4)
    }
    @Synchronized fun enqueue(input: ByteBuffer, frames: Int, sourceFrame: Double): Int {
        alive(); buffer(input, frames, false)
        return nativeEnqueue(handle, input, input.position(), frames, sourceFrame)
    }
    @Synchronized fun dequeue(output: ByteBuffer, frames: Int, outputFrame: Double): Int {
        alive(); buffer(output, frames, true)
        return nativeDequeue(handle, output, output.position(), frames, outputFrame)
    }
    @Synchronized fun configure(controls: Controls) {
        alive(); nativeConfigure(handle, controls.rate, controls.pitch, controls.smoothness,
            controls.coherence, controls.preserveTransients)
    }
    /** Null detaches mapping; an empty list attaches the source identity map. */
    @Synchronized fun setTimeMap(segments: List<Segment>?) {
        alive(); require(segments == null || segments.size <= 16384)
        val values = segments?.let { list -> DoubleArray(list.size * 5).also { data ->
            list.forEachIndexed { index, s ->
                data[index * 5] = s.startRate; data[index * 5 + 1] = s.endRate
                data[index * 5 + 2] = s.startSourceFrame; data[index * 5 + 3] = s.endSourceFrame
                data[index * 5 + 4] = s.startOutputFrame
            }
        } }
        nativeSetTimeMap(handle, values)
    }
    @Synchronized fun reset(inputFrame: Double = 0.0, outputFrame: Double = 0.0) {
        alive(); nativeReset(handle, inputFrame, outputFrame)
    }
    @Synchronized override fun close() {
        if (handle != 0L) { nativeDestroy(handle); handle = 0L }
    }
    private external fun nativeCreate(fs: Double, channels: Int, maxFrames: Int, scheduled: Boolean,
        rate: Double, pitch: Float, smoothness: Float, coherence: Boolean, transients: Boolean): Long
    private external fun nativeEnqueue(handle: Long, input: ByteBuffer, offset: Int, frames: Int, time: Double): Int
    private external fun nativeDequeue(handle: Long, output: ByteBuffer, offset: Int, frames: Int, time: Double): Int
    private external fun nativeConfigure(handle: Long, rate: Double, pitch: Float, smoothness: Float, coherence: Boolean, transients: Boolean)
    private external fun nativeSetTimeMap(handle: Long, segments: DoubleArray?)
    private external fun nativeReset(handle: Long, inputFrame: Double, outputFrame: Double)
    private external fun nativeDestroy(handle: Long)
    companion object { init { System.loadLibrary("lmg_automix_jni") } }
}
