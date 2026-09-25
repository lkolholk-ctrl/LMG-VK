// Modified for LMG VK: layer-only activity transitions and compositing only when required.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun LyricsLineItem(
    isFocused: Boolean,
    isRightAligned: Boolean,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
    blurRadius: () -> Float,
    modifier: Modifier = Modifier,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.4f,
    blendMode: BlendMode = BlendMode.SrcOver,
    isInteractive: Boolean = true,
    content: @Composable () -> Unit
) {
    val scaleState by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.98f,
        animationSpec = lineActivityScaleSpec(isFocused),
        label = "scale"
    )

    val alphaState by animateFloatAsState(
        targetValue = if (isFocused) activeAlpha else inactiveAlpha,
        animationSpec = lineActivityAlphaSpec(isFocused),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scaleState
                scaleY = scaleState
                alpha = alphaState
                transformOrigin = TransformOrigin(if (isRightAligned) 1f else 0f, 1f)
                this.blendMode = blendMode
                // Preserve group opacity for overlapping glyph shadows; opaque rows need no buffer.
                compositingStrategy = CompositingStrategy.Auto

                val radius = blurRadius()
                renderEffect = if (radius > 0f) {
                    BlurEffect(
                        radiusX = radius,
                        radiusY = radius,
                        edgeTreatment = TileMode.Clamp
                    )
                } else null
            }
            .then(
                if (isInteractive) Modifier.clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        interactionSource = null,
                        indication = if (isRightAligned) LyricsPressIndication.End else LyricsPressIndication.Start,
                        onClick = onLineClicked,
                        onLongClick = onLinePressed
                    )
                else Modifier
            )

    ) {
        content()
    }
}
