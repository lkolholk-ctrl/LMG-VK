package com.lmg.vk.ui.player

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal data class MotionSheetFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val alpha: Float,
) {
    // Scale video uniformly and crop its container, never squash a portrait frame to a square.
    fun scale(fullWidth: Float) = width / fullWidth.coerceAtLeast(1f)
}

/** t0.u1/u0: full player bounds -> actual sheet thumbnail, with simultaneous fade/corners. */
internal fun motionSheetFrame(full: Size, thumbnail: Rect, progress: Float, corner: Float): MotionSheetFrame {
    val p = progress.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * p
    return MotionSheetFrame(
        left = thumbnail.left * p,
        top = thumbnail.top * p,
        width = mix(full.width, thumbnail.width).coerceAtLeast(1f),
        height = mix(full.height, thumbnail.height).coerceAtLeast(1f),
        radius = corner * p,
        alpha = 1f - p,
    )
}

/** Clip in the unscaled layer's coordinates; TextureView/EGL buffers stay full-sized. */
internal data class MotionSheetClip(val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, height.coerceIn(0f, size.height)),
            CornerRadius(radius.coerceAtLeast(0f))))
}
