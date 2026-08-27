package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Apple Music 6.5.2 Instrumental / Interlude 3-Dots Animation.
 *
 * Implements confirmed Apple dimensions and keyframes:
 * - 3 dots
 * - Dot size: 10dp
 * - Horizontal spacing/margin: 6dp
 * - Keyframes: scale 1.0 -> 1.2 in 750ms (0.25, 0.1, 0.25, 1), then scale 1.2 -> 0.5 + alpha 0 in 250ms (0.25, 0, 1, 0.2)
 */
@Composable
fun AppleInterlude(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.85f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AppleInterludeTransition")

    val expandEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val collapseEasing = CubicBezierEasing(0.25f, 0.0f, 1.0f, 0.2f)

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                1.0f at 0
                1.2f at 750 using expandEasing
                0.5f at 1000 using collapseEasing
                1.0f at 1400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot1Scale"
    )

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.85f at 0
                0.95f at 750
                0.20f at 1000 using collapseEasing
                0.85f at 1400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot1Alpha"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                1.0f at 200
                1.2f at 950 using expandEasing
                0.5f at 1200 using collapseEasing
                1.0f at 1400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot2Scale"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.85f at 200
                0.95f at 950
                0.20f at 1200 using collapseEasing
                0.85f at 1400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot2Alpha"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                1.0f at 400
                1.2f at 1150 using expandEasing
                0.5f at 1400 using collapseEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot3Scale"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.85f at 400
                0.95f at 1150
                0.20f at 1400 using collapseEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot3Alpha"
    )

    Row(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = dot1Scale
                    scaleY = dot1Scale
                    alpha = dot1Alpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = dot2Scale
                    scaleY = dot2Scale
                    alpha = dot2Alpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = dot3Scale
                    scaleY = dot3Scale
                    alpha = dot3Alpha
                }
                .clip(CircleShape)
                .background(color)
        )
    }
}
