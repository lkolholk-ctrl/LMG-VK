package com.lmg.vk.engine.automix.render

import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.LmgPcmBoundaryListener
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Runs against the built fork's AudioSink API, with a recording delegate, not AudioTrack. */
class Media3BoundaryAudioSinkTest {
    private class Fixture(val handler:(String,Array<out Any?>)->Any? = { _,_->null }) {
        val calls=mutableListOf<String>()
        val controller=RenderBoundaryController()
        val delegate=Proxy.newProxyInstance(AudioSink::class.java.classLoader,arrayOf(AudioSink::class.java)) { _,m,args ->
            calls+=m.name
            val result=handler(m.name,args?:emptyArray())
            result ?: when(m.returnType) { Boolean::class.javaPrimitiveType->false
                Int::class.javaPrimitiveType->0;Long::class.javaPrimitiveType->0L;else->null }
        } as AudioSink
        val wrapped=RenderBoundarySinkFactory.wrap(delegate,controller)
    }
    @Test fun actualForkHookIsPresent() {
        val f=Fixture();assertEquals(1,LmgPcmBoundaryListener.protocolVersion())
        assertTrue(f.wrapped is LmgPcmBoundaryListener);assertNotSame(f.delegate,f.wrapped)
    }
    @Test fun partialDelegateResultAndBufferAreUnchanged() {
        val b=ByteBuffer.allocateDirect(32);val f=Fixture{n,a->if(n=="handleBuffer"){
            assertSame(b,a[0]);assertEquals(123L,a[1]);assertEquals(1,a[2]);b.position(8);false
        }else null}
        assertFalse(f.wrapped.handleBuffer(b,123,1));assertEquals(8,b.position());assertEquals(32,b.limit())
        assertEquals(listOf("handleBuffer"),f.calls)
    }
    @Test fun completeDelegateResultIsUnchanged() {
        val b=ByteBuffer.allocateDirect(32);val f=Fixture{n,_->if(n=="handleBuffer"){b.position(32);true}else null}
        assertTrue(f.wrapped.handleBuffer(b,0,1));assertEquals(32,b.position())
    }
    @Test fun exactDelegateExceptionIsPreserved() {
        val error=IllegalStateException("delegate failure")
        val f=Fixture{n,_->if(n=="handleBuffer")throw error else null}
        try{f.wrapped.handleBuffer(ByteBuffer.allocateDirect(8),0,1);fail("missing exception")}
        catch(e:IllegalStateException){assertSame(error,e)}
    }
    @Test fun volumeStillBelongsToDelegate() {
        val f=Fixture{n,a->if(n=="setVolume")assertEquals(.37f,a[0]);null}
        f.wrapped.setVolume(.37f);assertEquals(listOf("setVolume"),f.calls)
        assertFalse(f.controller.snapshot().ownsTransitionGain)
    }
    @Test fun lifecycleIsForwardedOnceInOrder() {
        val f=Fixture();f.wrapped.pause();f.wrapped.flush();f.wrapped.reset();f.wrapped.release()
        assertEquals(listOf("pause","flush","reset","release"),f.calls)
    }
    @Test fun eosAndReadinessAreNotInvented() {
        val f=Fixture{n,_->when(n){"isEnded"->false;"hasPendingData"->true;else->null}}
        f.wrapped.playToEndOfStream();assertFalse(f.wrapped.isEnded);assertTrue(f.wrapped.hasPendingData())
        assertEquals(listOf("playToEndOfStream","isEnded","hasPendingData"),f.calls)
    }
    @Test fun noControllerReturnsOriginalSink() {
        val f=Fixture();assertSame(f.delegate,RenderBoundarySinkFactory.wrap(f.delegate,null))
    }
}
