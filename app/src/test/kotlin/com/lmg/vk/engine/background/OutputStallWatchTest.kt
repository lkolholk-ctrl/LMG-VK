package com.lmg.vk.engine.background

import org.junit.Assert.*
import org.junit.Test

class OutputStallWatchTest {
    private fun input(now: Long = 1000) = PlaybackHealthInput(now, 1, 2, 46736, 125000, 39000,
        true, AudioOutputProgress(0, 2, true, 1f, 10000, now, 1000000, false, true, now))

    @Test fun detectsOutputStallWithoutAnyFurtherMainPolls() {
        val w = OutputStallWatch();val s = input()
        assertFalse(w.observe(s,s.output,1000))
        assertTrue(w.observe(s,s.output,9000))
    }
    @Test fun outputContinuesWhileMainIsBusy() {
        val w = OutputStallWatch();val s = input()
        for (now in 1000L..15000L step 500L) {
            assertFalse(w.observe(s,s.output!!.copy(acceptedBytes=10000+now, lastAcceptedMs=now,
                positionUs=1000000+now*1000,lastPositionAdvanceMs=now),now))
        }
    }
    @Test fun drainingHardwareBufferIsRealProgress() {
        val w=OutputStallWatch();val s=input()
        for (now in 1000L..15000L step 500L) assertFalse(w.observe(s,s.output!!.copy(
            positionUs=1000000+now*1000,lastPositionAdvanceMs=now),now))
    }
    @Test fun blockedWriteCanBeReportedForStackCapture() {
        val w=OutputStallWatch();val s=input()
        assertFalse(w.observe(s,s.output,1000))
        assertTrue(w.observe(s,s.output!!.copy(inFlight=true),9000))
    }
    @Test fun noReportsOnPauseOrWithoutOutput() {
        val w=OutputStallWatch();val s=input()
        w.observe(s,s.output,1000)
        assertFalse(w.observe(s.copy(eligible=false),s.output,9000))
        assertFalse(w.observe(s,null,10000))
        assertFalse(w.observe(null,s.output,11000))
    }
    @Test fun sourceChangeRequiresSettlingAgain() {
        val w=OutputStallWatch();val s=input()
        w.observe(s,s.output,1000)
        assertFalse(w.observe(s.copy(epoch=2),s.output,9000))
    }
    @Test fun sinkReplacementRequiresSettlingAgain() {
        val w=OutputStallWatch();val s=input()
        w.observe(s,s.output,1000)
        assertFalse(w.observe(s,s.output!!.copy(epoch=4),9000))
    }
    @Test fun unavailablePositionTimestampIsNotAStall() {
        val w=OutputStallWatch();val s=input()
        w.observe(s,s.output,1000)
        assertFalse(w.observe(s,s.output!!.copy(lastPositionAdvanceMs=-1),9000))
    }
    @Test fun futureOrExpiredMainStateCannotAuthorizeWakeup() {
        val s=input()
        assertFalse(OutputStallWatch.usable(s,s.output,0))
        assertFalse(OutputStallWatch.usable(s,s.output,35001))
    }
    @Test fun recentlyRecoveredOutputIsNotStale() {
        assertFalse(OutputStallWatch.stale(input(8999).output!!,9000))
    }
    @Test fun reportingIsRateLimited() {
        val w=OutputStallWatch();val s=input()
        w.observe(s,s.output,1000)
        assertTrue(w.observe(s,s.output,9000))
        assertFalse(w.observe(s,s.output,10000))
    }
    @Test fun workRequestsAreLimitedAcrossSourceChanges() {
        val w=OutputStallWatch()
        assertTrue(w.claimWakeup(9000))
        assertFalse(w.claimWakeup(10000))
        w.observe(null,null,50000)
        assertTrue(w.claimWakeup(69000))
        assertFalse(w.claimWakeup(129000))
        assertTrue(w.claimWakeup(609001))
    }
}
