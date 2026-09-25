package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs the production mailbox/ledger. No Media3 or Android classes are replaced by stubs. */
object RenderBoundaryScenarios {
    private fun key(uid:String="A", uri:String="https://private.invalid/$uid?secret=redacted") =
        RenderSourceKey(uid,"same-media-id",uri,null,"r1")
    private val format=RenderPcmFormat(48000,2,4)
    private class Rig(val aKey:RenderSourceKey=key(),val bKey:RenderSourceKey=key("B")) {
        val controller=RenderBoundaryController()
        val a=controller.newEndpoint();val b=controller.newEndpoint()
        val tokenA=Any();val tokenB=Any()
        fun plan(gen:Long=1,rev:Long=1,cue:Double=1.0)=
            RenderBoundaryPlan.create(gen,rev,aKey,bKey,cue,cue,9)
        fun offer(gen:Long=1,rev:Long=1,cue:Double=1.0){controller.offer(plan(gen,rev,cue))}
        fun context(endpoint:RenderBoundaryEndpoint=a,source:RenderSourceKey=aKey,
            token:Any=tokenA,pcm:RenderPcmFormat=format,position:Long=0,offset:Long=0,
            periodOffset:Long=0,sequence:Long=10,tunnel:Boolean=false) {
            endpoint.outputContext(token,RenderOutputIdentity(source,source.windowUid,sequence,periodOffset,offset),
                pcm,position,tunnel)
        }
        fun both(){context();context(b,bKey,tokenB,sequence=11)}
        fun status(expected:RenderBoundaryStatus){check(controller.snapshot().status==expected){
            "Expected $expected; got ${controller.snapshot()}"}}
    }
    private fun pcm(frames:Int=48):ByteBuffer=ByteBuffer.allocateDirect(frames*8).order(ByteOrder.nativeOrder())
    private fun rejected(block:()->Unit){var bad=false;try{block()}catch(_:IllegalArgumentException){bad=true};check(bad)}
    val names=mutableListOf<String>()
    private val cases=linkedMapOf<String,()->Unit>()
    private fun test(name:String,body:()->Unit){names+=name;cases[name]=body}
    init {
        test("noPlan") { val r=Rig();r.status(RenderBoundaryStatus.NO_PLAN);check(r.controller.reserve()==null) }
        test("outgoingOnly") { val r=Rig();r.offer();r.context();r.status(RenderBoundaryStatus.OUTGOING_BOUND) }
        test("incomingOnly") { val r=Rig();r.offer();r.context(r.b,r.bKey,r.tokenB);r.status(RenderBoundaryStatus.INCOMING_BOUND) }
        test("bothNotExecutable") { val r=Rig();r.offer();r.both();r.status(RenderBoundaryStatus.BOTH_STREAMS_BOUND);val v=r.controller.snapshot();check(!v.canExecute&&!v.ownsPcm&&!v.ownsTransitionGain) }
        test("reservation") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());check(r.controller.isCurrent(x)&&!x.canExecute) }
        test("rendererOrderNotRole") { val r=Rig();r.offer();r.context(r.a,r.bKey,r.tokenB);r.context(r.b,r.aKey,r.tokenA);r.status(RenderBoundaryStatus.BOTH_STREAMS_BOUND) }
        test("duplicateMediaIdsAreDistinct") { val r=Rig();check(r.aKey.mediaId==r.bKey.mediaId);r.offer();r.both();r.status(RenderBoundaryStatus.BOTH_STREAMS_BOUND) }
        test("wrongUriDoesNotBind") { val r=Rig();r.offer();r.context(source=key("A","different"));r.status(RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS) }
        test("wrongRevisionDoesNotBind") { val r=Rig();r.offer();r.context(source=RenderSourceKey("A","same-media-id",r.aKey.uri,null,"r2"));r.status(RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS) }
        test("wrongCacheKeyDoesNotBind") { val r=Rig();r.offer();r.context(source=RenderSourceKey("A","same-media-id",r.aKey.uri,"cache", "r1"));r.status(RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS) }
        test("sameWindowRejected") { val r=Rig(bKey=key());r.offer();r.both();r.status(RenderBoundaryStatus.AMBIGUOUS_OCCURRENCE) }
        test("duplicateRenderersRejected") { val r=Rig();r.offer();r.context();r.context(r.b,r.aKey,r.tokenB);r.status(RenderBoundaryStatus.DUPLICATE_RENDERER_BINDING) }
        test("formatMismatch") { val r=Rig();r.offer();r.context();r.context(r.b,r.bKey,r.tokenB,format.copy(sampleRate=44100));r.status(RenderBoundaryStatus.FORMAT_MISMATCH) }
        test("nonPcmRejected") { val r=Rig();r.offer();r.context(pcm=format.copy(bytesPerSample=0));r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("channelMapRejected") { val r=Rig();r.offer();r.context(pcm=format.copy(identityChannelMapping=false));r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("encoderDelayRejected") { val r=Rig();r.offer();r.context(pcm=format.copy(encoderDelay=1024));r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("encoderPaddingRejected") { val r=Rig();r.offer();r.context(pcm=format.copy(encoderPadding=1));r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("multichannelRejected") { val r=Rig();r.offer();r.context(pcm=format.copy(channels=6));r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("unsupportedSampleRate") { val r=Rig();r.offer();r.context(pcm=format.copy(sampleRate=0));r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("tunnelingRejected") { val r=Rig();r.offer();r.context(tunnel=true);r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("positionPastCue") { val r=Rig();r.offer();r.context(position=1_000_001);r.status(RenderBoundaryStatus.POSITION_PAST_CUE) }
        test("equalPositionCue") { val r=Rig();r.offer();r.context(position=1_000_000);r.context(r.b,r.bKey,r.tokenB,position=1_000_000);r.status(RenderBoundaryStatus.BOTH_STREAMS_BOUND) }
        test("offsetConversion") { val r=Rig();r.offer();r.context(position=11_000_001,offset=10_000_000);r.status(RenderBoundaryStatus.POSITION_PAST_CUE) }
        test("windowPeriodOffset") { val r=Rig();r.offer();r.context(position=900_001,periodOffset=100_000);r.status(RenderBoundaryStatus.POSITION_PAST_CUE) }
        test("overflowRejected") { val r=Rig();r.offer();r.context(position=Long.MAX_VALUE,offset=-1);r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("partialCountsAcceptedOnly") { val r=Rig();r.offer(cue=.0005);r.both();val x=checkNotNull(r.controller.reserve());val b=pcm()
            val result=r.a.forwardUnchanged(b,0,1){b.position(8);false};check(!result&&b.position()==8)
            r.status(RenderBoundaryStatus.BOTH_STREAMS_BOUND);check(!r.controller.isCurrent(x))
            r.context();r.a.forwardUnchanged(b,0,1){b.position(b.limit());true};r.status(RenderBoundaryStatus.CUE_ALREADY_FORWARDED) }
        test("zeroAcceptanceDoesNotOwn") { val r=Rig();r.offer();r.both();val b=pcm();val x=checkNotNull(r.controller.reserve());check(!r.a.forwardUnchanged(b,0,1){false});check(b.position()==0&&r.controller.isCurrent(x)) }
        test("bytesUntouched") { val r=Rig();r.offer();r.context();val b=pcm();for(i in 0 until b.capacity())b.put(i,(i and 127).toByte())
            val before=ByteArray(b.capacity()){b.get(it)}
            b.position(4);b.limit(b.capacity()-4) // A frame can start at a nonzero buffer offset.
            r.a.forwardUnchanged(b,0,1){b.position(b.limit());true};r.status(RenderBoundaryStatus.OUTGOING_BOUND)
            b.limit(b.capacity());check(before.contentEquals(ByteArray(b.capacity()){b.get(it)})) }
        test("limitUnchanged") { val r=Rig();r.offer();r.context();val b=pcm();val n=b.limit();r.a.forwardUnchanged(b,0,1){b.position(8);false};check(b.limit()==n) }
        test("sameException") { val r=Rig();r.offer();r.context();val e=IllegalStateException("delegate");try{r.a.forwardUnchanged(pcm(),0,1){throw e};error("missing")}catch(x:IllegalStateException){check(x===e)};r.status(RenderBoundaryStatus.SINK_FAILURE) }
        test("retryDifferentBufferRejected") { val r=Rig();r.offer();r.context();r.a.forwardUnchanged(pcm(),0,1){false};r.context();r.a.forwardUnchanged(pcm(),0,1){false};r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("retryPtsRejected") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){false};r.context();r.a.forwardUnchanged(b,1,1){false};r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("retryLimitRejected") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){false};b.limit(16);r.context();r.a.forwardUnchanged(b,0,1){false};r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("retryPositionRejected") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){false};b.position(8);r.context();r.a.forwardUnchanged(b,0,1){false};r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("partialFrameRejected") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){b.position(1);false};r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("falseFullRejected") { val r=Rig();r.offer();r.context();val b=pcm();check(!r.a.forwardUnchanged(b,0,1){b.position(b.limit());false});r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("truePartialRejected") { val r=Rig();r.offer();r.context();check(r.a.forwardUnchanged(pcm(),0,1){true});r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("zeroFrameNoAdvance") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());check(r.a.forwardUnchanged(pcm(0),0,1){true});check(r.controller.isCurrent(x)) }
        test("newBufferContinuity") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){b.position(b.limit());true};r.context();val c=pcm();r.a.forwardUnchanged(c,1000,1){c.position(c.limit());true};r.status(RenderBoundaryStatus.OUTGOING_BOUND) }
        test("newBufferJumpRejected") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){b.position(b.limit());true};r.context();r.a.forwardUnchanged(pcm(),3000,1){false};r.status(RenderBoundaryStatus.DISCONTINUOUS_PCM) }
        test("ptsUncertaintyIsConservative") { val r=Rig();r.offer(cue=.001);r.context();val b=pcm();r.a.forwardUnchanged(b,0,1){b.position(b.limit());true};r.status(RenderBoundaryStatus.CUE_ALREADY_FORWARDED) }
        test("unknownContext") { val r=Rig();r.offer();r.a.before(pcm(),0,1);r.status(RenderBoundaryStatus.OUTPUT_CONTEXT_UNAVAILABLE) }
        test("encodedUnitsRejected") { val r=Rig();r.offer();r.context();r.a.before(pcm(),0,3);r.status(RenderBoundaryStatus.UNSUPPORTED_FORMAT) }
        test("newGenerationRevokes") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.offer(2);check(!r.controller.isCurrent(x));r.status(RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS) }
        test("newRevisionRevokes") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.offer(rev=2);check(!r.controller.isCurrent(x)) }
        test("stalePublicationIgnored") { val r=Rig();r.offer(5,3);r.offer(4,99);check(r.controller.snapshot().generation==5L);r.offer(5,2);check(r.controller.snapshot().revision==3L) }
        test("staleInvalidationIgnored") { val r=Rig();r.offer(5,3);r.controller.invalidate(4);check(r.controller.snapshot().generation==5L&&r.controller.snapshot().styleId==9) }
        test("invalidateSyncBarrier") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.controller.invalidate(2);check(!r.controller.isCurrent(x));r.status(RenderBoundaryStatus.NO_PLAN) }
        test("lateAfterCannotOverwrite") { val r=Rig();r.offer();r.context();val b=pcm();r.a.before(b,0,1);r.offer(2);b.position(b.limit());r.a.after(b,true);r.status(RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS);check(r.controller.snapshot().generation==2L) }
        test("newEpochStillKnowsQueuedPcm") { val r=Rig();r.offer();r.context();val b=pcm();r.a.forwardUnchanged(b,1_000_000,1){b.position(b.limit());true};r.offer(2);r.context();r.status(RenderBoundaryStatus.CUE_ALREADY_FORWARDED) }
        test("streamReplacementRevokes") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.context(token=Any(),sequence=12);check(!r.controller.isCurrent(x)) }
        test("flushRevokes") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.a.reset();check(!r.controller.isCurrent(x)) }
        test("routeChangeRevokes") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.a.fault(RenderBoundaryStatus.ROUTE_CHANGED);check(!r.controller.isCurrent(x));r.status(RenderBoundaryStatus.ROUTE_CHANGED) }
        test("speedChangeRevokes") { val r=Rig();r.offer();r.both();r.a.fault(RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED);r.status(RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED) }
        test("secondThreadFailsClosed") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());val thread=Thread{r.a.before(pcm(),0,1)};thread.start();thread.join();check(!r.controller.isCurrent(x));r.status(RenderBoundaryStatus.THREAD_VIOLATION) }
        test("closePermanent") { val r=Rig();r.offer();r.both();val x=checkNotNull(r.controller.reserve());r.controller.close();r.offer(9);r.context();check(!r.controller.isCurrent(x));r.status(RenderBoundaryStatus.CLOSED) }
        test("invalidPlanRejected") { val r=Rig();rejected{r.plan(cue=Double.NaN)};rejected{r.plan(cue=-1.0)};rejected{r.plan(gen=0)};rejected{r.plan(rev=-1)} }
        test("redactedKeys") { val r=Rig();check(!r.aKey.toString().contains("secret"));val p=r.plan();check(!p.canExecute);check(!r.controller.snapshot().toString().contains("private.invalid")) }
        test("format16bitAccepted") { val r=Rig();r.offer();val f=format.copy(bytesPerSample=2);r.context(pcm=f);r.context(r.b,r.bKey,r.tokenB,f);r.status(RenderBoundaryStatus.BOTH_STREAMS_BOUND) }
        test("oldIdentityNotInputIdentity") { val r=Rig();r.offer();r.context(source=key("OLD"));r.context(r.b,r.bKey,r.tokenB);r.status(RenderBoundaryStatus.INCOMING_BOUND) }
        test("latePhysicalAcceptanceCarriedForward") {
            val r=Rig();r.offer();r.context();val b=pcm();r.a.before(b,1_000_000,1)
            r.offer(2);b.position(b.limit());r.a.after(b,true)
            r.status(RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS)
            r.context();r.status(RenderBoundaryStatus.CUE_ALREADY_FORWARDED)
        }
        test("diagnosticFailureCannotReplaceDelegateSuccess") {
            val r=Rig();r.offer();r.context();r.a.version=Long.MAX_VALUE
            val b=pcm();check(r.a.forwardUnchanged(b,0,1){b.position(b.limit());true})
            check(b.position()==b.limit());r.status(RenderBoundaryStatus.CLOSED)
        }
    }
    fun run(name:String){checkNotNull(cases[name]) { "Unknown test" }.invoke()}
    @JvmStatic fun main(args:Array<String>) {
        var passed=0
        for(name in if(args.isEmpty())names else args.toList()) {
            run(name);passed++;println("PASS $name")
        }
        println("Render boundary: $passed/${if(args.isEmpty())names.size else args.size} scenarios PASSED")
    }
}
