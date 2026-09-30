package com.lmg.vk.engine.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DspAudioProcessorTest {
    private val stereo = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
    private fun pcm(size: Int) = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

    @Test fun `empty prepared stream ends without phantom output`() {
        val p = DspAudioProcessor()
        p.configure(stereo); p.flush(); p.queueEndOfStream()
        assertTrue(p.isEnded)
        p.reset()
    }

    @Test fun `bounded blocks preserve offset and every dry sample`() {
        val p = DspAudioProcessor()
        p.configure(stereo); p.flush()
        val input = pcm(8 + 5000 * 4)
        input.position(8)
        repeat(10000) { input.putShort((it - 5000).toShort()) }
        input.flip(); input.position(8)
        var sample = 0
        while (input.hasRemaining()) {
            p.queueInput(input)
            val out = p.output
            assertTrue(out.remaining() <= 4096 * 4)
            while (out.hasRemaining()) assertEquals((sample++ - 5000).toShort(), out.short)
        }
        assertEquals(10000, sample)
        p.queueEndOfStream(); assertTrue(p.isEnded)
        p.reset()
    }

    @Test fun `configure defers new format until old stream drains`() {
        val p = DspAudioProcessor()
        p.configure(stereo); p.flush()
        val monoFloat = AudioFormat(96000, 1, C.ENCODING_PCM_FLOAT)
        p.configure(monoFloat)
        val old = pcm(4).putShort(123).putShort(-456)
        old.flip(); p.queueInput(old)
        val oldOut = p.output
        assertEquals(123.toShort(), oldOut.short); assertEquals((-456).toShort(), oldOut.short)
        p.queueEndOfStream(); assertTrue(p.isEnded)
        p.flush()
        val next = pcm(4).putFloat(0.25f)
        next.flip(); p.queueInput(next)
        assertEquals(0.25f, p.output.float, 0f)
        p.reset()
    }

    @Test fun `unsupported formats are inactive and reset releases old format`() {
        val p = DspAudioProcessor()
        p.configure(stereo); p.flush()
        assertEquals(AudioFormat.NOT_SET, p.configure(AudioFormat(22050, 2, C.ENCODING_PCM_16BIT)))
        assertFalse(p.isActive)
        p.flush(); p.queueEndOfStream(); assertTrue(p.isEnded)
        p.reset()
        assertFalse(p.isActive)
    }
}
