package com.lmg.vk.engine.automix.render

import com.lmg.vk.engine.automix.nativecore.NativePcmOwnerIngress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** REAL JNI input admission + real gate. The output lease is an explicit test double:
 * there is no AudioTrack/DSP/device claim in these tests. No copied test DSP is linked.
 */
object PcmOwnerTransactionScenarios {
    private class Lease : CuePlaybackLease {
        var valid=true;var revocations=0;var onCheck:(()->Unit)?=null
        override fun isCurrent(generation:Long,revision:Long,format:RenderPcmFormat):Boolean { onCheck?.invoke();return valid&&generation==1L&&revision==1L&&format.supported }
        override fun revoke(){valid=false;revocations++}
    }
    private class Rig(val width:Int=4,val channels:Int=1,val capacity:Int=16) : AutoCloseable {
        var now=1000L
        val c=RenderBoundaryController{now}
        val a=c.newEndpoint();val b=c.newEndpoint()
        val ak=RenderSourceKey("A","same","memory:A",null,null)
        val bk=RenderSourceKey("B","same","memory:B",null,null)
        val tokens=arrayOf(Any(),Any());val f=RenderPcmFormat(8000,channels,width)
        val native=NativePcmOwnerIngress(channels,width,capacity)
        val backend=NativeCueOwnerIngress(native)
        val lease=Lease()
        var writes=0
        val legacy=ArrayList<Byte>()
        init {c.offer(RenderBoundaryPlan.create(1,1,ak,bk,4.0/8000,4.0/8000,9))}
        fun ep(side:Int)=if(side==0)a else b
        fun context(side:Int){val k=if(side==0)ak else bk
            ep(side).outputContext(tokens[side],RenderOutputIdentity(k,k.windowUid,side+10L,0,0),f,0)}
        fun input(frames:Int=8,start:Int=0,padding:Int=0):ByteBuffer {
            val data=ByteBuffer.allocateDirect(frames*f.bytesPerFrame+padding*2).order(ByteOrder.LITTLE_ENDIAN)
            data.position(padding)
            for(i in 0 until frames*channels){
                if(width==4)data.putFloat((start*channels+i).toFloat()/16)
                else data.putShort(((start*channels+i)*128-16384).toShort())
            }
            data.limit(data.position());data.position(padding);return data
        }
        fun write(side:Int,input:ByteBuffer,pts:Long=0,consume:Int=input.remaining()):Boolean {
            context(side)
            return ep(side).forwardWithCueGate(input,pts,1){
                writes++;repeat(minOf(consume,input.remaining())){legacy.add(input.get())};!input.hasRemaining()
            }
        }
        fun held(x:ByteBuffer=input(),y:ByteBuffer=input()):Pair<ByteBuffer,ByteBuffer> {
            check(c.requestCueProbe(1,1)==CueProbeRequest.REQUESTED)
            check(!write(0,x)&&!write(1,y));check(writes==0)
            return x to y
        }
        fun prepare(admission:CueOwnerAdmission=backend)=checkNotNull(c.prepareCueOwner(admission,lease))
        fun commit(x:ByteBuffer,y:ByteBuffer,admission:CueOwnerAdmission=backend):CueOwnerTicket {
            held(x,y);val t=prepare(admission);check(c.commitCueOwner(t));return t
        }
        fun stats(side:Int=0)=LongArray(8).also{native.stats(side,it)}
        fun read(side:Int=0,frames:Int=capacity):Pair<LongArray,List<Float>> {
            val out=ByteBuffer.allocateDirect(frames*channels*4).order(ByteOrder.nativeOrder());val info=LongArray(3)
            native.read(side,out,frames,info);out.flip();val values=ArrayList<Float>();while(out.hasRemaining())values.add(out.float)
            return info to values
        }
        override fun close(){native.close()}
    }
    private fun failure(action:()->Unit){var failed=false;try{action()}catch(_:Exception){failed=true};check(failed)}
    private val cases=linkedMapOf<String,()->Unit>()
    val names:List<String> get()=cases.keys.toList()
    private fun test(name:String,action:()->Unit){check(cases.put(name,action)==null)}
    init {
        test("stageCopiesNeitherOriginalConsumed") {Rig().use{r->val(x,y)=r.held();r.prepare();check(x.position()==0&&y.position()==0&&r.writes==0);check(r.stats()[0]==1L&&r.stats()[7]==0L)}}
        test("commitBothVisibleNoEarlyCodecAck") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);check(x.position()==0&&y.position()==0);check(r.stats(0)[7]==8L&&r.stats(1)[7]==8L)}}
        test("eachOriginalAcknowledgedOnItsCallback") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);check(r.write(1,y));check(y.position()==y.limit()&&x.position()==0);check(r.write(0,x));check(x.position()==x.limit()&&r.writes==0)}}
        test("nativeConsumptionNotSinkLedger") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);check(r.a.acceptedEndUpperUs==null&&r.b.acceptedEndUpperUs==null)}}
        test("prefixSeparatelyPreserved") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);val a=r.read();val b=r.read();check(a.first.contentEquals(longArrayOf(0,4,1)));check(b.first.contentEquals(longArrayOf(4,4,0)));check(a.second+b.second==(0..7).map{it/16f})}}
        test("pcm16ConversionAndStereo") {Rig(2,2).use{r->val x=r.input();val y=r.input();r.commit(x,y);val values=r.read().second+r.read().second;check(values==(0..15).map{(it*128-16384)/32768f})}}
        test("unreadNativeCopiesIndependentOfOriginals") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);for(i in 0 until x.capacity())x.put(i,0);check(r.read().second==listOf(0f,.0625f,.125f,.1875f))}}
        test("rollbackBeforeCommitReturnsOriginalExactly") {Rig().use{r->val(x,y)=r.held();val t=r.prepare();r.c.releaseCueProbe();check(!r.c.commitCueOwner(t));check(r.write(0,x)&&r.write(1,y));check(r.writes==2&&r.stats()[0]==0L)}}
        test("deadlineBeforeCommitRollsBack") {Rig().use{r->val(x,y)=r.held();val t=r.prepare();r.now+=100_000_000;check(!r.c.commitCueOwner(t));check(r.write(0,x)&&r.write(1,y));check(r.stats()[0]==0L)}}
        test("revokedLeaseBeforeCommitRollsBack") {Rig().use{r->val(x,y)=r.held();val t=r.prepare();r.lease.valid=false;check(!r.c.commitCueOwner(t));check(r.write(0,x)&&r.write(1,y));check(r.stats()[0]==0L)}}
        test("generationChangeBeforeCommitRollsBack") {Rig().use{r->val(x,y)=r.held();val t=r.prepare();r.c.invalidate(2);check(!r.c.commitCueOwner(t));check(r.write(0,x)&&r.write(1,y));check(r.stats()[0]==0L)}}
        test("generationChangeAfterCommitNeverReplays") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.c.invalidate(2);failure{r.write(0,x)};failure{r.write(1,y)};check(r.writes==0&&r.stats()[0]==3L&&r.stats()[6]==8L)}}
        test("releaseAfterCommitRequiresFlush") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.c.releaseCueProbe();failure{r.write(0,x)};check(r.writes==0&&r.stats()[6]==8L)}}
        test("deadlineDoesNotCancelOwnedInput") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.now+=600_000_000;check(r.write(0,x)&&r.write(1,y));check(r.writes==0)}}
        test("doubleCommitAndForeignReceiptRejected") {Rig().use{r->val x=r.input();val y=r.input();val t=r.commit(x,y);check(!r.c.commitCueOwner(t));check(!r.c.commitCueOwner(CueOwnerTicket()));check(r.stats()[4]==8L)}}
        test("capacityFailureBeforeClaimReturnsOriginals") {Rig(capacity=4).use{r->val(x,y)=r.held();failure{r.prepare()};check(r.write(0,x)&&r.write(1,y));check(r.writes==2&&r.stats()[7]==0L)}}
        test("nonfiniteSecondSideAtomicStageFailure") {Rig().use{r->val x=r.input();val y=r.input();y.putFloat(4,Float.NaN);r.held(x,y);failure{r.prepare()};check(x.position()==0&&y.position()==0);check(r.stats()[0]==0L);check(r.write(0,x)&&r.write(1,y))}}
        test("backpressureAcceptsOnlyFramesWithCapacity") {Rig(capacity=8).use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);val z=r.input(8,8);check(!r.write(0,z,1000)&&z.position()==0);r.read(0,3);check(!r.write(0,z,1000)&&z.position()==12);r.read();r.read();check(r.write(0,z,1000));check(r.stats()[4]==16L&&r.writes==0)}}
        test("partialOwnerRetryMustUseSameOriginal") {Rig(capacity=8).use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);val z=r.input(8,8);check(!r.write(0,z,1000));failure{r.write(0,r.input(8,8),1000)};check(r.writes==0)}}
        test("postcommitNativeFailureNeverFallsBack") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);val z=r.input(8,8);z.putFloat(0,Float.NaN);failure{r.write(0,z,1000)};check(r.writes==0&&r.stats()[0]==3L)}}
        test("wrongThreadCannotForwardOwnedBuffer") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);val e=AtomicReference<Throwable?>();val t=Thread{try{r.write(0,x)}catch(f:Throwable){e.set(f)}};t.start();t.join();check(e.get()!=null&&r.writes==0);failure{r.write(0,x)}}}
        test("flushPermitsNewLegacyStreamOnlyAfterDiscard") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.a.cuePort.cancel(CueProbeReason.OUTPUT_RESET,true);r.a.reset();r.b.cuePort.cancel(CueProbeReason.OUTPUT_RESET,true);r.b.reset();r.c.invalidate(2);val fresh=r.input();check(r.write(0,fresh));check(r.writes==1&&r.stats()[0]==3L)}}
        test("pauseDoesNotAuthorizeLegacyReplay") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.a.cuePort.cancel(CueProbeReason.PAUSED);failure{r.write(0,x)};check(r.writes==0)}}
        test("outputTokenChangeAfterCommitQuarantines") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.tokens[0]=Any();failure{r.write(0,x)};check(r.writes==0)}}
        test("nativeCommitFailureAfterClaimQuarantines") {Rig().use{r->val(x,y)=r.held();val broken=object:CueOwnerAdmission by r.backend{override fun commit(ticket:Any)=false};val t=r.prepare(broken);check(!r.c.commitCueOwner(t));failure{r.write(0,x)};failure{r.write(1,y)};check(r.writes==0)}}
        test("cancelInsideNativeCommitDiscardsBoth") {Rig().use{r->val(x,y)=r.held();val backend=object:CueOwnerAdmission by r.backend{override fun commit(ticket:Any):Boolean{val ok=r.backend.commit(ticket);r.c.invalidate(2);return ok}};val t=r.prepare(backend);check(!r.c.commitCueOwner(t));failure{r.write(0,x)};failure{r.write(1,y)};check(r.stats()[0]==3L&&r.writes==0)}}
        test("cancelInsideStageRollsBackWithoutClaim") {Rig().use{r->val(x,y)=r.held();val backend=object:CueOwnerAdmission by r.backend{override fun stage(generation:Long,revision:Long,format:RenderPcmFormat,outgoing:ByteBuffer,outInfo:CueBufferInfo,incoming:ByteBuffer,inInfo:CueBufferInfo):Any{val t=r.backend.stage(generation,revision,format,outgoing,outInfo,incoming,inInfo);r.c.invalidate(2);return t}};check(r.c.prepareCueOwner(backend,r.lease)==null);check(r.write(0,x)&&r.write(1,y));check(r.stats()[0]==0L)}}
        test("nonzeroOffsetsAndReadOnlyInput") {Rig().use{r->val x=r.input(padding=12);val y=r.input(padding=8).asReadOnlyBuffer();r.commit(x,y);check(r.write(0,x)&&r.write(1,y));check(r.read().second==listOf(0f,.0625f,.125f,.1875f))}}
        test("invalidNativeReadDoesNotConsume") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);val out=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asReadOnlyBuffer().order(ByteOrder.nativeOrder());failure{r.native.read(0,out,4,LongArray(3))};check(r.stats()[7]==8L)}}
        test("nativeTicketCannotCrossOwners") {Rig().use{r->val x=r.input();val y=r.input();val t=r.native.stage(1,1,NativePcmOwnerIngress.Input(x,0,4),NativePcmOwnerIngress.Input(y,0,4));NativePcmOwnerIngress(1,4,16).use{other->check(!other.commit(t))};check(r.native.commit(t))}}
        test("ownerResultNeverClaimsAudioOrDSP") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);val state=checkNotNull(r.c.cueOwnerSnapshot());check(state.phase==CueOwnerPhase.INPUT_OWNED&&!state.canExecute&&!state.liveDspInstalled);check(!state.toString().contains("memory:"))}}
        test("largerSubsequentBufferUsesBoundedPartialAcceptance") {Rig(capacity=8).use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);r.read();r.read();val z=r.input(24,8);check(!r.write(0,z,1000)&&z.position()==32);r.read();check(!r.write(0,z,1000)&&z.position()==64);r.read();check(r.write(0,z,1000)&&z.position()==96);check(r.stats()[4]==32L&&r.writes==0)}}
        test("foreignReceiptCannotCommitPreparedInput") {Rig().use{r->r.held();val t=r.prepare();check(!r.c.commitCueOwner(CueOwnerTicket()));check(r.stats()[0]==1L);check(r.c.commitCueOwner(t))}}
        test("publicationWinsBetweenValidationAndClaim") {Rig().use{r->val(x,y)=r.held();val ticket=r.prepare()
            val entered=java.util.concurrent.CountDownLatch(1);val done=java.util.concurrent.CountDownLatch(1)
            r.lease.onCheck={entered.countDown();check(done.await(5,java.util.concurrent.TimeUnit.SECONDS))}
            val t=Thread{check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));r.c.invalidate(2);done.countDown()}
            t.start();check(!r.c.commitCueOwner(ticket));t.join();r.lease.onCheck=null
            check(r.stats()[0]==0L);check(r.write(0,x)&&r.write(1,y))
        }}
        test("ownerPollPreservesCueSplitAndCursor") {Rig().use{r->val x=r.input();val y=r.input();val t=r.commit(x,y)
            val d=ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder());d.position(8);val info=LongArray(3)
            check(r.c.readCueOwner(t,0,d,16,info)==4&&d.position()==24&&info.contentEquals(longArrayOf(0,4,1)))
            check(r.c.readCueOwner(t,0,d,16,info)==4&&d.position()==40&&info.contentEquals(longArrayOf(4,4,0)))
        }}
        test("cancelDuringPollDoesNotPublishDestinationCursor") {Rig().use{r->val x=r.input();val y=r.input()
            val adapter=object:CueOwnerAdmission by r.backend {override fun poll(side:Int,destination:ByteBuffer,maxFrames:Int,info:LongArray):Int {
                val n=r.backend.poll(side,destination,maxFrames,info);r.c.invalidate(2);return n }}
            val t=r.commit(x,y,adapter);val d=ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder());d.position(8)
            failure{r.c.readCueOwner(t,0,d,16,LongArray(3))};check(d.position()==8&&r.stats()[0]==3L&&r.writes==0)
        }}
        test("randomizedOwnerAcksConservePcmWithoutLegacy") {
            repeat(20){seed->Rig(capacity=17).use{r->val rng=java.util.Random(seed.toLong());val x=r.input();val y=r.input()
                r.commit(x,y);r.write(0,x);r.write(1,y)
                val sent=intArrayOf(8,8);val all=arrayOf(ArrayList<Float>(),ArrayList<Float>())
                val pending=arrayOfNulls<ByteBuffer>(2);val pts=LongArray(2)
                var loops=0
                while(all[0].size<512||all[1].size<512){check(++loops<10000)
                    for(side in 0..1){
                        if(pending[side]==null&&sent[side]<512){val n=minOf(1+rng.nextInt(24),512-sent[side]);pending[side]=r.input(n,sent[side]);pts[side]=sent[side]*125L;sent[side]+=n}
                        pending[side]?.let { if(r.write(side,it,pts[side]))pending[side]=null }
                        all[side].addAll(r.read(side,1+rng.nextInt(17)).second)
                    }
                }
                check(all[0]==(0..511).map{it/16f}&&all[1]==all[0]&&r.writes==0)
                for(side in 0..1){val st=r.stats(side);check(st[4]==512L&&st[5]==512L&&st[6]==0L&&st[7]==0L)}
            }}
        }
        test("replacedAttemptDiscardsOldInvisibleStageOnCallback") {Rig().use{r->
            val(x,_)=r.held();r.prepare()
            r.c.offer(RenderBoundaryPlan.create(2,1,r.ak,r.bk,4.0/8000,4.0/8000,9))
            check(r.c.requestCueProbe(2,1)==CueProbeRequest.REQUESTED)
            check(!r.write(0,x));check(r.stats()[0]==0L&&r.lease.revocations==1&&r.writes==0)
        }}
        test("replacedAttemptDiscardsOldInvisibleStageOnFlush") {Rig().use{r->
            r.held();r.prepare()
            r.c.offer(RenderBoundaryPlan.create(2,1,r.ak,r.bk,4.0/8000,4.0/8000,9))
            check(r.c.requestCueProbe(2,1)==CueProbeRequest.REQUESTED)
            r.a.cuePort.cancel(CueProbeReason.OUTPUT_RESET,true)
            check(r.stats()[0]==0L&&r.lease.revocations==1)
        }}
        test("wrongSubsequentPTSQuarantines") {Rig().use{r->val x=r.input();val y=r.input();r.commit(x,y);r.write(0,x);r.write(1,y);failure{r.write(0,r.input(8,8),1125)};check(r.writes==0)}}
    }
    fun run(name:String){checkNotNull(cases[name]){"Unknown case"}.invoke()}
    @JvmStatic fun main(args:Array<String>){
        System.getProperty("lmg.owner.expectedJar")?.let { expected ->
            val actual=java.io.File(PcmCueGate::class.java.protectionDomain.codeSource.location.toURI()).canonicalPath
            check(actual==java.io.File(expected).canonicalPath)
            println("OWNER_IMPLEMENTATION=$actual")
        }
        val selected=if(args.isEmpty())names else args.toList();for(n in selected){run(n);println("PASS $n")};println("REAL JNI owner transactions: ${selected.size}/${selected.size} PASSED")}
}
