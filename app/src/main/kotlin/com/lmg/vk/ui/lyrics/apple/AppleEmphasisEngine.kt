package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Apple Music 6.5.2 Emphasis and Physics Engine.
 *
 * Implements confirmed formulas from C3463z.java, C3420h.java, and native diagnostics:
 * - Scale: lerp(1.0, 1.14, seconds - 1.0) clamped between 1s..2s
 * - Stagger: min(0.4 * dur / count, 400ms)
 * - Easing: CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
 * - Lift Spring: dampingRatio = 0.93, stiffness = 25, targetY = -2dp
 * - Center-based group neighbor displacement
 */
object AppleEmphasisEngine {

    val StandardEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val ReturnEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val ScrollEasing = CubicBezierEasing(0.4f, 0.1f, 0.0f, 1f)

    const val LIFT_TARGET_DP = -2f
    const val LIFT_DAMPING = 0.93f
    const val LIFT_STIFFNESS = 25f
    const val SHADOW_RADIUS_DP = 5f
    const val GRADIENT_FEATHER_DP = 30f

    val LiftSpringSpec = spring<Float>(
        dampingRatio = LIFT_DAMPING,
        stiffness = LIFT_STIFFNESS
    )

    fun isCoreEligible(isBackground: Boolean, durationMs: Long, charCount: Int): Boolean {
        if (isBackground) return false
        if (durationMs < 1000L) return false
        if (charCount > 7) return false
        return true
    }

    /**
     * Confirmed Apple target scale formula:
     * seconds = clamp(durationMs / 1000f, 1f, 2f)
     * scale = lerp(1.0, 1.14, seconds - 1.0)
     */
    fun calculateTargetScale(durationMs: Long): Float {
        val seconds = (durationMs / 1000f).coerceIn(1f, 2f)
        return 1f + 0.14f * (seconds - 1f)
    }

    /**
     * Confirmed Apple stagger formula:
     * stagger = min(0.4 * originalDurationMs / pieceCount, 400ms)
     */
    fun calculateStaggerMs(durationMs: Long, pieceCount: Int): Long {
        val count = pieceCount.coerceAtLeast(1)
        val raw = (0.4f * durationMs / count).roundToLong()
        return raw.coerceIn(0L, 400L)
    }

    /**
     * Confirmed Apple return phase base delay:
     * originalDuration / (pieceCount / 2.0)
     */
    fun calculateReturnBaseDelayMs(durationMs: Long, pieceCount: Int): Long {
        val count = pieceCount.coerceAtLeast(1)
        return (durationMs / (count / 2.0)).roundToLong()
    }

    /**
     * Confirmed Apple animator duration cap: min(durationMs, 3000ms)
     */
    fun calculateAnimationDurationMs(durationMs: Long): Long {
        return durationMs.coerceIn(100L, 3000L)
    }

    /**
     * Confirmed Apple Expansion:
     * expansion = ((scale - 1) * textWidth) / 2
     */
    fun calculateExpansionPx(scale: Float, baseWidthPx: Float): Float {
        return ((scale - 1f) * baseWidthPx) * 0.5f
    }

    /**
     * Confirmed Apple Center-Based Neighbor Displacement:
     * Computes signed translationX offsets from center outward to prevent syllable separation.
     */
    fun calculateAppleNeighborTranslations(
        expansionPx: FloatArray,
        isRtl: Boolean = false
    ): FloatArray {
        val size = expansionPx.size
        if (size <= 1) return FloatArray(size)

        val translations = FloatArray(size)
        val center = if (size % 2 == 1) {
            (size / 2).toFloat()
        } else {
            (size / 2.0f) - 0.5f
        }

        // Left side (from center - 0.5 downward to 0)
        var accumulatedLeft = 0f
        val leftStart = if (size % 2 == 1) (size / 2) - 1 else size / 2 - 1
        for (i in leftStart downTo 0) {
            val ownExpansion = expansionPx[i]
            val neighborExpansion = expansionPx[i + 1]
            accumulatedLeft += (ownExpansion + neighborExpansion)
            translations[i] = -accumulatedLeft * 0.5f
        }

        // Right side (from center + 0.5 upward to size - 1)
        var accumulatedRight = 0f
        val rightStart = if (size % 2 == 1) (size / 2) + 1 else size / 2
        for (i in rightStart until size) {
            val ownExpansion = expansionPx[i]
            val neighborExpansion = expansionPx[i - 1]
            accumulatedRight += (ownExpansion + neighborExpansion)
            translations[i] = accumulatedRight * 0.5f
        }

        if (isRtl) {
            for (i in 0 until size) {
                translations[i] = -translations[i]
            }
        }

        return translations
    }

    /**
     * Confirmed Apple Lower-Row Cascade Start Delay:
     * n = min(distanceFromActive, 4)
     * delay = round((25 * 0.25) * (5*n - ((n+1)*n)/2))
     *
     * Resulting progression:
     * +1 row -> 25ms
     * +2 row -> 44ms
     * +3 row -> 56ms
     * +4+ row -> 63ms
     */
    fun calculateCascadeDelayMs(distanceFromActive: Int): Long {
        if (distanceFromActive <= 0) return 0L
        val n = distanceFromActive.coerceAtMost(4)
        val formula = (25f * 0.25f) * (5f * n - ((n + 1) * n) / 2f)
        return formula.roundToLong()
    }
}
