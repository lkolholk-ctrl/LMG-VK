package com.lmg.vk.debug

import java.util.Locale

internal class StartupTraceStats(val startedNs: Long, val seconds: Int = 16) {
    private class Cost(var count: Int = 0, var total: Long = 0, var max: Long = 0)
    private class Bucket {
        var frames = 0
        var firstDraws = 0
        var slow = 0
        var dropped = 0
        var totalMax = 0L
        var drawMax = 0L
        var layoutMax = 0L
        var syncMax = 0L
        var gpuMax = 0L
        var delayMax = 0L
        val work = linkedMapOf<String, Cost>()
    }
    private val buckets = Array(seconds) { Bucket() }

    private fun bucket(timeNs: Long): Bucket? {
        val elapsed = timeNs - startedNs
        if (elapsed < 0 || elapsed >= seconds * 1_000_000_000L) return null
        return buckets[(elapsed / 1_000_000_000L).toInt()]
    }

    @Synchronized fun work(name: String, started: Long, ended: Long) {
        val bucket = bucket(started) ?: return
        if (ended < started) return
        val cost = bucket.work.getOrPut(name) { Cost() }
        val elapsed = ended - started
        cost.count++
        cost.total += elapsed
        cost.max = maxOf(cost.max, elapsed)
    }

    @Synchronized fun frame(
        timestamp: Long, total: Long, budget: Long, draw: Long, layout: Long,
        sync: Long, gpu: Long, delay: Long, firstDraw: Boolean, dropped: Int
    ) {
        val bucket = bucket(timestamp) ?: return
        if (total < 0) return
        bucket.frames++
        if (firstDraw) bucket.firstDraws++
        else if (budget > 0 && total > budget) bucket.slow++
        bucket.dropped += dropped.coerceAtLeast(0)
        bucket.totalMax = maxOf(bucket.totalMax, total)
        bucket.drawMax = maxOf(bucket.drawMax, draw)
        bucket.layoutMax = maxOf(bucket.layoutMax, layout)
        bucket.syncMax = maxOf(bucket.syncMax, sync)
        bucket.gpuMax = maxOf(bucket.gpuMax, gpu)
        bucket.delayMax = maxOf(bucket.delayMax, delay)
    }

    @Synchronized fun report(): List<String> = buckets.mapIndexedNotNull { second, bucket ->
        if (bucket.frames == 0 && bucket.work.isEmpty()) return@mapIndexedNotNull null
        fun ms(ns: Long) = String.format(Locale.US, "%.2f", ns / 1_000_000.0)
        buildString {
            append("t=$second frames=${bucket.frames} overBudget=${bucket.slow} first=${bucket.firstDraws} dropped=${bucket.dropped}")
            append(" maxMs(total/draw/layout/sync/gpu/delay)=${ms(bucket.totalMax)}/${ms(bucket.drawMax)}/${ms(bucket.layoutMax)}/${ms(bucket.syncMax)}/${ms(bucket.gpuMax)}/${ms(bucket.delayMax)}")
            bucket.work.forEach { (name, cost) ->
                append(" $name(count/totalMs/maxMs)=${cost.count}/${ms(cost.total)}/${ms(cost.max)}")
            }
        }
    }
}
