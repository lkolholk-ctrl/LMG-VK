package com.lmg.vk.engine.automix.nativecore

/** Control-thread metadata only. No PCM buffers, player handles, or DSP graph creation. */
object NativeObservationBridge {
    init { System.loadLibrary("lmg_automix_jni") }

    // Standard UTF-8 byte arrays, not JNI Modified UTF-8 strings. Signatures are
    // intentionally stable; keep this class and its native methods through R8.
    external fun describeSong(input: ByteArray, songId: ByteArray): ByteArray
    external fun describeStyles(input: ByteArray): ByteArray

    /** Fixed-size, presence-preserving metadata probe; never returns a playback plan. */
    external fun probePair(values: DoubleArray, presentMask: Int, traits: Int): LongArray
}
