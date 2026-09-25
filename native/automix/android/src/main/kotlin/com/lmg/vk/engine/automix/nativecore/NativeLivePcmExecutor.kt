package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
) : AutoCloseable {
    private var handle: Long
    private var owner: Thread? = null
    init {
        require(sampleRate in 8000..192000 && channels in 1..2 && maximumFrames in 1..4096)
        check(nativeProtocol() == 1)
        handle = nativeCreate(planWords.copyOf(), sampleRate, channels, maximumFrames, effectStepFrames, generation, revision)
        check(handle != 0L)
    }
    private fun alive() {
        check(handle != 0L) { "Live PCM executor closed" }
        val t = Thread.currentThread()
        if (owner == null) owner = t
        check(owner === t) { "Live PCM playback thread mismatch" }
    }
    private fun buffer(b: ByteBuffer, frames: Int, width: Int, write: Boolean) {
        require(frames in 0..maximumFrames && b.isDirect && b.order() == ByteOrder.LITTLE_ENDIAN)
        require(!write || !b.isReadOnly)
        require(b.position() % width == 0 && b.remaining() >= frames * channels * width)
    }
    @Synchronized fun push(side: Int, pcm: ByteBuffer, frames: Int, firstFrame: Long): Int {
        alive(); require(side in 0..1); buffer(pcm,frames,4,false)
        return nativePush(handle,side,pcm,pcm.position(),frames,firstFrame)
    }
    @Synchronized fun endInput(side: Int, endFrame: Long) { alive(); require(side in 0..1); nativeEof(handle,side,endFrame) }
    /** Does not move output.position(). Only the returned frames are valid. */
    @Synchronized fun render(output: ByteBuffer, frames: Int, bytesPerSample: Int): Int {
        alive(); require(bytesPerSample == 2 || bytesPerSample == 4); buffer(output,frames,bytesPerSample,true)
        return nativeRender(handle,output,output.position(),frames,bytesPerSample)
    }
    @Synchronized fun encodePrefix(input: ByteBuffer, output: ByteBuffer, frames: Int, bytesPerSample: Int) {
        alive(); require(input !== output && (bytesPerSample == 2 || bytesPerSample == 4))
        buffer(input,frames,4,false); buffer(output,frames,bytesPerSample,true)
        nativeEncodePrefix(handle,input,input.position(),output,output.position(),frames,bytesPerSample)
    }
    @Synchronized fun sourceTimeSeconds(side: Int, outputFrame: Double): Double {
        alive(); require(side in 0..1 && outputFrame.isFinite()); return nativeSourceTime(handle,side,outputFrame)
    }
    @Synchronized fun stats(into: LongArray) { alive(); require(into.size == 19); nativeStats(handle,into) }
    @Synchronized override fun close() { if(handle != 0L) { alive(); nativeDestroy(handle); handle=0L } }
    private external fun nativeProtocol(): Int
    private external fun nativeCreate(plan: LongArray, fs: Int, channels: Int, frames: Int, cadence: Int, generation: Long, revision: Long): Long
    private external fun nativePush(id: Long, side: Int, input: ByteBuffer, offset: Int, frames: Int, first: Long): Int
    private external fun nativeEof(id: Long, side: Int, end: Long)
    private external fun nativeRender(id: Long, output: ByteBuffer, offset: Int, frames: Int, width: Int): Int
    private external fun nativeEncodePrefix(id: Long, input: ByteBuffer, offset: Int, output: ByteBuffer, outOffset: Int, frames: Int, width: Int)
    private external fun nativeSourceTime(id: Long, side: Int, frame: Double): Double
    private external fun nativeStats(id: Long, output: LongArray)
    private external fun nativeDestroy(id: Long)
    companion object { init { System.loadLibrary("lmg_automix_jni") } }
}
