package com.lmg.vk.engine.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** Run with the real host JNI library; no Android/device mocks. */
object DspJniScenarios {
    private fun parameters(preamp: Float = 0f) = FloatArray(67).apply {
        this[0] = 1f; this[1] = 0f; this[2] = preamp
        this[5] = -1f; this[6] = -0.3f; this[7] = 80f; this[8] = 20f
        repeat(8) { val b = 19 + it * 6; this[b + 2] = 1000f; this[b + 4] = 1f; this[b + 5] = 1f }
    }
    private fun buffer(n: Int) = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
    @JvmStatic fun main(args: Array<String>) {
        check(NativeDsp.available)
        check(NativeDsp.nativePublish(parameters(-6f)) == 1)
        val handle = NativeDsp.nativeCreate(48000, 2)
        check(handle != 0L)
        val input = buffer(40); val output = buffer(32)
        repeat(8) { input.putFloat(8 + it * 4, 0.5f) }
        check(NativeDsp.nativeProcess(handle, input, 8, output, 4, 4))
        check(kotlin.math.abs(output.getFloat(0) - 0.2505936f) < 0.00001f)
        check(!NativeDsp.nativeProcess(handle, input, 9, output, 4, 4)) // capacity guard
        check(!NativeDsp.nativeProcess(handle, ByteBuffer.allocate(32), 0, output, 4, 4))
        check(!NativeDsp.nativeProcess(handle, input, 0, output, 4097, 4))
        check(NativeDsp.nativePublish(floatArrayOf(1f)) == -1)
        var full = false
        repeat(20) { if (NativeDsp.nativePublish(parameters(-it.toFloat())) == 0) full = true }
        check(full)
        check(NativeDsp.nativeProcess(handle, input, 8, output, 4, 4))
        check(NativeDsp.nativeRetry())
        NativeDsp.nativeDestroy(handle)
        check(NativeDsp.nativeCreate(22050, 2) == 0L)
        val failed = AtomicReference<Throwable?>(null)
        val producer = Thread {
            try { repeat(500) { NativeDsp.nativePublish(parameters(-(it % 24).toFloat())); Thread.yield() } }
            catch (t: Throwable) { failed.set(t) }
        }
        producer.start()
        repeat(100) {
            val instance = NativeDsp.nativeCreate(44100, 2); check(instance != 0L)
            repeat(10) { check(NativeDsp.nativeProcess(instance, input, 8, output, 4, 4)) }
            NativeDsp.nativeReset(instance)
            NativeDsp.nativeDestroy(instance)
        }
        producer.join(); failed.get()?.let { throw it }
        println("JNI: direct-buffer bounds, parameters, queue retry and concurrent lifecycle passed")
    }
}
