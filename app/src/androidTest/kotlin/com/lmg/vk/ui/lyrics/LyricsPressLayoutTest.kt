package com.lmg.vk.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsLineItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LyricsPressLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pressAndReleaseDoNotRemeasureOrRecomposeTheLine() {
        val measurements = AtomicInteger()
        val compositions = AtomicInteger()
        var clicks = 0
        var longClicks = 0
        compose.setContent {
            LyricsLineItem(
                isFocused = true,
                isRightAligned = false,
                onLineClicked = { clicks++ },
                onLinePressed = { longClicks++ },
                blurRadius = { 0f },
                modifier = Modifier.width(280.dp).testTag("row").layout { measurable, constraints ->
                    measurements.incrementAndGet()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
            ) {
                SideEffect { compositions.incrementAndGet() }
                Box(Modifier.width(200.dp).height(64.dp).background(Color.White))
            }
        }
        compose.waitForIdle()
        val baselineMeasures = measurements.get()
        val baselineCompositions = compositions.get()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("row").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("row").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0, longClicks)
            assertEquals(baselineMeasures, measurements.get())
            assertEquals(baselineCompositions, compositions.get())
        }
        compose.onNodeWithTag("row").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("row").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0, longClicks)
            assertEquals(baselineMeasures, measurements.get())
            assertEquals(baselineCompositions, compositions.get())
        }
    }
}
