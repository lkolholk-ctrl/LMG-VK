package com.lmg.vk.ui.player

import org.junit.Assert.*
import org.junit.Test

class ArtworkBackgroundFrameScheduleTest {
    @Test fun hiddenNewArtworkWarmsAndPublishesFinalFrameBeforeSleeping() {
        val schedule = ArtworkBackgroundFrameSchedule()
        val cover = Any()
        assertTrue(schedule.needsFrame(cover, false, 0))
        schedule.rendered(0)
        assertTrue(schedule.needsFrame(cover, false, 999))
        schedule.rendered(999)
        assertTrue(schedule.needsFrame(cover, false, 1000))
        schedule.rendered(1000)
        assertFalse(schedule.needsFrame(cover, false, 1100))
        assertTrue(schedule.needsFrame(cover, true, 1100))
    }

    @Test fun closingQueueMidTrackBlendDoesNotFreezePreviousTrack() {
        val schedule = ArtworkBackgroundFrameSchedule()
        val first = Any()
        val next = Any()
        schedule.needsFrame(first, true, 0)
        schedule.rendered(1000)
        assertTrue(schedule.needsFrame(next, true, 2000))
        schedule.rendered(2200)
        assertTrue(schedule.needsFrame(next, false, 2500))
        schedule.rendered(2500)
        assertTrue(schedule.needsFrame(next, false, 3000))
        schedule.rendered(3000)
        assertFalse(schedule.needsFrame(next, false, 3100))
    }

    @Test fun switchingTracksBehindMotionPreparesNewCoverWithoutOpeningSheet() {
        val schedule = ArtworkBackgroundFrameSchedule()
        val first = Any()
        val next = Any()
        schedule.needsFrame(first, false, 0)
        schedule.rendered(1000)
        assertFalse(schedule.needsFrame(first, false, 1200))
        assertTrue(schedule.needsFrame(next, false, 1200))
        schedule.rendered(2200)
        assertFalse(schedule.needsFrame(next, false, 2300))
    }

    @Test fun rapidChangesRestartSettlementForLatestCoverIncludingMissingArtwork() {
        val schedule = ArtworkBackgroundFrameSchedule()
        schedule.needsFrame(Any(), false, 0)
        schedule.rendered(500)
        assertTrue(schedule.needsFrame(null, false, 500))
        schedule.rendered(1000)
        assertTrue(schedule.needsFrame(null, false, 1200))
        schedule.rendered(1500)
        assertFalse(schedule.needsFrame(null, false, 1500))
    }
}
