package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/** Exercises the real gate + existing endpoint, without Android models or DSP substitutes. */
object PcmCueGateScenarios {
    private fun key(s: String) = RenderSourceKey(s,"same","https://private.invalid/$s?token=not-logged",null,"r1")
    private class Rig(val fmt: RenderPcmFormat = RenderPcmFormat(48_000,2,4),
        val cueA: Double = .001, val cueB: Double = .001) {
        var clock = 1000L
        val c = RenderBoundaryController { clock }
        val a = c.newEndpoint(); val b = c.newEndpoint()
        val ak = key("A"); val bk = key("B"); val at = Any(); val bt = Any()
        var generation = 1L; var revision = 1L
        val calls = arrayListOf<String>(); val seen = arrayListOf<Byte>()
        init { offer() }
        fun offer(g:Long=generation,r:Long=revision) { generation=g;revision=r
            c.offer(RenderBoundaryPlan.create(g,r,ak,bk,cueA,cueB,9)) }
        fun context(side:Int, token:Any=if(side==0)at else bt, f:RenderPcmFormat=fmt,
            source:RenderSourceKey=if(side==0)ak else bk, offset:Long=0, position:Long=offset,
            tunnel:Boolean=false) {
            endpoint(side).outputContext(token,RenderOutputIdentity(source,source.windowUid,10L+side,0,offset),f,position,tunnel)
        }
        fun endpoint(side:Int) = if(side==0)a else b
        fun arm(ms:Int=100) = c.requestCueProbe(generation,revision,ms)
        fun write(side:Int,buf:ByteBuffer,pts:Long=0,units:Int=1,
            consume:Int=buf.remaining(),context:Boolean=true):Boolean {
            if(context)context(side)
            return endpoint(side).forwardWithCueGate(buf,pts,units) {
                calls+="$side:$pts:${buf.position()}:${buf.limit()}"
                val take=minOf(consume,buf.remaining()); repeat(take){seen+=buf.get()}
                !buf.hasRemaining()
            }
        }
        fun report(phase:CueProbePhase,reason:CueProbeReason?=null,mask:Int?=null) {
            val s=c.cueProbeSnapshot(); check(s.phase==phase){"Expected $phase; got $s"}
            if(reason!=null)check(s.reason==reason){"Expected $reason; got $s"}
            if(mask!=null)check(s.blockedMask==mask){"Expected mask $mask; got $s"}
            check(!s.canExecute&&!s.ownsTransitionGain&&!s.pcmAcceptedByExecutor)
        }
        fun both(x:ByteBuffer=pcm(96,fmt),y:ByteBuffer=pcm(96,fmt)) {
            check(!write(0,x));check(!write(1,y));report(CueProbePhase.PAIR_HELD,mask=3)
        }
    }
    private fun pcm(frames:Int=96,f:RenderPcmFormat=RenderPcmFormat(48000,2,4),padding:Int=0):ByteBuffer {
        val b=ByteBuffer.allocateDirect(frames*f.bytesPerFrame+padding*2).order(ByteOrder.nativeOrder())
        for(i in 0 until b.capacity())b.put(i,((i*31+17)%251).toByte())
        b.position(padding);b.limit(padding+frames*f.bytesPerFrame);return b
    }
    private fun bytes(b:ByteBuffer)=ByteArray(b.remaining()){b.get(b.position()+it)}
    private fun invalid(block:()->Unit) {var bad=false;try{block()}catch(_:IllegalArgumentException){bad=true};check(bad)}
    private val tests=linkedMapOf<String,()->Unit>()
    val names:List<String> get()=tests.keys.toList()
    private fun test(name:String, body:()->Unit) {check(tests.put(name,body)==null)}
    init {
        test("dormantSameBuffer") {val r=Rig();val x=pcm();val limit=x.limit();check(r.write(0,x));check(x.limit()==limit&&r.calls.size==1);r.report(CueProbePhase.IDLE)}
        test("dormantPartialAndExactReturn") {val r=Rig();val x=pcm();check(!r.write(0,x,consume=8));check(x.position()==8);check(r.write(0,x));check(r.calls.size==2)}
        test("requestRequiresPlan") {val c=RenderBoundaryController();c.invalidate(1,1);check(c.requestCueProbe(1,1)==CueProbeRequest.NO_PLAN)}
        test("requestRequiresGeneration") {val r=Rig();check(r.c.requestCueProbe(2,1)==CueProbeRequest.STALE)}
        test("requestRequiresRevision") {val r=Rig();check(r.c.requestCueProbe(1,2)==CueProbeRequest.STALE)}
        test("requestBounds") {val r=Rig();invalid{r.arm(0)};invalid{r.arm(501)};invalid{r.c.requestCueProbe(-1,0)};check(r.arm(1)==CueProbeRequest.REQUESTED)}
        test("sameAttemptCannotExtendTimeout") {val r=Rig();check(r.arm(1)==CueProbeRequest.REQUESTED);r.clock+=900_000;check(r.arm(500)==CueProbeRequest.ALREADY_ATTEMPTED);r.clock+=100_000;r.report(CueProbePhase.RELEASED,CueProbeReason.TIMEOUT)}
        test("sameRevisionRepublishCannotRearm") {val r=Rig();r.arm();r.c.releaseCueProbe();r.offer();check(r.arm()==CueProbeRequest.ALREADY_ATTEMPTED)}
        test("oneBufferHeldNoSinkCalls") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));check(x.position()==0&&r.calls.isEmpty());r.report(CueProbePhase.ONE_BUFFER_HELD,mask=1)}
        test("wholePrefixHeld") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));check(x.position()==0);r.context(1);val y=pcm();check(!r.write(1,y));val o=pcm();val i=pcm();val s=checkNotNull(r.c.copyHeldCuePair(o,i));check(s.outgoing.prefixFrames==48&&s.outgoing.frames==96)}
        test("twoBuffersHeldRolesNotRendererOrder") {val r=Rig();r.arm();val x=pcm();val y=pcm();r.context(0,source=r.bk);check(!r.write(0,x,context=false));r.context(1,source=r.ak);check(!r.write(1,y,context=false));r.report(CueProbePhase.PAIR_HELD)}
        test("nonzeroBufferPositionAndLimit") {val r=Rig();r.arm();val x=pcm(padding=16);val y=pcm(padding=8);val before=bytes(x);r.both(x,y);val o=pcm(padding=8);val i=pcm();val meta=checkNotNull(r.c.copyHeldCuePair(o,i));check(x.position()==16&&x.limit()==784&&meta.outgoing.bytes==768);o.position(8);check(bytes(o).contentEquals(before))}
        test("heldRetryDoesNotWriteOrAdvance") {val r=Rig();r.arm();val x=pcm();repeat(20){check(!r.write(0,x))};check(x.position()==0&&r.calls.isEmpty())}
        test("manualReleaseOriginalDataOnce") {val r=Rig();r.arm();val x=pcm();val expected=bytes(x);check(!r.write(0,x));r.c.releaseCueProbe();check(r.write(0,x));check(r.seen.toByteArray().contentEquals(expected));r.report(CueProbePhase.RELEASED,CueProbeReason.USER_RELEASE)}
        test("timeoutReleasesOriginal") {val r=Rig();r.arm(1);val x=pcm();check(!r.write(0,x));r.clock+=1_000_000;check(r.write(0,x));check(r.calls.size==1);r.report(CueProbePhase.RELEASED,CueProbeReason.TIMEOUT)}
        test("timeoutAtExactBoundary") {val r=Rig();r.arm(1);val x=pcm();r.clock+=999_999;check(!r.write(0,x));r.clock++;check(r.write(0,x));r.report(CueProbePhase.RELEASED,CueProbeReason.TIMEOUT)}
        test("nanoTimeWrap") {val r=Rig();r.clock=Long.MAX_VALUE-500_000;r.arm(1);r.clock+=1_000_000;r.report(CueProbePhase.RELEASED,CueProbeReason.TIMEOUT)}
        test("generationChangeReleasesBoth") {val r=Rig();r.arm();val x=pcm();val y=pcm();r.both(x,y);r.offer(2);check(r.write(0,x)&&r.write(1,y));check(r.c.copyHeldCuePair(pcm(),pcm())==null)}
        test("revisionChangeReleasesBoth") {val r=Rig();r.arm();r.both();r.offer(r=2);r.report(CueProbePhase.RELEASED,CueProbeReason.EPOCH_CHANGED)}
        test("closeRevokesRequest") {val r=Rig();r.arm();r.both();r.c.close();check(r.c.requestCueProbe(1,1)==CueProbeRequest.CLOSED);r.report(CueProbePhase.RELEASED,CueProbeReason.CLOSED)}
        test("noPlanRevokes") {val r=Rig();r.arm();r.both();r.c.invalidate(2);r.report(CueProbePhase.RELEASED,CueProbeReason.EPOCH_CHANGED)}
        test("partialAfterReleaseSameBuffer") {val r=Rig();r.arm();val x=pcm();val expected=bytes(x);check(!r.write(0,x));r.c.releaseCueProbe();repeat(95){check(!r.write(0,x,consume=8))};check(r.write(0,x,consume=8));check(r.seen.toByteArray().contentEquals(expected));check(r.calls.first().contains(":0:0:"))}
        test("previouslyOfferedZeroBytesCannotHold") {val r=Rig();val x=pcm();check(!r.write(0,x,consume=0));r.arm();check(!r.write(0,x,consume=8));r.report(CueProbePhase.RELEASED,CueProbeReason.BUFFER_ALREADY_OFFERED)}
        test("previouslyOfferedPartialCannotHold") {val r=Rig();val x=pcm();r.write(0,x,consume=8);r.arm();check(r.write(0,x));r.report(CueProbePhase.RELEASED,CueProbeReason.BUFFER_ALREADY_OFFERED)}
        test("passedCueBufferCannotArmLater") {val r=Rig();r.write(0,pcm());r.arm();check(r.write(0,pcm(),pts=2000));r.report(CueProbePhase.RELEASED,CueProbeReason.BUFFER_ALREADY_OFFERED)}
        test("preCueBufferPassesWithoutHold") {val r=Rig(cueA=.01);r.arm();val x=pcm();check(r.write(0,x));r.report(CueProbePhase.ARMED)}
        test("exactExclusiveEndThenCueBuffer") {val r=Rig();r.arm();check(r.write(0,pcm(48)));val y=pcm(48);check(!r.write(0,y,pts=1000));check(y.position()==0);r.report(CueProbePhase.ONE_BUFFER_HELD)}
        test("startAfterCueRejects") {val r=Rig();r.arm();check(r.write(0,pcm(),pts=2000));r.report(CueProbePhase.RELEASED,CueProbeReason.CUE_MISSED)}
        test("zeroLengthDoesNotBind") {val r=Rig();r.arm();check(r.write(0,pcm(0)));r.report(CueProbePhase.ARMED,mask=0)}
        test("unsupportedEncoding") {val r=Rig();r.arm();r.context(0,f=r.fmt.copy(bytesPerSample=0));check(r.write(0,pcm(),context=false));r.report(CueProbePhase.RELEASED,CueProbeReason.PLAYBACK_UNSUPPORTED)}
        test("tunnelingRejectsGateNotDelegate") {val r=Rig();r.arm();r.context(0,tunnel=true);check(r.write(0,pcm(),context=false));r.report(CueProbePhase.RELEASED,CueProbeReason.PLAYBACK_UNSUPPORTED)}
        test("unavailableContextIsPassthrough") {val r=Rig();r.arm();check(r.write(0,pcm(),context=false));check(r.calls.size==1)}
        test("formatMismatchReleasesFirst") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));r.context(1,f=r.fmt.copy(channels=1));check(r.write(1,pcm(96,r.fmt.copy(channels=1)),context=false));r.report(CueProbePhase.RELEASED,CueProbeReason.FORMAT_MISMATCH);check(r.write(0,x))}
        test("duplicateSideRejects") {val r=Rig();r.arm();check(!r.write(0,pcm()));r.context(1,source=r.ak);check(r.write(1,pcm(),context=false));r.report(CueProbePhase.RELEASED,CueProbeReason.DUPLICATE_SIDE)}
        test("wrongSourceNeverCaptures") {val r=Rig();r.arm();r.context(0,source=key("X"));check(r.write(0,pcm(),context=false));r.report(CueProbePhase.ARMED)}
        test("changedOutputReleases") {val r=Rig();r.arm();check(!r.write(0,pcm()));r.context(0,token=Any());check(r.write(0,pcm(),context=false));r.report(CueProbePhase.RELEASED,CueProbeReason.OUTPUT_CHANGED)}
        test("heldBufferIdentityMismatch") {val r=Rig();r.arm();check(!r.write(0,pcm()));check(r.write(0,pcm()));r.report(CueProbePhase.RELEASED,CueProbeReason.INVALID_BUFFER)}
        test("heldBufferPositionMismatch") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));x.position(8);check(r.write(0,x));r.report(CueProbePhase.RELEASED,CueProbeReason.INVALID_BUFFER)}
        test("heldBufferPtsMismatch") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));check(r.write(0,x,pts=1));r.report(CueProbePhase.RELEASED,CueProbeReason.INVALID_BUFFER)}
        test("heldBufferLimitMismatch") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));x.limit(16);check(r.write(0,x));r.report(CueProbePhase.RELEASED,CueProbeReason.INVALID_BUFFER)}
        test("pauseReleasesDoesNotDiscardDelegateRetry") {val r=Rig();r.arm();val x=pcm();val expected=bytes(x);r.write(0,x);r.a.cuePort.cancel(CueProbeReason.PAUSED);check(!r.write(0,x,consume=8));check(r.write(0,x));check(r.seen.toByteArray().contentEquals(expected));r.report(CueProbePhase.RELEASED,CueProbeReason.PAUSED)}
        test("flushForgetsWithoutReplaying") {val r=Rig();r.arm();check(!r.write(0,pcm()));r.a.cuePort.cancel(CueProbeReason.OUTPUT_RESET,true);r.a.reset();r.offer(2);check(r.arm()==CueProbeRequest.REQUESTED);check(!r.write(0,pcm()));check(r.calls.isEmpty())}
        test("routeRevocation") {val r=Rig();r.arm();r.both();r.a.cuePort.cancel(CueProbeReason.ROUTE_CHANGED);r.report(CueProbePhase.RELEASED,CueProbeReason.ROUTE_CHANGED)}
        test("sinkExceptionIdentity") {val r=Rig();r.arm();val x=pcm();r.write(0,x);r.c.releaseCueProbe();r.context(0);val err=IllegalStateException("delegate-error");try{r.a.forwardWithCueGate(x,0,1){throw err};error("missing")}catch(e:IllegalStateException){check(e===err)}}
        test("copyRequiresBoth") {val r=Rig();r.arm();r.write(0,pcm());val dst=pcm();val pos=dst.position();check(r.c.copyHeldCuePair(dst,pcm())==null&&dst.position()==pos)}
        test("copyPreservesOriginalBytesAndCursors") {val r=Rig();r.arm();val x=pcm();val y=pcm();val expected=bytes(x);r.both(x,y);val o=pcm();val i=pcm();val meta=checkNotNull(r.c.copyHeldCuePair(o,i));check(!meta.canExecute);check(x.position()==0&&y.position()==0&&bytes(x).contentEquals(expected));o.flip();check(bytes(o).contentEquals(expected))}
        test("copyCanRepeatWithoutConsumption") {val r=Rig();r.arm();r.both();repeat(5){checkNotNull(r.c.copyHeldCuePair(pcm(),pcm()))};r.report(CueProbePhase.PAIR_HELD)}
        test("copyRejectsTooSmallAtomically") {val r=Rig();r.arm();r.both();val o=pcm();val before=bytes(o);invalid{r.c.copyHeldCuePair(o,pcm(1))};check(o.position()==0&&bytes(o).contentEquals(before))}
        test("copyRejectsReadOnly") {val r=Rig();r.arm();r.both();invalid{r.c.copyHeldCuePair(pcm().asReadOnlyBuffer(),pcm())}}
        test("copyRejectsHeap") {val r=Rig();r.arm();r.both();invalid{r.c.copyHeldCuePair(ByteBuffer.allocate(768),pcm())}}
        test("copyRejectsSameDestination") {val r=Rig();r.arm();r.both();val x=pcm();invalid{r.c.copyHeldCuePair(x,x)}}
        test("copyRejectsSourceAlias") {val r=Rig();r.arm();val x=pcm();r.both(x);invalid{r.c.copyHeldCuePair(x,pcm())}}
        test("copyAfterInvalidationReturnsNull") {val r=Rig();r.arm();r.both();r.offer(2);check(r.c.copyHeldCuePair(pcm(),pcm())==null)}
        test("copyAfterOutputCallbackChangeReturnsNull") {val r=Rig();r.arm();r.both();r.context(0,token=Any());check(r.c.copyHeldCuePair(pcm(),pcm())==null)}
        test("newPlanWhileOldHoldCanRearm") {val r=Rig();r.arm();val x=pcm();r.both(x);r.offer(2);check(r.arm()==CueProbeRequest.REQUESTED);check(!r.write(0,x));r.report(CueProbePhase.ONE_BUFFER_HELD)}
        test("sourceGrid44100Rounding") {val f=RenderPcmFormat(44100,1,2);val r=Rig(f,100.0/44100,100.0/44100);r.arm();check(r.write(0,pcm(44,f),0));check(!r.write(0,pcm(100,f),998));r.report(CueProbePhase.ONE_BUFFER_HELD)}
        test("sourceGridUnsupportedSubframeOffset") {val r=Rig();r.arm();check(r.write(0,pcm(),pts=10));r.report(CueProbePhase.RELEASED,CueProbeReason.PCM_GRID_UNRESOLVED)}
        test("sourceFrameDiscontinuityRejects") {val r=Rig(cueA=.01);r.arm();r.write(0,pcm());check(r.write(0,pcm(),pts=4000));r.report(CueProbePhase.RELEASED,CueProbeReason.PLAYBACK_UNSUPPORTED)}
        test("rendererOffsetDoesNotShiftCue") {val r=Rig();r.arm();r.context(0,offset=1_000_000);check(!r.write(0,pcm(),pts=1_000_000,context=false));r.report(CueProbePhase.ONE_BUFFER_HELD)}
        test("cueRoundingHalfUpExplicit") {val r=Rig(cueA=48.5/48000,cueB=48.5/48000);r.arm();r.both();val s=checkNotNull(r.c.copyHeldCuePair(pcm(),pcm()));check(s.outgoing.cueSourceFrame==49L)}
        test("mono16AndStereoFloat") {for(f in listOf(RenderPcmFormat(8000,1,2),RenderPcmFormat(48000,2,4),RenderPcmFormat(192000,2,2))){val r=Rig(f);r.arm();r.both(pcm(400,f),pcm(400,f));checkNotNull(r.c.copyHeldCuePair(pcm(400,f),pcm(400,f)))}}
        test("otherThreadCannotCopy") {val r=Rig();r.arm();r.both();var result:CueCopiedPair?=null;val t=Thread{result=r.c.copyHeldCuePair(pcm(),pcm())};t.start();t.join();check(result==null);r.report(CueProbePhase.RELEASED,CueProbeReason.THREAD_VIOLATION)}
        test("mainReleaseAfterBothDoesNotConsume") {val r=Rig();r.arm();val x=pcm();val y=pcm();r.both(x,y);val t=Thread{r.c.releaseCueProbe()};t.start();t.join();check(x.position()==0&&y.position()==0&&r.calls.isEmpty());check(r.write(0,x)&&r.write(1,y))}
        test("randomizedPartialReleaseBytesOnce") {val random=java.util.Random(17);repeat(100){val r=Rig();r.arm();val x=pcm(49+random.nextInt(100),r.fmt,8);val expected=bytes(x);check(!r.write(0,x));r.c.releaseCueProbe();while(x.hasRemaining()){r.write(0,x,consume=8*(1+random.nextInt(12)))};check(expected.contentEquals(r.seen.toByteArray()))}}
        test("rendererPositionPastCueReleasesHeld") {val r=Rig();r.arm();val x=pcm();check(!r.write(0,x));r.context(0,position=1001);check(r.write(0,x,context=false));r.report(CueProbePhase.RELEASED,CueProbeReason.POSITION_PAST_CUE)}
        test("copyRechecksRendererPosition") {val r=Rig();r.arm();r.both();r.context(0,position=1001);check(r.c.copyHeldCuePair(pcm(),pcm())==null);r.report(CueProbePhase.RELEASED,CueProbeReason.POSITION_PAST_CUE)}
        test("largeBufferFailsBackToDelegate") {val r=Rig();r.arm();val x=pcm(131073);check(r.write(0,x));r.report(CueProbePhase.RELEASED,CueProbeReason.BUFFER_LIMIT)}
        test("encodedBatchDoesNotHold") {val r=Rig();r.arm();check(r.write(0,pcm(),units=2));r.report(CueProbePhase.RELEASED,CueProbeReason.PLAYBACK_UNSUPPORTED)}
        test("partialFrameOfferedNotHeld") {val r=Rig();r.arm();val x=pcm();x.limit(x.limit()-1);check(r.write(0,x));r.report(CueProbePhase.RELEASED,CueProbeReason.PLAYBACK_UNSUPPORTED)}
        test("snapshotIsRedactedAndNeverExecutable") {val r=Rig();r.arm();r.both();val text=r.c.cueProbeSnapshot().toString();check(!text.contains("token=")&&!text.contains("private.invalid"));r.report(CueProbePhase.PAIR_HELD)}
    }
    fun run(name:String) {checkNotNull(tests[name]){"Unknown test: $name"}.invoke()}
    @JvmStatic fun main(args:Array<String>) {
        System.getProperty("lmg.cue.expectedJar")?.let { expected ->
            val actual=java.io.File(PcmCueGate::class.java.protectionDomain.codeSource.location.toURI()).canonicalFile
            check(actual==java.io.File(expected).canonicalFile) { "Wrong loaded cue implementation" }
            println("CUE_IMPLEMENTATION=$actual")
        }
        val selected=if(args.isEmpty())names else args.toList();for(n in selected){run(n);println("PASS $n")};println("PCM cue gate: ${selected.size}/${selected.size} scenarios PASSED")}
}
