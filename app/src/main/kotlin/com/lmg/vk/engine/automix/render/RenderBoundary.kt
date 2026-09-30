package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

/** Playlist occurrence, not a title match and not a renderer number. Never log source keys. */
class RenderSourceKey(
    val windowUid: Any,
    val mediaId: String,
    val uri: String,
    val cacheKey: String?,
    val recordingRevision: String?,
) {
    override fun equals(other: Any?): Boolean = other is RenderSourceKey &&
        windowUid == other.windowUid && mediaId == other.mediaId && uri == other.uri &&
        cacheKey == other.cacheKey && recordingRevision == other.recordingRevision
    override fun hashCode(): Int = 31 * windowUid.hashCode() + mediaId.hashCode()
    override fun toString(): String = "RenderSourceKey(redacted)"
}

/** Native plan attachment: bytes are owned, immutable to callers and contain no raw song analysis. */
class RenderExecutionData internal constructor(words: LongArray) {
    private val words = words.copyOf()
    internal fun copyWords(): LongArray = words.copyOf()
}
data class LivePlaybackReport(val phase: String = "OFF", val generation: Long = 0,
    val revision: Long = 0, val liveDspInstalled: Boolean = false,
    val actualPlaybackLease: Boolean = false, val sourcePrerollDiscarded: Long = 0,
    val reason: String? = null)
internal interface RenderPlaybackAttachment {
    fun requestLive(generation: Long, revision: Long): Boolean = false
    fun liveReport(): LivePlaybackReport = LivePlaybackReport()
    fun cancelPreparation() {}
    fun ignoreOwnedHandoff(windowUid: Any, mediaId: String): Boolean = false
}

/** Values already produced by Stage 4a. There is no styling, DSP or tempo math here. */
class RenderBoundaryPlan private constructor(
    val generation: Long,
    val revision: Long,
    val outgoing: RenderSourceKey,
    val incoming: RenderSourceKey,
    val outgoingCueFloorUs: Long,
    val incomingCueFloorUs: Long,
    val styleId: Int,
    val outgoingCueSeconds: Double,
    val incomingCueSeconds: Double,
    val execution: RenderExecutionData?,
) {
    val canExecute: Boolean get() = false
    companion object {
        fun create(generation: Long, revision: Long, outgoing: RenderSourceKey,
            incoming: RenderSourceKey, outgoingCueSeconds: Double, incomingCueSeconds: Double,
            styleId: Int, execution: RenderExecutionData? = null): RenderBoundaryPlan {
            require(generation > 0 && revision >= 0 && (styleId == 8 || styleId == 9 || styleId == 12))
            require(outgoing.mediaId.isNotBlank() && incoming.mediaId.isNotBlank())
            require(outgoing.uri.isNotBlank() && incoming.uri.isNotBlank())
            fun lowerBound(seconds: Double): Long {
                require(seconds.isFinite() && seconds >= 0.0 && seconds <= 86_400.0)
                // Conservative microsecond deadline for a metadata gate, NOT a render-frame conversion.
                return floor(seconds * 1_000_000.0).toLong()
            }
            return RenderBoundaryPlan(generation,revision,outgoing,incoming,
                lowerBound(outgoingCueSeconds),lowerBound(incomingCueSeconds),styleId,
                outgoingCueSeconds,incomingCueSeconds,execution)
        }
    }
}

/** Concrete OUTPUT stream supplied by the patched MediaCodecRenderer's output queue. */
class RenderOutputIdentity(
    val source: RenderSourceKey,
    val periodUid: Any,
    val windowSequenceNumber: Long,
    val periodPositionInWindowUs: Long,
    val rendererOffsetUs: Long,
) {
    override fun toString(): String = "RenderOutputIdentity(redacted)"
}

data class RenderPcmFormat(val sampleRate: Int, val channels: Int, val bytesPerSample: Int,
    val identityChannelMapping: Boolean = true, val encoderDelay: Int = 0, val encoderPadding: Int = 0) {
    val supported: Boolean get() = sampleRate in 8000..192000 && channels in 1..2 &&
        (bytesPerSample == 2 || bytesPerSample == 4) && identityChannelMapping && encoderDelay == 0 && encoderPadding == 0
    val bytesPerFrame: Int get() = channels * bytesPerSample
}

