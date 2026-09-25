package com.lmg.vk.engine.automix.render

import com.lmg.vk.engine.automix.nativecore.NativePcmOwnerIngress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real Stage4c3 C++/JNI ingress + actual new output reservation, with recording outputs.
 * The clock lease below is a TEST DOUBLE. No MediaClock, DSP, speaker or hardware test.
 */
object SameSinkPairScenarios {
    private class Sink : PcmOutputBackend {
        var pending = false
        var take = Int.MAX_VALUE
        var calls = 0
        val bytes = ArrayList<Byte>()
        val stamps = ArrayList<Long>()
        val volumes = ArrayList<Float>()
        override fun write(buffer: ByteBuffer, ptsUs: Long): Boolean {
            calls++; stamps.add(ptsUs)
            repeat(minOf(take,buffer.remaining())) { bytes.add(buffer.get()) }
            return !buffer.hasRemaining()
        }
        override fun volume(value: Float) { volumes.add(value) }
        override fun positionUs(): Long = Long.MIN_VALUE
        override fun pending(): Boolean = pending
    }
    private class Rig(val offset:Long=0) : AutoCloseable {
        val c = RenderBoundaryController { 1000L }
        val endpoints = arrayOf(c.newEndpoint(), c.newEndpoint())
        val keys = arrayOf(RenderSourceKey("A","same","memory:A",null,null),
            RenderSourceKey("B","same","memory:B",null,null))
        val tokens = arrayOf(Any(),Any())
        val format = RenderPcmFormat(8000,1,4)
        val sinks = arrayOf(Sink(),Sink())
        val ports = arrayOf(SameSinkOutputPort(sinks[0]),SameSinkOutputPort(sinks[1]))
        val native = NativePcmOwnerIngress(1,4,16)
        val ingress = NativeCueOwnerIngress(native)
        var leaseValid = true
        val lease = object : CuePlaybackLease {
            override fun isCurrent(generation:Long,revision:Long,format:RenderPcmFormat) =
                leaseValid && generation==1L && revision==1L && format==this@Rig.format
            override fun revoke() { leaseValid = false }
        }
        var legacyCalls = 0
        val original = arrayOf(input(),input())
        init {
            c.offer(RenderBoundaryPlan.create(1,1,keys[0],keys[1],4.0/8000,4.0/8000,9))
            for (i in 0..1) {
                endpoints[i].sameSinkOutputPort = ports[i]
                ports[i].bindOutput(tokens[i],format)
                ports[i].transitionVolume(1f,.5f)
            }
        }
        fun input():ByteBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).also {
            repeat(8) { i->it.putFloat(i/16f) };it.flip()
        }
        fun context(i:Int) { val k=keys[i];endpoints[i].outputContext(tokens[i],RenderOutputIdentity(k,k.windowUid,i+10L,0,offset),format,offset) }
        fun callback(i:Int,buffer:ByteBuffer=original[i]):Boolean {
            context(i)
            return endpoints[i].forwardWithCueGate(buffer,offset,1) {
                ports[i].checkLegacyWrite();legacyCalls++;buffer.position(buffer.limit());true
            }
        }
        fun hold() {check(c.requestCueProbe(1,1)==CueProbeRequest.REQUESTED);check(!callback(0)&&!callback(1))}
        fun output():SameSinkPairOutput = checkNotNull(SameSinkPairOutput.reserve(c,checkNotNull(c.reserve())))
        fun prepare():CueOwnerTicket=checkNotNull(c.prepareCueOwner(ingress,lease))
        fun commit():CueOwnerTicket=prepare().also { check(c.commitCueOwner(it)) }
        fun read(receipt:CueOwnerTicket):Pair<ByteBuffer,LongArray> {
            val buffer=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());val info=LongArray(3)
            check(c.readCueOwner(receipt,0,buffer,16,info)==4);buffer.flip();return buffer to info
        }
        override fun close() {native.close()}
    }
    private val cases=linkedMapOf<String,()->Unit>()
    val names:List<String> get()=cases.keys.toList()
    private fun test(name:String,action:()->Unit){check(cases.put(name,action)==null)}
    private fun fails(action:()->Unit) {var failed=false;try{action()}catch(_:Exception){failed=true};check(failed)}
    init {
        test("pairRequiresRealHeldBuffers") {Rig().use{r->r.context(0);r.context(1);check(SameSinkPairOutput.reserve(r.c,checkNotNull(r.c.reserve()))==null)}}
        test("heldPairWithoutRegisteredPortsRejected") {Rig().use{r->r.hold();r.endpoints[1].sameSinkOutputPort=null;check(SameSinkPairOutput.reserve(r.c,checkNotNull(r.c.reserve()))==null)}}
        test("cannotReserveIdenticalDelegatePort") {Rig().use{r->r.hold();r.endpoints[1].sameSinkOutputPort=r.ports[0];check(SameSinkPairOutput.reserve(r.c,checkNotNull(r.c.reserve()))==null)}}
        test("companionPendingRejectedBeforeInputClaim") {Rig().use{r->r.hold();r.sinks[1].pending=true;check(SameSinkPairOutput.reserve(r.c,checkNotNull(r.c.reserve()))==null);check(r.c.cueOwnerSnapshot()==null)}}
        test("cannotWriteWithoutCommittedIngress") {Rig().use{r->r.hold();val out=r.output();fails{out.write(1,0,r.input())};check(r.sinks.all{it.calls==0})}}
        test("stagingDoesNotAuthorizeOutput") {Rig().use{r->r.hold();val out=r.output();val t=r.prepare();check(!out.bindCommittedInputs(t));fails{out.write(1,0,r.input())};check(r.sinks.all{it.calls==0})}}
        test("foreignIngressReceiptRejected") {Rig().use{r->r.hold();val out=r.output();r.commit();check(!out.bindCommittedInputs(CueOwnerTicket()));check(r.sinks.all{it.calls==0})}}
        test("nativePrefixWrittenToOriginalPrimaryOnly") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(b,info)=r.read(t);val expected=(0 until b.limit()).map{b.get(it)};check(info.contentEquals(longArrayOf(0,4,1)));check(out.write(1,0,b).bufferFinished);check(r.sinks[0].bytes==expected&&r.sinks[1].calls==0&&r.legacyCalls==0)}}
        test("nativePrefixAndCueDataStayInOrder") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(a,ai)=r.read(t);val(b,bi)=r.read(t);check(ai[2]==1L&&bi[2]==0L);out.write(1,0,a);out.write(2,4,b);val expected=r.input();check(r.sinks[0].bytes==(0 until expected.limit()).map{expected.get(it)}&&r.sinks[1].bytes.isEmpty())}}
        test("codecAckIsSeparateFromOutputConsumption") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));check(r.original.all{it.position()==0});check(r.callback(0));check(r.original[1].position()==0);check(r.callback(1));check(r.sinks.all{it.calls==0}&&r.legacyCalls==0)}}
        test("generationRevocationBlocksOutputAfterClaim") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(b,_)=r.read(t);r.c.invalidate(2);fails{out.write(1,0,b)};check(r.sinks.all{it.calls==0})}}
        test("revokedClockLeaseDoesNotReachBackend") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(b,_)=r.read(t);r.leaseValid=false;fails{out.write(1,0,b)};check(r.sinks.all{it.calls==0})}}
        test("nativeOutputDoesNotBecomeLegacyAcceptance") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(b,_)=r.read(t);out.write(1,0,b);check(r.endpoints.all{it.acceptedEndUpperUs==null});check(out.snapshot().acceptedBytes==16L)}}
        test("preclaimRollbackRestoresOriginals") {Rig().use{r->r.hold();val out=r.output();r.prepare();r.c.releaseCueProbe();check(out.rollbackBeforeInputClaim());check(r.callback(0)&&r.callback(1));check(r.legacyCalls==2&&r.sinks.all{it.calls==0})}}
        test("partialSinkWritesRetainNativeReadBuffer") {Rig().use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(b,_)=r.read(t);val expected=(0 until b.limit()).map{b.get(it)};r.sinks[0].take=3;while(!out.write(1,0,b).bufferFinished){};check(r.sinks[0].bytes==expected&&out.snapshot().completeFrames==4L&&r.sinks[1].calls==0)}}
        test("originalHeldOutputTimestampPreserved") {Rig(987654321L).use{r->r.hold();val out=r.output();val t=r.commit();check(out.bindCommittedInputs(t));val(a,_)=r.read(t);val(b,_)=r.read(t);out.write(1,0,a);out.write(2,4,b);check(r.sinks[0].stamps==listOf(987654321L,987654821L))}}
        test("claimedInputsCannotRollbackEvenBeforeOutputWrite") {Rig().use{r->r.hold();val out=r.output();r.commit();check(!out.rollbackBeforeInputClaim());fails{r.ports[0].checkLegacyWrite()};check(r.sinks.all{it.calls==0})}}
        test("queuedLegacyFadePreventsOutputActivation") {Rig().use{r->r.hold();r.ports[0].transitionVolume(.5f,.5f);r.sinks[0].pending=true;val out=r.output();val t=r.commit();check(!out.bindCommittedInputs(t));fails{out.write(1,0,r.input())};check(r.sinks[0].volumes.last()==.25f&&r.sinks.all{it.calls==0})}}
    }
    fun run(name:String)=checkNotNull(cases[name]){"Unknown test"}.invoke()
    @JvmStatic fun main(args:Array<String>){val names=if(args.isEmpty())names else args.toList();for(n in names){run(n);println("PASS $n")};println("SAME_SINK_PAIR_INGRESS_TESTS_PASSED: ${names.size}; recording backend and test clock lease only")}
}
