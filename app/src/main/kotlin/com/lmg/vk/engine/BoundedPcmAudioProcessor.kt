package com.lmg.vk.engine

import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** One fixed scratch buffer, allocated at configuration rather than queueInput.
 * Larger input buffers are consumed in frame-aligned portions by the normal Media3 loop. */
@UnstableApi
abstract class BoundedPcmAudioProcessor : BaseAudioProcessor() {
    protected fun prepareOutputStorage() { replaceOutputBuffer(CAPACITY).limit(0) }
    protected fun boundedBytes(input: ByteBuffer, frameBytes: Int): Int {
        require(frameBytes > 0 && input.remaining() % frameBytes == 0)
        return minOf(input.remaining(), CAPACITY - CAPACITY % frameBytes)
    }
    protected fun copy(input: ByteBuffer, output: ByteBuffer, bytes: Int) {
        val limit = input.limit()
        input.limit(input.position() + bytes)
        try { output.put(input) } finally { input.limit(limit) }
        output.flip()
    }
    private companion object { const val CAPACITY = 65_536 }
}