enum class RenderBoundaryStatus {
    NO_PLAN, WAITING_FOR_OUTPUT_STREAMS, OUTGOING_BOUND, INCOMING_BOUND,
    BOTH_STREAMS_BOUND, CUE_ALREADY_FORWARDED, POSITION_PAST_CUE,
    UNSUPPORTED_FORMAT, FORMAT_MISMATCH, AMBIGUOUS_OCCURRENCE,
    DUPLICATE_RENDERER_BINDING, DISCONTINUOUS_PCM, OUTPUT_CONTEXT_UNAVAILABLE,
    OUTPUT_RESET, ROUTE_CHANGED, PLAYBACK_PARAMETERS_UNSUPPORTED,
    THREAD_VIOLATION, SINK_FAILURE, CLOSED,
}

/** BOTH_STREAMS_BOUND is NOT decoder readiness, ownership of queued PCM or permission to play. */
data class RenderBoundaryReport(
    val generation: Long,
    val revision: Long,
    val status: RenderBoundaryStatus,
    val boundMask: Int = 0,
    val styleId: Int? = null,
) {
    val canExecute: Boolean get() = false
    val ownsPcm: Boolean get() = false
    val ownsTransitionGain: Boolean get() = false
    val executionBlock: String get() = "RENDER_EXECUTOR_AND_GAIN_HANDOFF_NOT_INSTALLED"
}

/** Opaque validation receipt only. It has no play/commit/seek/setVolume method. */
class RenderBoundaryReservation internal constructor(
    internal val epoch: Any, internal val a: Any, internal val b: Any,
    internal val aVersion: Long, internal val bVersion: Long,
) {
    val canExecute: Boolean get() = false
    override fun toString(): String = "RenderBoundaryReservation(non-executable)"
}

/**
 * One controller per ONE ExoPlayer. Main publishes immutable plans through a single CAS mailbox.
 * Endpoint state belongs exclusively to that player's playback thread. Sink calls are never
 * executed under the short publication/claim monitor; only the epoch CAS is serialized there, and no PCM sample copies are retained.
 * A buffer reference is kept only for the same-buffer retry contract of the delegate.
 * The mailbox contains command and report together: an old callback cannot overwrite a new epoch.
 */
class RenderBoundaryController(cueClockNanos: () -> Long = System::nanoTime) {
    internal class Epoch(val plan: RenderBoundaryPlan?, val generation: Long, val revision: Long,
        val closed: Boolean = false)
    private data class Mail(val epoch: Epoch, val report: RenderBoundaryReport)
    private val mail = AtomicReference(Mail(Epoch(null,0,0),
        RenderBoundaryReport(0,0,RenderBoundaryStatus.NO_PLAN)))
    private val renderThread = AtomicReference<Thread?>()
    private val endpoints = arrayOfNulls<RenderBoundaryEndpoint>(2)
    @Volatile internal var playbackAttachment: RenderPlaybackAttachment? = null
    fun requestLivePlayback(generation: Long, revision: Long): Boolean =
        playbackAttachment?.requestLive(generation, revision) ?: false
    fun livePlaybackReport(): LivePlaybackReport = playbackAttachment?.liveReport() ?: LivePlaybackReport()

    @Volatile internal var preparationExecutor: java.util.concurrent.Executor? = null
        private set
    /** Install on service construction, before either renderer starts. */
    fun installPreparationExecutor(executor: java.util.concurrent.Executor) {
        check(renderThread.get() == null)
        preparationExecutor = executor
    }
    fun cancelLivePreparation() { playbackAttachment?.cancelPreparation() }
    internal fun currentPcmFormat(source: RenderSourceKey): RenderPcmFormat? {
        check(owner())
        var selected: RenderPcmFormat? = null
        for (endpoint in endpoints) {
            if (endpoint?.identity?.source != source || endpoint.problem != null) continue
            val pcm = endpoint.format ?: continue
            if (!pcm.supported || selected != null) return null
            selected = pcm
        }
        return selected
    }

    internal val cueGate = PcmCueGate(this, cueClockNanos)
    fun requestCueProbe(generation: Long, revision: Long, maxWaitMs: Int = 100): CueProbeRequest =
        cueGate.request(generation, revision, maxWaitMs)
    fun releaseCueProbe() = cueGate.releaseRequested()
    fun cueProbeSnapshot(): CueProbeReport = cueGate.snapshot()
    internal fun copyHeldCuePair(outgoing: ByteBuffer, incoming: ByteBuffer): CueCopiedPair? =
        cueGate.copyHeldPair(outgoing, incoming)
    private var endpointCount = 0
    private val terminalStatuses = setOf(RenderBoundaryStatus.THREAD_VIOLATION,
        RenderBoundaryStatus.OUTPUT_RESET, RenderBoundaryStatus.ROUTE_CHANGED,
        RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED, RenderBoundaryStatus.SINK_FAILURE,
        RenderBoundaryStatus.DISCONTINUOUS_PCM, RenderBoundaryStatus.CUE_ALREADY_FORWARDED,
        RenderBoundaryStatus.POSITION_PAST_CUE)

