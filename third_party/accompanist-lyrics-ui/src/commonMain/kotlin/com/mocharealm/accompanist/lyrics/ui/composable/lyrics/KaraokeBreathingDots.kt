// LMG VK modification: audio-sampled instrumental phases in a fixed layout slot.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment

data class KaraokeBreathingDotsDefaults(
    val number: Int = 3,
    val size: Dp = 10.dp,
    val margin: Dp = 6.dp,
    val breathingDotsColor: Color = Color.White
)

@Composable
fun KaraokeBreathingDots(
    alignment: KaraokeAlignment,
    startTimeMs: Int,
    endTimeMs: Int,
    currentTimeProvider: () -> Int,
    modifier: Modifier = Modifier,
    defaults: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
    isRtl: Boolean = false
) {
    val latestPosition = rememberUpdatedState(currentTimeProvider)
    val drawPosition = remember(startTimeMs, endTimeMs) {
        derivedStateOf(structuralEqualityPolicy()) {
            latestPosition.value().coerceIn(startTimeMs, endTimeMs.coerceAtLeast(startTimeMs))
        }
    }
    val number = defaults.number.coerceIn(1, 12)
    // Keep the list's geometry stable during breathing and fading. Only drawing reads the clock.
    Box(modifier) {
        Canvas(
            Modifier.align(if (alignment == KaraokeAlignment.End) Alignment.TopEnd else Alignment.TopStart)
                .padding(vertical = 20.dp, horizontal = 16.dp)
                .size(defaults.size * number + defaults.margin * (number - 1), defaults.size)
        ) {
            val frame = waitingFrame(drawPosition.value, startTimeMs, endTimeMs, number)
            val diameter = defaults.size.toPx()
            val pitch = diameter + defaults.margin.toPx()
            val width = diameter * number + defaults.margin.toPx() * (number - 1)
            val pivot = Offset(if (alignment == KaraokeAlignment.End) width else 0f, diameter / 2f)
            scale(frame.scale, pivot = pivot) {
                repeat(number) { index ->
                    val physicalIndex = if (isRtl) number - 1 - index else index
                    drawCircle(
                        defaults.breathingDotsColor.copy(alpha = defaults.breathingDotsColor.alpha * frame.dotAlphas[index]),
                        radius = diameter / 2f,
                        center = Offset(diameter / 2f + pitch * physicalIndex, diameter / 2f)
                    )
                }
            }
        }
    }
}
