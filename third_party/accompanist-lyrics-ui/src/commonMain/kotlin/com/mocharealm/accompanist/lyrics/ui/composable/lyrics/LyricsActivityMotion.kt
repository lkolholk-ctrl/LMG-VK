// LMG VK addition: line activity timing recovered from Apple's karaoke highlight transitions.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

private val activityAlphaEasing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)
private val activityScaleEasing = CubicBezierEasing(0.4f, 0.1f, 0f, 1f)
private val highlightAlpha = tween<Float>(250, easing = activityAlphaEasing)
private val unhighlightAlpha = tween<Float>(350, delayMillis = 250, easing = activityAlphaEasing)
private val highlightScale = tween<Float>(500, delayMillis = 150, easing = activityScaleEasing)
private val unhighlightScale = tween<Float>(350, delayMillis = 50, easing = activityScaleEasing)

internal fun lineActivityAlphaSpec(focused: Boolean): TweenSpec<Float> =
    if (focused) highlightAlpha else unhighlightAlpha

internal fun lineActivityScaleSpec(focused: Boolean): TweenSpec<Float> =
    if (focused) highlightScale else unhighlightScale
