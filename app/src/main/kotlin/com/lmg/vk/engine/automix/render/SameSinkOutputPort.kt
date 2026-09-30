package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** Thin interface implemented by the EXISTING AudioSink. No factory/device constructor. */
internal interface PcmOutputBackend {
    fun write(buffer: ByteBuffer, ptsUs: Long): Boolean
    fun volume(value: Float)
    fun positionUs(): Long
    fun pending(): Boolean
    /** Return false when the backend does not implement an actual drain operation. */
    fun finish(): Boolean = false
}

internal enum class OutputPortPhase { IDLE, RESERVED, ACTIVE, WRITING, RESET_REQUIRED }
internal enum class OutputPortFault { NONE, REVOKED, CONTEXT_CHANGED, SINK_FAILURE, BUFFER_CONTRACT, THREAD_VIOLATION }
internal data class OutputPortSnapshot(
    val phase: OutputPortPhase, val fault: OutputPortFault,
    val acceptedBytes: Long, val completeFrames: Long, val remainderBytes: Int,
    val pendingBuffer: Boolean, val sinkPositionUs: Long?,
) {
    // This object owns a sink write domain, NOT ExoPlayer's period/MediaClock transaction.
    val canExecute: Boolean get() = false
    val liveDspInstalled: Boolean get() = false
    val rendererClockReserved: Boolean get() = false
}
internal data class OutputWriteReceipt(
    val acceptedBytes: Int, val newlyCompletedFrames: Int, val bufferFinished: Boolean,
)
/** Reusable playback-owned receipt. The immutable API below is diagnostic-only. */
internal class OutputWriteState {
    var acceptedBytes = 0
    var newlyCompletedFrames = 0
    var bufferFinished = false
}
internal class OutputPortResetRequired : IllegalStateException("Output port requires coordinated reset")

/**
 * Exclusive writer and gain separation for ONE already configured sink. All methods except
 * Ticket.revoke are playback-owner-only. Reservation is not CuePlaybackLease: source-period
 * clock arbitration, DSP consumption, decoder preroll and handback are separate obligations.
 *
 * The reserved writer accepts already encoded sink-format PCM. It does NOT resample, convert,
 * apply out_gain, infer EOF, change sink configuration, or allocate another audio output.
 */
internal class SameSinkOutputPort(private val backend: PcmOutputBackend) : RenderOutputAttachment {
    internal class Ticket internal constructor(
        internal val port: SameSinkOutputPort, internal val serial: Long,
        internal val generation: Long, internal val revision: Long,
        internal val format: RenderPcmFormat, internal val basePtsUs: Long,
        internal val epochCurrent: () -> Boolean, internal val version: Long,
        internal val mayWrite: Boolean,
    ) {
        private val revoked = AtomicBoolean(false)
        fun revoke() { revoked.set(true) } // No sink/native calls from a publishing thread.
        internal fun revoked(): Boolean = revoked.get()
        override fun toString(): String = "OutputTicket(opaque)"
    }
    private var owner: Thread? = null
    private var context: Any? = null
    private var format: RenderPcmFormat? = null
    private var version = 0L
    private var serial = 0L
    private var ticket: Ticket? = null
    private var phase = OutputPortPhase.IDLE
    private var fault = OutputPortFault.NONE
    private var master = 1f
    private var fade = 1f
    private var splitGainObserved = false
    private var gainDetached = false
    private var offered = false
    private var acceptedBytes = 0L
    private var pending: ByteBuffer? = null
    private var pendingLimit = 0
    private var pendingPosition = 0
    private var pendingPts = 0L
    private var pendingFirstFrame = 0L
    private var pendingPacket = 0L
    private var lastPacket = 0L
    private var lastSinkPosition = Long.MIN_VALUE

