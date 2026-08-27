package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.ui.theme.VkSansDisplay

@Composable
fun AppleKaraokePiece(
    piece: AppleLyricPiece,
    scale: Float,
    displacementX: Float,
    liftActive: Boolean,
    playbackEpoch: Long,
    fontSize: TextUnit,
    sungColor: Color,
    glowColor: Color,
    onMeasuredWidth: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lift = remember(piece.id) { Animatable(0f) }
    var baselineFraction by remember(piece.id) { mutableFloatStateOf(1f) }

    LaunchedEffect(liftActive, playbackEpoch, piece.id) {
        // Every new word/seek generation starts from Apple's explicit reset state;
        // never continue a stale spring left by the previous playback position.
        lift.snapTo(0f)
        if (liftActive) {
            lift.animateTo(
                targetValue = with(density) { AppleEmphasisEngine.LIFT_TARGET_DP.dp.toPx() },
                animationSpec = AppleEmphasisEngine.LiftSpringSpec,
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = displacementX
                translationY = lift.value
                transformOrigin = TransformOrigin(0.5f, baselineFraction)
                clip = false
            }
            .onSizeChanged { onMeasuredWidth(it.width.toFloat()) }
    ) {
        Text(
            text = piece.text,
            color = sungColor,
            fontSize = fontSize,
            fontFamily = VkSansDisplay,
            fontWeight = FontWeight.Bold,
            onTextLayout = { result ->
                if (result.size.height > 0) {
                    baselineFraction = (result.firstBaseline / result.size.height).coerceIn(0f, 1f)
                }
            },
            style = TextStyle(
                shadow = Shadow(
                    color = glowColor.copy(
                        alpha = ((scale - 1f) / 0.14f).coerceIn(0f, 1f) * 0.5f
                    ),
                    offset = Offset.Zero,
                    blurRadius = with(density) { AppleEmphasisEngine.SHADOW_RADIUS_DP.dp.toPx() },
                ),
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                ),
            ),
        )
    }
}
