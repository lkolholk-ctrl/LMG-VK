package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.ui.theme.VkSansDisplay
import kotlinx.coroutines.launch

@Composable
fun AppleKaraokePiece(
    piece: AppleLyricPiece,
    isActive: Boolean,
    sungFraction: Float,
    targetScale: Float,
    staggerMs: Long,
    returnDelayMs: Long,
    animationDurationMs: Long,
    displacementX: Float,
    fontSize: TextUnit,
    baseColor: Color,
    sungColor: Color,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    val scaleAnim = remember { Animatable(1.0f) }
    val liftAnim = remember { Animatable(0.0f) }
    val shadowAnim = remember { Animatable(0.0f) }

    LaunchedEffect(isActive, piece.id) {
        if (isActive && targetScale > 1.0f) {
            launch {
                if (staggerMs > 0) kotlinx.coroutines.delay(staggerMs)
                // Attack phase
                scaleAnim.animateTo(
                    targetValue = targetScale,
                    animationSpec = tween(
                        durationMillis = animationDurationMs.toInt(),
                        easing = AppleEmphasisEngine.StandardEasing
                    )
                )
                // Return phase
                if (returnDelayMs > 0) kotlinx.coroutines.delay(returnDelayMs)
                scaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(
                        durationMillis = animationDurationMs.toInt(),
                        easing = AppleEmphasisEngine.ReturnEasing
                    )
                )
            }

            launch {
                if (staggerMs > 0) kotlinx.coroutines.delay(staggerMs)
                // Lift spring (-2dp)
                liftAnim.animateTo(
                    targetValue = AppleEmphasisEngine.LIFT_TARGET_DP,
                    animationSpec = AppleEmphasisEngine.LiftSpringSpec
                )
                liftAnim.animateTo(
                    targetValue = 0.0f,
                    animationSpec = AppleEmphasisEngine.LiftSpringSpec
                )
            }

            launch {
                if (staggerMs > 0) kotlinx.coroutines.delay(staggerMs)
                shadowAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = animationDurationMs.toInt() / 2)
                )
                shadowAnim.animateTo(
                    targetValue = 0.0f,
                    animationSpec = tween(durationMillis = animationDurationMs.toInt() / 2)
                )
            }
        } else if (!isActive) {
            scaleAnim.snapTo(1.0f)
            liftAnim.snapTo(0.0f)
            shadowAnim.snapTo(0.0f)
        }
    }

    val displayColor = if (sungFraction >= 1.0f) {
        sungColor
    } else if (sungFraction <= 0.0f) {
        baseColor
    } else {
        // Interpolate between unsung and sung
        Color(
            red = baseColor.red + (sungColor.red - baseColor.red) * sungFraction,
            green = baseColor.green + (sungColor.green - baseColor.green) * sungFraction,
            blue = baseColor.blue + (sungColor.blue - baseColor.blue) * sungFraction,
            alpha = baseColor.alpha + (sungColor.alpha - baseColor.alpha) * sungFraction
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationX = displacementX
                translationY = liftAnim.value * density
                transformOrigin = TransformOrigin(0.5f, 1.0f) // Pivot near baseline
                clip = false
                if (shadowAnim.value > 0.01f) {
                    shadowElevation = AppleEmphasisEngine.SHADOW_RADIUS_DP * density * shadowAnim.value
                    spotShadowColor = glowColor.copy(alpha = 0.6f * shadowAnim.value)
                    ambientShadowColor = glowColor.copy(alpha = 0.3f * shadowAnim.value)
                }
            }
    ) {
        Text(
            text = piece.text,
            color = displayColor,
            fontSize = fontSize,
            fontFamily = VkSansDisplay,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false
                )
            )
        )
    }
}