    /** Factory-thread-only registration. Array order never assigns outgoing/incoming roles. */
    fun newEndpoint(): RenderBoundaryEndpoint {
        check(renderThread.get() == null) { "Create all sinks before rendering starts" }
        check(endpointCount < 2) { "Expected two audio sinks in one player" }
        val endpoint = RenderBoundaryEndpoint(this)
        endpoints[endpointCount++] = endpoint
        return endpoint
    }
    @Synchronized fun offer(plan: RenderBoundaryPlan) {
        while (true) {
            val old = mail.get()
            if (old.epoch.closed || plan.generation < old.epoch.generation ||
                (plan.generation == old.epoch.generation && plan.revision < old.epoch.revision)) return
            val e=Epoch(plan,plan.generation,plan.revision)
            if (mail.compareAndSet(old,Mail(e,RenderBoundaryReport(e.generation,e.revision,
                RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS,styleId=plan.styleId)))) return
        }
    }
    /** Synchronous publication barrier; call on every observation invalidation, not a delayed collector. */
    @Synchronized fun invalidate(generation: Long, revision: Long = 0) {
        require(generation >= 0 && revision >= 0)
        while (true) {
            val old=mail.get(); if(old.epoch.closed || generation < old.epoch.generation ||
                (generation == old.epoch.generation && revision < old.epoch.revision))return
            val e=Epoch(null,generation,revision)
            if(mail.compareAndSet(old,Mail(e,RenderBoundaryReport(generation,revision,RenderBoundaryStatus.NO_PLAN))))return
        }
    }
    @Synchronized fun close() {
        while(true) {
            val old=mail.get(); if(old.epoch.closed)return
            val e=Epoch(null,old.epoch.generation,old.epoch.revision,true)
            if(mail.compareAndSet(old,Mail(e,RenderBoundaryReport(e.generation,e.revision,RenderBoundaryStatus.CLOSED))))return
        }
    }
    fun snapshot(): RenderBoundaryReport = mail.get().report
    // Linearizes the irreversible input-claim against publication. No external IO under this lock.
    @Synchronized internal fun claimCueOwnership(expected: Epoch, claim: () -> Boolean): Boolean =
        mail.get().epoch === expected && !expected.closed && claim()
    internal fun prepareCueOwner(admission: CueOwnerAdmission, lease: CuePlaybackLease): CueOwnerTicket? =
        cueGate.prepareOwner(admission,lease)
    internal fun commitCueOwner(ticket: CueOwnerTicket): Boolean = cueGate.commitOwner(ticket)
    internal fun readCueOwner(ticket: CueOwnerTicket, side: Int, destination: ByteBuffer,
        maxFrames: Int, info: LongArray): Int = cueGate.readOwner(ticket,side,destination,maxFrames,info)
    internal fun cueOwnerSnapshot(): CueOwnerReport? = cueGate.ownerSnapshot()
    internal fun hasCommittedCueOwner(ticket: CueOwnerTicket): Boolean = cueGate.hasCommittedOwner(ticket)
    internal fun heldCueOutputAnchors(): CueOutputAnchors? = cueGate.heldOutputAnchors()
    internal fun reserveExecutionCue(): RenderBoundaryReservation? = cueGate.executionReservation()
    internal fun abortNativeCueOwner() { check(owner()); cueGate.abortNativeOwner() }
    internal fun epoch(): Epoch = mail.get().epoch
    internal fun owner(): Boolean {
        val current=Thread.currentThread()
        val previous=renderThread.get()
        if(previous === current)return true
        if(previous == null && renderThread.compareAndSet(null,current))return true
        report(epoch(),RenderBoundaryStatus.THREAD_VIOLATION)
        return false
    }
    internal fun report(epoch: Epoch, status: RenderBoundaryStatus, mask: Int=0) {
        while(true) {
            val old=mail.get()
            if(old.epoch !== epoch || epoch.closed)return
            // A second rendering owner invalidates the entire epoch, not just one callback.
            if (old.report.status in terminalStatuses && status != old.report.status) return
            if(old.report.status==status && old.report.boundMask==mask && old.report.styleId==epoch.plan?.styleId)return
            val r=RenderBoundaryReport(epoch.generation,epoch.revision,status,mask,epoch.plan?.styleId)
            if(old.report == r || mail.compareAndSet(old,Mail(epoch,r)))return
        }
    }
    internal fun evaluate(epoch: Epoch) {
        if(epoch.plan == null || epoch.closed)return
        val p=epoch.plan
        if(p.outgoing.windowUid == p.incoming.windowUid) {
            report(epoch,RenderBoundaryStatus.AMBIGUOUS_OCCURRENCE); return
        }
        var a:RenderBoundaryEndpoint?=null; var b:RenderBoundaryEndpoint?=null
        for(endpoint in endpoints) {
            if(endpoint == null || endpoint.seenEpoch !== epoch)continue
            val source=endpoint.identity?.source ?: continue
            if(source != p.outgoing && source != p.incoming)continue
            endpoint.problem?.let { report(epoch,it);return }
            if(source == p.outgoing) {
                if(a!=null){report(epoch,RenderBoundaryStatus.DUPLICATE_RENDERER_BINDING);return};a=endpoint
            }
            if(source == p.incoming) {
                if(b!=null){report(epoch,RenderBoundaryStatus.DUPLICATE_RENDERER_BINDING);return};b=endpoint
            }
        }
        val mask=(if(a!=null)1 else 0) or (if(b!=null)2 else 0)
        if ((a!=null && a.acceptedEndValue>p.outgoingCueFloorUs) ||
            (b!=null && b.acceptedEndValue>p.incomingCueFloorUs)) {
            report(epoch,RenderBoundaryStatus.CUE_ALREADY_FORWARDED,mask);return
        }
        if ((a!=null && a.rendererPositionValue>p.outgoingCueFloorUs) ||
            (b!=null && b.rendererPositionValue>p.incomingCueFloorUs)) {
            report(epoch,RenderBoundaryStatus.POSITION_PAST_CUE,mask);return
        }
        if(a!=null && b!=null && a.format!=b.format) {
            report(epoch,RenderBoundaryStatus.FORMAT_MISMATCH,mask);return
        }
        report(epoch,when(mask){1->RenderBoundaryStatus.OUTGOING_BOUND;2->RenderBoundaryStatus.INCOMING_BOUND;
            3->RenderBoundaryStatus.BOTH_STREAMS_BOUND;else->RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS},mask)
    }
    /** Playback-owner thread only. A receipt proves metadata still matches, NOT PCM ownership. */
    fun reserve(): RenderBoundaryReservation? {
        if(!owner())return null
        val e=epoch(); evaluate(e)
        if(mail.get().epoch !== e || snapshot().status != RenderBoundaryStatus.BOTH_STREAMS_BOUND)return null
        val a=endpoints.firstOrNull { it?.seenEpoch===e && it.identity?.source==e.plan?.outgoing } ?: return null
        val b=endpoints.firstOrNull { it?.seenEpoch===e && it.identity?.source==e.plan?.incoming } ?: return null
        return RenderBoundaryReservation(e,a,b,a.version,b.version)
    }
    fun isCurrent(receipt: RenderBoundaryReservation): Boolean {
        if(!owner())return false
        val e=epoch()
        if(e !== receipt.epoch || e.closed || e.plan==null)return false
        val a=receipt.a as? RenderBoundaryEndpoint ?: return false
        val b=receipt.b as? RenderBoundaryEndpoint ?: return false
        if(a.version!=receipt.aVersion || b.version!=receipt.bVersion)return false
        if(cueGate.executionReservationCurrent(receipt))return true
        evaluate(e)
        return mail.get().epoch===e && snapshot().status==RenderBoundaryStatus.BOTH_STREAMS_BOUND
    }
}

