package com.lmg.vk.debug

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StartupTraceStatsTest {
    private val start = 10_000_000_000L

    @Test fun bucketsUseFrameTimestampsAndExcludeEventsOutsideCapture() {
        val stats = StartupTraceStats(start, 2)
        fun frame(offset: Long) = stats.frame(start + offset, 20_000_000, 16_000_000,
            3_000_000, 2_000_000, 1_000_000, 4_000_000, 5_000_000, false, 0)
        frame(-1)
        frame(999_999_999)
        frame(1_000_000_000)
        frame(2_000_000_000)
        val lines = stats.report()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("t=0 frames=1 overBudget=1"))
        assertTrue(lines[1].startsWith("t=1 frames=1 overBudget=1"))
        assertTrue(lines[0].contains("20.00/3.00/2.00/1.00/4.00/5.00"))
    }

    @Test fun initialDrawIsReportedSeparatelyAndDroppedCallbacksAreVisible() {
        val stats = StartupTraceStats(start)
        stats.frame(start, 100_000_000, 16_000_000, 0, 0, 0, -1, 0, true, 3)
        assertTrue(stats.report().single().contains("frames=1 overBudget=0 first=1 dropped=3"))
    }

    @Test fun workKeepsUiAndWorkerCostsSeparateWithoutNegativeDurations() {
        val stats = StartupTraceStats(start)
        stats.work("measure.ui", start, start + 5_000_000)
        stats.work("measure.ui", start, start + 9_000_000)
        stats.work("measure.worker", start, start + 20_000_000)
        stats.work("invalid", start, start - 1)
        val report = stats.report().single()
        assertTrue(report.contains("measure.ui(count/totalMs/maxMs)=2/14.00/9.00"))
        assertTrue(report.contains("measure.worker(count/totalMs/maxMs)=1/20.00/20.00"))
        assertFalse(report.contains("invalid"))
    }

    @Test fun concurrentCallbacksDoNotLoseSamples() {
        val stats = StartupTraceStats(start)
        val pool = Executors.newFixedThreadPool(4)
        repeat(1000) { pool.submit { stats.work("measure.worker", start, start + 1000) } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue(stats.report().single().contains("measure.worker(count/totalMs/maxMs)=1000/1.00/0.00"))
    }
}
