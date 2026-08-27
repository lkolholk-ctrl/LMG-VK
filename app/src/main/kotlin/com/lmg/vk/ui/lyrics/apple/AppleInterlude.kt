package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppleInterlude(
    eventKey: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.85f),
) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val expand = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) }
    val collapse = remember { CubicBezierEasing(0.25f, 0f, 1f, 0.2f) }

    LaunchedEffect(eventKey) {
        scale.snapTo(1f)
        alpha.snapTo(1f)
        scale.animateTo(1.2f, tween(750, easing = expand))
        coroutineScope {
            launch { scale.animateTo(0.5f, tween(250, easing = collapse)) }
            launch { alpha.animateTo(0f, tween(250, easing = collapse)) }
        }
    }

    Row(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
