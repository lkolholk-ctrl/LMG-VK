package com.lmg.vk.ui.lyrics.apple

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp

/** Spatial DST_IN karaoke mask with Apple's confirmed 30dp feather. */
fun Modifier.appleKaraokeGradient(
    sungFraction: Float,
    unsungAlpha: Float,
    isRtl: Boolean,
): Modifier = drawWithCache {
    val progress = sungFraction.coerceIn(0f, 1f)
    val dim = unsungAlpha.coerceIn(0f, 1f)
    val featherPx = AppleEmphasisEngine.GRADIENT_FEATHER_DP.dp.toPx()
    val layerPaint = Paint()
    onDrawWithContent {
        if (progress >= 1f) {
            drawContent()
            return@onDrawWithContent
        }

        // Apple uses saveLayer + DST_IN. Extend the layer by the confirmed feather
        // so emphasis scale, lift and the 5dp shadow are not clipped by the mask.
        drawContext.canvas.saveLayer(
            Rect(-featherPx, -featherPx, size.width + featherPx, size.height + featherPx),
            layerPaint,
        )
        drawContent()
        when {
            progress <= 0f -> drawRect(
                Color.White.copy(alpha = dim),
                blendMode = BlendMode.DstIn,
            )
            else -> {
                val feather = (featherPx / size.width.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                val brush = if (!isRtl) {
                    val featherStart = (progress - feather).coerceAtLeast(0f)
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.White,
                            featherStart to Color.White,
                            progress to Color.White.copy(alpha = dim),
                            1f to Color.White.copy(alpha = dim),
                        )
                    )
                } else {
                    val boundary = 1f - progress
                    val featherEnd = (boundary + feather).coerceAtMost(1f)
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = dim),
                            boundary to Color.White.copy(alpha = dim),
                            featherEnd to Color.White,
                            1f to Color.White,
                        )
                    )
                }
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
        }
        drawContext.canvas.restore()
    }
}
