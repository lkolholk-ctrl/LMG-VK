package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

/** A reversible, explicitly requested transport probe, NOT a live-mix authorization. */
enum class CueProbeRequest { REQUESTED, NO_PLAN, STALE, ALREADY_ATTEMPTED, CLOSED, DISABLED }
enum class CueProbePhase { IDLE, ARMED, ONE_BUFFER_HELD, PAIR_HELD, RELEASED, INPUT_TRANSFERRED }
enum class CueProbeReason {
    NONE, USER_RELEASE, TIMEOUT, EPOCH_CHANGED, CLOSED, OUTPUT_CHANGED, OUTPUT_RESET,
    PAUSED, ROUTE_CHANGED, CONFIGURATION_CHANGED, PLAYBACK_UNSUPPORTED, SINK_FAILURE,
    THREAD_VIOLATION, INVALID_BUFFER, PCM_GRID_UNRESOLVED, DISCONTINUITY,
    CUE_MISSED, POSITION_PAST_CUE, BUFFER_ALREADY_OFFERED, FORMAT_MISMATCH, DUPLICATE_SIDE,
    BUFFER_LIMIT, OWNER_REJECTED, OWNER_FAILURE,
}

data class CueProbeReport(
    val generation: Long, val revision: Long, val phase: CueProbePhase,
    val reason: CueProbeReason = CueProbeReason.NONE, val blockedMask: Int = 0,
) {
    val canExecute: Boolean get() = false
    val ownsTransitionGain: Boolean get() = false
    // No DSP executor is installed. Native ingress acceptance is reported separately below.
    val pcmAcceptedByExecutor: Boolean get() = false
    val inputAcceptedByOwner: Boolean get() = phase == CueProbePhase.INPUT_TRANSFERRED
}

/** Metadata only. Raw source keys and buffer objects deliberately stay out of snapshots/logs. */
data class CueBufferInfo(
    val firstSourceFrame: Long, val cueSourceFrame: Long, val frames: Int,
    val prefixFrames: Int, val bytes: Int,
)
data class CueCopiedPair(
    val generation: Long, val revision: Long, val format: RenderPcmFormat,
    val outgoing: CueBufferInfo, val incoming: CueBufferInfo,
) {
    val canExecute: Boolean get() = false
}

/** A prepared output-owner contract. No implementation is installed by this package.
 * The future playback adapter must reserve the SAME output sink/clock/gain owner before
 * granting this lease, must emit NO samples before commit, and must revoke on flush/route.
 * Calls occur on the single playback owner. Never implement isCurrent as a hardcoded true.
 */
internal interface CuePlaybackLease {
    fun isCurrent(generation: Long, revision: Long, format: RenderPcmFormat): Boolean
    fun revoke()
}

/** Invisible stage; commit publishes BOTH initial buffers. Offer returns accepted frames
 * WITHOUT changing the supplied view. Failure after commit is terminal, never legacy fallback.
 */
internal interface CueOwnerAdmission {
    fun stage(generation: Long, revision: Long, format: RenderPcmFormat,
        outgoing: ByteBuffer, outInfo: CueBufferInfo, incoming: ByteBuffer, inInfo: CueBufferInfo): Any
    fun commit(ticket: Any): Boolean
    fun abort(ticket: Any)
    fun offer(side: Int, bytes: ByteBuffer, firstFrame: Long): Int
    fun poll(side: Int, destination: ByteBuffer, maxFrames: Int, info: LongArray): Int
}
internal data class CueOutputAnchors(val generation: Long, val revision: Long,
    val outgoingPtsUs: Long, val incomingPtsUs: Long)
internal class CueOwnerTicket internal constructor() {
    override fun toString(): String = "CueOwnerTicket(opaque)"
}
internal enum class CueOwnerPhase { PREPARED, CLAIMED, INPUT_OWNED, ABORTED, RESET_REQUIRED }
internal data class CueOwnerReport(val generation: Long, val revision: Long, val phase: CueOwnerPhase) {
    val canExecute: Boolean get() = false
    val liveDspInstalled: Boolean get() = false
}
internal class PcmOwnerResetRequired : IllegalStateException("PCM owner transfer requires coordinated flush")

