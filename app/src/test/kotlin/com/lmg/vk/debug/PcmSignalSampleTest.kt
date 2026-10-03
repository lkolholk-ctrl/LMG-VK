package com.lmg.vk.debug

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmSignalSampleTest {
    @Test fun pcm16ReadsOnlyRemainingBytesWithoutChangingBuffer() {
        val buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(32767).putShort(-16384).putShort(8192).putShort(32767)
        buffer.position(2); buffer.limit(6)
        val before = byteArrayOf(buffer.get(2), buffer.get(3), buffer.get(4), buffer.get(5))
        assertEquals(0.5f, PcmSignalSample.peak(buffer, 2), 0f)
        assertEquals(2, buffer.position()); assertEquals(6, buffer.limit())
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order())
        assertArrayEquals(before, byteArrayOf(buffer.get(2), buffer.get(3), buffer.get(4), buffer.get(5)))
    }

    @Test fun floatPcmReportsSignalAndNonFiniteOutput() {
        val buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        buffer.putFloat(-0.75f).putFloat(0.25f).flip()
        assertEquals(0.75f, PcmSignalSample.peak(buffer, 4), 0f)
        buffer.putFloat(0, Float.NaN)
        assertTrue(PcmSignalSample.peak(buffer, 4).isNaN())
    }

    @Test fun fullScaleNegative16BitIsOne() {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder())
        buffer.putShort(Short.MIN_VALUE).flip()
        assertEquals(1f, PcmSignalSample.peak(buffer, 2), 0f)
    }

    @Test fun silenceIsDistinctFromMissingOrUnsupportedSamples() {
        val buffer = ByteBuffer.allocate(16)
        assertEquals(0f, PcmSignalSample.peak(buffer, 2), 0f)
        assertEquals(-1f, PcmSignalSample.peak(buffer, 3), 0f)
        buffer.limit(1)
        assertEquals(-1f, PcmSignalSample.peak(buffer, 2), 0f)
    }

    @Test fun boundedSamplingIncludesEndOfLargeBuffer() {
        val buffer = ByteBuffer.allocateDirect(65536).order(ByteOrder.nativeOrder())
        buffer.putShort(buffer.limit() - 2, 16384)
        assertEquals(0.5f, PcmSignalSample.peak(buffer, 2), 0f)
        assertEquals(0, buffer.position())
    }
}
