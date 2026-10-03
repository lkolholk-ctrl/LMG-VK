package com.lmg.vk.engine

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class RetainingAudioOutputProviderTest {
    private class Physical {
        val calls = mutableListOf<String>()
        val listeners = mutableSetOf<AudioOutput.Listener>()
        var position = 0L
        var writeFailure: AudioOutput.WriteException? = null
        var flushFailure = false
        var stalled = false
        val output = Proxy.newProxyInstance(AudioOutput::class.java.classLoader,
            arrayOf(AudioOutput::class.java)) { _, method, args ->
            calls += method.name
            when (method.name) {
                "getAudioSessionId" -> 41
                "getPositionUs" -> position
                "isStalled" -> stalled
                "addListener" -> { listeners.add(args!![0] as AudioOutput.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as AudioOutput.Listener); null }
                "flush" -> { if (flushFailure) error("flush failed"); position = 0; null }
                "write" -> { writeFailure?.let { throw it }; val b = args!![0] as ByteBuffer; b.position(b.limit()); true }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        } as AudioOutput
    }

    private class Rig(enabled: Boolean = true) {
        val physical = mutableListOf<Physical>()
        val events = mutableListOf<String>()
        val listeners = mutableSetOf<AudioOutputProvider.Listener>()
        var releases = 0
        var nextConfig = config()
        val delegate = Proxy.newProxyInstance(AudioOutputProvider::class.java.classLoader,
            arrayOf(AudioOutputProvider::class.java)) { _, method, args ->
            when (method.name) {
                "getAudioOutput" -> Physical().also { physical.add(it) }.output
                "getOutputConfig" -> nextConfig
                "addListener" -> { listeners.add(args!![0] as AudioOutputProvider.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as AudioOutputProvider.Listener); null }
                "release" -> { releases++; null }
                else -> null
            }
        } as AudioOutputProvider
        val provider = RetainingAudioOutputProvider(delegate, enabled, trace = { events.add(it) })
        fun take(config: AudioOutputProvider.OutputConfig = config()) = provider.getAudioOutput(config)
    }

    @Test fun alternatingSinksReuseTheirOwnPhysicalOutputAcrossSeveralTracks() {
        val a = Rig(); val b = Rig()
        repeat(8) {
            val r = if (it % 2 == 0) a else b
            val lease = r.take()
            lease.play()
            assertTrue(lease.write(ByteBuffer.allocateDirect(128), 1, 0))
            lease.stop(); lease.release()
            assertEquals(1, r.physical.size)
            assertFalse(r.physical.single().calls.contains("stop"))
            assertFalse(r.physical.single().calls.contains("release"))
        }
        a.provider.release(); b.provider.release()
        for (r in listOf(a, b)) {
            assertEquals(4, r.physical.single().calls.count { it == "flush" })
            assertEquals(1, r.physical.single().calls.count { it == "release" })
            assertEquals(1, r.releases)
        }
    }

    @Test fun endOfStreamDrainsUsingActualPositionUntilParkingResetsIt() {
        val r = Rig(); val out = r.take(); val physical = r.physical.single()
        out.write(ByteBuffer.allocateDirect(128), 1, 0)
        physical.position = 50
        out.stop()
        assertEquals(50L, out.positionUs)
        physical.position = 100
        assertEquals(100L, out.positionUs)
        out.release()
        val next = r.take(config().buildUpon().setAudioSessionId(41).build())
        assertEquals(0L, next.positionUs)
        assertEquals(listOf("pause", "flush"), physical.calls.filter { it in listOf("pause", "flush", "stop") })
        r.provider.release()
    }

    @Test fun formatAndExplicitSessionChangesReleaseOldOutput() {
        val configs = listOf(config(44100), config().buildUpon().setAudioSessionId(42).build(),
            config().buildUpon().setChannelMask(4).build(),
            config().buildUpon().setBufferSize(8192).build())
        for (config in configs) {
            val r = Rig(); r.take().release(); r.take(config)
            assertEquals(2, r.physical.size)
            assertEquals(1, r.physical.first().calls.count { it == "release" })
            r.provider.release()
        }
    }

    @Test fun routeCapabilityChangeEvictsIdleAndInvalidatesActiveOutput() {
        val r = Rig(); r.take().release()
        r.listeners.toList().forEach { it.onFormatSupportChanged() }
        assertTrue(r.physical.first().calls.contains("release"))
        val out = r.take()
        r.listeners.toList().forEach { it.onFormatSupportChanged() }
        out.release()
        assertTrue(r.physical.last().calls.contains("release"))
        r.provider.release()
    }

    @Test fun incompatibleOutputIsReleasedDuringConfigurationBeforeNextInitialization() {
        val r = Rig(); r.take().release()
        r.nextConfig = config(44100)
        val config = r.provider.getOutputConfig(AudioOutputProvider.FormatConfig.Builder(
            Format.Builder().setSampleRate(44100).setChannelCount(2).build()).build())
        assertSame(r.nextConfig, config)
        assertTrue(r.physical.single().calls.contains("release"))
        r.take(config)
        assertEquals(2, r.physical.size)
        r.provider.release()
    }

    @Test fun failedWritePreservesExceptionAndCannotReturnDeadOutputToPool() {
        val r = Rig(); val out = r.take()
        val failure = AudioOutput.WriteException(-6, true)
        r.physical.single().writeFailure = failure
        try { out.write(ByteBuffer.allocateDirect(32), 1, 0); fail() }
        catch (caught: AudioOutput.WriteException) { assertSame(failure, caught) }
        out.release(); r.take()
        assertEquals(2, r.physical.size)
        assertTrue(r.physical.first().calls.contains("release"))
        r.provider.release()
    }

    @Test fun failedFlushAndStalledOutputAreDestroyed() {
        for (flushFails in listOf(true, false)) {
            val r = Rig(); val out = r.take(); val physical = r.physical.single()
            if (flushFails) physical.flushFailure = true
            else { physical.stalled = true; assertTrue(out.isStalled) }
            out.release(); r.take()
            assertEquals(2, r.physical.size)
            assertTrue(physical.calls.contains("release"))
            r.provider.release()
        }
    }

    @Test fun disabledOffloadAndTunnelingKeepDestructiveLifecycle() {
        for ((enabled, config) in listOf(false to config(),
            true to config().buildUpon().setIsOffload(true).build(),
            true to config().buildUpon().setIsTunneling(true).build())) {
            val r = Rig(enabled); val out = r.take(config); out.stop(); out.release()
            assertTrue(r.physical.single().calls.containsAll(listOf("stop", "release")))
            r.provider.release()
        }
    }

    @Test fun returnedLeaseDetachesListenersAndNotifiesReleaseExactlyOnce() {
        val r = Rig(); val out = r.take(); var releases = 0
        val listener = object : AudioOutput.Listener {
            override fun onPositionAdvancing(time: Long) {}
            override fun onOffloadDataRequest() {}
            override fun onOffloadPresentationEnded() {}
            override fun onUnderrun() {}
            override fun onReleased() { releases++ }
        }
        out.addListener(listener); out.release(); out.release()
        assertEquals(1, releases)
        assertTrue(r.physical.single().listeners.isEmpty())
        r.take(); r.provider.release(); r.provider.release()
        assertEquals(1, releases)
        assertEquals(1, r.physical.single().calls.count { it == "release" })
        assertTrue(r.listeners.isEmpty())
    }

    private companion object {
        fun config(rate: Int = 48000) = AudioOutputProvider.OutputConfig.Builder()
            .setSampleRate(rate).setChannelMask(12).setEncoding(C.ENCODING_PCM_16BIT)
            .setBufferSize(4096).build()
    }
}