/**
 * One gate shared by the existing two sink decorators in ONE ExoPlayer.
 * Main may request/release; only the established playback owner touches codec buffers.
 * Whole previously-unoffered buffers which CONTAIN a cue are held with handleBuffer=false.
 * A pre-cue prefix is held too: this avoids changing the delegate's same-buffer/limit contract.
 * No source position/limit/content is modified by holding or diagnostic copying.
 * Before ownership claim, release/timeout resumes the exact original via its original delegate.
 * Optional explicit admission transfers private copies into preallocated native queues; after
 * claim cancellation requires coordinated flush and NEVER replays those frames to legacy.
 * There is no wait(), sleep(), timer thread, player, output clock or DSP ownership here.
 */
internal class PcmCueGate(
    private val controller: RenderBoundaryController,
    private val nowNanos: () -> Long,
) {
    internal class Attempt(val epoch: RenderBoundaryController.Epoch, val startNanos: Long,
        val maxWaitNanos: Long) {
        val report = AtomicReference(CueProbeReport(epoch.generation, epoch.revision, CueProbePhase.ARMED))
        // Only playback-owner accesses these fields. Main only changes report to RELEASED.
        val holds = arrayOfNulls<Hold>(2)
        val ports = arrayOfNulls<Port>(2)
        var format: RenderPcmFormat? = null
        @Volatile var transfer: OwnerTransfer? = null
    }
    internal class Hold(val attempt: Attempt, val port: Port, val buffer: ByteBuffer,
        val position: Int, val limit: Int, val pts: Long, val info: CueBufferInfo)
    internal class OwnerTransfer(val a: Attempt, val admission: CueOwnerAdmission,
        val nativeTicket: Any, val lease: CuePlaybackLease, val receipt: CueOwnerTicket) {
        val phase = AtomicReference(CueOwnerPhase.PREPARED)
        @Volatile var claimed = false // Written by playback owner; once true, legacy replay is forbidden.
        var aborted = false
    }
    private val attempt = AtomicReference<Attempt?>()
    companion object { const val MAX_HELD_BYTES = 1_048_576 }

    fun request(generation: Long, revision: Long, maxWaitMs: Int): CueProbeRequest {
        require(generation > 0 && revision >= 0)
        require(maxWaitMs in 1..500) { "Probe duration must be 1..500 ms" }
        while (true) {
            val e = controller.epoch()
            if (e.closed) return CueProbeRequest.CLOSED
            if (e.generation != generation || e.revision != revision) return CueProbeRequest.STALE
            if (e.plan == null) return CueProbeRequest.NO_PLAN
            val old = attempt.get()
            // At most ONE attempt per publication token; retries cannot extend a stalled cue forever.
            if (old?.epoch?.generation == e.generation && old.epoch.revision == e.revision)
                return CueProbeRequest.ALREADY_ATTEMPTED
            val next = Attempt(e, nowNanos(), maxWaitMs.toLong() * 1_000_000L)
            if (attempt.compareAndSet(old, next)) {
                old?.let { release(it, CueProbeReason.EPOCH_CHANGED) }
                if (controller.epoch() !== e) {
                    release(next, CueProbeReason.EPOCH_CHANGED)
                    return CueProbeRequest.STALE
                }
                return CueProbeRequest.REQUESTED
            }
        }
    }
    fun releaseRequested() { attempt.get()?.let { release(it, CueProbeReason.USER_RELEASE) } }
    fun snapshot(): CueProbeReport {
        val a = attempt.get() ?: return CueProbeReport(0, 0, CueProbePhase.IDLE)
        live(a)
        return a.report.get()
    }
    private fun release(a: Attempt, reason: CueProbeReason) {
        a.transfer?.let { transfer ->
            while (true) {
                val old = transfer.phase.get()
                val next = when(old) {
                    CueOwnerPhase.PREPARED -> CueOwnerPhase.ABORTED
                    CueOwnerPhase.CLAIMED, CueOwnerPhase.INPUT_OWNED -> CueOwnerPhase.RESET_REQUIRED
                    else -> old
                }
                if (next == old || transfer.phase.compareAndSet(old,next)) break
            }
        }
        while (true) {
            val old = a.report.get()
            if (old.phase == CueProbePhase.RELEASED) return
            if (a.report.compareAndSet(old, old.copy(phase = CueProbePhase.RELEASED,
                    reason = reason, blockedMask = 0))) return
        }
    }
    private fun live(a: Attempt): Boolean {
        val e = controller.epoch()
        if (e.closed) release(a, CueProbeReason.CLOSED)
        else if (attempt.get() !== a || e !== a.epoch) release(a, CueProbeReason.EPOCH_CHANGED)
        // Signed subtraction intentionally handles System.nanoTime's wraparound for short intervals.
        else if (a.transfer?.claimed != true && nowNanos() - a.startNanos >= a.maxWaitNanos)
            release(a, CueProbeReason.TIMEOUT)
        return a.report.get().phase != CueProbePhase.RELEASED
    }
    private fun markHeld(a: Attempt, side: Int, h: Hold): Boolean {
        if (!live(a)) return false
        a.holds[side] = h
        while (true) {
            val old = a.report.get()
            if (!live(a) || old.phase == CueProbePhase.RELEASED) return false
            val mask = old.blockedMask or (1 shl side)
            val next = old.copy(blockedMask = mask, phase =
                if (mask == 3) CueProbePhase.PAIR_HELD else CueProbePhase.ONE_BUFFER_HELD)
            if (a.report.compareAndSet(old, next)) return live(a)
        }
    }

    /**
     * Owner-thread diagnostic copy into caller-preallocated DIRECT, writable buffers.
     * Null means not current/ready. It NEVER advances original buffers or delivers to an executor.
     * On cancellation during copying destination bytes may have changed: caller must discard them.
     * The method changes destination positions only on success. Do not concurrently modify them.
     * Destinations must be independently allocated and disjoint from each other/source memory;
     * distinct ByteBuffer view objects alone do not prove non-aliasing.
     */
    fun copyHeldPair(outgoing: ByteBuffer, incoming: ByteBuffer): CueCopiedPair? {
        if (!controller.owner()) { attempt.get()?.let { release(it, CueProbeReason.THREAD_VIOLATION) }; return null }
        val a = attempt.get() ?: return null
        if (!live(a) || a.report.get().phase != CueProbePhase.PAIR_HELD) return null
        val h0 = a.holds[0] ?: return null
        val h1 = a.holds[1] ?: return null
        val f = a.format ?: return null
        if (!h0.port.contextMatches() || !h1.port.contextMatches()) {
            release(a, CueProbeReason.OUTPUT_CHANGED); return null
        }
        require(outgoing !== incoming) { "Distinct destination buffers required" }
        for ((h, dst) in listOf(h0 to outgoing, h1 to incoming)) {
            require(dst.isDirect && !dst.isReadOnly && dst.remaining() >= h.info.bytes)
            require(dst !== h0.buffer && dst !== h1.buffer)
            if (h.buffer.position() != h.position || h.buffer.limit() != h.limit) {
                release(a, CueProbeReason.INVALID_BUFFER); return null
            }
        }
        val plan = requireNotNull(a.epoch.plan)
        if (h0.port.positionPastCue(plan.outgoingCueFloorUs) ||
            h1.port.positionPastCue(plan.incomingCueFloorUs)) {
            release(a, CueProbeReason.POSITION_PAST_CUE); return null
        }
        // Independent views preserve original positions/limits and destination state on preflight failure.
        val d0 = outgoing.duplicate(); val d1 = incoming.duplicate()
        d0.put(h0.buffer.duplicate()); d1.put(h1.buffer.duplicate())
        if (!live(a)) return null
        outgoing.position(d0.position()); incoming.position(d1.position())
        return CueCopiedPair(a.epoch.generation, a.epoch.revision, f, h0.info, h1.info)
    }

    private fun heldPairValid(a: Attempt): Boolean {
        if (!live(a)) return false
        val h0=a.holds[0] ?: return false; val h1=a.holds[1] ?: return false
        val p=a.epoch.plan ?: return false
        return h0.port.contextMatches() && h1.port.contextMatches() &&
            h0.buffer.position()==h0.position && h0.buffer.limit()==h0.limit &&
            h1.buffer.position()==h1.position && h1.buffer.limit()==h1.limit &&
            !h0.port.positionPastCue(p.outgoingCueFloorUs) && !h1.port.positionPastCue(p.incomingCueFloorUs)
    }
    private fun abortTransfer(t: OwnerTransfer) {
        if (t.aborted) return
        t.aborted=true
        try { t.admission.abort(t.nativeTicket) } finally { t.lease.revoke() }
    }
    /** Playback-owner only. Prepares invisible native queues; no codec acknowledgements. */
    fun prepareOwner(admission: CueOwnerAdmission, lease: CuePlaybackLease): CueOwnerTicket? {
        if(!controller.owner()) return null
        val a=attempt.get() ?: return null
        if(a.transfer!=null || a.report.get().phase!=CueProbePhase.PAIR_HELD || !heldPairValid(a))return null
        val f=a.format ?: return null
        if(!lease.isCurrent(a.epoch.generation,a.epoch.revision,f))return null
        val x=requireNotNull(a.holds[0]);val y=requireNotNull(a.holds[1])
        val receipt=CueOwnerTicket()
        val token=try { admission.stage(a.epoch.generation,a.epoch.revision,f,
            x.buffer.asReadOnlyBuffer(),x.info,y.buffer.asReadOnlyBuffer(),y.info) }
        catch(failure: Throwable) { lease.revoke();release(a,CueProbeReason.OWNER_REJECTED);throw failure }
        val t=try { OwnerTransfer(a,admission,token,lease,receipt) } catch(failure: Throwable) {
            try { admission.abort(token) } finally { lease.revoke();release(a,CueProbeReason.OWNER_REJECTED) }
            throw failure
        }
        a.transfer=t
        if(!heldPairValid(a) || !lease.isCurrent(a.epoch.generation,a.epoch.revision,f)) {
            release(a,CueProbeReason.EPOCH_CHANGED);abortTransfer(t);return null
        }
        return receipt
    }
    /** Short publication lock covers only identity/phase CAS, never JNI, copies or sink calls.
     * Before claim a cancel wins -> originals resume. After claim ANY failure requires flush.
     */
    fun commitOwner(receipt: CueOwnerTicket): Boolean {
        if(!controller.owner())return false
        val a=attempt.get() ?: return false;val t=a.transfer ?: return false
        if(t.receipt !== receipt)return false
        if(t.phase.get()!=CueOwnerPhase.PREPARED) {
            if(!t.claimed && t.phase.get()==CueOwnerPhase.ABORTED)abortTransfer(t)
            return false
        }
        val f=a.format ?: return false
        if(!heldPairValid(a) || !t.lease.isCurrent(a.epoch.generation,a.epoch.revision,f)) {
            release(a,CueProbeReason.OWNER_REJECTED);abortTransfer(t);return false
        }
        val claimed=controller.claimCueOwnership(a.epoch) {
            if(t.phase.compareAndSet(CueOwnerPhase.PREPARED,CueOwnerPhase.CLAIMED)) {
                t.claimed=true;true
            } else false
        }
        if(!claimed) { release(a,CueProbeReason.EPOCH_CHANGED);abortTransfer(t);return false }
        // Install quarantine BEFORE crossing JNI: uncertain commit cannot fall back to legacy.
        for(side in 0..1) requireNotNull(a.ports[side]).own(t,side,requireNotNull(a.holds[side]))
        try {
            if(!t.admission.commit(t.nativeTicket) || !heldPairValid(a) ||
                !t.lease.isCurrent(a.epoch.generation,a.epoch.revision,f) ||
                !t.phase.compareAndSet(CueOwnerPhase.CLAIMED,CueOwnerPhase.INPUT_OWNED)) {
                release(a,CueProbeReason.OWNER_FAILURE);abortTransfer(t);return false
            }
            while(true) {
                val old=a.report.get()
                if(old.phase==CueProbePhase.RELEASED) { release(a,CueProbeReason.OWNER_FAILURE);abortTransfer(t);return false }
                if(a.report.compareAndSet(old,old.copy(phase=CueProbePhase.INPUT_TRANSFERRED,blockedMask=0)))break
            }
            return true
        } catch(failure: Throwable) {
            release(a,CueProbeReason.OWNER_FAILURE)
            try { abortTransfer(t) } catch(_: Throwable) { }
            throw failure
        }
    }
    /** Read owned input under the same lease/epoch, never directly into an AudioTrack.
     * On exception, discard destination bytes/info; destination cursor is not published.
     */
    fun readOwner(receipt: CueOwnerTicket, side: Int, destination: ByteBuffer,
        maxFrames: Int, info: LongArray): Int {
        if(!controller.owner())throw PcmOwnerResetRequired()
        val a=attempt.get() ?: throw PcmOwnerResetRequired()
        val t=a.transfer ?: throw PcmOwnerResetRequired()
        require(side in 0..1 && maxFrames>=0 && info.size==3)
        require(destination.isDirect && !destination.isReadOnly)
        fun valid(): Boolean = t.receipt === receipt && live(a) && t.phase.get()==CueOwnerPhase.INPUT_OWNED &&
            a.ports.all { it?.contextMatches()==true } &&
            t.lease.isCurrent(a.epoch.generation,a.epoch.revision,requireNotNull(a.format))
        try {
            if(!valid())throw PcmOwnerResetRequired()
            val view=destination.duplicate().order(destination.order())
            val n=t.admission.poll(side,view,maxFrames,info)
            if(n !in 0..maxFrames || info[1]!=n.toLong() || !valid())throw PcmOwnerResetRequired()
            check(view.position()-destination.position()==n*requireNotNull(a.format).channels*4)
            destination.position(view.position());return n
        } catch(failure: Throwable) {
            release(a,CueProbeReason.OWNER_FAILURE)
            try { abortTransfer(t) } catch(_: Throwable) { }
            throw failure
        }
    }
    /** Read-only receipt check for a separately reserved output port; no output authority is granted. */
    /** Original output-buffer timestamps, not caller-supplied or reconstructed song time. */
    fun heldOutputAnchors(): CueOutputAnchors? {
        if (!controller.owner()) return null
        val a=attempt.get() ?: return null
        if (a.report.get().phase != CueProbePhase.PAIR_HELD || !heldPairValid(a)) return null
        return CueOutputAnchors(a.epoch.generation, a.epoch.revision,
            requireNotNull(a.holds[0]).pts, requireNotNull(a.holds[1]).pts)
    }

    fun hasCommittedOwner(receipt: CueOwnerTicket): Boolean {
        if (!controller.owner()) return false
        val a = attempt.get() ?: return false
        val t = a.transfer ?: return false
        return t.receipt === receipt && live(a) && t.phase.get() == CueOwnerPhase.INPUT_OWNED &&
            a.ports.all { it?.contextMatches() == true } &&
            t.lease.isCurrent(a.epoch.generation, a.epoch.revision, requireNotNull(a.format))
    }

    fun ownerSnapshot(): CueOwnerReport? {
        val a=attempt.get() ?: return null;live(a)
        val t=a.transfer ?: return null
        return CueOwnerReport(a.epoch.generation,a.epoch.revision,t.phase.get())
    }

    fun port(endpoint: RenderBoundaryEndpoint): Port = Port(endpoint)

    internal inner class Port(private val endpoint: RenderBoundaryEndpoint) {
        private var token: Any? = null
        private var identity: RenderOutputIdentity? = null
        private var format: RenderPcmFormat? = null
        private var lastEnd: Long? = null
        private var greatestOfferedEnd: Long? = null
        private var retry: ByteBuffer? = null
        private var retryPts = 0L
        private var retryLimit = 0
        private var retryPosition = 0
        private var retryEnd: Long? = null
        private var hold: Hold? = null
        private var invalidUntilReset = false
        private var owned: OwnerTransfer? = null
        private var ownedSide = -1
        private var initialOwned: Hold? = null
        private var ownedRetry: ByteBuffer? = null
        private var ownedPosition = 0
        private var ownedInitialPosition = 0
        private var ownedLimit = 0
        private var ownedPts = 0L
        private var ownedFirst = 0L
        private var nextOwnedFrame = 0L
        internal fun own(t: OwnerTransfer, side: Int, h: Hold) {
            owned=t;ownedSide=side;initialOwned=h;ownedRetry=null
            nextOwnedFrame=h.info.firstSourceFrame+h.info.frames
        }

        fun cancel(reason: CueProbeReason, discard: Boolean = false) {
            if (!controller.owner()) {
                attempt.get()?.let { release(it, CueProbeReason.THREAD_VIOLATION) }; return
            }
            // A newer request may have replaced the mailbox while the old pair was staged.
            // Retained codec holds keep the old transfer reachable until owner-thread cleanup.
            hold?.attempt?.transfer?.let { t ->
                if(!t.claimed) { release(t.a, reason); abortTransfer(t) }
            }
            attempt.get()?.let { a ->
                release(a, reason)
                a.transfer?.let { if(!it.claimed && it.phase.get()==CueOwnerPhase.ABORTED)abortTransfer(it) }
            }
            owned?.let { t ->
                release(t.a,reason)
                abortTransfer(t)
                if(discard) { owned=null;initialOwned=null;ownedRetry=null }
            }
            hold = null
            if (discard) clearStream()
        }
        private fun clearStream() {
            token = null; identity = null; format = null
            retry = null; hold = null; lastEnd = null; greatestOfferedEnd = null
            retryEnd = null; invalidUntilReset = false
        }
        fun ownsInput(): Boolean = owned != null
        fun positionPastCue(cueUs: Long): Boolean = endpoint.rendererSongPositionUs?.let { it > cueUs } == true
        fun contextMatches(): Boolean = token === endpoint.streamToken &&
            sameIdentity(identity, endpoint.identity) && format == endpoint.format &&
            endpoint.seenEpoch === controller.epoch() && endpoint.problem == null
        private fun sameIdentity(a: RenderOutputIdentity?, b: RenderOutputIdentity?): Boolean =
            a?.source == b?.source && a?.periodUid == b?.periodUid &&
                a?.windowSequenceNumber == b?.windowSequenceNumber &&
                a?.periodPositionInWindowUs == b?.periodPositionInWindowUs &&
                a?.rendererOffsetUs == b?.rendererOffsetUs

        /** Exact arithmetic for a unique zero-origin sample grid within ONE integer-us timestamp tick.
         * This is a supported transport domain, not an arbitrary timing-jitter correction.
         */
        private fun firstFrame(pts: Long, id: RenderOutputIdentity, f: RenderPcmFormat): Long? = try {
            val us = Math.addExact(Math.subtractExact(pts, id.rendererOffsetUs), id.periodPositionInWindowUs)
            if (us < 0 || us > 86_400_000_000L) null else {
                val n = Math.multiplyExact(us, f.sampleRate.toLong())
                val frame = (n + 500_000L) / 1_000_000L
                val distance = kotlin.math.abs(Math.subtractExact(frame * 1_000_000L, n))
                if (distance <= f.sampleRate.toLong()) frame else null
            }
        } catch (_: ArithmeticException) { null }

        private fun forwardOwned(t: OwnerTransfer, buffer: ByteBuffer, pts: Long, units: Int): Boolean {
            fun reset(): Nothing {
                release(t.a,CueProbeReason.OWNER_FAILURE)
                try { abortTransfer(t) } catch(_: Throwable) { }
                throw PcmOwnerResetRequired()
            }
            try {
                val f=t.a.format ?: reset()
                if(!live(t.a) || t.phase.get()!=CueOwnerPhase.INPUT_OWNED || !contextMatches() || units!=1 ||
                    !t.lease.isCurrent(t.a.epoch.generation,t.a.epoch.revision,f))reset()
                val h=initialOwned
                if(h!=null) {
                    if(buffer !== h.buffer || buffer.position()!=h.position || buffer.limit()!=h.limit || pts!=h.pts)reset()
                    // Both private copies are already committed. Acknowledge each original once,
                    // only on THAT renderer's callback; the other codec buffer is never mutated here.
                    buffer.position(h.limit)
                    initialOwned=null;hold=null
                    return true
                }
                if(ownedRetry==null) {
                    if(buffer.remaining()%f.bytesPerFrame!=0)reset()
                    val first=firstFrame(pts,requireNotNull(identity),f) ?: reset()
                    if(first!=nextOwnedFrame)reset()
                    if(!buffer.hasRemaining())return true
                    ownedRetry=buffer;ownedInitialPosition=buffer.position();ownedPosition=buffer.position()
                    ownedLimit=buffer.limit();ownedPts=pts;ownedFirst=first
                }
                if(ownedRetry !== buffer || buffer.position()!=ownedPosition || buffer.limit()!=ownedLimit || pts!=ownedPts)reset()
                val first=ownedFirst+(buffer.position()-ownedInitialPosition)/f.bytesPerFrame
                val frames=buffer.remaining()/f.bytesPerFrame
                val accepted=t.admission.offer(ownedSide,buffer.asReadOnlyBuffer(),first)
                if(accepted !in 0..frames)reset()
                // Recheck cancellation before acknowledging; accepted obsolete data is discarded,
                // never replayed to legacy. Coordinated flush releases the codec's old originals.
                if(!live(t.a) || t.phase.get()!=CueOwnerPhase.INPUT_OWNED)reset()
                buffer.position(buffer.position()+accepted*f.bytesPerFrame);ownedPosition=buffer.position()
                nextOwnedFrame=first+accepted
                if(!buffer.hasRemaining()) { ownedRetry=null;return true }
                return false
            } catch(failure: Throwable) {
                release(t.a,CueProbeReason.OWNER_FAILURE)
                try { abortTransfer(t) } catch(_: Throwable) { }
                throw failure
            }
        }

        fun forward(buffer: ByteBuffer, pts: Long, units: Int, write: () -> Boolean): Boolean {
            if (!controller.owner()) {
                attempt.get()?.let { release(it, CueProbeReason.THREAD_VIOLATION) }
                if(owned!=null)throw PcmOwnerResetRequired()
                return write()
            }
            owned?.let { return forwardOwned(it,buffer,pts,units) }
            val a = attempt.get()
            hold?.attempt?.transfer?.let { old ->
                if(old.a !== a && !old.claimed) { release(old.a,CueProbeReason.EPOCH_CHANGED);abortTransfer(old) }
            }
            a?.transfer?.let { if(!it.claimed && it.phase.get()==CueOwnerPhase.ABORTED)abortTransfer(it) }
            val id = endpoint.identity
            val f = endpoint.format
            if (token !== endpoint.streamToken || !sameIdentity(identity, id) || format != f) {
                if (token != null && a != null) release(a, CueProbeReason.OUTPUT_CHANGED)
                clearStream(); token = endpoint.streamToken; identity = id; format = f
            }
            val oldHold = hold
            if (oldHold != null && (a !== oldHold.attempt || !live(oldHold.attempt))) hold = null
            val relevant = a?.epoch?.plan?.let { id?.source == it.outgoing || id?.source == it.incoming } == true
            val active = a != null && live(a) && relevant

            if (retry != null && (retry !== buffer || pts != retryPts || retryLimit != buffer.limit() ||
                    retryPosition != buffer.position())) {
                invalidUntilReset = true
                if (a != null) release(a, CueProbeReason.INVALID_BUFFER)
            }
            if (hold != null) {
                val h = requireNotNull(hold)
                if (h.buffer !== buffer || h.position != buffer.position() || h.limit != buffer.limit() || h.pts != pts) {
                    release(h.attempt, CueProbeReason.INVALID_BUFFER); hold = null; invalidUntilReset = true
                } else if (endpoint.rendererSongPositionUs?.let { it >
                        (if (endpoint.identity?.source == h.attempt.epoch.plan?.outgoing)
                            h.attempt.epoch.plan!!.outgoingCueFloorUs else h.attempt.epoch.plan!!.incomingCueFloorUs)
                    } == true) {
                    release(h.attempt, CueProbeReason.POSITION_PAST_CUE); hold = null
                } else if (live(h.attempt)) return false else hold = null
            }
            val valid = !invalidUntilReset && id != null && f?.supported == true &&
                units == 1 && buffer.remaining() % f.bytesPerFrame == 0 &&
                endpoint.seenEpoch === controller.epoch() && endpoint.problem == null
            val start = if (valid) firstFrame(pts, requireNotNull(id), requireNotNull(f)) else null
            val frames = if (valid) buffer.remaining() / requireNotNull(f).bytesPerFrame else 0
            val end = if (start != null) Math.addExact(start, frames.toLong()) else null
            if (retry == null && start != null && frames > 0 && lastEnd != null && start != lastEnd) {
                if (a != null) release(a, CueProbeReason.DISCONTINUITY)
                invalidUntilReset = true
            }
            if (active && a != null) {
                val p = requireNotNull(a.epoch.plan)
                val side = if (id?.source == p.outgoing) 0 else 1
                val seconds = if (side == 0) p.outgoingCueSeconds else p.incomingCueSeconds
                // Explicit LMG frame-seam policy, round nearest; ties towards positive infinity.
                val cue = if (f?.supported == true) floor(seconds * f.sampleRate + 0.5).toLong() else -1
                when {
                    !valid || invalidUntilReset -> release(a, CueProbeReason.PLAYBACK_UNSUPPORTED)
                    buffer.remaining() > MAX_HELD_BYTES -> release(a, CueProbeReason.BUFFER_LIMIT)
                    endpoint.rendererSongPositionUs?.let { it >
                        (if (side == 0) p.outgoingCueFloorUs else p.incomingCueFloorUs)
                    } == true -> release(a, CueProbeReason.POSITION_PAST_CUE)
                    start == null -> release(a, CueProbeReason.PCM_GRID_UNRESOLVED)
                    p.outgoing.windowUid == p.incoming.windowUid -> release(a, CueProbeReason.DUPLICATE_SIDE)
                    greatestOfferedEnd?.let { it > cue } == true -> release(a, CueProbeReason.BUFFER_ALREADY_OFFERED)
                    start > cue -> release(a, CueProbeReason.CUE_MISSED)
                    a.ports[side] != null && a.ports[side] !== this -> release(a, CueProbeReason.DUPLICATE_SIDE)
                    a.format != null && a.format != f -> release(a, CueProbeReason.FORMAT_MISMATCH)
                    else -> {
                        a.ports[side] = this; a.format = f
                        if (frames > 0 && requireNotNull(end) > cue) {
                            if (retry != null) release(a, CueProbeReason.BUFFER_ALREADY_OFFERED)
                            else {
                                val info = CueBufferInfo(start, cue, frames, (cue - start).toInt(), buffer.remaining())
                                val h = Hold(a, this, buffer, buffer.position(), buffer.limit(), pts, info)
                                if (markHeld(a, side, h)) { hold = h; return false }
                            }
                        }
                    }
                }
            }
            // A released original buffer is forwarded unchanged; there is no replay copy or second sink.
            if (retry == null) {
                retry = buffer; retryPts = pts; retryLimit = buffer.limit(); retryPosition = buffer.position()
                retryEnd = end
                if (end != null && frames > 0) greatestOfferedEnd = maxOf(greatestOfferedEnd ?: end, end)
            }
            val before = buffer.position(); val limit = buffer.limit()
            val result = try { write() } catch (failure: Throwable) {
                if (a != null) release(a, CueProbeReason.SINK_FAILURE)
                invalidUntilReset = true
                throw failure
            }
            if (buffer.position() < before || buffer.limit() != limit || result != !buffer.hasRemaining()) {
                invalidUntilReset = true
                if (a != null) release(a, CueProbeReason.INVALID_BUFFER)
            }
            retryPosition = buffer.position()
            if (result) {
                if (retryEnd != null && buffer.position() > before) lastEnd = retryEnd
                retry = null; retryEnd = null
            }
            return result
        }
    }
}
