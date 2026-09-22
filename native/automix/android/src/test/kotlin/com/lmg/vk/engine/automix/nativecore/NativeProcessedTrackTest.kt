package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private fun pcm(bytes: Int) = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
private fun rejects(block: () -> Unit) {
    var rejected = false
    try { block() } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected)
}
fun main() {
    val controls = NativeTimePitch.Controls(1.0, 1f, 8f, true, true)
    fun create(segments: List<NativeTimePitch.Segment>? = null) = NativeProcessedTrack(
        48000.0, 2, 256, 0, 0.0, true, controls,
        mapOf("Ga1g" to .25, "Ga3g" to 1.0, "Ga4g" to 0.0, "bypa" to 1.0),
        listOf(NativeTrackEffects.Write(8000, "Ga1g", .5)), segments,
    )
    val input = pcm(256 * 8 + 16).apply {
        for (i in 0 until 256) { putFloat(8 + i * 8, .2f); putFloat(12 + i * 8, -.1f) }
        position(8)
    }
    val output = pcm(256 * 8 + 16).apply { position(8) }
    create(emptyList()).use { stream ->
        var sent = 0
        var received = 0
        var pressure = false
        var checkedLevel = false
        var finished = false
        for (iteration in 0 until 1000) {
            while (sent < 32768) {
                val n = stream.enqueue(input, 256)
                check(input.position() == 8)
                if (n == 0) { pressure = true; break }
                sent += n
                check(stream.inputPositionFrames() == sent.toLong())
            }
            val n = stream.dequeue(output, 256, received.toDouble())
            check(output.position() == 8)
            if (received > 20000) for (i in 0 until n) {
                check(abs(output.getFloat(8 + i * 8) - .1f) < 1e-6f)
                check(abs(output.getFloat(12 + i * 8) + .05f) < 1e-6f)
                checkedLevel = true
            }
            received += n
            if (sent == 32768 && n == 0 && stream.pendingFrames() == 0) { finished = true; break }
        }
        check(pressure && checkedLevel && finished)
        val before = stream.inputPositionFrames()
        rejects { stream.enqueue(ByteBuffer.allocate(2048), 256) }
        rejects { stream.enqueue(input, 257) }
        rejects { stream.enqueue(input, 1, 16) }
        rejects { stream.dequeue(output.asReadOnlyBuffer().order(ByteOrder.nativeOrder()), 1, 0.0) }
        rejects { stream.dequeue(output, 1, Double.NaN) }
        input.putFloat(8, Float.NaN)
        rejects { stream.enqueue(input, 1) }
        input.putFloat(8, .2f)
        check(stream.inputPositionFrames() == before)
        check(stream.enqueue(null, 0) == 0)
    }
    // Mapping is owned by the native context and survives constructor temporaries.
    create(listOf(NativeTimePitch.Segment(.75, 1.25, 0.0, 32768.0, 0.0))).use { stream ->
        var received = 0
        repeat(80) {
            stream.enqueue(input, 256)
            val n = stream.dequeue(output, 256, received.toDouble())
            repeat(n * 2) { check(output.getFloat(8 + it * 4).isFinite()) }
            received += n
        }
        check(received > 0)
    }
    rejects { create(listOf(NativeTimePitch.Segment(0.0, 1.0, 0.0, 1.0, 0.0))) }
    rejects { NativeProcessedTrack(48000.0, 2, 256, 0, 0.0, true, controls,
        mapOf("RVmi" to Double.NaN)) }
    val closed = create(); closed.close(); closed.close()
    var rejectedClosed = false
    try { closed.pendingFrames() } catch (_: IllegalStateException) { rejectedClosed = true }
    check(rejectedClosed)
    println("NativeProcessedTrack JNI: source events, buffer offsets, backpressure, mapping and lifecycle passed")
}
