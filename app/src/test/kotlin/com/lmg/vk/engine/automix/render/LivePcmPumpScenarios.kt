package com.lmg.vk.engine.automix.render

import com.lmg.vk.engine.automix.nativecore.NativeLivePcmExecutor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Real ingress + real existing DSP + production pump. Recording sink, NOT hardware.
 * The small plan is a synthetic compiled-plan fixture, not an Apple capture. */
object LivePcmPumpScenarios {
    fun plan(rate:Double=1.0):LongArray {
        val w=MutableList(40){0L}
        w[0]=0x4c4d47535031;w[1]=1;w[3]=9;w[4]=1;w[5]=10;w[6]=20;w[8]=10;w[9]=8;w[10]=8
        val d=doubleArrayOf(15.0,5.0,6.0,0.0,rate,1.0,rate,1.0,1.0,rate,rate,0.0,1.0,.5,0.0,1.0,0.0,1.0,5.0,0.0,1.0,1.0)
        d.forEachIndexed{i,v->w[11+i]=v.toRawBits()}
        w[33]=3;w[34]=3;w[35]=12;w[37]=1
        for(side in 0..1)for(id in intArrayOf(26,27,28)){
            val first=if(side==0)5.0 else 0.0;val end=if(side==0)6.0 else rate
            val value=when(id){26->if(side==0)1.0 else rate;27->.5;else->1.0}
            w+=id.toLong();w+=2;w+=value.toRawBits();w+=first.toRawBits();w+=128
            w+=value.toRawBits();w+=end.toRawBits();w+=128
        }
        w[2]=w.size.toLong();return w.toLongArray()
    }
    private fun pcm(frames:Int,value:Float,width:Int,channels:Int=1):ByteBuffer = ByteBuffer.allocateDirect(frames*width*channels).order(ByteOrder.LITTLE_ENDIAN).also{b->
        repeat(frames){repeat(channels){ch->val v=if(ch==0)value else value*-.5f;if(width==4)b.putFloat(v)else b.putShort((v*32768).toInt().toShort())}};b.flip()
    }
    private class Sink(val width:Int,var limit:Int,val channels:Int=1) : PcmOutputBackend {
        val bytes=ArrayList<Byte>();var calls=0;var firstPts:Long?=null;val volumes=ArrayList<Float>();var stalled=false;var afterAccept:(()->Unit)?=null
        override fun write(buffer:ByteBuffer,ptsUs:Long):Boolean{calls++;if(firstPts==null)firstPts=ptsUs
            if(stalled)return false
            repeat(minOf(limit,buffer.remaining())){bytes+=buffer.get()};afterAccept?.invoke();return !buffer.hasRemaining()}
        override fun pending()=false
        override fun positionUs():Long=firstPts?.let{it+(bytes.size/(width*channels))*125L}?:Long.MIN_VALUE
        override fun volume(value:Float){volumes+=value}
        override fun finish()=true
    }
    private fun run(width:Int,chunk:Int,starve:Boolean=false,partial:Int=Int.MAX_VALUE,rate:Double=1.0,exact:Boolean=false,cancelWrite:Boolean=false,channels:Int=1):List<Byte>{
        var now=0L
        val c=RenderBoundaryController{now};val ep=arrayOf(c.newEndpoint(),c.newEndpoint())
        val keys=arrayOf(RenderSourceKey("A","same","mem:A",null,null),RenderSourceKey("B","same","mem:B",null,null))
        val tokens=arrayOf(Any(),Any());val format=RenderPcmFormat(8000,channels,width);val frameBytes=width*channels
        val sinks=arrayOf(Sink(width,partial,channels),Sink(width,partial,channels));val ports=Array(2){SameSinkOutputPort(sinks[it])}
        c.offer(RenderBoundaryPlan.create(1,1,keys[0],keys[1],5.0,0.0,9,RenderExecutionData(plan(rate))))
        val offset=10_000_000L
        fun context(side:Int){ep[side].outputContext(tokens[side],RenderOutputIdentity(keys[side],keys[side].windowUid,side+10L,0,offset),format,offset+(if(side==0)4_900_000 else 0))}
        fun callback(side:Int,b:ByteBuffer,first:Long):Boolean{context(side);return ep[side].forwardWithCueGate(b,offset+first*125,1){error("ASSERT legacy write after hold")}}
        for(i in 0..1){ep[i].sameSinkOutputPort=ports[i];ports[i].bindOutput(tokens[i],format);ports[i].transitionVolume(1f,.5f)}
        var valid=true;val lease=object:CuePlaybackLease{
            override fun isCurrent(generation:Long,revision:Long,format:RenderPcmFormat)=valid&&generation==1L&&revision==1L&&format==ep[0].format
            override fun revoke(){valid=false}
        }
        if(exact){context(0);val prior=pcm(8,.1f,width,channels)
            check(ep[0].forwardWithCueGate(prior,offset+39992*125L,1){prior.position(prior.limit());true})}
        check(c.cueGate.requestLive(1,1)==CueProbeRequest.REQUESTED)
        var pos0=if(exact)40000L else 39996L;var pos1=0L
        var a=pcm(128,.1f,width,channels);var b=pcm(128,.2f,width,channels)
        check(!callback(0,a,pos0)&&!callback(1,b,pos1))
        val pump=checkNotNull(LivePcmPump.start(c,lease){now})
        val prefix=if(exact)0 else 4;check(pump.outgoingPrefixFrames==prefix.toLong())
        check(callback(0,a,pos0)&&callback(1,b,pos1));pos0+=128;pos1+=128
        a=pcm(minOf(chunk,(48000-pos0).toInt()),.1f,width,channels)
        val endB=(16000*rate).toLong();b=pcm(minOf(chunk,endB.toInt()-pos1.toInt()),.2f,width,channels)
        var ea=false;var eb=false
        try{
            if(cancelWrite){sinks[0].afterAccept={c.invalidate(2,0)}
                var rejected=false;try{pump.tick()}catch(_:IllegalStateException){rejected=true}
                check(rejected&&!valid);check(sinks[0].bytes.size==minOf(partial,4*frameBytes));check(sinks[1].bytes.isEmpty())
                return sinks[0].bytes.toList()
            }
            var count=0
            while(!pump.isComplete&&count++<100000){
                if(pos0<48000){val before=a.position();callback(0,a,pos0);if(!a.hasRemaining()){pos0+=a.limit()/frameBytes;a=pcm(minOf(chunk,(48000-pos0).toInt()),.1f,width,channels)}else check(a.position()>=before)}
                else if(!ea){pump.endInput(0);ea=true}
                if(!starve||count>40){if(pos1<endB){callback(1,b,pos1);if(!b.hasRemaining()){pos1+=b.limit()/frameBytes;b=pcm(minOf(chunk,(endB-pos1).toInt()),.2f,width,channels)}}else if(!eb){pump.endInput(1);eb=true}}
                now+=10000;pump.tick()
            }
            check(pump.isComplete){"ASSERT pump failed to finish"};check(sinks[1].calls==0)
            val stats=pump.nativeSnapshot();check(stats[12]==16000L&&stats[17]==1L)
            check(sinks[0].bytes.size==(16000+prefix)*frameBytes){"ASSERT unexpected output length"}
            check(pump.sourcePositionUs(1)!=null)
            val out=sinks[0].bytes.toByteArray();val read=ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            if(width==4){repeat(prefix){repeat(channels){ch->check(read.getFloat()==if(ch==0).1f else -.05f)}};if(rate==1.0)for(i in 3000..6000)for(ch in 0 until channels)check(abs(read.getFloat(i*frameBytes+ch*4)-(if(ch==0).15f else -.075f))<2e-5f)}
            return sinks[0].bytes.toList()
        }finally{pump.close()}
    }
    val names=listOf("realDspPrefixAndBContinuation","realDspStarvationRetainsOutgoing","partialByteWritesDoNotReplay","pcm16ExplicitConversion","unequalRatesReachTrueEof","immutablePlanCopy","malformedPlanRejected","nativeOwnerThreadEnforced","exactCueAfterAcceptedPrefix","cancelInsidePartialWrite","postCueOfferCannotBeReserved","nonfiniteInputRejected","stereoFramesAndChannelsPreserved")
    fun run(name:String){when(name){
        names[0]->run(4,128)
        names[1]->check(run(4,128)==run(4,31,true))
        names[2]->check(run(4,128)==run(4,73,false,13))
        names[3]->run(2,127,true,17)
        names[4]->run(4,89,true,101,1.25)
        names[5]->{val p=plan();NativeLivePcmExecutor(p,8000,1,128,1,1).use{p.fill(0);val st=LongArray(19);it.stats(st);check(st[1]==40000L)}}
        names[6]->{var failed=false;try{NativeLivePcmExecutor(LongArray(40),8000,1,128,1,1)}catch(_:IllegalArgumentException){failed=true};check(failed)}
        names[7]->NativeLivePcmExecutor(plan(),8000,1,128,1,1).use{e->e.stats(LongArray(19));var rejected=false;Thread{try{e.stats(LongArray(19))}catch(_:IllegalStateException){rejected=true}}.also{it.start();it.join()};check(rejected)}
        names[8]->run(4,128,exact=true)
        names[9]->run(4,128,partial=13,cancelWrite=true)
        names[10]->{
            val c=RenderBoundaryController();val a=c.newEndpoint();val b=c.newEndpoint();val f=RenderPcmFormat(8000,1,4)
            val ka=RenderSourceKey("A","A","mem:A",null,null);val kb=RenderSourceKey("B","B","mem:B",null,null)
            c.offer(RenderBoundaryPlan.create(1,1,ka,kb,5.0,0.0,9,RenderExecutionData(plan())))
            val ta=Any();a.outputContext(ta,RenderOutputIdentity(ka,"a",1,0,0),f,4_900_000)
            b.outputContext(Any(),RenderOutputIdentity(kb,"b",2,0,0),f,0)
            val p=pcm(128,.1f,4);check(a.forwardWithCueGate(p,5_000_000,1){p.position(p.limit());true})
            c.cueGate.requestLive(1,1)
            val next=pcm(128,.1f,4);a.outputContext(ta,RenderOutputIdentity(ka,"a",1,0,0),f,4_900_000)
            a.forwardWithCueGate(next,5_016_000,1){next.position(next.limit());true}
            check(c.reserveExecutionCue()==null)
        }
        names[11]->NativeLivePcmExecutor(plan(),8000,1,128,1,1).use{e->
            val b=pcm(1,Float.NaN,4);var rejected=false
            try{e.push(0,b,1,40000)}catch(_:IllegalArgumentException){rejected=true}
            check(rejected);val st=LongArray(19);e.stats(st);check(st[4]==0L)
        }
        names[12]->check(run(4,128,channels=2)==run(4,31,true,13,channels=2))
        else->error("Unknown scenario")
    }}
    @JvmStatic fun main(args:Array<String>){for(n in if(args.isEmpty())names else args.toList()){run(n);println("PASS $n")};println("LIVE_PCM_PUMP_REAL_DSP_JNI_PASSED")}
}
