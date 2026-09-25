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
) : AutoCloseable {
    private val maximum=engine.maximumFrames
    private val source=Array(2){ByteBuffer.allocateDirect(maximum*format.channels*4).order(ByteOrder.LITTLE_ENDIAN).apply{limit(0)}}
    private val meta=Array(2){LongArray(3)}
    private val queueStats=LongArray(8)
    private val engineStats=LongArray(19)
    private val packet=ByteBuffer.allocateDirect(maximum*format.bytesPerFrame).order(ByteOrder.LITTLE_ENDIAN).apply{limit(0)}
    private val ended=booleanArrayOf(false,false)
    private val nativeEnded=booleanArrayOf(false,false)
    private val sourceFirst=longArrayOf(0,0)
    private val sourcePrefix=booleanArrayOf(false,false)
    private var packetId=0L
    private var packetFirst=0L
    private var submittedFrames=0L
    private var firstNonPrefix=false
    private var sentPrefix=0L
    private var closed=false
    private var draining=false
    private var complete=false
    private var lastProgress=now()
    private var lastPhysical: Long?=null
    var discardedIncomingPreroll=0L; private set
    var discardedOutgoingAfterTransition=0L; private set
    val outputBasePtsUs: Long get()=output.basePtsUs
    val isComplete: Boolean get()=complete
    val transitionFrames: Long get(){engine.stats(engineStats);return engineStats[3]}

    private fun current(){check(!closed&&lease.isCurrent(generation,revision,format)&&controller.hasCommittedCueOwner(ticket)){"Live PCM lease revoked"}}
    fun endInput(side: Int){require(side in 0..1);current();ended[side]=true}
    private fun writePending(): Boolean {
        if(!packet.hasRemaining() && packetId==0L)return true
        val result=output.write(packetId,packetFirst,packet)
        if(result.acceptedBytes>0)lastProgress=now()
        if(!result.bufferFinished)return false
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
        lastProgress=now();return true
    }
    private fun feed(side: Int): Boolean {
        if(!read(side)){
            if(ended[side]&&!nativeEnded[side]){
                ingress.stats(side,queueStats)
                if(queueStats[7]==0L){
                    engine.stats(engineStats)
                    if(side!=0||engineStats[12]<engineStats[3])engine.endInput(side,queueStats[2])
                    nativeEnded[side]=true;lastProgress=now()
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
        if(accepted>0)lastProgress=now()
        return accepted>0
    }
    /** Bounded work; a blocked sink or missing source yields to the existing playback loop. */
    fun tick() {
        current();if(complete)return
        try {
            repeat(8){
                if(packetId!=0L&&!writePending())return@repeat
                if(draining){if(output.drain()){complete=true;lastProgress=now()};return@repeat}
                feed(0)
                if(packetId!=0L){writePending();return@repeat}
                feed(1)
                if(!firstNonPrefix||sentPrefix!=outgoingPrefixFrames)return@repeat
                packet.clear();val n=engine.render(packet,maximum,format.bytesPerSample)
                if(n>0){beginPacket(n);lastProgress=now();writePending()}
                else packet.limit(0)
                engine.stats(engineStats)
                if(engineStats[17]==1L&&packetId==0L)draining=true
            }
            current()
            val position=output.sampleSinkClock()
            if(position!=null&&position!=lastPhysical){lastPhysical=position;lastProgress=now()}
            // Timeout controls failure/recovery only. It NEVER advances the audio clock.
            check(complete||now()-lastProgress<3_000_000_000L){"Live PCM starvation; coordinated reset required"}
        }catch(failure:Throwable){output.revoke();lease.revoke();throw failure}
    }
    /** Existing AudioSink media time -> output-frame position; never use wall time. */
    fun physicalOutputFrame(): Long? {
        current();val time=output.sampleSinkClock()?:return null
        if(time<output.basePtsUs)return null
        val frame=Math.multiplyExact(time-output.basePtsUs,format.sampleRate.toLong())/1_000_000L
        // Never report physical progress beyond bytes actually accepted by the sink.
        return minOf(frame,output.snapshot().completeFrames)
    }
    fun sourcePositionUs(side: Int): Long? {
        val frame=physicalOutputFrame()?:return null
        if(side==0&&frame<outgoingPrefixFrames)
            return Math.multiplyExact(outgoingPrefixStartFrame+frame,1_000_000L)/format.sampleRate
        val transitionFrame=maxOf(0L,frame-outgoingPrefixFrames)
        return (engine.sourceTimeSeconds(side,transitionFrame.toDouble())*1_000_000.0).toLong()
    }
    fun transitionAudible(): Boolean = physicalOutputFrame()?.let{it>=outgoingPrefixFrames+transitionFrames}==true
    fun nativeSnapshot(): LongArray {engine.stats(engineStats);return engineStats.copyOf()}
    fun revoke(){output.revoke();lease.revoke()}
    /** Invoke after the owner has caused actual stream flush/reset (or fully drained EOS). */
    override fun close(){if(!closed){closed=true;controller.abortNativeCueOwner();try{engine.close()}finally{ingress.close()}}}
    companion object {
        fun start(controller:RenderBoundaryController,lease:CuePlaybackLease,now:()->Long=System::nanoTime):LivePcmPump?{
            check(controller.owner())
            val reservation=controller.reserveExecutionCue()?:return null
            val e=controller.epoch();val data=e.plan?.execution?:return null
            val f=(reservation.a as RenderBoundaryEndpoint).format?:return null
            if(!lease.isCurrent(e.generation,e.revision,f))return null
            val output=SameSinkPairOutput.reserve(controller,reservation)?:return null
            if (!output.prefixPreservesLegacyLevel()) { output.rollbackBeforeInputClaim(); return null }
            var ingress:NativePcmOwnerIngress?=null;var engine:NativeLivePcmExecutor?=null;var claimed=false
            try{
                engine=NativeLivePcmExecutor(data.copyWords(),f.sampleRate,f.channels,1024,e.generation,e.revision)
                ingress=NativePcmOwnerIngress(f.channels,f.bytesPerSample,65536)
                val ticket=controller.prepareCueOwner(NativeCueOwnerIngress(ingress),lease)
                    ?:run{output.rollbackBeforeInputClaim();engine.close();ingress.close();return null}
                claimed=controller.commitCueOwner(ticket)
                check(claimed&&output.bindCommittedInputs(ticket)){"PCM owner commit failed"}
                val stats=LongArray(8);ingress.stats(0,stats);val prefix=stats[3]-stats[1];val first=stats[1]
                check(prefix in 0..65536)
                return LivePcmPump(controller,lease,f,e.generation,e.revision,ingress,engine,ticket,output,prefix,first,now)
            }catch(failure:Throwable){
                if(!claimed)output.rollbackBeforeInputClaim()else output.revoke()
                // First terminally abort the gate admission. Retained callbacks stay quarantined
                // and cannot call a destroyed native ingress before the real renderer reset.
                controller.abortNativeCueOwner()
                try{engine?.close()}finally{ingress?.close()}
                lease.revoke();throw failure
            }
        }
    }
}
