package com.lmg.vk.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Набор общих PathInterpolator Apple. Не считать его полным набором motion primitives:
 * в lyrics renderer Apple также использует физическую spring-анимацию (например syllable lift).
 */
object AppleEasings {
    /** Обычные переходы (у Apple самый частый — CSS `ease`). */
    val Standard: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    /** Сильный ease-out — «оседание» элемента на место. */
    val EaseOut: Easing = CubicBezierEasing(1f, 0f, 0.35f, 1f)
    /** Резкий — закрытия/дисмиссы. */
    val Sharp: Easing = CubicBezierEasing(0.25f, 0f, 1f, 0.2f)
    /** Подтверждённая кривая скролла лирики Apple Music. */
    val LyricsScroll: Easing = CubicBezierEasing(0.4f, 0.1f, 0.0f, 1f)
    /** Заливка лирики (заливка слова / переход строки для legacy). */
    val Lyrics: Easing = CubicBezierEasing(0.75f, 0f, 0.25f, 1f)
}
