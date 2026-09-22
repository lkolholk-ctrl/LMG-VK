package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun pcm(samples: Int) = ByteBuffer.allocateDirect(samples * 4).order(ByteOrder.nativeOrder())
private inline fun <reified T : Throwable> rejects(block: () -> Unit) {
    try { block() } catch (error: Throwable) { check(error is T) { error }; return }
    error("Expected ${T::class.java.name}")
}

fun main() {
    val input = pcm(12)
    val output = pcm(12)
    repeat(12) { input.putFloat(it * 4, (it + 1) / 16f) }
    NativeTrackEffects(48000.0, 2, 4, 123).use { graph ->
        input.position(8)
        output.position(8)
        graph.process(input, output, 4)
        check(graph.positionFrames() == 127L)
        check(input.position() == 8 && output.position() == 8)
        repeat(8) { check(output.getFloat(8 + it * 4) == input.getFloat(8 + it * 4)) }
        graph.process(output, output, 4)
        check(graph.positionFrames() == 131L)
        graph.process(null, output, 4)
        repeat(8) { check(output.getFloat(8 + it * 4) == 0f) }
        graph.process(null, output, 0)
        check(graph.positionFrames() == 135L)
        rejects<IllegalArgumentException> { graph.process(input, output, 5) }
        rejects<IllegalArgumentException> { graph.process(input, output.asReadOnlyBuffer(), 1) }
        rejects<IllegalArgumentException> { graph.process(ByteBuffer.allocate(32), output, 1) }
        val overlap = input.duplicate().order(ByteOrder.nativeOrder()).apply { position(12) }
        rejects<IllegalArgumentException> { graph.process(input, overlap, 3) }
        input.putFloat(8, Float.NaN)
        rejects<IllegalArgumentException> { graph.process(input, output, 1) }
        check(graph.positionFrames() == 135L)
        graph.close()
        graph.close()
        rejects<IllegalStateException> { graph.process(null, output, 1) }
    }
    rejects<IllegalArgumentException> { NativeTrackEffects(48000.0, 2, 4, -1) }
    rejects<IllegalArgumentException> { NativeTrackEffects(Double.NaN, 2, 4, 0) }
    rejects<IllegalArgumentException> { NativeTrackEffects(48000.0, 2, 4, 0,
        listOf(NativeTrackEffects.Write(0, "oops", 1.0))) }
    rejects<IllegalArgumentException> { NativeTrackEffects(48000.0, 2, 4, 0,
        listOf(NativeTrackEffects.Write(0, "Ga1g", 1.0), NativeTrackEffects.Write(0, "Ga1g", 0.0))) }
    // The first gain write is immediate, later gain changes have native smoothing.
    NativeTrackEffects(48000.0, 1, 4, 0,
        listOf(NativeTrackEffects.Write(0, "Ga1g", 0.25))).use { graph ->
        val samples = pcm(4).apply { repeat(4) { putFloat(it * 4, 1f) } }
        graph.process(samples, samples, 4)
        repeat(4) { check(samples.getFloat(it * 4) == .25f) }
    }
    println("NativeTrackEffects JNI: lifecycle, PCM, scheduled writes and failure paths passed")
}
