package com.lmg.vk.engine.automix.nativecore

import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun direct(size: Int) = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

fun main() {
    val controls = NativeTimePitch.Controls(1.0, 1f, 8f, true, true)
    fun track() = NativeProcessedTrack(48000.0, 2, 127, 0, 300.0, true, controls,
        mapOf("Ga1g" to 1.0, "Ga3g" to 1.0, "Ga4g" to 0.0, "bypa" to 1.0))
    for (encoding in NativeTrackPcmQueue.Encoding.entries) {
        NativeTrackPcmQueue(track(), 2, 127, 300.0).use { queue ->
            track().use { reference ->
                val baseline = direct(127 * 8)
                val expected = direct(127 * 8)
                var sourceFrame = 0
                var outputFrame = 300.0
                var totalOutput = 0
                repeat(400) { block ->
                    val frames = 1 + block % 127
                    val stride = encoding.bytesPerSample * 2
                    // Read-only, heap-backed input with an unaligned byte offset.
                    val bytes = ByteBuffer.allocate(frames * stride + 7).order(ByteOrder.nativeOrder())
                    baseline.clear(); bytes.position(3)
                    repeat(frames * 2) { sample ->
                        val integer = ((sourceFrame * 17 + sample * 37) % 65536 - 32768).toShort()
                        val value = integer.toFloat() / 32768f
                        baseline.putFloat(value)
                        when (encoding) {
                            NativeTrackPcmQueue.Encoding.PCM16 -> bytes.putShort(integer)
                            NativeTrackPcmQueue.Encoding.FLOAT32 -> bytes.putFloat(value)
                        }
                    }
                    sourceFrame += frames
                    baseline.flip(); bytes.limit(bytes.position()); bytes.position(3)
                    val input = bytes.asReadOnlyBuffer() // Deliberately BIG_ENDIAN metadata.
                    val limit = input.limit()
                    val accepted = queue.offer(input, encoding)
                    val expectedAccepted = reference.enqueue(baseline, frames)
                    check(accepted == expectedAccepted)
                    check(input.position() == 3 + accepted * stride && input.limit() == limit)
                    check(input.order() == ByteOrder.BIG_ENDIAN)
                    val output = queue.pull()
                    val count = reference.dequeue(expected, 127, outputFrame)
                    check(output.remaining() == count * 8)
                    check(queue.outputStartFrame == outputFrame)
                    repeat(count * 2) { check(output.getInt(it * 4) == expected.getInt(it * 4)) }
                    if (count > 0) {
                        val position = input.position()
                        output.position(1)
                        check(queue.pull() === output && output.position() == 1)
                        check(queue.offer(input, encoding) == 0 && input.position() == position)
                        output.position(output.limit())
                    }
                    outputFrame += count; totalOutput += count
                }
                check(totalOutput > 10000)
            }
        }
    }
    val queue = NativeTrackPcmQueue(track(), 2, 127, 300.0)
    val bad = direct(8).apply { putFloat(Float.NaN); putFloat(0f); flip() }
    var rejected = false
    try { queue.offer(bad, NativeTrackPcmQueue.Encoding.FLOAT32) }
    catch (_: IllegalArgumentException) { rejected = true }
    check(rejected && bad.position() == 0)
    queue.close(); queue.close()
    rejected = false
    try { queue.pull() } catch (_: IllegalStateException) { rejected = true }
    check(rejected)
    println("PCM queue: PCM16/float JNI output matches direct native processing; partial downstream reads, offsets and lifecycle passed")
}
