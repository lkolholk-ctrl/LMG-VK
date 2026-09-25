package com.lmg.vk.engine.automix.render

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LmgLivePlaybackClient
import androidx.media3.exoplayer.LmgLivePlaybackLease
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import com.lmg.vk.BuildConfig
import com.lmg.vk.engine.DjStreamFx
import com.lmg.vk.engine.PlayerSettings
import java.nio.ByteBuffer
import java.util.Collections
import java.util.IdentityHashMap

/** Production adapter to a lease minted by the SAME ExoPlayer's retained actual periods.
 * First live scope is opt-in DEBUG, one nonspatial A->B with B final, normalizers/DJ idle.
 * The native stream continues B to true EOF on the same selected sink. No fake clock grant. */
@UnstableApi
internal class ForkLivePlaybackDriver private constructor(
    private val controller: RenderBoundaryController,
) : LmgLivePlaybackClient, RenderPlaybackAttachment {
    enum class Phase { OFF, REQUESTED, PREPARING, LIVE, COMPLETED, REJECTED, RESET_REQUIRED }
    data class Report(val phase:Phase, val generation:Long=0,val revision:Long=0,
        val nativeDspInstalled:Boolean=false, val actualPlaybackLease:Boolean=false,
        val sourcePrerollDiscarded:Long=0, val reason:String?=null)
    @Volatile var report=Report(Phase.OFF);private set
    @Volatile private var requested: RenderBoundaryController.Epoch?=null
    @Volatile private var forkLease:LmgLivePlaybackLease?=null
    @Volatile private var handoff=false
    private var pump:LivePcmPump?=null
    private val initialConfiguration=Collections.newSetFromMap(IdentityHashMap<RenderBoundaryEndpoint,Boolean>())
    private var preparedAtNanos=0L
    private var prerollFrames=0L
    private fun policySafe() = !PlayerSettings.volumeNormalization.value && DjStreamFx.mode==DjStreamFx.MODE_NONE
    @Synchronized override fun requestLive(generation:Long,revision:Long):Boolean {
        if(!BuildConfig.DEBUG || !policySafe() || forkLease!=null)return false
        val e=controller.epoch();if(e.closed||e.generation!=generation||e.revision!=revision||e.plan?.execution==null)return false
        if(report.generation==generation&&report.revision==revision&&report.phase!=Phase.OFF)return false
        requested=e;report=Report(Phase.REQUESTED,generation,revision);return true
    }
    override fun liveReport():LivePlaybackReport {
        val r=report;return LivePlaybackReport(r.phase.name,r.generation,r.revision,
            r.nativeDspInstalled,r.actualPlaybackLease,r.sourcePrerollDiscarded,r.reason)
    }
    fun checkLegacyWrite(endpoint:RenderBoundaryEndpoint) {
        check(forkLease==null||side(endpoint)!=1){"Incoming live source cannot write legacy sink"}
    }
    override fun pendingRequest():LmgLivePlaybackClient.Request? {
        val e=requested?:return null
        if(forkLease!=null)return null
        if(controller.epoch()!==e||e.closed||!policySafe()) {requested=null;report=Report(Phase.REJECTED,e.generation,e.revision,reason="INPUT_CHANGED");return null}
        val p=e.plan?:return null
        return LmgLivePlaybackClient.Request(e.generation,e.revision,p.outgoingCueFloorUs,p.incomingCueFloorUs)
    }
    override fun matches(timeline:Timeline,id:MediaPeriodId,side:Int):Boolean {
        if(side !in 0..1||id.isAd||timeline.isEmpty)return false
        val p=requested?.plan?:return false;val expected=if(side==0)p.outgoing else p.incoming
        val index=timeline.getIndexOfPeriod(id.periodUid);if(index==C.INDEX_UNSET)return false
        val period=timeline.getPeriod(index,Timeline.Period());val window=timeline.getWindow(period.windowIndex,Timeline.Window())
        if(window.isLive||window.isDynamic||window.firstPeriodIndex!=window.lastPeriodIndex||period.positionInWindowUs!=0L)return false
        val item=window.mediaItem;val local=item.localConfiguration?:return false;val clip=item.clippingConfiguration
        if(clip.startPositionMs!=0L||clip.endPositionMs!=C.TIME_END_OF_SOURCE||clip.relativeToDefaultPosition||clip.relativeToLiveWindow)return false
        return expected==RenderSourceKey(window.uid,item.mediaId,local.uri.toString(),local.customCacheKey,
            item.mediaMetadata.extras?.getString("lmg.automix.recordingRevision"))
    }
    override fun onLease(lease:LmgLivePlaybackLease) {
        check(controller.owner());val e=checkNotNull(requested)
        check(controller.epoch()===e&&lease.isCurrent(e.generation,e.revision)&&policySafe())
        forkLease=lease;handoff=false;initialConfiguration.clear();preparedAtNanos=System.nanoTime()
        check(controller.cueGate.requestLive(e.generation,e.revision)==CueProbeRequest.REQUESTED)
        report=Report(Phase.PREPARING,e.generation,e.revision,actualPlaybackLease=true)
    }
    private fun valid():Boolean {
        val e=requested?:return false;val lease=forkLease?:return false
        return controller.epoch()===e&&!e.closed&&policySafe()&&lease.isCurrent(e.generation,e.revision)
    }
    fun side(endpoint:RenderBoundaryEndpoint):Int? {
        val p=requested?.plan?:return null;val key=endpoint.identity?.source?:return null
        return when(key){p.outgoing->0;p.incoming->1;else->null}
    }
    fun owns(endpoint:RenderBoundaryEndpoint)=forkLease!=null && side(endpoint)!=null
    /** Called only by the sink whose stream is not the current outgoing binding. Format
     * startup may occur after lease acquisition; it must not masquerade as midstream reset. */
    fun allowIncomingStartup(endpoint:RenderBoundaryEndpoint):Boolean {
        if(forkLease==null||pump!=null||!valid())return false
        if(endpoint.identity?.source==requested?.plan?.outgoing)return false
        if(controller.cueProbeSnapshot().phase==CueProbePhase.PAIR_HELD)return false
        return initialConfiguration.add(endpoint)
    }
    fun ignorePrerollDiscontinuity(endpoint:RenderBoundaryEndpoint):Boolean = forkLease!=null&&pump==null&&valid()&&
        endpoint.identity?.source!=requested?.plan?.outgoing&&controller.cueProbeSnapshot().phase!=CueProbePhase.PAIR_HELD
    /** Skip only decoded B preroll BEFORE cue. It is never sent to the suppressed sink.
     * The cue-containing original stays intact and is held by the accepted gate. */
    fun discardIncomingPreroll(endpoint:RenderBoundaryEndpoint,buffer:ByteBuffer,ptsUs:Long):Boolean {
        if(forkLease==null||pump!=null||side(endpoint)!=1)return false
        check(valid())
        val f=endpoint.format?:return false;val id=endpoint.identity?:return false;val p=requested?.plan?:return false
        check(f.supported&&buffer.remaining()%f.bytesPerFrame==0){"Unsupported incoming live PCM"}
        val firstUs=Math.addExact(Math.subtractExact(ptsUs,id.rendererOffsetUs),id.periodPositionInWindowUs)
        check(firstUs in 0..86_400_000_000L)
        val ticks=Math.multiplyExact(firstUs,f.sampleRate.toLong())
        val first=(ticks+500_000L)/1_000_000L
        check(kotlin.math.abs(first*1_000_000L-ticks)<=f.sampleRate){"Incoming PCM grid unavailable"}
        val end=first+buffer.remaining()/f.bytesPerFrame
        val cue=kotlin.math.floor(p.incomingCueSeconds*f.sampleRate+.5).toLong()
        if(end<=cue){prerollFrames+=buffer.remaining()/f.bytesPerFrame;buffer.position(buffer.limit());return true}
        return false
    }
    fun inputEnded(endpoint:RenderBoundaryEndpoint):Boolean {
        val side=side(endpoint)?:return false;if(forkLease==null)return false
        check(valid());checkNotNull(pump){"Source ended before live pair prepared"}.endInput(side);return true
    }
    /** Report codec-side readiness independently from the output device. Ended stays false
     * while final output is draining, even if the source codec has already reached EOS. */
    fun interceptedIsEnded(endpoint:RenderBoundaryEndpoint):Boolean? = if(owns(endpoint))pump?.isComplete==true else null
    fun willInvalidate(endpoint:RenderBoundaryEndpoint) {
        if(forkLease!=null&&(side(endpoint)!=null||pump!=null))forkLease?.revoke()
    }
    override fun onWork() {
        check(controller.owner());val e=checkNotNull(requested)
        check(valid()){"Live source/processor policy revoked; reset required"}
        if(pump==null){
            if(controller.cueProbeSnapshot().phase==CueProbePhase.RELEASED)error("Cue barrier rejected live pair")
            check(System.nanoTime()-preparedAtNanos<5_000_000_000L){"Live pair preparation timed out"}
            if(controller.cueProbeSnapshot().phase!=CueProbePhase.PAIR_HELD)return
            val actual=checkNotNull(forkLease)
            actual.pinClock()
            val lease=object:CuePlaybackLease {
                override fun isCurrent(generation:Long,revision:Long,format:RenderPcmFormat):Boolean =
                    valid()&&generation==e.generation&&revision==e.revision&&format.supported&&actual.isClockPinned()
                override fun revoke(){actual.revoke()}
            }
            pump=checkNotNull(LivePcmPump.start(controller,lease)){"Live pair output reservation rejected"}
            report=Report(Phase.LIVE,e.generation,e.revision,true,true,prerollFrames)
        }
        pump!!.tick()
    }
    override fun sourcePositionUs(side:Int):Long = pump?.sourcePositionUs(side)?:C.TIME_UNSET
    override fun transitionReachedOutput():Boolean = pump?.transitionAudible()==true
    override fun outputFullyEnded():Boolean = pump?.isComplete==true
    override fun beforeMetadataSwitch(){handoff=true}
    override fun ignoreOwnedHandoff(windowUid:Any,mediaId:String):Boolean {
        val e=requested?:return false;val p=e.plan?:return false
        return handoff&&forkLease!=null&&controller.epoch()===e&&windowUid==p.incoming.windowUid&&mediaId==p.incoming.mediaId
    }
    override fun onCancelled(){
        check(controller.owner());val e=requested;forkLease?.revoke()
        // Abort before close: retained codec callbacks remain quarantined until real reset.
        try{pump?.close()?:controller.abortNativeCueOwner()}finally{
            pump=null;forkLease=null;requested=null;handoff=false
            if(report.phase!=Phase.COMPLETED)report=Report(Phase.RESET_REQUIRED,e?.generation?:report.generation,e?.revision?:report.revision,reason="CANCELLED_OR_RESET")
        }
    }
    override fun onCompleted(){
        check(controller.owner());val e=checkNotNull(requested)
        check(pump?.isComplete==true)
        try{pump?.close()}finally{pump=null;forkLease=null;requested=null;handoff=false;report=Report(Phase.COMPLETED,e.generation,e.revision,sourcePrerollDiscarded=prerollFrames)}
    }
    companion object {
        fun forController(controller:RenderBoundaryController):ForkLivePlaybackDriver = synchronized(controller){
            val old=controller.playbackAttachment
            if(old==null)ForkLivePlaybackDriver(controller).also{controller.playbackAttachment=it}
            else old as ForkLivePlaybackDriver
        }
    }
}
