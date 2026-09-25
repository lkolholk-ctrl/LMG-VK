package com.lmg.vk.ui.lyrics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.drawStableLyricGlyph
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricGlyphRasterTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fractionalLiftMovesCoverageWithoutWholePixelBaselineSteps() {
        lateinit var glyph: TextLayoutResult
        compose.setContent {
            glyph = rememberTextMeasurer().measure("H", TextStyle(
                fontFamily = LyricsFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp, textMotion = TextMotion.Animated))
        }
        compose.runOnIdle {
            val size = maxOf(glyph.size.width, glyph.size.height) + 160
            fun render(lift: Float, glow: Float = 0f): IntArray {
                val image = ImageBitmap(size, size)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image),
                    Size(size.toFloat(), size.toFloat())) {
                    withTransform({ translate(top = lift) }) {
                        drawStableLyricGlyph(glyph, Offset(60f, 60f), ColorFilter.tint(Color.White), 1f, glow)
                    }
                }
                return IntArray(size * size).also {
                    image.asAndroidBitmap().getPixels(it, 0, size, 0, 0, size, size)
                }
            }
            fun centre(pixels: IntArray): Double {
                var mass = 0.0
                var moment = 0.0
                pixels.forEachIndexed { index, color ->
                    val alpha = color ushr 24
                    mass += alpha
                    moment += alpha * (index / size).toDouble()
                }
                assertTrue("The Alpha8 mask must produce visible glyphs", mass > 0)
                return moment / mass
            }
            val initial = render(0f)
            var previous = centre(initial)
            for (step in 1..8) {
                val next = centre(render(-step * 0.25f))
                assertEquals("Quarter-pixel lift must not stick then jump", -0.25, next - previous, 0.08)
                previous = next
            }
            val glowing = render(-1f, 0.5f)
            assertTrue(glowing.sumOf { (it ushr 24).toLong() } > render(-1f).sumOf { (it ushr 24).toLong() })
            assertArrayEquals("Cached glyph must not retain lift or glow from a previous frame", initial, render(0f))
        }
    }
}
