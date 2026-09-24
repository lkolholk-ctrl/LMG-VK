package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.TransitionStyleCatalog
import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/** Server gate: original catalog bytes and real raw-JSON/JNI composition, no substitute decoder. */
class NativeScheduleIntegrationTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNative() {
            assumeTrue("Pass -PautomixHostLibraryPath", System.getProperty("automix.requireJni") == "true")
            val unknown=MusicKitSourceContext.create(MusicKitOutgoingCriteria.LateInSong,MusicKitIncomingCriteria.InSong,3)
            val q=PlannerSourceContextWire.request(0,1,unknown,null,null)
            val w=NativeObservationBridge.compileMusicKitScheduleV1(byteArrayOf(),byteArrayOf(),byteArrayOf(),byteArrayOf(),byteArrayOf(),q)
            check(PlannerScheduleWire.decode(w,q,emptySet()).scheduleStatus==PlannerScheduleStatus.SOURCE_REJECTED)
        }
    }
    private fun response(id: String,duration: Int=120000,bars: Int=60,bpm: Double=120.0,key: String="C"): ByteArray {
        val times=(0..bars*4).joinToString(","){(it*(60.0/bpm)).toString()}
        val scores=(0..bars*4).joinToString(","){when{it%16==0->"800";it%8==0->"600";it%4==0->"400";else->"200"}}
        return """{"data":[{"type":"songs","id":"$id","attributes":{"durationInMillis":$duration,"supportsSmartTransitions":true},
          "relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"aa"}]},
          "flexml-analysis":{"data":[{"type":"flexml-analysis","id":"ff"}]}}}],"included":[
          {"type":"audio-analysis","id":"aa","attributes":{"bpm":{"main":7},"key":{"main":{"tonic":"$key","mode":"minor"}},"melodicness":{"main":0.8},"vocalActivity":[]}},
          {"type":"flexml-analysis","id":"ff","attributes":{"videoEvents":{"timeInSeconds":[$times],"score":[$scores]}}}]}""".encodeToByteArray()
    }
    private fun bytes()=File(requireNotNull(System.getProperty("automix.catalogPath"))).readBytes()
    private fun catalog()=(TransitionStyleCatalog.loadBundled{bytes().inputStream()} as TransitionStyleCatalog.LoadResult.Loaded).catalog
    private fun context()=PlannerSourceTransportScenarios.context()
    private fun q(duration: Long=120000,context: MusicKitSourceContext=context())=PlannerSourceContextWire.request(71,1,context,duration,duration)
    private fun raw(request: LongArray=q(),a: ByteArray=response("a"),b: ByteArray=response("b"),aId: String="a",source: ByteArray=bytes())=
        NativeObservationBridge.compileMusicKitScheduleV1(a,aId.encodeToByteArray(),b,"b".encodeToByteArray(),source,request)
    private fun decode(w: LongArray,request: LongArray)=PlannerScheduleWire.decode(w,request,catalog().styles.map{it.id.toLong()}.toSet())
    private fun direct(request: LongArray=q(),a: ByteArray=response("a"),b: ByteArray=response("b"))=decode(raw(request,a,b),request)
    private fun rejected(action: ()->Unit) {var failed=false;try{action()}catch(_:IllegalArgumentException){failed=true};check(failed)}
    private fun pair(bound: Boolean=true)=ObservationPair(
        ObservationTrack("a",0,ObservationSource("same","memory:a"),120000),
        ObservationTrack("b",1,ObservationSource("same","memory:b"),120000),0,false,
        selectionScope=if(bound)context() else null,selectionGeneration=71,selectionRevision=if(bound)1 else 0)
    @Test fun canonicalNineCompilesWithoutSuppliedWindowsOrRates() {
        val r=direct();val s=requireNotNull(r.schedule)
        check(r.scheduleStatus==PlannerScheduleStatus.COMPILED&&s.styleId==9&&s.score==15.104)
        check(s.outgoing.sourceStartSeconds==104.0&&s.outgoing.sourceEndSeconds==120.0)
        check(s.incoming.sourceStartSeconds==0.0&&s.incoming.sourceEndSeconds==16.0)
        check(s.transitionStartSeconds==0.0&&s.transitionEndSeconds==16.0&&s.referenceTransitionTimeSeconds==8.0)
        check(s.outgoing.automations.size==5&&s.incoming.automations.size==5&&!s.canExecute&&s.requiresPositionRevalidation)
    }
    @Test fun canonicalEightUsesItsOwnCatalogInstructions() {
        val r=direct(b=response("b",key="F#"));val s=requireNotNull(r.schedule)
        check(s.styleId==8&&s.score==10.104&&s.outgoing.automations.size==4&&s.incoming.automations.size==5)
    }
    @Test fun expandedTwelveHasCalculatedNonUnityRates() {
        val r=direct(q(96000),response("a",96000,48,120.0),response("b",96000,60,150.0));val s=requireNotNull(r.schedule)
        check(s.styleId==12&&s.score==10.064&&s.outgoing.startRate==1.0&&s.outgoing.endRate>1.0)
        check(s.incoming.startRate==0.8&&s.outgoing.endRate==1.25&&s.incoming.endRate==1.0)
        check(s.outgoing.sourceStartSeconds==64.0&&s.outgoing.sourceEndSeconds==96.0)
        check(s.incoming.sourceStartSeconds==6.4&&s.incoming.sourceEndSeconds==32.0)
        check(kotlin.math.abs(s.transitionEndSeconds-28.56237456821885)<1e-9)
        check(s.outgoing.automations.size==4&&s.incoming.automations.size==5)
    }
    @Test fun continuousRecordsKeepOrderAndDuplicateBypassTimes() {
        val s=requireNotNull(direct().schedule)
        val gate=s.outgoing.automations.first();check(gate.parameterId=="bypa")
        check(gate.points.map{it.songTimeSeconds}==listOf(104.0,104.0,120.0,120.0))
        check(gate.points.map{it.value}==listOf(1.0,0.0,0.0,1.0))
        val rate=s.outgoing.automations.single{it.parameterId=="ts_rate"}
        check(rate.points.all{it.value==1.0&&it.curve==128})
        var immutable=0
        try{(s.outgoing.automations as MutableList).clear()}catch(_:UnsupportedOperationException){immutable++}
        try{(gate.points as MutableList).clear()}catch(_:UnsupportedOperationException){immutable++};check(immutable==2)
    }
    @Test fun priorSourceSelectionEnvelopeIsUnchanged() {
        val q=q();val a=response("a");val b=response("b");val c=bytes()
        val old=NativeObservationBridge.selectMusicKitSourcePairV1(a,"a".encodeToByteArray(),b,"b".encodeToByteArray(),c,q)
        val now=raw(q,a,b,source=c);check(old.contentEquals(now.copyOfRange(10,10+now[6].toInt())))
    }
    @Test fun malformedInputsAndWrongIdentityCannotYieldPlan() {
        rejected{raw(aId="wrong")};rejected{raw(a="broken".encodeToByteArray())};rejected{raw(source="broken".encodeToByteArray())}
        for(request in listOf(longArrayOf(),q().copyOf(25),q().also{it[8]=1},q().also{it[4]=0}))rejected{raw(request)}
    }
    @Test fun unsupportedSourceFactsAndDurationStayExplicit() {
        for(c in listOf(context().copy(outgoingSpatial=MusicKitSourceKnowledge.UNKNOWN),
            context().copy(outgoingSpatial=MusicKitSourceKnowledge.PRESENT),context().copy(incomingPreviousState=MusicKitSourceKnowledge.PRESENT))) {
            val r=direct(q(context=c));check(r.scheduleStatus==PlannerScheduleStatus.SOURCE_REJECTED&&r.schedule==null&&r.candidate==null)
        }
        val r=direct(q(120001));check(r.sourceContextResolution==PlannerSourceContextResolution.DURATION_MAPPING_REQUIRED&&r.schedule==null)
    }
    @Test fun exhaustedBudgetDoesNotKeepProvisionalSchedule() {
        val r=direct(q(context=context().copy(workBudget=1)))
        check(r.scheduleStatus==PlannerScheduleStatus.SELECTION_UNAVAILABLE&&r.candidate==null&&r.schedule==null&&!r.canExecute)
    }
    @Test fun calculatorUsesPrivateBytesAndNewNativeEntry() {
        val calc=AppleObservationCalculator{bytes().inputStream()};val raw=response("a");val a=calc.decode(raw,"a");raw.fill(0)
        val r=calc.calculate(a,calc.decode(response("b"),"b"),pair()).selection
        check(r?.schedule?.styleId==9&&r.generation==71L&&r.bindingRevision==1L)
    }
    @Test fun fullPipelinePublishesScheduleInBoundGeneration()=runBlocking {
        val calc=AppleObservationCalculator{bytes().inputStream()};val pipeline=ObservationPipeline(this,calc::decode,calc::calculate)
        try{
            pipeline.update(pair(false).copy(selectionGeneration=0));val tickets=pipeline.state.value.requests
            pipeline.submitOwned(tickets[0],"a",response("a"));pipeline.submitOwned(tickets[1],"b",response("b"))
            withTimeout(10000){pipeline.state.first{it.phase==ObservationPhase.OBSERVED}}
            check(pipeline.bindResolvedScope(tickets[0],context())==ObservationScopeSubmission.ACCEPTED)
            val snapshot=withTimeout(10000){pipeline.state.first{it.phase==ObservationPhase.OBSERVED}}
            val s=requireNotNull(snapshot.result?.selection?.schedule)
            check(s.styleId==9&&s.generation==snapshot.generation&&s.bindingRevision==1L&&!s.canExecute)
        }finally{pipeline.close()}
    }
}
