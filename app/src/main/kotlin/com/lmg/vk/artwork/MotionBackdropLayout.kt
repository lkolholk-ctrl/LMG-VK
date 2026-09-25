package com.lmg.vk.artwork

import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class MotionBackdropLayout(
    val videoFraction: Float,
    val overlapFraction: Float,
    val stripFraction: Float,
    val stripWidth: Int,
    val stripHeight: Int,
) {
    companion object {
        fun create(width: Int, height: Int, density: Float): MotionBackdropLayout {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            val videoHeight = (w * 4f / 3f).roundToInt().coerceIn(1, h)
            val reducedHeight = (videoHeight / 16f).roundToInt().coerceAtLeast(2)
            val stripHeight = (reducedHeight / 8).coerceAtLeast(2)
            return MotionBackdropLayout(
                videoFraction = videoHeight.toFloat() / h,
                overlapFraction = (150f * density).coerceIn(1f, videoHeight.toFloat()) / h,
                stripFraction = stripHeight.toFloat() / reducedHeight,
                stripWidth = (ceil(w / 80f).toInt() * 10).coerceAtLeast(2),
                stripHeight = stripHeight,
            )
        }
    }
}
