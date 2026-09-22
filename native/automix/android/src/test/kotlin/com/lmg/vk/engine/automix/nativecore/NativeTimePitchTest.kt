package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private fun pitchPcm(samples: Int) = ByteBuffer.allocateDirect(samples * 4).order(ByteOrder.nativeOrder())
private inline fun <reified T: Throwable> pitchRejects(block: () -> Unit) {
    try { block() } catch (error: Throwable) { check(error is T) { error }; return }
    error("Expected ${T::class.java.name}")
}
fun main() {
    val controls = NativeTimePitch.Controls(1.0, 1f, 8f, true, true)
    val input = pitchPcm(258).apply { position(8) }
    val output = pitchPcm(258).apply { position(8) }
    repeat(128) { input.putFloat(8 + it * 8, .2f); input.putFloat(12 + it * 8, -.1f) }
    val processor = NativeTimePitch(8000.0, 2, 128, false, controls)
    var source = 0.0
    var rendered = 0.0
    repeat(40) { block ->
        val accepted = processor.enqueue(input, 128, source); source += accepted
        check(accepted == 128)
        val delivered = processor.dequeue(output, 128, rendered); rendered += delivered
        if (block > 12) repeat(delivered) {
            check(abs(output.getFloat(8 + it * 8) - .2f) < 1e-6f)
            check(abs(output.getFloat(12 + it * 8) + .1f) < 1e-6f)
        }
        check(input.position() == 8 && output.position() == 8)
    }
    check(processor.enqueue(input, 0, source) == 0)
    check(processor.dequeue(output, 0, rendered) == 0)
    pitchRejects<IllegalArgumentException> { processor.enqueue(input, 129, source) }
    pitchRejects<IllegalArgumentException> { processor.enqueue(ByteBuffer.allocate(16), 1, source) }
    pitchRejects<IllegalArgumentException> { processor.dequeue(output.asReadOnlyBuffer(), 1, rendered) }
    pitchRejects<IllegalArgumentException> { processor.configure(controls.copy(rate = 0.0)) }
    pitchRejects<IllegalArgumentException> { processor.reset(Double.NaN, 0.0) }
    processor.reset()
    input.putFloat(8, Float.NaN)
    pitchRejects<IllegalArgumentException> { processor.enqueue(input, 1, 0.0) }
    input.putFloat(8, .2f)
    processor.setTimeMap(listOf(NativeTimePitch.Segment(.75, 1.25, 0.0, 12000.0, 0.0)))
    source = 0.0; rendered = 0.0
    repeat(30) {
        source += processor.enqueue(input, 128, source)
        val count = processor.dequeue(output, 128, rendered); rendered += count
        repeat(count * 2) { check(output.getFloat(8 + it * 4).isFinite()) }
    }
    check(rendered > 0)
    pitchRejects<IllegalArgumentException> {
        processor.setTimeMap(listOf(NativeTimePitch.Segment(-1.0, 1.0, 0.0, 1.0, 0.0)))
    }
    processor.setTimeMap(emptyList())
    processor.setTimeMap(null)
    processor.close(); processor.close()
    pitchRejects<IllegalStateException> { processor.enqueue(input, 1, 0.0) }
    println("NativeTimePitch JVM/JNI PCM, map, validation and lifecycle tests passed")
}
