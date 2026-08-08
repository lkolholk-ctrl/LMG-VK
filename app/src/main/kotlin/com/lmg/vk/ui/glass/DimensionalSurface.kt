package com.lmg.vk.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Объёмная поверхность — плоская заливка заменяется мягким «куполом» света.
 *
 * ПРИЁМ. Взят из Appica UI (MIT, appica.dev/ui) — там объём делается не 3D-сценой,
 * а радиальным градиентом со СМЕЩЁННЫМ вверх центром на псевдоэлементе:
 *
 *     radial-gradient(138% 78% at 52% 50%, var(--primary) 0%, var(--primary-muted) 80%)
 *
 * Глаз читает такой градиент как подсветку сверху, то есть как выпуклость. Ни
 * WebGL, ни шейдеров, ни `RuntimeShader` (который потребовал бы Android 13+ при
 * нашем minSdk 29) для этого не нужно — только две кисти на слой.
 *
 * ЧТО ЗДЕСЬ СВОЁ. В CSS хватает одного градиента, потому что рядом работает
 * `box-shadow` с несколькими слоями. У нас вместо него:
 *  - центр смещён в верхнюю треть (0.5, 0.35) — на вытянутых карточках
 *    оригинальные 50% дают «пятно» посередине вместо купола;
 *  - радиус считается от БОЛЬШЕЙ стороны, иначе на широкой карточке градиент
 *    сжимается в полосу;
 *  - краевой блик (верхняя кромка светлее нижней) — в вебе эту работу делает
 *    inset-тень, у Compose её нет, рисуем штрихом по контуру.
 *
 * ПОЧЕМУ НЕ ГОТОВАЯ БИБЛИОТЕКА. Appica UI — это React + Tailwind + Base UI, для
 * веба. Затащить её в Compose можно только через WebView, что убило бы жесты,
 * плавность скролла и батарею. Берётся приём, не код.
 *
 * Нажатие здесь НЕ обрабатывается: за него уже отвечает [liquidClickable] с
 * пружиной из `LiquidMotion`. Дублировать его вторым механизмом означало бы два
 * несогласованных отклика на одно касание.
 */

/** Насколько верх поверхности светлее базового цвета. */
private const val HIGHLIGHT_DARK = 0.055f
private const val HIGHLIGHT_LIGHT = 0.065f

/** Насколько низ темнее — «уход в тень». */
private const val SHADE_DARK = 0.30f
private const val SHADE_LIGHT = 0.045f

/** Кромка: верхняя светлая линия и нижняя тёмная. */
private const val EDGE_TOP_DARK = 0.10f
private const val EDGE_TOP_LIGHT = 0.085f
private const val EDGE_BOTTOM_DARK = 0.04f
private const val EDGE_BOTTOM_LIGHT = 0.02f

/**
 * Купол света вместо плоской заливки [base].
 *
 * Ставить ПОСЛЕ `clip(...)`: кисть заливает весь слой, и обрезка формы должна
 * быть уже применена, иначе градиент вылезет за скругления.
 *
 * @param base базовый цвет поверхности (тот, что раньше уходил в `background`)
 * @param edge рисовать ли световую кромку по контуру
 * @param cornerRadius радиус для кромки — должен совпадать с `clip`, иначе
 *   штрих ляжет по прямоугольнику поверх скруглённой формы
 */
fun Modifier.dimensionalSurface(
    base: Color,
    isDark: Boolean,
    edge: Boolean = true,
    cornerRadius: Dp = 0.dp,
): Modifier = this.drawWithCache {
    // Верх светлее, низ темнее — направление света сверху, как в физическом мире.
    val top = base.blend(Color.White, if (isDark) HIGHLIGHT_DARK else HIGHLIGHT_LIGHT)
    val bottom = base.blend(Color.Black, if (isDark) SHADE_DARK else SHADE_LIGHT)

    // Радиус от БОЛЬШЕЙ стороны: иначе на широкой карточке купол сплющивается.
    val radius = maxOf(size.width, size.height) * 0.85f
    val dome = Brush.radialGradient(
        colors = listOf(top, bottom),
        center = Offset(size.width * 0.5f, size.height * 0.35f),
        radius = radius,
    )

    val edgeBrush = if (edge) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) EDGE_TOP_DARK else EDGE_TOP_LIGHT),
                Color.Transparent,
                Color.Black.copy(alpha = if (isDark) EDGE_BOTTOM_DARK else EDGE_BOTTOM_LIGHT),
            ),
        )
    } else null

    val px = cornerRadius.toPx()

    onDrawBehind {
        drawRoundRect(brush = dome, cornerRadius = CornerRadius(px, px))
        edgeBrush?.let {
            // Штрих внутрь на половину толщины: центрированный по контуру, он
            // наполовину срезался бы обрезкой формы, и кромка выглядела бы вдвое
            // тоньше задуманного.
            val w = 1f
            drawRoundRect(
                brush = it,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(px, px),
                style = Stroke(width = w),
            )
        }
    }
}

/**
 * Смешать два цвета. [amount] 0 = [this], 1 = [other].
 *
 * Своя функция, а не `lerp` из Compose: тот работает в sRGB и на тёмных
 * поверхностях даёт заметный серый провал в середине перехода.
 */
private fun Color.blend(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * a,
        green = green + (other.green - green) * a,
        blue = blue + (other.blue - blue) * a,
        alpha = alpha,
    )
}