/** Counts bytes accepted by the REAL delegate, not buffer.remaining() offered by the decoder. */
// Marker keeps the existing pure ledger independent of Android/output implementation files.
internal interface RenderOutputAttachment

class RenderBoundaryEndpoint internal constructor(internal val controller: RenderBoundaryController) {
    internal var sameSinkOutputPort: RenderOutputAttachment? = null
    internal var seenEpoch: RenderBoundaryController.Epoch?=null
    internal var identity: RenderOutputIdentity?=null
    internal var format: RenderPcmFormat?=null
    internal var problem: RenderBoundaryStatus?=null
    internal var version=0L
    internal var acceptedEndValue: Long = Long.MIN_VALUE
    internal var acceptedEndUpperUs: Long?
        get() = if (acceptedEndValue == Long.MIN_VALUE) null else acceptedEndValue
        set(value) { acceptedEndValue = value ?: Long.MIN_VALUE }
    internal var rendererPositionValue: Long = Long.MIN_VALUE
    internal var rendererSongPositionUs: Long?
        get() = if (rendererPositionValue == Long.MIN_VALUE) null else rendererPositionValue
        set(value) { rendererPositionValue = value ?: Long.MIN_VALUE }
    internal var streamToken: Any?=null
    internal val cuePort = controller.cueGate.port(this)
    internal val sourceHistory = PcmSourceHistory()
    private var retryBuffer: ByteBuffer?=null
    private var retryLimit=0
    private var retryExpectedPosition=0
    private var retryInitialPosition=0
    private var retryPts=0L
    private var beforePosition=0
    private var beforeLimit=0
    private var beforeEpoch:RenderBoundaryController.Epoch?=null
    private var beforeValid=false
    private var callbackAvailable=false
    private var previousBufferEndFloorUs: Long?=null
    private var previousBufferEndCeilUs: Long?=null

