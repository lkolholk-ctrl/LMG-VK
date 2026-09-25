package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.render.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Production state machine + synchronous metadata publication. No fake native execution. */
object RenderBoundaryPublicationScenarios {
    private fun pair()=ObservationPair(
        ObservationTrack("a",0,ObservationSource("same","a"),120000),
        ObservationTrack("b",1,ObservationSource("same","b"),120000),0,false)
    private fun result(generation:Long,revision:Long, compiled:Boolean=true):MetadataProbeReport {
        val a=ObservedScheduleSide(0,32,32,104.0,120.0,0.0,16.0,104.0,16.0,1.0,1.0,emptyList())
        val b=ObservedScheduleSide(0,32,32,0.0,16.0,0.0,16.0,0.0,16.0,1.0,1.0,emptyList())
        val schedule=ObservedTransitionSchedule(generation,revision,9,1,15.104,1.0,16.0,0.0,16.0,8.0,a,b)
        val selection=PlannerSelectionReport(generation,revision,PlannerSelectionStatus.OBSERVED,false,
            0,true,1,1,0,null,listOf(8,9,12),scheduleStatus=if(compiled)PlannerScheduleStatus.COMPILED
            else PlannerScheduleStatus.INVALID_SCHEDULE,schedule=if(compiled)schedule else null)
        return MetadataProbeReport("fixture-not-native",emptySet(),null,null,null,null,null,null,selection=selection)
    }
    fun compiledPublication(){val c=RenderBoundaryController();var requests=0;publishRenderBoundary(c,
        ObservationState(1,ObservationPhase.OBSERVED,result=result(1,2)),pair(),2) { generation, revision ->
            check(generation==1L&&revision==2L)
            check(c.snapshot().status==RenderBoundaryStatus.WAITING_FOR_OUTPUT_STREAMS)
            requests++
        }
        check(requests==1)
        check(c.snapshot().generation==1L&&c.snapshot().revision==2L&&c.snapshot().styleId==9&&!c.snapshot().canExecute)}
    fun mismatchedEpoch(){val c=RenderBoundaryController();publishRenderBoundary(c,
        ObservationState(2,ObservationPhase.OBSERVED,result=result(1,2)),pair(),2) { _, _ -> error("Stale plan activated") }
        check(c.snapshot().status==RenderBoundaryStatus.NO_PLAN)}
    fun mismatchedRevision(){val c=RenderBoundaryController();publishRenderBoundary(c,
        ObservationState(1,ObservationPhase.OBSERVED,result=result(1,1)),pair(),2)
        check(c.snapshot().status==RenderBoundaryStatus.NO_PLAN)}
    fun missingPair(){val c=RenderBoundaryController();publishRenderBoundary(c,
        ObservationState(1,ObservationPhase.OBSERVED,result=result(1,2)),null,2)
        check(c.snapshot().status==RenderBoundaryStatus.NO_PLAN)}
    fun rejectedSchedule(){val c=RenderBoundaryController();publishRenderBoundary(c,
        ObservationState(1,ObservationPhase.OBSERVED,result=result(1,2,false)),pair(),2) { _, _ -> error("Invalid plan activated") }
        check(c.snapshot().status==RenderBoundaryStatus.NO_PLAN)}
    fun closePublication(){val c=RenderBoundaryController();publishRenderBoundary(c,
        ObservationState(3,ObservationPhase.CLOSED),null,0);check(c.snapshot().status==RenderBoundaryStatus.CLOSED)}
    fun barriersBeforeFlow()=runBlocking {
        val c=RenderBoundaryController()
        lateinit var p:ObservationPipeline<String,MetadataProbeReport>
        var calls=0; var beforeObserved=false;var onOwner=true;val owner=Thread.currentThread()
        p=ObservationPipeline(this,{_,_->"ok"},{_,_,scope->result(scope.selectionGeneration,scope.selectionRevision)},
            onStatePublished={s,pair,revision->
                calls++;onOwner=onOwner&&(Thread.currentThread()===owner)
                if(s.phase==ObservationPhase.OBSERVED)beforeObserved=p.state.value.phase!=ObservationPhase.OBSERVED
                publishRenderBoundary(c,s,pair,revision)
            })
        try {
            p.update(pair());check(c.snapshot().generation==1L)
            val tickets=p.state.value.requests
            p.submitOwned(tickets[0],"a",byteArrayOf(1));p.submitOwned(tickets[1],"b",byteArrayOf(2))
            withTimeout(5000){p.state.first{it.phase==ObservationPhase.OBSERVED}}
            check(beforeObserved&&onOwner&&calls>=4&&c.snapshot().styleId==9)
            p.update(null,ObservationReason.PAUSED);check(c.snapshot().status==RenderBoundaryStatus.NO_PLAN)
            p.close();check(c.snapshot().status==RenderBoundaryStatus.CLOSED)
        }finally{p.close()}
    }
    fun sameEpochBindInvalidatesBeforeRecalculation()=runBlocking {
        val c=RenderBoundaryController()
        val p=ObservationPipeline(this,{_:ByteArray,_:String->"ok"},{_:String,_:String,q:ObservationPair->result(q.selectionGeneration,q.selectionRevision)},
            onStatePublished={s,pair,revision->publishRenderBoundary(c,s,pair,revision)})
        try {
            p.update(pair());val tickets=p.state.value.requests
            p.submitOwned(tickets[0],"a",byteArrayOf(1));p.submitOwned(tickets[1],"b",byteArrayOf(2))
            withTimeout(5000){p.state.first{it.phase==ObservationPhase.OBSERVED}}
            check(c.snapshot().styleId==9)
            p.bindResolvedScope(tickets[0],ObservationBindingScenarios.scope())
            check(c.snapshot().revision==1L&&c.snapshot().status==RenderBoundaryStatus.NO_PLAN)
            withTimeout(5000){p.state.first{it.phase==ObservationPhase.OBSERVED&&it.result?.selection?.bindingRevision==1L}}
            check(c.snapshot().revision==1L&&c.snapshot().styleId==9)
        }finally{p.close()}
    }
    val tests=listOf(::compiledPublication,::mismatchedEpoch,::mismatchedRevision,::missingPair,
        ::rejectedSchedule,::closePublication,::barriersBeforeFlow,::sameEpochBindInvalidatesBeforeRecalculation)
    @JvmStatic fun main(args:Array<String>){tests.forEach{it();println("PASS ${it.name}")};println("Publication: 8/8 PASSED")}
}
