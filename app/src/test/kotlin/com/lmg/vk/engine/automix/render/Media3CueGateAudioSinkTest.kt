package com.lmg.vk.engine.automix.render

import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioSink
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Real fork AudioSink API; recording delegate instead of a device AudioTrack. */
class Media3CueGateAudioSinkTest {
    private class Rig {
        val c=RenderBoundaryController()
        val a=c.newEndpoint();val b=c.newEndpoint()
        val aKey=RenderSourceKey("A","same","source-a",null,null)
        val bKey=RenderSourceKey("B","same","source-b",null,null)
        val at=Any();val bt=Any();val calls=mutableListOf<String>()
        var take=Int.MAX_VALUE
        var failure:RuntimeException?=null
        var original:ByteBuffer?=null
        val delegate=Proxy.newProxyInstance(AudioSink::class.java.classLoader,arrayOf(AudioSink::class.java)) {_,method,args->
            calls+=method.name
            when(method.name) {
                "handleBuffer" -> {
                    failure?.let { throw it }
                    val bytes=args!![0] as ByteBuffer
                    original?.let { assertSame(it,bytes) }
                    assertEquals(0L,args[1]);assertEquals(1,args[2])
                    bytes.position(bytes.position()+minOf(bytes.remaining(),take));!bytes.hasRemaining()
                }
                else -> when(method.returnType) {
                    Boolean::class.javaPrimitiveType->false
                    Int::class.javaPrimitiveType->0
                    Long::class.javaPrimitiveType->0L
                    else->null
                }
            }
        } as AudioSink
        val sink=Media3BoundaryAudioSink(delegate,a)
        init {
            c.offer(RenderBoundaryPlan.create(1,1,aKey,bKey,.001,.001,9))
            assertEquals(CueProbeRequest.REQUESTED,c.requestCueProbe(1,1,500))
        }
        fun context()=a.outputContext(at,RenderOutputIdentity(aKey,"A",1,0,0),RenderPcmFormat(48000,2,4),0)
        fun hold():ByteBuffer {val bytes=ByteBuffer.allocateDirect(768);original=bytes;context()
            assertFalse(sink.handleBuffer(bytes,0,1));assertTrue(calls.isEmpty());return bytes}
    }
    @Test fun holdsOriginalBeforeDelegateAndReturnsFalse() {
        val r=Rig();val b=r.hold();assertEquals(0,b.position());assertEquals(768,b.limit())
        assertEquals(CueProbePhase.ONE_BUFFER_HELD,r.c.cueProbeSnapshot().phase)
    }
    @Test fun releaseForwardsExactOriginalWithExactTimestamp() {
        val r=Rig();val b=r.hold();r.c.releaseCueProbe();r.context()
        assertTrue(r.sink.handleBuffer(b,0,1));assertEquals(listOf("handleBuffer"),r.calls)
    }
    @Test fun releasedPartialBufferMustBeRetriedWithoutReplay() {
        val r=Rig();val b=r.hold();r.c.releaseCueProbe();r.take=8;r.context()
        assertFalse(r.sink.handleBuffer(b,0,1));assertEquals(8,b.position())
        r.take=1000;r.context();assertTrue(r.sink.handleBuffer(b,0,1))
        assertEquals(listOf("handleBuffer","handleBuffer"),r.calls)
    }
    @Test fun pauseRevokesWithoutWritingHeldData() {
        val r=Rig();val b=r.hold();r.sink.pause();assertEquals(listOf("pause"),r.calls)
        assertEquals(CueProbeReason.PAUSED,r.c.cueProbeSnapshot().reason)
        assertEquals(0,b.position());r.context();assertTrue(r.sink.handleBuffer(b,0,1))
    }
    @Test fun flushDiscardsBindingNotByReplayingData() {
        val r=Rig();val b=r.hold();r.sink.flush();assertEquals(listOf("flush"),r.calls)
        assertEquals(0,b.position());assertEquals(CueProbePhase.RELEASED,r.c.cueProbeSnapshot().phase)
    }
    @Test fun configureRevokesAndDelegatesOnce() {
        val r=Rig();r.hold();r.sink.configure(Format.Builder().build(),4096,null)
        assertEquals(listOf("configure"),r.calls)
        assertEquals(CueProbeReason.CONFIGURATION_CHANGED,r.c.cueProbeSnapshot().reason)
    }
    @Test fun nonUnityPlaybackRevokesWithoutOwningVolume() {
        val r=Rig();r.hold();r.sink.setPlaybackParameters(PlaybackParameters(1.25f))
        assertEquals(listOf("setPlaybackParameters"),r.calls)
        assertEquals(CueProbeReason.PLAYBACK_UNSUPPORTED,r.c.cueProbeSnapshot().reason)
        assertFalse(r.c.cueProbeSnapshot().ownsTransitionGain)
    }
    @Test fun delegateFailureAfterReleaseIsNotReplaced() {
        val r=Rig();val b=r.hold();r.c.releaseCueProbe();r.failure=IllegalStateException("sink")
        r.context();try{r.sink.handleBuffer(b,0,1);fail("Expected original sink exception")}
        catch(error:IllegalStateException){assertSame(r.failure,error)}
    }
}
