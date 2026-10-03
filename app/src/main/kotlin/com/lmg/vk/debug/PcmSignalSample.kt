package com.lmg.vk.debug

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/** A bounded signal sample, not a silence detector. Does not consume or modify PCM. */
internal object PcmSignalSample {
    fun peak(buffer: ByteBuffer, bytesPerSample: Int): Float {
        if (bytesPerSample != 2 && bytesPerSample != 4) return -1f
        val count = buffer.remaining() / bytesPerSample
        if (count == 0) return -1f
        val samples = min(count, 128)
        var peak = 0f
        for (i in 0 until samples) {
            val index = if (samples == 1) 0 else i.toLong() * (count - 1) / (samples - 1)
            val offset = buffer.position() + index.toInt() * bytesPerSample
            val value = if (bytesPerSample == 2) buffer.getShort(offset) / 32768f
                else buffer.getFloat(offset)
            if (!value.isFinite()) return Float.NaN
            peak = maxOf(peak, abs(value))
        }
        return peak
    }
}