    fun outputContext(token: Any, source: RenderOutputIdentity?, pcm: RenderPcmFormat?,
        rendererPositionUs: Long, tunneling: Boolean=false) {
        if(!controller.owner())return
        val e=controller.epoch()
        val oldIdentity=identity
        val sameIdentity=oldIdentity === source || (oldIdentity!=null && source!=null &&
            oldIdentity.periodUid==source.periodUid && oldIdentity.windowSequenceNumber==source.windowSequenceNumber &&
            oldIdentity.rendererOffsetUs==source.rendererOffsetUs && oldIdentity.source==source.source)
        if(streamToken !== token || !sameIdentity || format!=pcm) {
            if(seenEpoch===e && e.plan!=null && identity!=null &&
                (identity?.source==e.plan.outgoing || identity?.source==e.plan.incoming)) {
                // A plan cannot survive replacement/reconfiguration of a bound output occurrence.
                controller.report(e,RenderBoundaryStatus.OUTPUT_RESET)
            }
            streamToken=token;identity=source;format=pcm
            resetLedger(); problem=null; version=Math.addExact(version,1)
        }
        seenEpoch=e; callbackAvailable=true
        if(source==null)problem=RenderBoundaryStatus.OUTPUT_CONTEXT_UNAVAILABLE
        else if(pcm?.supported!=true || tunneling)problem=RenderBoundaryStatus.UNSUPPORTED_FORMAT
        rendererPositionValue = try {
            if(source==null)Long.MIN_VALUE else
                Math.addExact(Math.subtractExact(rendererPositionUs,source.rendererOffsetUs),source.periodPositionInWindowUs)
        }catch(_:ArithmeticException){problem=RenderBoundaryStatus.DISCONTINUOUS_PCM;Long.MIN_VALUE}
        controller.evaluate(e)
    }
    /** Decode a bounded real source history before handing this original to either path.
     * This is state priming only, never a replay/output queue. The whole retained
     * range must match the current source/format; seek/flush clears it.
     */
    internal fun capturePreroll(buffer: ByteBuffer, ptsUs: Long, units: Int) {
        if (!controller.owner() || cuePort.ownsInput()) return
        val id=identity ?: return; val f=format ?: return
        if (!f.supported || units!=1) { sourceHistory.reset();return }
        val plan=controller.epoch().plan
        val stop=if (plan!=null && plan.outgoing==id.source)
            kotlin.math.floor(plan.outgoingCueSeconds*f.sampleRate+.5).toLong() else Long.MAX_VALUE
        sourceHistory.capture(buffer,ptsUs,id,f,stop)
    }
    /** Call immediately before delegate.handleBuffer. This never reads/writes sample bytes. */
    fun before(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int) {
        beforeValid=false
        if(!controller.owner())return
        val e=controller.epoch();beforeEpoch=e
        val f=format
        if(!callbackAvailable || seenEpoch!==e || f?.supported!=true || identity==null || encodedAccessUnitCount!=1) {
            problem=if(!callbackAvailable || identity==null || seenEpoch!==e)RenderBoundaryStatus.OUTPUT_CONTEXT_UNAVAILABLE else RenderBoundaryStatus.UNSUPPORTED_FORMAT
            controller.report(e,requireNotNull(problem));callbackAvailable=false;return
        }
        callbackAvailable=false
        val size=f.bytesPerFrame
        if(buffer.remaining()%size!=0) {
            problem=RenderBoundaryStatus.DISCONTINUOUS_PCM;controller.evaluate(e);return
        }
        if(retryBuffer==null) {
            // Integer-microsecond PTS may truncate or round the previous exact frame end.
            // Accept that representational interval only, never an arbitrary drift tolerance.
            val lo=previousBufferEndFloorUs
            val hi=previousBufferEndCeilUs
            if(lo!=null && hi!=null && (presentationTimeUs<lo || presentationTimeUs>hi)) {
                problem=RenderBoundaryStatus.DISCONTINUOUS_PCM;controller.evaluate(e);return
            }
            retryBuffer=buffer;retryPts=presentationTimeUs;retryInitialPosition=buffer.position()
            retryExpectedPosition=buffer.position();retryLimit=buffer.limit()
        } else if(retryBuffer!==buffer || retryPts!=presentationTimeUs || retryLimit!=buffer.limit() ||
            retryExpectedPosition!=buffer.position()) {
            problem=RenderBoundaryStatus.DISCONTINUOUS_PCM;controller.evaluate(e);return
        }
        beforePosition=buffer.position();beforeLimit=buffer.limit();beforeValid=true
    }
    /** Pass the delegate's ORIGINAL return value; do not invent full acceptance on false. */
    fun after(buffer: ByteBuffer, fullyConsumed: Boolean) {
        if(!controller.owner() || !beforeValid)return
        beforeValid=false
        val e=beforeEpoch ?: return
        // Physical acceptance must remain accounted even if Main revoked the old epoch
        // while the delegate was writing. report/evaluate cannot publish into a new epoch.
        val f=format ?: return
        if(buffer !== retryBuffer || buffer.limit()!=beforeLimit || buffer.position()<beforePosition ||
            buffer.position()>beforeLimit || (buffer.position()-beforePosition)%f.bytesPerFrame!=0 ||
            fullyConsumed!=!buffer.hasRemaining()) {
            problem=RenderBoundaryStatus.DISCONTINUOUS_PCM;controller.evaluate(e);return
        }
        val consumed=buffer.position()-beforePosition
        if(consumed>0) {
            version=Math.addExact(version,1)
            val frames=(buffer.position()-retryInitialPosition).toLong()/f.bytesPerFrame
            val id=identity ?: return
            val end=try {
                val seconds=frames/f.sampleRate;val rest=frames%f.sampleRate
                val micros=Math.addExact(Math.multiplyExact(seconds,1_000_000L),
                    (rest*1_000_000L+f.sampleRate-1)/f.sampleRate)
                // Timestamp is integer microseconds; add one tick as an uncertainty upper bound.
                Math.addExact(Math.addExact(Math.addExact(Math.subtractExact(retryPts,id.rendererOffsetUs),
                    id.periodPositionInWindowUs),micros),1L)
            }catch(_:ArithmeticException){problem=RenderBoundaryStatus.DISCONTINUOUS_PCM;null}
            if(end!=null)acceptedEndUpperUs=maxOf(acceptedEndUpperUs?:Long.MIN_VALUE,end)
        }
        retryExpectedPosition=buffer.position()
        if(fullyConsumed) {
            val total=(buffer.position()-retryInitialPosition).toLong()/f.bytesPerFrame
            // Zero-length buffers neither consume PCM nor establish a new timestamp anchor.
            if(total>0) try {
                val numerator=Math.multiplyExact(total,1_000_000L)
                previousBufferEndFloorUs=Math.addExact(retryPts,numerator/f.sampleRate)
                previousBufferEndCeilUs=Math.addExact(retryPts,(numerator+f.sampleRate-1)/f.sampleRate)
            }catch(_:ArithmeticException){problem=RenderBoundaryStatus.DISCONTINUOUS_PCM}
            retryBuffer=null
        }
        controller.evaluate(e)
    }
    /** A diagnostic failure disables only this player's passive observer. */
    fun disable() { controller.close() }

