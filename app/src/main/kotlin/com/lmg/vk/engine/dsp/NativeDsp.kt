package com.lmg.vk.engine.dsp

import java.nio.ByteBuffer

internal object NativeDsp {
    val available = try { System.loadLibrary("lmg_player_dsp"); true } catch (_: LinkageError) { false }
    external fun nativeCreate(sampleRate: Int, channels: Int): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeReset(handle: Long)
    external fun nativePublish(parameters: FloatArray): Int
    external fun nativeRetry(): Boolean
    external fun nativeProcess(handle: Long, input: ByteBuffer, offset: Int,
                               output: ByteBuffer, frames: Int, bytesPerSample: Int): Boolean
}
