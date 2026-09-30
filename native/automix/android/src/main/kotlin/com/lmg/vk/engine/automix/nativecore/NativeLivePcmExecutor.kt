package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** Bounded native DSP owner. Construction prepares coefficients/storage; steady-state
 * methods belong to one playback thread. This object cannot authorize an AudioTrack. */
class NativeLivePcmExecutor(
    planWords: LongArray,
    val sampleRate: Int,
    val channels: Int,
    val maximumFrames: Int,
    generation: Long,
    revision: Long,
    effectStepFrames: Int = 256,
    primeOutgoing: Boolean = false,
) : AutoCloseable {
    @Volatile private var handle: Long
    private val owner = AtomicReference<Thread?>()
    val outgoingPrerollFrames: Long
    val outgoingPrerollStartFrame: Long
    init {
        require(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
        require(sampleRate in 8000..192000 && channels in 1..2 && maximumFrames in 1..4096)
        check(nativeProtocol() == 1)
        val info = if (primeOutgoing) LongArray(2) else null
        handle = if (info != null) nativeCreatePrimed(planWords.copyOf(), sampleRate, channels,
            maximumFrames, effectStepFrames, generation, revision, info)
        else nativeCreate(planWords.copyOf(), sampleRate, channels, maximumFrames, effectStepFrames, generation, revision)
        outgoingPrerollFrames = info?.get(0) ?: 0L
        outgoingPrerollStartFrame = info?.get(1) ?: -1L
        check(handle != 0L)
    }
    private fun alive() {
        val t = Thread.currentThread()
        if (owner.get() == null) owner.compareAndSet(null, t)
        check(owner.get() === t) { "Live PCM playback thread mismatch" }
        check(handle != 0L) { "Live PCM executor closed" }
    }
    private fun buffer(b: ByteBuffer, frames: Int, width: Int, write: Boolean) {
        require(frames in 0..maximumFrames && b.isDirect && b.order() == ByteOrder.LITTLE_ENDIAN)
        require(!write || !b.isReadOnly)
        require(b.position() % width == 0 && b.remaining() >= frames * channels * width)
    }
    fun push(side: Int, pcm: ByteBuffer, frames: Int, firstFrame: Long): Int {
        alive(); require(side in 0..1); buffer(pcm,frames,4,false)
        return nativePush(handle,side,pcm,pcm.position(),frames,firstFrame)
    }
    fun endInput(side: Int, endFrame: Long) { alive(); require(side in 0..1); nativeEof(handle,side,endFrame) }
    /** Does not move output.position(). Only the returned frames are valid. */
    fun render(output: ByteBuffer, frames: Int, bytesPerSample: Int): Int {
        alive(); require(bytesPerSample == 2 || bytesPerSample == 4); buffer(output,frames,bytesPerSample,true)
        return nativeRender(handle,output,output.position(),frames,bytesPerSample)
    }
    fun encodePrefix(input: ByteBuffer, output: ByteBuffer, frames: Int, bytesPerSample: Int) {
        alive(); require(input !== output && (bytesPerSample == 2 || bytesPerSample == 4))
        buffer(input,frames,4,false); buffer(output,frames,bytesPerSample,true)
        nativeEncodePrefix(handle,input,input.position(),output,output.position(),frames,bytesPerSample)
    }
    fun sourceTimeSeconds(side: Int, outputFrame: Double): Double {
        alive(); require(side in 0..1 && outputFrame.isFinite()); return nativeSourceTime(handle,side,outputFrame)
    }
    fun stats(into: LongArray) { alive(); require(into.size == 19); nativeStats(handle,into) }
    override fun close() { if(handle != 0L) { alive(); nativeDestroy(handle); handle=0L } }
    private external fun nativeProtocol(): Int
    private external fun nativeCreate(plan: LongArray, fs: Int, channels: Int, frames: Int, cadence: Int, generation: Long, revision: Long): Long
    private external fun nativeCreatePrimed(plan: LongArray, fs: Int, channels: Int, frames: Int, cadence: Int, generation: Long, revision: Long, info: LongArray): Long
    private external fun nativePush(id: Long, side: Int, input: ByteBuffer, offset: Int, frames: Int, first: Long): Int
    private external fun nativeEof(id: Long, side: Int, end: Long)
    private external fun nativeRender(id: Long, output: ByteBuffer, offset: Int, frames: Int, width: Int): Int
    private external fun nativeEncodePrefix(id: Long, input: ByteBuffer, offset: Int, output: ByteBuffer, outOffset: Int, frames: Int, width: Int)
    private external fun nativeSourceTime(id: Long, side: Int, frame: Double): Double
    private external fun nativeStats(id: Long, output: LongArray)
    private external fun nativeDestroy(id: Long)
    companion object { init { System.loadLibrary("lmg_automix_jni") } }
}
