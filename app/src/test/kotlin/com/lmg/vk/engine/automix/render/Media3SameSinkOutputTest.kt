package com.lmg.vk.engine.automix.render

import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.LmgTransitionGainSink
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Actual Media3 interface + actual decorator, RECORDING delegate instead of AudioTrack. */
class Media3SameSinkOutputTest {
    private class Fixture(val handler:(String,Array<out Any?>)->Any? = {_,_->null}) {
        val calls=ArrayList<String>()
        val values=ArrayList<Float>()
        val c=RenderBoundaryController()
        val endpoint=c.newEndpoint()
        val sink=Proxy.newProxyInstance(AudioSink::class.java.classLoader,arrayOf(AudioSink::class.java)) {_,m,args->
            calls.add(m.name)
            if(m.name=="setVolume")values.add(args!![0] as Float)
            handler(m.name,args?:emptyArray()) ?: when(m.returnType){
                Boolean::class.javaPrimitiveType->false
                Int::class.javaPrimitiveType->0
                Long::class.javaPrimitiveType->Long.MIN_VALUE
                else->null
            }
        } as AudioSink
        val wrapper=Media3BoundaryAudioSink(sink,endpoint)
        val format=RenderPcmFormat(48000,2,4)
        fun ticket(activate:Boolean=true):SameSinkOutputPort.Ticket {
            wrapper.outputPort.bindOutput(Any(),format)
            wrapper.onLmgTransitionGain(.4f,.5f)
            val t=checkNotNull(wrapper.outputPort.reserve(1,1,format,125,{true}))
            if(activate)check(wrapper.outputPort.activate(t))
            return t
        }
        fun packet()=ByteBuffer.allocateDirect(32)
    }
    @Test fun protocolAndActualAttachment() {val f=Fixture();assertEquals(1,LmgTransitionGainSink.protocolVersion());assertTrue((f.wrapper as AudioSink) is LmgTransitionGainSink);assertSame(f.wrapper.outputPort,f.endpoint.sameSinkOutputPort);assertTrue(f.calls.isEmpty())}
    @Test fun idleSplitGainPreservesLegacyProduct() {val f=Fixture();f.wrapper.onLmgTransitionGain(.4f,.5f);assertEquals(listOf(.2f),f.values)}
    @Test fun ordinaryPlayerVolumeIsStillDirect() {val f=Fixture();f.wrapper.onLmgTransitionGain(.4f,.5f);f.wrapper.setVolume(.7f);assertEquals(listOf(.2f,.7f),f.values)}
    @Test fun ownedGainRetainsPlayerAndDucking() {val f=Fixture();f.ticket();f.wrapper.onLmgTransitionGain(.01f,.13f);assertEquals(.13f,f.values.last(),0f)}
    @Test fun reservationDoesNotModifyQueuedGain() {val f=Fixture{n,_->if(n=="hasPendingData")true else null};val t=f.ticket(false);assertEquals(listOf(.2f),f.values);assertFalse(f.wrapper.outputPort.activate(t));assertEquals(listOf(.2f),f.values)}
    @Test fun writerReusesExactDelegateAndBuffer() {val b=ByteBuffer.allocateDirect(32);val f=Fixture{n,a->if(n=="handleBuffer"){assertSame(b,a[0]);assertEquals(125L,a[1]);assertEquals(1,a[2]);b.position(b.limit());true}else null};val t=f.ticket();assertTrue(f.wrapper.outputPort.write(t,1,0,b).bufferFinished);assertEquals(1,f.calls.count{it=="handleBuffer"})}
    @Test fun partialWriteUsesIdenticalBufferOnRetry() {val b=ByteBuffer.allocateDirect(32);val f=Fixture{n,a->if(n=="handleBuffer"){assertSame(b,a[0]);b.position(minOf(b.limit(),b.position()+3));!b.hasRemaining()}else null};val t=f.ticket();while(!f.wrapper.outputPort.write(t,1,0,b).bufferFinished){};assertEquals(32L,f.wrapper.outputPort.snapshot().acceptedBytes)}
    @Test fun pausedPortCannotResumeLegacyWithoutFlush() {val f=Fixture();f.ticket();f.wrapper.pause();try{f.wrapper.outputPort.checkLegacyWrite();fail("No reset required")}catch(_:OutputPortResetRequired){};assertEquals(1,f.calls.count{it=="pause"})}
    @Test fun successfulRealFlushClearsReservation() {val f=Fixture();f.ticket();f.wrapper.flush();f.wrapper.outputPort.checkLegacyWrite();assertEquals(OutputPortPhase.IDLE,f.wrapper.outputPort.snapshot().phase);assertEquals(1,f.calls.count{it=="flush"})}
    @Test fun failedRealFlushDoesNotReleaseQuarantine() {val failure=IllegalStateException("test flush");val f=Fixture{n,_->if(n=="flush")throw failure else null};f.ticket();try{f.wrapper.flush();fail("Missing exception")}catch(e:IllegalStateException){assertSame(failure,e)};assertEquals(OutputPortPhase.RESET_REQUIRED,f.wrapper.outputPort.snapshot().phase)}
    @Test fun outputOffsetChangeRevokesBeforeDelegate() {val f=Fixture();f.ticket();f.wrapper.setOutputStreamOffsetUs(900);assertEquals(OutputPortPhase.RESET_REQUIRED,f.wrapper.outputPort.snapshot().phase);assertEquals(1,f.calls.count{it=="setOutputStreamOffsetUs"})}
    @Test fun clockReadingIsExistingSinkNotManufacturedFrames() {val f=Fixture{n,_->if(n=="getCurrentPositionUs")123456L else null};val t=f.ticket();assertEquals(123456L,checkNotNull(f.wrapper.outputPort.sampleSinkClock(t)));assertEquals(0L,f.wrapper.outputPort.snapshot().completeFrames);assertFalse(f.wrapper.outputPort.snapshot().rendererClockReserved)}
}
