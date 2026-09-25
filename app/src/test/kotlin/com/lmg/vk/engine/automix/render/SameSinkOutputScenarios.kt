package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/** Actual production output-port code, with an explicitly simulated sink. No device/clock parity claim. */
object SameSinkOutputScenarios {
    private class Backend : PcmOutputBackend {
        val received = ArrayList<Byte>()
        val objects = ArrayList<ByteBuffer>()
        val stamps = ArrayList<Long>()
        val volumes = ArrayList<Float>()
        var take = Int.MAX_VALUE
        var falseWhenEmpty = false
        var pending = false
        var clock = Long.MIN_VALUE
        var after: (() -> Unit)? = null
        var error: Throwable? = null
        override fun write(buffer: ByteBuffer, ptsUs: Long): Boolean {
            objects += buffer; stamps += ptsUs
            repeat(minOf(take, buffer.remaining())) { received += buffer.get() }
            after?.invoke(); error?.let { throw it }
            return !buffer.hasRemaining() && !falseWhenEmpty
        }
        override fun volume(value: Float) { volumes += value }
        override fun positionUs() = clock
        override fun pending() = pending
    }
    private class Rig(val width: Int = 4, val channels: Int = 1, val rate: Int = 8000) {
        val sink = Backend()
        val port = SameSinkOutputPort(sink)
        val format = RenderPcmFormat(rate, channels, width)
        var current = true
        val token = Any()
        init { port.bindOutput(token, format);port.transitionVolume(.25f, .8f) }
        fun reserve(mayWrite: Boolean = true, base: Long = 1_000_000, activate: Boolean = true): SameSinkOutputPort.Ticket {
            val t=checkNotNull(port.reserve(1, 1, format, base, { current }, mayWrite))
            if (activate) check(port.activate(t))
            return t
        }
        fun buffer(frames: Int = 8): ByteBuffer = ByteBuffer.allocateDirect(frames * format.bytesPerFrame).also {
            for (i in 0 until it.capacity()) it.put(i, (i * 7 + 3).toByte())
        }
    }
    private fun fails(action: () -> Unit): Throwable {
        try { action() } catch (failure: Exception) { return failure }
        error("TEST_EXPECTED_FAILURE")
    }
    private val cases = linkedMapOf<String, () -> Unit>()
    val names: List<String> get() = cases.keys.toList()
    private fun test(name: String, body: () -> Unit) { check(cases.put(name, body) == null) }
    init {
        test("idleFadeProductPreserved") { val r=Rig();check(r.sink.volumes.last()==.2f) }
        test("idleMasterMessagePreservesDirectSemantics") { val r=Rig();r.port.playerVolume(.3f);check(r.sink.volumes.last()==.3f) }
        test("activatedRemovesOnlyTransitionGain") { val r=Rig();r.reserve();check(r.sink.volumes.last()==.8f) }
        test("activatedTracksDucking") { val r=Rig();r.reserve();r.port.transitionVolume(.1f,.15f);check(r.sink.volumes.last()==.15f) }
        test("activatedTracksPlayerVolume") { val r=Rig();r.reserve();r.port.playerVolume(.4f);check(r.sink.volumes.last()==.4f) }
        test("zeroMasterNeverBoosted") { val r=Rig();r.port.transitionVolume(.9f,0f);r.reserve();check(r.sink.volumes.last()==0f) }
        test("rollbackRestoresLatestProduct") { val r=Rig();val t=r.reserve();r.port.transitionVolume(.6f,.5f);check(r.port.rollback(t));check(r.sink.volumes.last()==.3f) }
        test("missingSplitHookCannotReserve") { val s=Backend();val p=SameSinkOutputPort(s);val f=RenderPcmFormat(8000,1,4);p.bindOutput(Any(),f);p.playerVolume(1f);check(p.reserve(1,1,f,0,{true})==null) }
        test("missingOutputCannotReserve") {val s=Backend();val p=SameSinkOutputPort(s);p.transitionVolume(1f,1f);check(p.reserve(1,1,RenderPcmFormat(8000,1,4),0,{true})==null)}
        test("wrongFormatCannotReserve") {val r=Rig();check(r.port.reserve(1,1,RenderPcmFormat(48000,1,4),0,{true})==null)}
        test("staleEpochCannotReserve") {val r=Rig();r.current=false;check(r.port.reserve(1,1,r.format,0,{r.current})==null)}
        test("cannotReserveTwice") {val r=Rig();r.reserve();check(r.port.reserve(1,1,r.format,0,{true})==null)}
        test("companionPendingBlocksReservation") {val r=Rig();r.sink.pending=true;check(r.port.reserve(1,1,r.format,0,{true},false)==null)}
        test("outgoingPendingMayRemainOnSameOutput") {val r=Rig();r.sink.pending=true;r.reserve(activate=false);check(r.sink.received.isEmpty())}
        test("companionCannotWrite") {val r=Rig();val t=r.reserve(false);fails{r.port.write(t,1,0,r.buffer())};check(r.sink.received.isEmpty())}
        test("legacyWriteAllowedOnlyIdle") {val r=Rig();r.port.checkLegacyWrite();r.reserve();fails{r.port.checkLegacyWrite()};check(r.port.snapshot().phase==OutputPortPhase.RESET_REQUIRED)}
        test("firstPacketRetainsBytesAndIdentity") {val r=Rig();val b=r.buffer();val expected=(0 until b.limit()).map{b.get(it)};val t=r.reserve();val n=r.port.write(t,1,0,b);check(n.bufferFinished&&n.newlyCompletedFrames==8&&r.sink.received==expected&&r.sink.objects.single()===b)}
        test("zeroAcceptanceStillOwnsBuffer") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=0;check(r.port.write(t,1,0,b).acceptedBytes==0);check(!r.port.rollback(t));check(r.port.snapshot().pendingBuffer)}
        test("partialBytesOnlyAdvanceCompletedFrames") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=3;val x=r.port.write(t,1,0,b);check(x.acceptedBytes==3&&x.newlyCompletedFrames==0);val y=r.port.write(t,1,0,b);check(y.newlyCompletedFrames==1&&r.port.snapshot().remainderBytes==2)}
        test("retryRequiresOriginalBuffer") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=1;r.port.write(t,1,0,b);fails{r.port.write(t,1,0,b.duplicate())};check(r.sink.objects.size==1)}
        test("retryRequiresSameLimit") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=1;r.port.write(t,1,0,b);b.limit(b.limit()-1);fails{r.port.write(t,1,0,b)}}
        test("retryRequiresExpectedPosition") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=1;r.port.write(t,1,0,b);b.position(0);fails{r.port.write(t,1,0,b)}}
        test("retryRequiresSamePacketId") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=1;r.port.write(t,1,0,b);fails{r.port.write(t,2,0,b)}}
        test("retryRequiresSameFirstFrame") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=1;r.port.write(t,1,0,b);fails{r.port.write(t,1,1,b)}}
        test("falseExhaustedRetriesSameEmptyBuffer") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.falseWhenEmpty=true;check(!r.port.write(t,1,0,b).bufferFinished);r.sink.falseWhenEmpty=false;check(r.port.write(t,1,0,b).bufferFinished);check(r.sink.objects.all{it===b}&&r.sink.received.size==32)}
        test("timestampStableForAllRetries") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=3;while(!r.port.write(t,1,0,b).bufferFinished){};check(r.sink.stamps.toSet()==setOf(1_000_000L))}
        test("nextTimestampUsesAbsoluteFrameNotAccumulatedRounding") {val r=Rig(rate=44100);val t=r.reserve();repeat(20){r.port.write(t,it+1L,it.toLong(),r.buffer(1))};check(r.sink.stamps==(0L..19L).map{1_000_000L+it*1_000_000L/44100})}
        test("nextPacketCannotSkipFrames") {val r=Rig();val t=r.reserve();r.port.write(t,1,0,r.buffer());fails{r.port.write(t,2,9,r.buffer())};check(r.sink.objects.size==1)}
        test("oldPacketCannotReplayAfterCompletion") {val r=Rig();val t=r.reserve();r.port.write(t,1,0,r.buffer());fails{r.port.write(t,1,8,r.buffer())};check(r.sink.objects.size==1)}
        test("heapPacketRejectedBeforeDelegate") {val r=Rig();val t=r.reserve();fails{r.port.write(t,1,0,ByteBuffer.allocate(32))};check(r.sink.objects.isEmpty())}
        test("unalignedPacketRejectedBeforeDelegate") {val r=Rig();val t=r.reserve();val b=r.buffer();b.limit(31);fails{r.port.write(t,1,0,b)};check(r.sink.objects.isEmpty())}
        test("sinkExceptionPreservesPartialSideEffects") {val r=Rig();val t=r.reserve();val b=r.buffer();val original=IllegalStateException("test sink");r.sink.error=original;r.sink.take=7;check(fails{r.port.write(t,1,0,b)}===original);check(r.port.snapshot().acceptedBytes==7L&&r.port.snapshot().phase==OutputPortPhase.RESET_REQUIRED);check(!r.port.rollback(t))}
        test("cancelInsideWriteDoesNotReplayAcceptedBytes") {val r=Rig();val t=r.reserve();val b=r.buffer();r.sink.take=4;r.sink.after={r.current=false};fails{r.port.write(t,1,0,b)};check(r.port.snapshot().acceptedBytes==4L);fails{r.port.write(t,1,0,b)};check(r.sink.objects.size==1)}
        test("ticketRevocationStopsNextWrite") {val r=Rig();val t=r.reserve();t.revoke();fails{r.port.write(t,1,0,r.buffer())};check(r.sink.objects.isEmpty())}
        test("outputTokenChangeRevokes") {val r=Rig();val t=r.reserve();r.port.bindOutput(Any(),r.format);fails{r.port.write(t,1,0,r.buffer())}}
        test("formatChangeRevokes") {val r=Rig();val t=r.reserve();r.port.bindOutput(r.token,r.format.copy(sampleRate=48000));fails{r.port.write(t,1,0,r.buffer())}}
        test("sameContextDoesNotInvalidate") {val r=Rig();val t=r.reserve();r.port.bindOutput(r.token,r.format);check(r.port.write(t,1,0,r.buffer()).bufferFinished)}
        test("invalidateRequiresActualReset") {val r=Rig();r.reserve();r.port.invalidate();fails{r.port.checkLegacyWrite()};r.port.afterActualReset();r.port.checkLegacyWrite()}
        test("oldTicketCannotCrossReset") {val r=Rig();val old=r.reserve();r.port.afterActualReset();r.port.bindOutput(Any(),r.format);r.port.transitionVolume(1f,1f);r.reserve();fails{r.port.write(old,1,0,r.buffer())};check(r.sink.objects.isEmpty())}
        test("clockUnavailableHasNoWallclockFallback") {val r=Rig();val t=r.reserve();check(r.port.sampleSinkClock(t)==null&&r.port.snapshot().sinkPositionUs==null)}
        test("clockReportsSinkNotAcceptedCounter") {val r=Rig();val t=r.reserve();r.port.write(t,1,0,r.buffer());r.sink.clock=999999;check(r.port.sampleSinkClock(t)==999999L&&r.port.snapshot().completeFrames==8L)}
        test("clockRegressionRequiresReset") {val r=Rig();val t=r.reserve();r.sink.clock=100;r.port.sampleSinkClock(t);r.sink.clock=99;fails{r.port.sampleSinkClock(t)}}
        test("clockEpochChangesDuringSamplingRejected") {val r=Rig();val t=r.reserve();r.current=false;fails{r.port.sampleSinkClock(t)}}
        test("foreignOwnerThreadRejected") {val r=Rig();val t=r.reserve();val result=AtomicReference<Throwable?>();val thread=Thread{try{r.port.write(t,1,0,r.buffer())}catch(e:Throwable){result.set(e)}};thread.start();thread.join();check(result.get()!=null&&r.sink.objects.isEmpty())}
        test("revocationMayComeFromPublisherThread") {val r=Rig();val t=r.reserve();val thread=Thread{t.revoke()};thread.start();thread.join();fails{r.port.write(t,1,0,r.buffer())}}
        test("invalidGainRejectedWithoutSetter") {val r=Rig();val count=r.sink.volumes.size;for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.1f)){fails{r.port.transitionVolume(v,1f)};fails{r.port.playerVolume(v)}};check(r.sink.volumes.size==count)}
        test("snapshotNeverClaimsPlaybackOrDsp") {val r=Rig();r.reserve();val s=r.port.snapshot();check(!s.canExecute&&!s.liveDspInstalled&&!s.rendererClockReserved)}
        test("pcm16StereoConservation") {val r=Rig(2,2);val t=r.reserve();val b=r.buffer();r.sink.take=3;while(!r.port.write(t,1,0,b).bufferFinished){};check(r.port.snapshot().completeFrames==8L&&r.port.snapshot().remainderBytes==0)}
        test("reservationDoesNotChangeQueuedGain") {val r=Rig();r.sink.pending=true;val before=r.sink.volumes.toList();r.reserve(activate=false);check(r.sink.volumes==before)}
        test("queuedFadedAudioBlocksActivation") {val r=Rig();r.sink.pending=true;val t=r.reserve(activate=false);check(!r.port.activate(t)&&r.sink.volumes.last()==.2f);fails{r.port.write(t,1,0,r.buffer())};check(r.sink.objects.isEmpty())}
        test("queuedUnityAudioMayKeepExistingMaster") {val r=Rig();r.port.transitionVolume(1f,.5f);r.sink.pending=true;r.reserve();check(r.sink.volumes.last()==.5f)}
        test("reservedFadeStillAffectsOldAudio") {val r=Rig();r.reserve(activate=false);r.port.transitionVolume(.5f,.6f);check(r.sink.volumes.last()==.3f)}
        test("bufferCannotWriteBeforeActivation") {val r=Rig();val t=r.reserve(activate=false);fails{r.port.write(t,1,0,r.buffer())};check(r.sink.objects.isEmpty())}
        test("revokedActiveWriterKeepsGainSeparated") {val r=Rig();val t=r.reserve();r.port.write(t,1,0,r.buffer());r.port.invalidate();r.port.transitionVolume(.2f,.7f);check(r.sink.volumes.last()==.7f&&r.port.snapshot().phase==OutputPortPhase.RESET_REQUIRED)}
        test("revokedUnactivatedReservationKeepsLegacyGain") {val r=Rig();r.reserve(activate=false);r.port.invalidate();r.port.transitionVolume(.2f,.7f);check(r.sink.volumes.last()==.2f*.7f)}
        test("actualResetRestoresOrdinaryGainSemantics") {val r=Rig();r.reserve();r.port.invalidate();r.port.afterActualReset();r.port.transitionVolume(.2f,.7f);check(r.sink.volumes.last()==.2f*.7f)}
        test("constructionVolumeDoesNotBindWrongThread") {val s=Backend();val p=SameSinkOutputPort(s);p.playerVolume(.5f);val result=AtomicReference<Throwable?>();val thread=Thread{try{val f=RenderPcmFormat(8000,1,4);p.bindOutput(Any(),f);p.transitionVolume(1f,.5f);check(p.reserve(1,1,f,0,{true})!=null)}catch(e:Throwable){result.set(e)}};thread.start();thread.join();check(result.get()==null)}
        test("typedPlayerMessageCanReserveWithoutManualFade") {val s=Backend();val p=SameSinkOutputPort(s);val f=RenderPcmFormat(8000,1,4);p.bindOutput(Any(),f);p.confirmedPlayerVolume(.4f);val t=checkNotNull(p.reserve(1,1,f,0,{true}));check(p.activate(t)&&s.volumes.last()==.4f)}
        test("rollbackAfterDirectMasterRestoresDirectValue") {val r=Rig();val t=r.reserve();r.port.playerVolume(.4f);check(r.port.rollback(t)&&r.sink.volumes.last()==.4f)}
        test("randomPartialWritesConserveEveryByte") {repeat(100){seed->val r=Rig(channels=2);val t=r.reserve();val rng=java.util.Random(seed.toLong());val expected=ArrayList<Byte>();var first=0L;repeat(20){packet->val frames=1+rng.nextInt(60);val b=r.buffer(frames);expected.addAll((0 until b.limit()).map{b.get(it)});var done=false;var attempts=0;while(!done){check(++attempts<2000);r.sink.take=rng.nextInt(33);done=r.port.write(t,packet+1L,first,b).bufferFinished};first+=frames};check(r.sink.received==expected&&r.port.snapshot().completeFrames==first)}}
    }
    fun run(name: String) = checkNotNull(cases[name]) { "Unknown scenario" }.invoke()
    @JvmStatic fun main(args: Array<String>) {
        System.getProperty("lmg.output.expectedJar")?.let { expected ->
            val actual=java.io.File(SameSinkOutputPort::class.java.protectionDomain.codeSource.location.toURI()).canonicalPath
            check(actual==java.io.File(expected).canonicalPath) { "Wrong implementation origin" }
            println("OUTPUT_IMPLEMENTATION=$actual")
        }
        for (name in if (args.isEmpty()) names else args.toList()) { run(name);println("PASS $name") }
        println("OUTPUT_PORT_SCENARIOS_PASSED: ${if(args.isEmpty())names.size else args.size}")
    }
}
