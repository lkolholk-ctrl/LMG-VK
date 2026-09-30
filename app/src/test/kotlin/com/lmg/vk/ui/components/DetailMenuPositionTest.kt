package com.lmg.vk.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailMenuPositionTest {
    @Test fun menuStaysInsidePhoneForButtonsAtEveryEdge() {
        val window = IntSize(360, 740)
        val menu = IntSize(280, 380)
        for (x in listOf(0f, 150f, 316f)) for (y in listOf(24f, 340f, 680f)) {
            val p = detailMenuPosition(Rect(x, y, x + 44, y + 44), window, menu, 12)
            assertTrue(p.x >= 12 && p.x + menu.width <= window.width - 12)
            assertTrue(p.y >= 12 && p.y + menu.height <= window.height - 12)
        }
    }
    @Test fun usesSpaceBelowTopButtonAndAboveBottomButton() {
        val window = IntSize(360, 740)
        val menu = IntSize(280, 240)
        val top = Rect(304f, 60f, 348f, 104f)
        val bottom = Rect(304f, 620f, 348f, 664f)
        assertTrue(detailMenuPosition(top, window, menu, 12).y >= top.bottom)
        assertTrue(detailMenuPosition(bottom, window, menu, 12).y + menu.height <= bottom.top)
    }
    @Test fun shortLandscapeWindowDoesNotProduceNegativeCoordinates() {
        val p = detailMenuPosition(Rect(680f, 230f, 724f, 274f), IntSize(740, 320), IntSize(280, 296), 12)
        assertTrue(p.x >= 12 && p.y == 12)
    }
    @Test fun popupCoordinatesAccountForStatusBarAndWindowOrigin() {
        val anchor = Rect(304f, 460f, 348f, 504f)
        val converted = detailAnchorInPopup(anchor, androidx.compose.ui.geometry.Offset(0f, 24f), androidx.compose.ui.geometry.Offset(0f, 48f))
        org.junit.Assert.assertEquals(Rect(304f, 436f, 348f, 480f), converted)
    }

}