    private fun thread(bind: Boolean = false) {
        val t = Thread.currentThread()
        if (owner == null && bind) owner = t
        if (owner != null && owner !== t) throw IllegalStateException("Output port playback thread mismatch")
    }
    private fun unit(v: Float) { require(v.isFinite() && v in 0f..1f) }
    private fun fail(why: OutputPortFault): Nothing {
        phase = OutputPortPhase.RESET_REQUIRED; fault = why
        ticket?.revoke()
        throw OutputPortResetRequired()
    }
    /** Live prefix must preserve legacy amplitude even when the old device queue is empty. */
    internal fun livePrefixIsUnity(): Boolean { thread(); return splitGainObserved && fade == 1f }
    fun bindOutput(token: Any?, pcm: RenderPcmFormat?) {
        thread(bind = true)
        if (token !== context || pcm != format) {
            if (ticket != null) {
                ticket?.revoke(); phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.CONTEXT_CHANGED
            }
            version = Math.addExact(version, 1); context = token; format = pcm
        }
    }
    /** Ordinary Player volume messages retain their old direct-set semantics when idle. */
    fun playerVolume(value: Float) {
        thread(); unit(value); master = value; fade = 1f
        try { backend.volume(value) } catch (failure: Throwable) {
            if (ticket != null) { phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; ticket?.revoke() }
            throw failure
        }
    }
    /** Typed normal-volume branch of the patched renderer, distinct from legacy direct calls. */
    fun confirmedPlayerVolume(value: Float) {
        thread(); unit(value); splitGainObserved = true
        master = value
        // This typed message is master gain, not a replacement transition curve.
        // Keep the last transition factor when idle; detach ONLY it while owned.
        try { backend.volume(if (gainDetached) master else fade * master) }
        catch (failure: Throwable) {
            if (ticket != null) { phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; ticket?.revoke() }
            throw failure
        }
    }
    /** The new fork message carries both components, rather than their irreversible product. */
    fun transitionVolume(transition: Float, player: Float) {
        thread(); unit(transition); unit(player)
        fade = transition; master = player; splitGainObserved = true
        // Only after activation does output PCM include its own transition gain. Player gain
        // and focus ducking MUST still reach the same sink. A revoked active writer keeps
        // that separation until an actual reset; queued owned PCM must not be faded twice.
        try { backend.volume(if (gainDetached) master else fade * master) }
        catch (failure: Throwable) {
            if (ticket != null) { phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; ticket?.revoke() }
            throw failure
        }
    }
    fun reserve(generation: Long, revision: Long, pcm: RenderPcmFormat, basePtsUs: Long,
        epochCurrent: () -> Boolean, mayWrite: Boolean = true): Ticket? {
        thread()
        require(generation > 0 && revision >= 0 && basePtsUs >= 0 && pcm.supported)
        if (phase != OutputPortPhase.IDLE || ticket != null || context == null ||
            format != pcm || !splitGainObserved || !epochCurrent()) return null
        val t = Ticket(this, Math.addExact(serial, 1), generation, revision, pcm,
            basePtsUs, epochCurrent, version, mayWrite)
        // A suppressed companion is permitted only when its OLD sink has no queued audio.
        if (!mayWrite && backend.pending()) return null
        serial = t.serial; ticket = t; phase = OutputPortPhase.RESERVED; fault = OutputPortFault.NONE
        acceptedBytes = 0; offered = false; pending = null; lastPacket = 0; lastSinkPosition = Long.MIN_VALUE
        // Reservation does not modify gain of already queued/playing legacy PCM.
        valid(t)
        return t
    }
    /** Caller first commits source ownership and proves the renderer timeline contract.
     * A global sink gain change must not retroactively alter queued legacy audio.
     */
    fun activate(t: Ticket): Boolean {
        valid(t)
        if (phase != OutputPortPhase.RESERVED) return false
        if (fade != 1f && backend.pending()) return false
        try {
            gainDetached = true
            backend.volume(master)
            valid(t)
            phase = OutputPortPhase.ACTIVE
            return true
        } catch (failure: Throwable) {
            phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; t.revoke()
            throw failure
        }
    }
    private fun valid(t: Ticket) {
        thread()
        if (ticket !== t || t.port !== this || phase == OutputPortPhase.RESET_REQUIRED ||
            t.revoked() || version != t.version || !t.epochCurrent()) fail(OutputPortFault.REVOKED)
    }
    /** Called only on the delegate branch AFTER the cue gate. An owned packet never reaches it. */
    fun checkLegacyWrite() {
        thread()
        if (ticket != null || phase != OutputPortPhase.IDLE) fail(OutputPortFault.BUFFER_CONTRACT)
    }
    /** Caller preserves the exact packet object/content until bufferFinished, even on zero acceptance.
     * The SAME buffer, PTS and limit are retried. Buffer movement is the sole acceptance authority.
     * A cancellation during write still records accepted bytes, then requires reset: no replay.
     */
    fun write(t: Ticket, packetId: Long, firstOutputFrame: Long, bytes: ByteBuffer): OutputWriteReceipt {
        val result = OutputWriteState()
        writeInto(t, packetId, firstOutputFrame, bytes, result)
        return OutputWriteReceipt(result.acceptedBytes, result.newlyCompletedFrames, result.bufferFinished)
    }
    /** Steady-state entry: no receipt allocation on a sink retry. */
    fun writeInto(t: Ticket, packetId: Long, firstOutputFrame: Long, bytes: ByteBuffer, resultInto: OutputWriteState) {
        valid(t)
        check(phase == OutputPortPhase.ACTIVE || phase == OutputPortPhase.WRITING) { "Output not activated" }
        check(t.mayWrite) { "Companion output cannot write" }
        val bpf = t.format.bytesPerFrame
        require(bytes.isDirect && packetId > 0 && firstOutputFrame >= 0)
        if (pending == null) {
            require(bytes.hasRemaining() && bytes.position() % bpf == 0 && bytes.remaining() % bpf == 0)
            require(bytes.remaining() <= MAX_PACKET_BYTES && acceptedBytes % bpf == 0L)
            require(packetId > lastPacket && firstOutputFrame == acceptedBytes / bpf)
            val delta = Math.multiplyExact(firstOutputFrame, 1_000_000L) / t.format.sampleRate
            pendingPts = Math.addExact(t.basePtsUs, delta)
            pending = bytes; pendingPosition = bytes.position(); pendingLimit = bytes.limit()
            pendingPacket = packetId; pendingFirstFrame = firstOutputFrame
        } else if (pending !== bytes || pendingLimit != bytes.limit() || pendingPosition != bytes.position() ||
            pendingPacket != packetId || pendingFirstFrame != firstOutputFrame) fail(OutputPortFault.BUFFER_CONTRACT)
        val before = bytes.position()
        val framesBefore = acceptedBytes / bpf
        offered = true; phase = OutputPortPhase.WRITING
        var result = false
        var error: Throwable? = null
        try { result = backend.write(bytes, pendingPts) } catch (failure: Throwable) { error = failure }
        val after = bytes.position()
        val shapeValid = bytes.limit() == pendingLimit && after >= before && after <= pendingLimit
        if (shapeValid) {
            acceptedBytes = Math.addExact(acceptedBytes, (after - before).toLong()); pendingPosition = after
        }
        if (error != null) {
            phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; t.revoke()
            throw error // Preserve the exact original sink exception, including partial side effects.
        }
        if (!shapeValid || (result && after != pendingLimit)) fail(OutputPortFault.BUFFER_CONTRACT)
        valid(t)
        if (result) { pending = null; lastPacket = packetId }
        resultInto.acceptedBytes = after - before
        resultInto.newlyCompletedFrames = (acceptedBytes / bpf - framesBefore).toInt()
        resultInto.bufferFinished = result
    }
    /** Sample the existing sink clock. This is NOT an ExoPlayer renderer/period clock binding. */
    fun sampleSinkClock(t: Ticket): Long? {
        val result = sampleSinkClockValue(t)
        return if (result == Long.MIN_VALUE) null else result
    }
    fun sampleSinkClockValue(t: Ticket): Long {
        valid(t)
        val position = try { backend.positionUs() } catch (failure: Throwable) {
            phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; t.revoke(); throw failure
        }
        valid(t)
        // AudioSink.CURRENT_POSITION_NOT_SET is Long.MIN_VALUE. No inferred wall-clock fallback.
        if (position == Long.MIN_VALUE) return Long.MIN_VALUE
        if (lastSinkPosition != Long.MIN_VALUE && position < lastSinkPosition) fail(OutputPortFault.CONTEXT_CHANGED)
        lastSinkPosition = position
        return position
    }
    /** No write attempt (even one returning zero) => reversible. Otherwise coordinated flush only. */
    fun rollback(t: Ticket): Boolean {
        thread()
        if (ticket !== t || phase == OutputPortPhase.RESET_REQUIRED || offered) return false
        t.revoke()
        try { backend.volume(fade * master) } catch (failure: Throwable) {
            phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.SINK_FAILURE; throw failure
        }
        ticket = null; gainDetached = false; phase = OutputPortPhase.IDLE; fault = OutputPortFault.NONE
        return true
    }
    fun invalidate() {
        thread()
        if (ticket != null) { ticket?.revoke(); phase = OutputPortPhase.RESET_REQUIRED; fault = OutputPortFault.CONTEXT_CHANGED }
    }
    /** Invoke only AFTER the real delegate.flush/reset/release completed successfully. */
    fun afterActualReset() {
        thread(); ticket?.revoke(); ticket = null; pending = null; offered = false
        version = Math.addExact(version, 1); context = null; format = null; splitGainObserved = false; gainDetached = false
        phase = OutputPortPhase.IDLE; fault = OutputPortFault.NONE; acceptedBytes = 0; lastSinkPosition = Long.MIN_VALUE
    }
    fun drain(t: Ticket): Boolean {
        valid(t)
        if (pending != null) return false
        if (!backend.finish()) return false
        valid(t)
        return !backend.pending()
    }
    fun completeFrameCount(): Long {
        thread()
        return acceptedBytes / (ticket?.format?.bytesPerFrame ?: 1)
    }
    fun snapshot(): OutputPortSnapshot {
        thread()
        val bpf = ticket?.format?.bytesPerFrame ?: 1
        return OutputPortSnapshot(phase, fault, acceptedBytes, acceptedBytes / bpf,
            (acceptedBytes % bpf).toInt(), pending != null, if (lastSinkPosition == Long.MIN_VALUE) null else lastSinkPosition)
    }
    companion object { const val MAX_PACKET_BYTES = 1_048_576 }
}
