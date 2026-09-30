package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** Preallocated two-source PCM admission queues. NOT a Player, DSP graph, output writer or clock.
 * Allocate/close outside real-time callbacks. After the first stage, use exactly one owner thread.
 * Input bytes: explicit little-endian PCM16 or float32. Read output: native-order float32.
 * Stage and push never advance caller buffer cursors. Reads advance the destination only.
 */
class NativePcmOwnerIngress(val channels: Int, val bytesPerSample: Int, val capacityFrames: Int) : AutoCloseable {
    class Ticket internal constructor(internal val owner: NativePcmOwnerIngress, internal val serial: Long) {
        override fun toString(): String = "PcmOwnerTicket(opaque)"
    }
    data class Input(val buffer: ByteBuffer, val firstFrame: Long, val cueFrame: Long)
    @Volatile private var handle: Long
    private val thread = AtomicReference<Thread?>()
    private var serial = 0L
    private var ticket: Ticket? = null
    init {
        require(channels in 1..2 && bytesPerSample in listOf(2,4) && capacityFrames in 1..262144)
        check(nativeProtocol() == 1) { "PCM ingress JNI ABI mismatch" }
        handle = nativeCreate(channels, bytesPerSample, capacityFrames)
        check(handle != 0L)
    }
    private fun owner() {
        val current = Thread.currentThread()
        if (thread.get() == null) thread.compareAndSet(null, current)
        check(thread.get() === current) { "PCM owner thread mismatch" }
        check(handle != 0L) { "PCM owner closed" }
    }
    private fun input(input: ByteBuffer, staging: Boolean = true) {
        require(input.isDirect && input.remaining() % (channels*bytesPerSample) == 0)
        require(input.remaining() <= 1_048_576)
        if(staging)require(input.remaining() / (channels*bytesPerSample) <= capacityFrames)
    }
    fun stage(generation: Long, revision: Long, outgoing: Input, incoming: Input): Ticket {
        owner(); require(generation > 0 && revision >= 0)
        check(ticket == null) { "Owner already staged" }
        input(outgoing.buffer); input(incoming.buffer)
        val id = Math.addExact(serial,1L)
        val result = Ticket(this,id) // Allocate before native side effects.
        nativeStage(handle,id,generation,revision,
            outgoing.buffer,outgoing.buffer.position(),outgoing.buffer.remaining(),outgoing.firstFrame,outgoing.cueFrame,
            incoming.buffer,incoming.buffer.position(),incoming.buffer.remaining(),incoming.firstFrame,incoming.cueFrame)
        serial=id;ticket=result
        return result
    }
    fun commit(value: Ticket): Boolean {
        owner();return value.owner === this && ticket === value && nativeCommit(handle,value.serial)
    }
    fun abort(value: Ticket): Boolean {
        owner();if(value.owner !== this || ticket !== value)return false
        val result=nativeAbort(handle,value.serial)
        // A postcommit native abort is terminal; a subsequent stage is rejected natively.
        if(result)ticket=null
        return result
    }
    fun push(side: Int, input: ByteBuffer, firstFrame: Long): Int {
        owner();require(side in 0..1); input(input, false)
        if(!input.hasRemaining())return 0
        return nativePush(handle,side,input,input.position(),input.remaining(),firstFrame)
    }
    /** info[0]=first source frame; info[1]=frames read; info[2]=1 for pre-cue prefix.
     * Pre-cue and post-cue samples are never merged in a single read.
     */
    fun read(side: Int, destination: ByteBuffer, maxFrames: Int, info: LongArray): Int {
        val n = readWithoutMoving(side, destination, maxFrames, info)
        destination.position(destination.position()+n*channels*4)
        return n
    }
    /** Playback gate publishes the cursor only after its generation/lease recheck. */
    fun readWithoutMoving(side: Int, destination: ByteBuffer, maxFrames: Int, info: LongArray): Int {
        owner();require(side in 0..1 && maxFrames in 0..capacityFrames && info.size==3)
        require(destination.isDirect && !destination.isReadOnly && destination.order()==ByteOrder.nativeOrder())
        require(destination.position()%4==0 && destination.remaining()>=maxFrames*channels*4)
        val n=nativeRead(handle,side,destination,destination.position(),maxFrames,info)
        check(n in 0..maxFrames && info[1]==n.toLong())
        return n
    }
    /** phase, nextRead, nextWrite, cue, accepted, read, discarded, queued. */
    fun stats(side: Int, target: LongArray) {
        owner();require(side in 0..1 && target.size==8);nativeStats(handle,side,target)
    }
    override fun close() {
        if(handle==0L)return
        // Explicit same-owner cleanup once bound; avoids destruction during use.
        owner()
        nativeDestroy(handle);handle=0;ticket=null
    }
    private external fun nativeProtocol(): Int
    private external fun nativeCreate(channels: Int, encoding: Int, capacity: Int): Long
    private external fun nativeStage(handle: Long, ticket: Long, generation: Long, revision: Long,
        a: ByteBuffer, ao: Int, an: Int, af: Long, ac: Long,
        b: ByteBuffer, bo: Int, bn: Int, bf: Long, bc: Long)
    private external fun nativeCommit(handle: Long, ticket: Long): Boolean
    private external fun nativeAbort(handle: Long, ticket: Long): Boolean
    private external fun nativePush(handle: Long, side: Int, input: ByteBuffer, offset: Int, bytes: Int, first: Long): Int
    private external fun nativeRead(handle: Long, side: Int, output: ByteBuffer, offset: Int, frames: Int, meta: LongArray): Int
    private external fun nativeStats(handle: Long, side: Int, meta: LongArray)
    private external fun nativeDestroy(handle: Long)
    companion object { init { System.loadLibrary("lmg_automix_jni") } }
}
