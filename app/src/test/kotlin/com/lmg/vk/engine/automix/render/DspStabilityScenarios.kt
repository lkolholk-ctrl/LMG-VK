package com.lmg.vk.engine.automix.render

import com.lmg.vk.engine.*
import com.lmg.vk.engine.automix.nativecore.NativeLivePcmExecutor
import com.lmg.vk.engine.automix.nativecore.NativePcmOwnerIngress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sin

/** Production classes with real project JNI; recording sinks are NOT physical AudioTracks.
 * Allocation instrumentation is a separate host gate, never replaced with an Android stub.
 */
object DspStabilityScenarios {
    private fun direct(bytes: Int) = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun expectFailure(block: () -> Unit) { var failed=false;try{block()}catch(_:Exception){failed=true};check(failed) }
    private val cases=linkedMapOf<String,()->Unit>()
    val names: List<String> get()=cases.keys.toList()
    private fun test(name:String, block:()->Unit){check(cases.put(name,block)==null)}
    private class Backend : PcmOutputBackend {
        var calls=0;var bytes=0L;var gain=0f;var stalled=false;var base=Long.MIN_VALUE
        override fun write(buffer:ByteBuffer,ptsUs:Long):Boolean {
            calls++;if(base==Long.MIN_VALUE)base=ptsUs
            if(stalled)return false
            bytes+=buffer.remaining();buffer.position(buffer.limit());return true
        }
        override fun volume(value:Float){gain=value}
        override fun positionUs()=if(base==Long.MIN_VALUE)Long.MIN_VALUE else base+(bytes/4)*125
        override fun pending()=false
        override fun finish()=true
    }
    private class QueueExecutor : Executor {
        val jobs=java.util.ArrayDeque<Runnable>()
        override fun execute(command:Runnable){jobs.add(command)}
        fun drain(){while(jobs.isNotEmpty())jobs.removeFirst().run()}
    }
    private class Resource : AutoCloseable { var closed=0;override fun close(){closed++} }
    private class PumpRig(private val primed:Boolean=false) : AutoCloseable {
        var now=0L;var valid=true
        val c=RenderBoundaryController {now}
        val a=c.newEndpoint();val b=c.newEndpoint()
        val ak=RenderSourceKey("A","same","mem:A",null,null)
        val bk=RenderSourceKey("B","same","mem:B",null,null)
        val f=RenderPcmFormat(8000,1,4)
        val at=Any();val bt=Any()
        val ai=RenderOutputIdentity(ak,"a",10,0,10_000_000)
        val bi=RenderOutputIdentity(bk,"b",11,0,10_000_000)
        val sinks=arrayOf(Backend(),Backend())
        val ports=Array(2){SameSinkOutputPort(sinks[it])}
        val inputA=direct(512);val inputB=direct(512)
        val lease=object:CuePlaybackLease {
            override fun isCurrent(generation:Long,revision:Long,format:RenderPcmFormat)=
                valid&&generation==1L&&revision==1L&&format==f
            override fun revoke(){valid=false}
        }
        val pump:LivePcmPump
        var nextA=40124L;var nextB=128L;var endedA=false
        init {
            repeat(128){inputA.putFloat(.1f);inputB.putFloat(.2f)};inputA.flip();inputB.flip()
            c.offer(RenderBoundaryPlan.create(1,1,ak,bk,5.0,0.0,9,RenderExecutionData(LivePcmPumpScenarios.plan())))
            a.sameSinkOutputPort=ports[0];b.sameSinkOutputPort=ports[1]
            a.outputContext(at,ai,f,14_900_000);b.outputContext(bt,bi,f,10_000_000)
            ports[0].bindOutput(at,f);ports[1].bindOutput(bt,f)
            for(p in ports)p.transitionVolume(1f,.5f)
            if(primed){
                val before=direct(252*4);repeat(252){before.putFloat(.1f)};before.flip()
                a.capturePreroll(before,10_000_000+39744*125L,1)
                a.capturePreroll(inputA,10_000_000+39996*125L,1)
                check(a.sourceHistory.has(39744,256,1))
            }
            check(c.cueGate.requestLive(1,1)==CueProbeRequest.REQUESTED)
            check(!a.forwardWithCueGate(inputA,10_000_000+39996*125L,1){error("legacy")})
            check(!b.forwardWithCueGate(inputB,10_000_000,1){error("legacy")})
            pump=if(primed)checkNotNull(LivePcmPump.startPrepared(c,lease,
                LivePcmPump.Prepared(checkNotNull(c.epoch().plan?.execution),f,1,1,true)){now})
                else checkNotNull(LivePcmPump.start(c,lease){now})
            check(a.forwardWithCueGate(inputA,10_000_000+39996*125L,1){error("legacy")})
            check(b.forwardWithCueGate(inputB,10_000_000,1){error("legacy")})
        }
        fun step() {
            if(nextA<48000){
                val n=minOf(128,(48000-nextA).toInt());inputA.position(0);inputA.limit(n*4)
                a.outputContext(at,ai,f,14_900_000)
                if(a.forwardWithCueGate(inputA,10_000_000+nextA*125,1){error("legacy")})nextA+=n
                else error("Unexpected test backpressure on outgoing input")
            }else if(!endedA){pump.endInput(0);endedA=true}
            inputB.position(0);inputB.limit(512)
            b.outputContext(bt,bi,f,10_000_000)
            check(b.forwardWithCueGate(inputB,10_000_000+nextB*125,1){error("legacy")})
            nextB+=128;pump.tick()
        }
        override fun close(){pump.close()}
    }
    init {
        test("historyPreservesPcm16AndNativeFloat") {
            for(width in intArrayOf(2,4)) {
                val h=PcmSourceHistory();val f=RenderPcmFormat(48000,2,width)
                val id=RenderOutputIdentity(RenderSourceKey("A","A","A",null,null),"A",1,0,10_000_000)
                val input=direct(2048*2*width)
                repeat(4096){if(width==2)input.putShort((it-2000).toShort())else input.putFloat((it-2000)/32768f)};input.flip()
                // Intentionally ignore ByteBuffer order metadata; wire encoding remains LE.
                input.order(ByteOrder.BIG_ENDIAN);h.capture(input,10_000_000,id,f,Long.MAX_VALUE)
                val out=direct(4096*4);check(h.copy(0,2048,2,out))
                repeat(4096){check(out.getFloat(it*4)==(it-2000)/32768f)}
                check(input.position()==0&&out.position()==0)
            }
        }
        test("historyRetryDoesNotReplaySource") {
            val h=PcmSourceHistory();val f=RenderPcmFormat(8000,1,4)
            val id=RenderOutputIdentity(RenderSourceKey("A","A","A",null,null),"A",1,0,0)
            val input=direct(512);repeat(128){input.putFloat(it.toFloat())};input.flip()
            h.capture(input,0,id,f,Long.MAX_VALUE);input.position(128)
            h.capture(input,0,id,f,Long.MAX_VALUE)
            val out=direct(512);check(h.copy(0,128,1,out));repeat(128){check(out.getFloat(it*4)==it.toFloat())}
        }
        test("historyStopsExactlyAtCueAndResetsOnGap") {
            val h=PcmSourceHistory();val f=RenderPcmFormat(8000,1,4)
            val id=RenderOutputIdentity(RenderSourceKey("A","A","A",null,null),"A",1,0,0)
            val input=direct(512);repeat(128){input.putFloat(.1f)};input.flip()
            h.capture(input,0,id,f,100);check(h.has(0,100,1)&&!h.has(0,101,1))
            h.capture(input,200*125,id,f,Long.MAX_VALUE);check(!h.has(0,100,1)&&h.has(200,128,1))
            h.reset();check(!h.has(200,128,1))
        }
        test("historyRejectsNonfiniteWithoutChangingCodecCursor") {
            val h=PcmSourceHistory();val f=RenderPcmFormat(8000,1,4)
            val id=RenderOutputIdentity(RenderSourceKey("A","A","A",null,null),"A",1,0,0)
            val input=direct(512);repeat(128){input.putFloat(if(it==100)Float.NaN else .1f)};input.flip()
            h.capture(input,0,id,f,Long.MAX_VALUE)
            check(!h.has(0,128,1)&&input.position()==0)
        }
        test("nativeWarmupUsesRealHistoryWithoutOutputReplay") {
            val e=NativeLivePcmExecutor(LivePcmPumpScenarios.plan(),8000,1,128,1,1,primeOutgoing=true)
            try {
                check(e.outgoingPrerollFrames==256L&&e.outgoingPrerollStartFrame==39744L)
                val a=direct(512);val b=direct(512);val out=direct(512)
                repeat(128){a.putFloat(.1f);b.putFloat(0f)};a.flip();b.flip()
                var sent=0L;var inB=0L;var got=0;var first=Float.NaN
                repeat(20){sent+=e.push(0,a,128,39744+sent);inB+=e.push(1,b,128,inB)
                    val n=e.render(out,128,4);if(n>0&&got==0)first=out.getFloat(0);got+=n}
                check(got>0&&abs(first-.05f)<2e-6) { "Warm first sample $first" }
                check(a.position()==0&&b.position()==0&&out.position()==0)
            }finally{e.close()}
        }
        test("primedPumpUsesCapturedHistoryAndPreservesPrefix") {
            PumpRig(true).use { r->
                repeat(80){r.step()}
                check(r.pump.outgoingPrefixFrames==4L)
                check(r.pump.nativeSnapshot()[4]>=256L)
                check(r.sinks[0].bytes>1000&&r.sinks[1].bytes==0L)
            }
        }
        test("routingUsesPlaylistOccurrence") {
            val r=SinkAudioRouting();val a=r.newSink();val b=r.newSink()
            a.bind(Any(),"first","same");b.bind(Any(),"second","same")
            a.publish(.2f,.3f,.4f);b.publish(.8f,.7f,.6f)
            r.select("first","same");check(r.levels()==a.levels)
            r.select("second","same");check(r.levels()==b.levels)
            r.select("first","same");check(r.levels()==a.levels)
        }
        test("otherSinkResetCannotBlankSelectedMeter") {
            val r=SinkAudioRouting();val a=r.newSink();val b=r.newSink()
            a.bind(Any(),"A","A");b.bind(Any(),"B","B");r.select("A","A")
            a.publish(.3f,.6f,.9f);val before=r.levels();b.resetLevels();b.bind(Any(),"C","C")
            check(r.levels()==before)
        }
        test("mixedOutputHasSingleMeterOwner") {
            val r=SinkAudioRouting();val a=r.newSink();val b=r.newSink()
            a.bind(Any(),"A","A");b.bind(Any(),"B","B");r.select("B","B")
            a.publish(.1f,.2f,.3f);b.publish(.9f,.8f,.7f)
            a.mixedOutput=true;check(r.levels()==a.levels)
            a.bind(null,null,null);check(r.levels()==b.levels)
        }
        test("djCommandCapturesTargetNotCreationOrder") {
            val r=SinkAudioRouting();val a=r.newSink();val b=r.newSink();AudioReactor.attach(r)
            try {
                a.bind(Any(),"A","same");b.bind(Any(),"B","same");r.select("B","same")
                DjStreamFx.begin(4);val command=DjStreamFx.snapshot();check(!command.matches(a)&&command.matches(b))
                r.select("A","same");check(command.matches(b)&&!command.matches(a))
                b.mixedOutput=true;check(!DjStreamFx.isOutgoing(b))
                DjStreamFx.stop();check(DjStreamFx.mode==0)
            }finally{AudioReactor.detach(r);DjStreamFx.stop()}
        }
        test("ambiguousOccurrenceNeverUsesConstructionOrder") {
            val r=SinkAudioRouting();val a=r.newSink();val b=r.newSink();AudioReactor.attach(r)
            try { a.bind(Any(),"same","same");b.bind(Any(),"same","same");r.select("same","same")
                a.publish(.2f,.3f,.4f);b.publish(.8f,.7f,.6f);check(r.levels()==0L)
                DjStreamFx.begin(4);check(!DjStreamFx.isOutgoing(a)&&!DjStreamFx.isOutgoing(b))
            } finally {DjStreamFx.stop();AudioReactor.detach(r)}
        }
        test("meterStereoEnergyAndPartition") {
            val input=direct(4096*8)
            repeat(4096){input.putFloat(0f);input.putFloat((.2*sin(it*.13)).toFloat())};input.flip()
            val a=PcmBandMeter(null);val b=PcmBandMeter(null);a.configure(48000,2,4);b.configure(48000,2,4)
            a.process(input,0,4096);var pos=0
            while(pos<4096){val n=minOf(63,4096-pos);b.process(input,pos*8,n);pos+=n}
            check(a.packedLevels==b.packedLevels&&a.packedLevels!=0L)
            check(input.position()==0&&input.limit()==4096*8)
        }
        test("meterOppositeChannelsDoNotCancel") {
            val input=direct(4096*8);repeat(4096){val s=(.2*sin(it*.13)).toFloat();input.putFloat(s);input.putFloat(-s)};input.flip()
            val meter=PcmBandMeter(null);meter.configure(44100,2,4);meter.process(input,0,4096)
            check(meter.packedLevels!=0L)
        }
        test("normalizationPartitionIndependent") {
            for(width in intArrayOf(2,4)) {
                val input=direct(8192*2*width)
                repeat(8192*2){val v=(.12*sin(it*.2)).toFloat();if(width==2)input.putShort((v*32768).toInt().toShort())else input.putFloat(v)};input.flip()
                fun run(chunk:Int):ByteArray {
                    val kernel=PcmNormalizationKernel();kernel.configure(48000,2,width)
                    val out=direct(8192*2*width);val scratch=direct(chunk*2*width);var at=0
                    while(at<8192){val n=minOf(chunk,8192-at);scratch.clear();kernel.process(input,at*2*width,n,scratch,true,1)
                        scratch.limit(n*2*width);out.put(scratch);at+=n}
                    return ByteArray(out.position()).also{out.flip();out.get(it)}
                }
                check(run(1024).contentEquals(run(63)))
            }
        }
        test("normalizationMeasuresFramesNotChannelSamples") {
            for(ch in 1..2){val k=PcmNormalizationKernel();k.configure(8000,ch,4)
                val pcm=direct(1024*ch*4);repeat(1024*ch){pcm.putFloat(.1f)};pcm.flip();val out=direct(pcm.capacity())
                var count=0;while(count<160000){val n=minOf(1024,160000-count);k.process(pcm,0,n,out,true,1);count+=n
                    check(k.frozen==(count==160000));check(k.framesSeen==count.toLong())}
            }
        }
        test("disabledNormalizationPreservesFloatBits") {
            val k=PcmNormalizationKernel();k.configure(48000,1,4)
            val words=intArrayOf(0,Int.MIN_VALUE,0x40000000,0xc0000000.toInt(),1,0x7fc00123)
            val input=direct(words.size*4);for(w in words)input.putInt(w);input.flip();val output=direct(input.capacity())
            k.process(input,0,words.size,output,false,1);for(i in words.indices)check(output.getInt(i*4)==words[i])
        }
        test("normalizationDisableSlewsRatherThanSteps") {
            val k=PcmNormalizationKernel();k.configure(8000,1,4)
            val input=direct(4096*4);repeat(4096){input.putFloat(.1f)};input.flip();val output=direct(input.capacity())
            k.process(input,0,4096,output,true,1);val last=output.getFloat(4095*4)
            k.process(input,0,256,output,false,1);check(abs(output.getFloat(0)-last)<.003f)
            check(output.getFloat(255*4)==.1f)
        }
        test("nativeMixCannotReceiveResidualNormalization") {
            val k=PcmNormalizationKernel();k.configure(48000,1,4)
            val input=direct(4096);repeat(1024){input.putFloat(.03f)};input.flip();val out=direct(4096)
            repeat(20){k.process(input,0,1024,out,true,1)}
            check(out.getFloat(4092) != .03f)
            k.process(input,0,1024,out,true,1,nativeOwned=true)
            repeat(1024){check(out.getInt(it*4)==input.getInt(it*4))}
        }
        test("confirmedMasterPreservesManualFade") {
            val b=Backend();val p=SameSinkOutputPort(b);p.bindOutput(Any(),RenderPcmFormat(8000,1,4))
            p.transitionVolume(.25f,.8f);p.confirmedPlayerVolume(.4f);check(abs(b.gain-.1f)<1e-7f)
            val t=checkNotNull(p.reserve(1,1,RenderPcmFormat(8000,1,4),0,{true}));check(p.activate(t))
            p.confirmedPlayerVolume(.2f);check(b.gain==.2f)
            p.transitionVolume(.01f,.3f);check(b.gain==.3f)
        }
        test("blockedSinkIsAttemptedOnlyOncePerTick") {
            PumpRig().use{r->r.sinks[0].stalled=true;val before=r.sinks[0].calls;r.pump.tick()
                check(r.sinks[0].calls-before==1);check(r.sinks[0].bytes==0L)
                val retryCalls=r.sinks[0].calls;r.pump.tick();check(r.sinks[0].calls-retryCalls==1)
                r.sinks[0].stalled=false;r.pump.tick();check(r.sinks[0].bytes>0)}
        }
        test("blockedSinkTimesOutWithoutFakeClock") {
            PumpRig().use{r->r.sinks[0].stalled=true;r.pump.tick();r.now=3_000_000_001L
                expectFailure{r.pump.tick()};check(!r.valid&&r.sinks[0].bytes==0L)}
        }
        test("nativeFloatReadWithoutCursorMutation") {
            NativePcmOwnerIngress(2,4,16).use{e->val input=direct(8*8);repeat(16){input.putFloat(it/16f)};input.flip()
                val t=e.stage(1,1,NativePcmOwnerIngress.Input(input,0,0),NativePcmOwnerIngress.Input(input,0,0));check(e.commit(t))
                val out=direct(128);out.position(8);val info=LongArray(3)
                check(e.readWithoutMoving(0,out,8,info)==8);check(out.position()==8&&out.limit()==128)
                repeat(16){check(out.getFloat(8+it*4)==it/16f)}
            }
        }
        test("nativeForeignDestroyCannotEraseCachedIngress") {
            NativePcmOwnerIngress(1,4,16).use{e->e.stats(0,LongArray(8))
                val field=e.javaClass.getDeclaredField("handle").apply{isAccessible=true};val id=field.getLong(e)
                val method=e.javaClass.getDeclaredMethod("nativeDestroy",java.lang.Long.TYPE).apply{isAccessible=true}
                val error=AtomicReference<Throwable?>();val thread=Thread{try{method.invoke(e,id)}catch(x:Throwable){error.set(x)}}
                thread.start();thread.join();check(error.get()?.cause is IllegalStateException)
                e.stats(0,LongArray(8)) // entry and current owner cache must still be alive
            }
        }
        test("nativeForeignDestroyCannotEraseCachedExecutor") {
            NativeLivePcmExecutor(LivePcmPumpScenarios.plan(),8000,1,128,1,1).use{e->e.stats(LongArray(19))
                val field=e.javaClass.getDeclaredField("handle").apply{isAccessible=true};val id=field.getLong(e)
                val method=e.javaClass.getDeclaredMethod("nativeDestroy",java.lang.Long.TYPE).apply{isAccessible=true}
                val error=AtomicReference<Throwable?>();val thread=Thread{try{method.invoke(e,id)}catch(x:Throwable){error.set(x)}}
                thread.start();thread.join();check(error.get()?.cause is IllegalStateException);e.stats(LongArray(19))
            }
        }
        test("cancelBeforePreparationDoesNotConstruct") {
            val executor=QueueExecutor();var built=0
            val task=PreparedResourceTask(executor,{built++;Resource()},{true});task.cancel();executor.drain()
            check(built==0&&task.take()==null)
        }
        test("cancelPreparedDisposesExactlyOnce") {
            val executor=QueueExecutor();val r=Resource();val task=PreparedResourceTask(executor,{r},{true})
            executor.drain();check(task.ready);task.cancel();task.cancel();executor.drain();check(r.closed==1)
        }
        test("adoptedResourceSurvivesLateCancellation") {
            val executor=QueueExecutor();val r=Resource();val task=PreparedResourceTask(executor,{r},{true})
            executor.drain();check(task.take()===r);task.cancel();executor.drain();check(r.closed==0&&task.take()==null);r.close()
        }
        test("stalePreparationNeverPublished") {
            val executor=QueueExecutor();val r=Resource();val task=PreparedResourceTask(executor,{r},{false})
            executor.drain();check(r.closed==1&&task.take()==null&&task.failed)
        }
        test("preparationRaceDisposesOnWorker") {
            val executor=Executors.newSingleThreadExecutor();val entered=CountDownLatch(1);val proceed=CountDownLatch(1);val closed=CountDownLatch(1)
            val worker=AtomicReference<Thread>();val closer=AtomicReference<Thread>()
            try {
                val task=PreparedResourceTask(executor,{
                    worker.set(Thread.currentThread());entered.countDown();check(proceed.await(5,TimeUnit.SECONDS))
                    AutoCloseable{closer.set(Thread.currentThread());closed.countDown()}
                },{true})
                check(entered.await(5,TimeUnit.SECONDS));task.cancel();proceed.countDown();check(closed.await(5,TimeUnit.SECONDS))
                check(worker.get()===closer.get()&&task.take()==null)
            }finally{executor.shutdownNow()}
        }
        test("nativePreparedObjectsTransferToPlaybackOwner") {
            val executor=Executors.newSingleThreadExecutor();val done=CountDownLatch(1)
            val task=PreparedResourceTask(executor,{
                LivePcmPump.Prepared(RenderExecutionData(LivePcmPumpScenarios.plan()),RenderPcmFormat(8000,1,4),1,1)
            },{true})
            executor.execute{done.countDown()};check(done.await(10,TimeUnit.SECONDS))
            try { val resources=checkNotNull(task.take());resources.engine.stats(LongArray(19));resources.ingress.stats(0,LongArray(8));resources.close() }
            finally{task.cancel();executor.shutdownNow()}
        }
        test("rejectedPreparationDoesNotBuild") {
            var built=0;val executor=Executor{throw java.util.concurrent.RejectedExecutionException()}
            val task=PreparedResourceTask(executor,{built++;Resource()},{true});check(task.failed&&task.take()==null&&built==0)
        }
    }
    fun run(name:String){checkNotNull(cases[name]){"Unknown scenario"}.invoke()}
    private fun measured(label:String,iterations:Int=20000,call:()->Unit) {
        val mxFactoryClass = Class.forName("java.lang.management.ManagementFactory")
        val mxBean = mxFactoryClass.getMethod("getThreadMXBean").invoke(null)
        val iface = Class.forName("com.sun.management.ThreadMXBean")
        val lookup = java.lang.invoke.MethodHandles.publicLookup()
        val isSupported = iface.getMethod("isThreadAllocatedMemorySupported").invoke(mxBean) as Boolean
        check(isSupported)
        iface.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(mxBean, true)
        val handle = lookup.findVirtual(iface, "getThreadAllocatedBytes", java.lang.invoke.MethodType.methodType(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType))
        @Suppress("DEPRECATION")
        val id = Thread.currentThread().id
        repeat(100) { handle.invoke(mxBean, id) }
        repeat(iterations){call()}
        val before = handle.invoke(mxBean, id) as Long
        repeat(iterations){call()}
        val allocated = (handle.invoke(mxBean, id) as Long) - before
        println("ALLOCATIONS $label: $allocated bytes / $iterations calls")
        check(allocated==0L){"Steady-state allocation in $label: $allocated bytes"}
    }
    fun allocationChecks() {
        NativeLivePcmExecutor(LivePcmPumpScenarios.plan(),8000,1,128,1,1).use{e->
            val input=direct(512);val output=direct(512);val stats=LongArray(19)
            measured("live JNI encode/stats/clock") { e.encodePrefix(input,output,128,4);e.stats(stats);e.sourceTimeSeconds(0,10000.0) }
        }
        val backend=Backend();val port=SameSinkOutputPort(backend);val format=RenderPcmFormat(8000,1,4)
        port.bindOutput(Any(),format);port.transitionVolume(1f,1f)
        val token=checkNotNull(port.reserve(1,1,format,0,{true}));check(port.activate(token))
        val receipt=OutputWriteState();val packet=direct(512);var sequence=0L;var frame=0L
        measured("same-sink output receipt/clock") { packet.clear();port.writeInto(token,++sequence,frame,packet,receipt);frame+=128;port.sampleSinkClockValue(token) }
        val state=SinkAudioRouting().newSink();state.bind(Any(),"A","A")
        val meter=PcmBandMeter(state);meter.configure(48000,2,4);val pcm=direct(256*8)
        val norm=PcmNormalizationKernel();norm.configure(48000,2,4);val out=direct(pcm.capacity())
        measured("meter + normalization",4000) {meter.process(pcm,0,256);norm.process(pcm,0,256,out,true,1)}
        PumpRig().use{r->measured("committed gate + JNI DSP pump",1500){r.step()};check(r.sinks[1].bytes==0L)}
    }
    @JvmStatic fun main(args:Array<String>) {
        if(args.contentEquals(arrayOf("--allocations"))){allocationChecks();return}
        for(name in if(args.isEmpty())names else args.toList()){run(name);println("PASS $name")}
        println("DSP_STABILITY_SCENARIOS: ${if(args.isEmpty())names.size else args.size} passed")
    }
}
