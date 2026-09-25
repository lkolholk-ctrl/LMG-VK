package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class MotionArtworkFrameDeliveryTest {
    @Test
    fun delayedUiNeverStopsTheDecoderOutput() {
        val ui = ArrayDeque<() -> Unit>()
        var presented = 0
        var latest = 0
        val delivery = MotionArtworkFrameDelivery { ui.addLast { presented = latest } }
        repeat(300) {
            latest = it + 1
            delivery.invalidate()
            assertTrue(delivery.needsFrame)
            delivery.submitted()
        }
        assertEquals(0, presented)
        ui.removeLast().invoke()
        assertEquals(300, presented)
    }

    @Test
    fun firstFrameArrivingAfterNetworkDelayDoesNotNeedAnInterfaceEvent() {
        var draws = 0
        val delivery = MotionArtworkFrameDelivery { draws++ }
        assertEquals(0, draws)
        delivery.invalidate()
        assertTrue(delivery.needsFrame)
        delivery.submitted()
        assertEquals(1, draws)
        delivery.invalidate()
        assertTrue(delivery.needsFrame)
    }

    @Test
    fun openingAndClosingAirSheetDoesNotGateTheFullPlayer() {
        var fullFrames = 0
        var airFrames = 0
        val full = MotionArtworkFrameDelivery { fullFrames++ }
        repeat(10) {
            val air = MotionArtworkFrameDelivery { airFrames++ }
            repeat(30) {
                full.invalidate()
                air.invalidate()
                assertTrue(full.needsFrame)
                assertTrue(air.needsFrame)
                full.submitted()
                air.submitted()
            }
            repeat(10) {
                full.invalidate()
                assertTrue(full.needsFrame)
                full.submitted()
            }
        }
        assertEquals(400, fullFrames)
        assertEquals(300, airFrames)
    }

    @Test
    fun severalInvalidationsCoalesceAndAnUnchangedFrameIsNotResubmitted() {
        var draws = 0
        val delivery = MotionArtworkFrameDelivery { draws++ }
        repeat(10) { delivery.invalidate() }
        delivery.submitted()
        assertEquals(1, draws)
        assertFalse(delivery.needsFrame)
    }

    @Test
    fun newSurfaceCanReceiveTheExistingFrameWithoutWaitingForAnotherDecodedFrame() {
        val old = MotionArtworkFrameDelivery {}
        old.submitted()
        assertFalse(old.needsFrame)
        var draws = 0
        val replacement = MotionArtworkFrameDelivery { draws++ }
        assertTrue(replacement.needsFrame)
        replacement.submitted()
        assertEquals(1, draws)
    }
}
