package com.lmg.vk.debug

import org.junit.Assert.*
import org.junit.Test

class StartupStallGateTest {
    @Test fun backgroundAndDelayedMonitorDoNotConsumeSamples() {
        val gate = StartupStallGate()
        repeat(20) {
            assertFalse(gate.shouldSample(2000, 1000, 100, false))
            assertFalse(gate.shouldSample(2000, 1000, 500, true))
        }
        assertTrue(gate.shouldSample(2000, 1000, 100, true))
    }

    @Test fun missingOrFreshHeartbeatIsNotAStall() {
        val gate = StartupStallGate()
        assertFalse(gate.shouldSample(2000, 0, 100, true))
        assertFalse(gate.shouldSample(2000, 1900, 100, true))
        assertTrue(gate.shouldSample(2020, 1900, 100, true))
    }

    @Test fun samplingIsThrottledAndBounded() {
        val gate = StartupStallGate()
        repeat(12) { index ->
            val now = 2000L + index * 5000
            assertTrue(gate.shouldSample(now, 1000, 100, true))
            assertFalse(gate.shouldSample(now + 100, 1000, 100, true))
        }
        assertFalse(gate.shouldSample(70_000, 1000, 100, true))
    }
}
