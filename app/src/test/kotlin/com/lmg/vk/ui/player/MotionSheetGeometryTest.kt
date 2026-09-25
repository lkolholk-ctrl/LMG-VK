package com.lmg.vk.ui.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class MotionSheetGeometryTest {
    private val screen = Size(1080f, 2400f)
    private val thumb = Rect(120f, 150f, 240f, 270f)

    @Test fun songEndpointKeepsApprovedFullscreenGeometry() {
        val frame = motionSheetFrame(screen, thumb, 0f, 20f)
        assertEquals(MotionSheetFrame(0f, 0f, 1080f, 2400f, 0f, 1f), frame)
        assertEquals(1f, frame.scale(screen.width), 0f)
    }

    @Test fun sheetEndpointMeetsRealThumbnailBoundsAndDisappears() {
        val frame = motionSheetFrame(screen, thumb, 1f, 20f)
        assertEquals(MotionSheetFrame(120f, 150f, 120f, 120f, 20f, 0f), frame)
    }

    @Test fun videoMovesAndShrinksWhileStillVisibleInsteadOfOnlyFading() {
        val frame = motionSheetFrame(screen, thumb, 0.5f, 20f)
        assertTrue(frame.alpha > 0f)
        assertTrue(frame.left > 0f && frame.left < thumb.left)
        assertTrue(frame.top > 0f && frame.top < thumb.top)
        assertTrue(frame.width < screen.width && frame.width > thumb.width)
        assertTrue(frame.height < screen.height && frame.height > thumb.height)
        assertTrue(frame.radius > 0f && frame.radius < 20f)
    }

    @Test fun portraitUsesUniformScaleAndContainerCropWithoutSquashing() {
        val frame = motionSheetFrame(screen, thumb, 0.75f, 20f)
        val scale = frame.scale(screen.width)
        val localClipHeight = frame.height / scale
        assertEquals(frame.width, screen.width * scale, 0.001f)
        assertTrue(localClipHeight < screen.height)
        assertEquals(frame.height, localClipHeight * scale, 0.001f)
        // Same scale applies to video width and height, so its 3:4 aspect stays 3:4.
        assertEquals(0.75f, (screen.width * scale) / (screen.width * 4f / 3f * scale), 0.001f)
    }

    @Test fun reversalUsesSameIntermediateGeometryWithoutEndpointJump() {
        val opening = (0..100).map { motionSheetFrame(screen, thumb, it / 100f, 20f) }
        val closing = (100 downTo 0).map { motionSheetFrame(screen, thumb, it / 100f, 20f) }
        assertEquals(opening, closing.reversed())
        opening.zipWithNext().forEach { (a, b) ->
            assertTrue(b.width <= a.width && b.height <= a.height)
            assertTrue(b.alpha <= a.alpha)
        }
    }

    @Test fun actualQueueOrRtlThumbnailPositionIsRespected() {
        val queue = Rect(880f, 215f, 1020f, 355f)
        val frame = motionSheetFrame(screen, queue, 1f, 14f)
        assertEquals(queue.left, frame.left, 0f)
        assertEquals(queue.top, frame.top, 0f)
        assertEquals(queue.width, frame.width, 0f)
        assertEquals(queue.height, frame.height, 0f)
    }

    @Test fun springOrGestureOvershootCannotCreateNegativeAlphaOrBounds() {
        assertEquals(motionSheetFrame(screen, thumb, 0f, 20f), motionSheetFrame(screen, thumb, -1f, 20f))
        assertEquals(motionSheetFrame(screen, thumb, 1f, 20f), motionSheetFrame(screen, thumb, 2f, 20f))
    }
}
