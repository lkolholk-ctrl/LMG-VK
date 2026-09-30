package com.lmg.vk.engine.automix.render

import com.lmg.vk.engine.automix.nativecore.NativeLivePcmExecutor
import com.lmg.vk.engine.automix.nativecore.NativePcmOwnerIngress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One playback-thread driver from committed ingress through REAL native DSP to the
 * selected existing sink. No Player/device construction or synthetic clock/lease. */
internal class LivePcmPump private constructor(
    private val controller: RenderBoundaryController,
    private val lease: CuePlaybackLease,
    val format: RenderPcmFormat,
    private val generation: Long,
    private val revision: Long,
    private val ingress: NativePcmOwnerIngress,
    private val engine: NativeLivePcmExecutor,
    private val ticket: CueOwnerTicket,
    private val output: SameSinkPairOutput,
    val outgoingPrefixFrames: Long,
    val outgoingPrefixStartFrame: Long,
    private val now: () -> Long,
    private val storage: Prepared,
) : AutoCloseable {
    private val maximum=engine.maximumFrames
    private val source = storage.source
    private val meta = storage.meta
    private val queueStats = storage.queueStats
    private val engineStats = storage.engineStats
    private val packet = storage.packet
    private val ended = storage.ended
    private val nativeEnded = storage.nativeEnded
    private val sourceFirst = storage.sourceFirst
    private val sourcePrefix = storage.sourcePrefix
    private var packetId=0L
    private var packetFirst=0L
    private var submittedFrames=0L
    private var firstNonPrefix=false
    private var sentPrefix=0L
    private var closed=false
    private var draining=false
    private var complete=false
    private var lastProgress=now()
    private var lastPhysical=Long.MIN_VALUE
    private val writeState = storage.writeState
    var discardedIncomingPreroll=0L; private set
    var discardedOutgoingAfterTransition=0L; private set
    val outputBasePtsUs: Long get()=output.basePtsUs
    val isComplete: Boolean get()=complete
    val transitionFrames: Long = run { engine.stats(engineStats); engineStats[3] }

    private fun current(){check(!closed&&lease.isCurrent(generation,revision,format)&&controller.hasCommittedCueOwner(ticket)){"Live PCM lease revoked"}}
    fun endInput(side: Int){require(side in 0..1);current();ended[side]=true}
    private fun writePending(): Boolean {
        if(!packet.hasRemaining() && packetId==0L)return true
        output.writeInto(packetId,packetFirst,packet,writeState)
        if(writeState.acceptedBytes>0)lastProgress=now()
        if(!writeState.bufferFinished)return false
        submittedFrames=packetFirst+packet.limit()/format.bytesPerFrame
        packet.clear();packet.limit(0);packetId=0L
        return true
    }
    private var sequence=0L
    private fun beginPacket(frames: Int){check(packetId==0L);packet.position(0);packet.limit(frames*format.bytesPerFrame);packetFirst=submittedFrames;packetId=++sequence}
    private fun read(side: Int): Boolean {
        val b=source[side]
        if(b.hasRemaining())return true
        b.clear();val n=controller.readCueOwner(ticket,side,b,maximum,meta[side]);b.flip()
        if(n==0){b.limit(0);return false}
        check(meta[side][1]==n.toLong())
        sourceFirst[side]=meta[side][0];sourcePrefix[side]=meta[side][2]==1L
        return true
    }
    private fun feed(side: Int): Boolean {
        if(!read(side)){
            if(ended[side]&&!nativeEnded[side]){
                ingress.stats(side,queueStats)
                if(queueStats[7]==0L){
                    engine.stats(engineStats)
                    if(side!=0||engineStats[12]<engineStats[3])engine.endInput(side,queueStats[2])
                    nativeEnded[side]=true
                }
            }
            return false
        }
        val b=source[side];val n=b.remaining()/(format.channels*4)
        if(sourcePrefix[side]){
            if(side==1){discardedIncomingPreroll+=n;b.position(b.limit());return true}
            if(packetId!=0L)return false
            engine.encodePrefix(b,packet.apply{clear()},n,format.bytesPerSample)
            b.position(b.limit());sentPrefix+=n;beginPacket(n);return true
        }
        if(side==0)firstNonPrefix=true
        engine.stats(engineStats)
        if(side==0&&engineStats[12]>=engineStats[3]){
            discardedOutgoingAfterTransition+=n;b.position(b.limit());return true
        }
        val offset=b.position()/(format.channels*4)
        val accepted=engine.push(side,b,n,sourceFirst[side]+offset)
        check(accepted in 0..n)
        b.position(b.position()+accepted*format.channels*4)
        return accepted>0
    }
    /** Bounded work; a blocked sink or missing source yields to the existing playback loop. */
    fun tick() {
        current();if(complete)return
        try {
            var budget = 8
            while (budget-- > 0 && !complete) {
                current()
                // Yield immediately on sink backpressure, not eight repeated writes to
                // the same blocked backend within one ExoPlayer work invocation.
                if (packetId != 0L && !writePending()) break
                if (draining) {
                    if (output.drain()) { complete = true; lastProgress = now() }
                    break
                }
                val fedA = feed(0)
                if (packetId != 0L) { if (!writePending()) break; continue }
                val fedB = feed(1)
                if (!firstNonPrefix || sentPrefix != outgoingPrefixFrames) {
                    if (!fedA && !fedB) break
                    continue
                }
                packet.clear()
                val n = engine.render(packet, maximum, format.bytesPerSample)
                if (n > 0) {
                    beginPacket(n); lastProgress = now()
                    if (!writePending()) break
                } else packet.limit(0)
                engine.stats(engineStats)
                if (engineStats[17] == 1L && packetId == 0L) draining = true
                if (n == 0 && !fedA && !fedB && !draining) break
            }
            current()
            val position = output.sampleSinkClockValue()
            if (position != Long.MIN_VALUE && position != lastPhysical) {
                lastPhysical = position; lastProgress = now()
            }
            // Timeout controls failure/recovery only. It NEVER advances the audio clock.
            check(complete||now()-lastProgress<3_000_000_000L){"Live PCM starvation; coordinated reset required"}
        }catch(failure:Throwable){output.revoke();lease.revoke();throw failure}
    }
    /** Existing AudioSink media time -> output-frame position; never use wall time. */
    fun physicalOutputFrameValue(): Long {
        current()
        val time=output.sampleSinkClockValue()
        if(time==Long.MIN_VALUE || time<output.basePtsUs)return Long.MIN_VALUE
        val frame=Math.multiplyExact(time-output.basePtsUs,format.sampleRate.toLong())/1_000_000L
        return minOf(frame,output.completeFrameCount())
    }
    fun physicalOutputFrame(): Long? {
        val frame=physicalOutputFrameValue()
        return if(frame==Long.MIN_VALUE)null else frame
    }
    fun sourcePositionUsValue(side: Int): Long {
        val frame=physicalOutputFrameValue()
        if(frame==Long.MIN_VALUE)return Long.MIN_VALUE
        if(side==0&&frame<outgoingPrefixFrames)
            return Math.multiplyExact(outgoingPrefixStartFrame+frame,1_000_000L)/format.sampleRate
        val transitionFrame=maxOf(0L,frame-outgoingPrefixFrames)
        return (engine.sourceTimeSeconds(side,transitionFrame.toDouble())*1_000_000.0).toLong()
    }
    fun sourcePositionUs(side: Int): Long? {
        val value=sourcePositionUsValue(side)
        return if(value==Long.MIN_VALUE)null else value
    }
    fun transitionAudible(): Boolean {
        val frame=physicalOutputFrameValue()
        return frame!=Long.MIN_VALUE && frame>=outgoingPrefixFrames+transitionFrames
    }
    fun nativeSnapshot(): LongArray {engine.stats(engineStats);return engineStats.copyOf()}
    fun revoke(){output.revoke();lease.revoke()}
    /** Invoke after the owner has caused actual stream flush/reset (or fully drained EOS). */
    override fun close(){if(!closed){closed=true;controller.abortNativeCueOwner();try{engine.close()}finally{ingress.close()}}}
    /** Prepared on the service's worker executor, before requesting a fork lease.
     * Constructors allocate; no native method which binds thread ownership is called.
     * Adopt exactly once on the playback owner after checking epoch and actual format.
     */
    internal class Prepared(val data: RenderExecutionData, val format: RenderPcmFormat,
        val generation: Long, val revision: Long, primeOutgoing:Boolean=false) : AutoCloseable {
        val maximum = 1024
        val source = Array(2) { ByteBuffer.allocateDirect(maximum*format.channels*4)
            .order(ByteOrder.LITTLE_ENDIAN).apply { limit(0) } }
        val meta = Array(2) { LongArray(3) }
        val queueStats = LongArray(8)
        val engineStats = LongArray(19)
        val packet = ByteBuffer.allocateDirect(maximum*format.bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN).apply { limit(0) }
        val ended = BooleanArray(2)
        val nativeEnded = BooleanArray(2)
        val sourceFirst = LongArray(2)
        val sourcePrefix = BooleanArray(2)
        val writeState = OutputWriteState()
        val engine = NativeLivePcmExecutor(data.copyWords(),format.sampleRate,format.channels,
            maximum,generation,revision,primeOutgoing=primeOutgoing)
        val ingress: NativePcmOwnerIngress
        private var adopted = false
        init {
            try { ingress = NativePcmOwnerIngress(format.channels,format.bytesPerSample,65536) }
            catch (failure: Throwable) { engine.close(); throw failure }
        }
        fun adopt(expected: RenderExecutionData, pcm: RenderPcmFormat, epoch: RenderBoundaryController.Epoch) {
            check(!adopted && data === expected && format == pcm &&
                generation == epoch.generation && revision == epoch.revision)
            adopted = true
        }
        override fun close() { try { engine.close() } finally { ingress.close() } }
    }
    companion object {
        /** Synchronous construction is retained for host tests/offline callers ONLY.
         * Production ForkLivePlaybackDriver must use startPrepared. */
        fun start(controller:RenderBoundaryController,lease:CuePlaybackLease,now:()->Long=System::nanoTime):LivePcmPump? {
            check(controller.owner())
            val reservation=controller.reserveExecutionCue() ?: return null
            val e=controller.epoch(); val data=e.plan?.execution ?: return null
            val f=(reservation.a as RenderBoundaryEndpoint).format ?: return null
            val resources=Prepared(data,f,e.generation,e.revision)
            return startPrepared(controller,lease,resources,now)
        }
        fun startPrepared(controller:RenderBoundaryController,lease:CuePlaybackLease,
            resources:Prepared,now:()->Long=System::nanoTime):LivePcmPump? {
            check(controller.owner())
            var output:SameSinkPairOutput?=null;var claimed=false
            try {
                val reservation=controller.reserveExecutionCue() ?: run { resources.close();return null }
                val e=controller.epoch();val data=e.plan?.execution ?: run { resources.close();return null }
                val f=(reservation.a as RenderBoundaryEndpoint).format ?: run { resources.close();return null }
                resources.adopt(data,f,e)
                if(!lease.isCurrent(e.generation,e.revision,f)) { resources.close();return null }
                val selected=SameSinkPairOutput.reserve(controller,reservation) ?: run { resources.close();return null }
                output=selected
                if(!selected.prefixPreservesLegacyLevel()) { selected.rollbackBeforeInputClaim();resources.close();return null }
                val ingress=resources.ingress;val engine=resources.engine
                val history=reservation.a.sourceHistory
                val warmup=engine.outgoingPrerollFrames
                val firstWarm=engine.outgoingPrerollStartFrame
                if(warmup>0 && !history.has(firstWarm,warmup.toInt(),f.channels)) {
                    // No synthetic zeros, gain boost or replay of old output. Refuse
                    // BEFORE source claim if the exact source history is unavailable.
                    selected.rollbackBeforeInputClaim();resources.close();return null
                }
                var primed=0L;val scratch=resources.source[0]
                while(primed<warmup){
                    val frames=minOf(resources.maximum.toLong(),warmup-primed).toInt()
                    scratch.clear();check(history.copy(firstWarm+primed,frames,f.channels,scratch))
                    check(engine.push(0,scratch,frames,firstWarm+primed)==frames)
                    primed+=frames
                }
                scratch.clear();scratch.limit(0)
                val ticket=controller.prepareCueOwner(NativeCueOwnerIngress(ingress),lease)
                    ?:run { selected.rollbackBeforeInputClaim();resources.close();return null }
                claimed=controller.commitCueOwner(ticket)
                check(claimed&&selected.bindCommittedInputs(ticket)){"PCM owner commit failed"}
                val stats=resources.queueStats;ingress.stats(0,stats);val prefix=stats[3]-stats[1];val first=stats[1]
                check(prefix in 0..65536)
                return LivePcmPump(controller,lease,f,e.generation,e.revision,ingress,engine,ticket,
                    selected,prefix,first,now,resources)
            } catch(failure:Throwable) {
                if(!claimed)output?.rollbackBeforeInputClaim() else output?.revoke()
                controller.abortNativeCueOwner()
                resources.close();lease.revoke();throw failure
            }
        }
    }
}
