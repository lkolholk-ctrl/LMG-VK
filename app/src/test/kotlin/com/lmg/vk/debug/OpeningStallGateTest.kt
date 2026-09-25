package com.lmg.vk.debug

import org.junit.Assert.*
import org.junit.Test

class OpeningStallGateTest {
    @Test fun samplesShortStallsBeforeTheyFinish() {
        val gate = OpeningStallGate()
        assertFalse(gate.shouldSample(1016, 1000, 16))
        assertFalse(gate.shouldSample(1032, 1000, 16))
        assertTrue(gate.shouldSample(1048, 1000, 16))
        assertFalse(gate.shouldSample(1064, 1000, 16))
        assertTrue(gate.shouldSample(1080, 1000, 16))
        assertTrue(gate.shouldSample(1112, 1000, 16))
    }

    @Test fun recoveredHeartbeatAndDelayedObserverDoNotConsumeBudget() {
        val gate = OpeningStallGate()
        repeat(30) {
            assertFalse(gate.shouldSample(1200, 0, 16))
            assertFalse(gate.shouldSample(1200, 1000, 200))
            assertFalse(gate.shouldSample(1200, 1300, 16))
        }
        assertTrue(gate.shouldSample(1200, 1000, 16))
    }

    @Test fun eachBlockedHeartbeatHasAtMostThreeSamples() {
        val gate = OpeningStallGate()
        repeat(3) { assertTrue(gate.shouldSample(1048L + 32 * it, 1000, 16)) }
        assertFalse(gate.shouldSample(1200, 1000, 16))
        assertFalse(gate.shouldSample(1400, 1000, 16))
        assertTrue(gate.shouldSample(1548, 1500, 16))
    }

    @Test fun totalStackSamplesAreBounded() {
        val gate = OpeningStallGate()
        repeat(18) { index ->
            val posted = 1000L + index * 150
            assertTrue(gate.shouldSample(posted + 48, posted, 16))
        }
        assertFalse(gate.shouldSample(6000, 5900, 16))
    }
}