    fun fault(status:RenderBoundaryStatus=RenderBoundaryStatus.SINK_FAILURE) {
        if(!controller.owner())return
        version=Math.addExact(version,1);problem=status;resetLedger()
        seenEpoch=controller.epoch();controller.report(requireNotNull(seenEpoch),status)
    }
    fun reset(status:RenderBoundaryStatus=RenderBoundaryStatus.OUTPUT_RESET) {
        if(!controller.owner())return
        version=Math.addExact(version,1);resetLedger();streamToken=null;identity=null;format=null
        problem=null;seenEpoch=null;controller.report(controller.epoch(),status)
    }
    private fun resetLedger() {
        sourceHistory.reset()
        retryBuffer=null;beforeValid=false;beforeEpoch=null;callbackAvailable=false
        acceptedEndUpperUs=null;rendererSongPositionUs=null
        previousBufferEndFloorUs=null;previousBufferEndCeilUs=null
    }
}

/** Bounded per-OUTPUT-source float history, allocated with its sink, never on PCM calls.
 * Absolute byte access is independent of a codec buffer's byte-order flag and leaves
 * its position/limit unchanged. A malformed/discontinuous history disables priming,
 * not ordinary playback. Accepted source PCM is not confused with audible frames.
 */
internal class PcmSourceHistory {
    private val capacity=8192
    private val samples=FloatArray(capacity*2)
    private var start=0L
    private var end=0L
    private var channels=0
    private var lastBuffer:ByteBuffer?=null
    private var lastPts=Long.MIN_VALUE
    private var lastLimit=0
    fun reset(){start=0;end=0;channels=0;lastBuffer=null;lastPts=Long.MIN_VALUE}
    fun has(first:Long,frames:Int,ch:Int):Boolean = first>=start && first>=0 && frames>=0 &&
        frames<=capacity && channels==ch && first<=end && frames.toLong()<=end-first
    fun copy(first:Long,frames:Int,ch:Int,into:ByteBuffer):Boolean {
        if(!has(first,frames,ch) || into.isReadOnly || into.order()!=java.nio.ByteOrder.LITTLE_ENDIAN ||
            frames.toLong()*ch*4>into.remaining())return false
        var offset=into.position()
        for(f in 0 until frames){val slot=((first+f)%capacity).toInt()*2
            for(c in 0 until ch){into.putFloat(offset,samples[slot+c]);offset+=4}}
        return true
    }
    fun capture(buffer:ByteBuffer,pts:Long,id:RenderOutputIdentity,f:RenderPcmFormat,stop:Long) {
        if(lastBuffer===buffer && lastPts==pts){if(lastLimit!=buffer.limit())reset();return}
        lastBuffer=buffer;lastPts=pts;lastLimit=buffer.limit()
        try {
            if(buffer.remaining()%f.bytesPerFrame!=0){reset();return}
            val us=Math.addExact(Math.subtractExact(pts,id.rendererOffsetUs),id.periodPositionInWindowUs)
            if(us !in 0..86_400_000_000L){reset();return}
            val ticks=us*f.sampleRate
            val first=(ticks+500_000)/1_000_000
            if(kotlin.math.abs(first*1_000_000-ticks)>f.sampleRate){reset();return}
            val frames=buffer.remaining()/f.bytesPerFrame
            val last=minOf(first+frames,stop)
            if(last<=first)return
            val keepFirst=maxOf(first,last-capacity)
            if(channels!=f.channels || end!=keepFirst){start=keepFirst;end=keepFirst;channels=f.channels}
            var pos=buffer.position()+((keepFirst-first)*f.bytesPerFrame).toInt()
            var at=keepFirst
            while(at<last) {
                val slot=(at%capacity).toInt()*2
                for(c in 0 until f.channels) {
                    val lo=buffer.get(pos).toInt() and 255
                    val hi=buffer.get(pos+1).toInt() and 255
                    val value=if(f.bytesPerSample==2) ((lo or (hi shl 8)).toShort().toInt()/32768f)
                    else java.lang.Float.intBitsToFloat(lo or (hi shl 8) or
                        ((buffer.get(pos+2).toInt() and 255) shl 16) or (buffer.get(pos+3).toInt() shl 24))
                    if(!value.isFinite()){reset();return}
                    samples[slot+c]=value;pos+=f.bytesPerSample
                }
                at++
            }
            end=last;start=maxOf(start,end-capacity)
        } catch(_:ArithmeticException){reset()}
          catch(_:IndexOutOfBoundsException){reset()}
    }
}
