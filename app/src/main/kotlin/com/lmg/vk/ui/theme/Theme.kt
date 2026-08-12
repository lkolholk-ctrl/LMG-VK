package com.lmg.vk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

// ═══════════════════════════════════════════════════════════
//  Liquid Colors — все цвета приложения
// ═══════════════════════════════════════════════════════════

@Immutable
data class LiquidColors(
    val isDark: Boolean,
    val screenBackground: Brush,
    val settingsBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val divider: Color,
    val cardSurface: Color,
    /** Единый акцент приложения (бледно-зелёный). Селекторы/галочки/кнопки. */
    val accent: Color,
    val accentRed: Color,
    val accentGreen: Color,
    val iconDefault: Color,
    val iconMuted: Color,
    val sectionLabel: Color,
    val searchFieldBg: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val bottomBarTint: Color,
    val miniPlayerTint: Color,
    val miniPlayerBorder: Color
)

private val DarkLiquidColors = LiquidColors(
    isDark = true,
    screenBackground = Brush.verticalGradient(
        colors = listOf(
            Color.Black,
            Color.Black,
            Color.Black,
            Color.Black
        )
    ),
    settingsBackground = Color.Black,
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.55f),
    textTertiary = Color.White.copy(alpha = 0.30f),
    glassTint = Color.White.copy(alpha = 0.04f),
    glassBorder = Color.White.copy(alpha = 0.20f),
    divider = Color.White.copy(alpha = 0.06f),
    // Серые подложки-карточки как в настройках (были Transparent — секции
    // AudioFx/библиотеки/тегов рисовались «голыми», полевой фидбек).
    cardSurface = Color(0xFF1C1C1E),
    accent = Color(0xFF88C088),
    accentRed = Color(0xFFFC3C44),
    accentGreen = Color(0xFF34C759),
    iconDefault = Color.White,
    iconMuted = Color.White.copy(alpha = 0.40f),
    sectionLabel = Color.White.copy(alpha = 0.45f),
    searchFieldBg = Color.White.copy(alpha = 0.08f),
    chipBg = Color.White.copy(alpha = 0.04f),
    chipBorder = Color.White.copy(alpha = 0.08f),
    bottomBarTint = Color(0xFF121212),
    miniPlayerTint = Color.White.copy(alpha = 0.01f),
    miniPlayerBorder = Color.White.copy(alpha = 0.22f)
)

private val LightLiquidColors = LiquidColors(
    isDark = false,
    screenBackground = Brush.verticalGradient(
        colors = listOf(
            Color.White,
            Color.White,
            Color.White,
            Color.White
        )
    ),
    settingsBackground = Color.White,
    textPrimary = Color.Black,
    textSecondary = Color.Black.copy(alpha = 0.55f),
    textTertiary = Color.Black.copy(alpha = 0.30f),
    glassTint = Color.Black.copy(alpha = 0.03f),
    glassBorder = Color.Black.copy(alpha = 0.08f),
    divider = Color.Black.copy(alpha = 0.06f),
    cardSurface = Color(0xFFF2F2F7),
    accent = Color(0xFF7FB77E),
    accentRed = Color(0xFFFC3C44),
    accentGreen = Color(0xFF34C759),
    iconDefault = Color.Black,
    iconMuted = Color.Black.copy(alpha = 0.40f),
    sectionLabel = Color.Black.copy(alpha = 0.45f),
    searchFieldBg = Color.Black.copy(alpha = 0.05f),
    chipBg = Color.Black.copy(alpha = 0.04f),
    chipBorder = Color.Black.copy(alpha = 0.06f),
    bottomBarTint = Color(0xFFF2F2F7),
    miniPlayerTint = Color.Black.copy(alpha = 0.03f),
    miniPlayerBorder = Color.Black.copy(alpha = 0.08f)
)

// ── Высококонтрастные варианты (тумблер «Увеличение контрастности») ──
// Меньше прозрачности у стекла/разделителей, ярче вторичный/третичный текст,
// плотнее обводки — читаемость в glassmorphism заметно выше.
private val DarkLiquidColorsHighContrast = DarkLiquidColors.copy(
    textSecondary = Color.White.copy(alpha = 0.82f),
    textTertiary = Color.White.copy(alpha = 0.55f),
    glassTint = Color.White.copy(alpha = 0.14f),
    glassBorder = Color.White.copy(alpha = 0.42f),
    divider = Color.White.copy(alpha = 0.16f),
    iconMuted = Color.White.copy(alpha = 0.70f),
    sectionLabel = Color.White.copy(alpha = 0.75f),
    searchFieldBg = Color.White.copy(alpha = 0.16f),
    chipBg = Color.White.copy(alpha = 0.12f),
    chipBorder = Color.White.copy(alpha = 0.22f),
    miniPlayerTint = Color.White.copy(alpha = 0.10f),
    miniPlayerBorder = Color.White.copy(alpha = 0.42f)
)

private val LightLiquidColorsHighContrast = LightLiquidColors.copy(
    textSecondary = Color.Black.copy(alpha = 0.78f),
    textTertiary = Color.Black.copy(alpha = 0.52f),
    glassTint = Color.Black.copy(alpha = 0.10f),
    glassBorder = Color.Black.copy(alpha = 0.22f),
    divider = Color.Black.copy(alpha = 0.14f),
    iconMuted = Color.Black.copy(alpha = 0.68f),
    sectionLabel = Color.Black.copy(alpha = 0.72f),
    searchFieldBg = Color.Black.copy(alpha = 0.12f),
    chipBg = Color.Black.copy(alpha = 0.10f),
    chipBorder = Color.Black.copy(alpha = 0.18f),
    miniPlayerTint = Color.Black.copy(alpha = 0.10f),
    miniPlayerBorder = Color.Black.copy(alpha = 0.22f)
)

val LocalLiquidColors = staticCompositionLocalOf { DarkLiquidColors }

// Shortcut
object LiquidTheme {
    val colors: LiquidColors
        @Composable get() = LocalLiquidColors.current
}

// ═══════════════════════════════════════════════════════════
//  Settings background as Brush (gradient)
// ═══════════════════════════════════════════════════════════

val DarkSettingsGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF22222E),
        Color(0xFF1A1A24),
        Color(0xFF14141C),
        Color(0xFF101018)
    )
)

val LightSettingsGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF5F5FA),
        Color(0xFFF0F0F5),
        Color(0xFFEBEBF0),
        Color(0xFFE5E5EA)
    )
)

// ═══════════════════════════════════════════════════════════
//  Material schemes
// ═══════════════════════════════════════════════════════════

private val LiquidDarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB8C2D3),
    background = Color(0xFF080A0F),
    surface = Color(0xFF10141D)
)

private val LiquidLightScheme = lightColorScheme(
    primary = Color(0xFF000000),
    secondary = Color(0xFF4A5568),
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF)
)

/** Перенос системной Material You палитры в собственные поверхности LMG VK. */
private fun ColorScheme.toDynamicLiquidColors(
    isDark: Boolean,
    highContrast: Boolean,
): LiquidColors {
    val base = when {
        isDark && highContrast -> DarkLiquidColorsHighContrast
        isDark -> DarkLiquidColors
        highContrast -> LightLiquidColorsHighContrast
        else -> LightLiquidColors
    }
    val secondaryAlpha = if (highContrast) 0.88f else 0.72f
    val tertiaryAlpha = if (highContrast) 0.68f else 0.50f
    val borderAlpha = if (highContrast) 0.42f else 0.22f
    val surfaceAlpha = if (highContrast) 0.92f else 0.78f

    return base.copy(
        screenBackground = Brush.verticalGradient(List(4) { background }),
        settingsBackground = background,
        textPrimary = onBackground,
        textSecondary = onSurfaceVariant.copy(alpha = secondaryAlpha),
        textTertiary = onSurfaceVariant.copy(alpha = tertiaryAlpha),
        glassTint = primary.copy(alpha = if (highContrast) 0.14f else 0.06f),
        glassBorder = outline.copy(alpha = borderAlpha),
        divider = outlineVariant.copy(alpha = if (highContrast) 0.72f else 0.48f),
        cardSurface = surfaceVariant.copy(alpha = surfaceAlpha),
        // В тёмной Material You палитре primary обычно очень светлый. Немного
        // углубляем его к onPrimary: так сохраняется оттенок обоев и остаётся
        // читаемым белый контент существующих акцентных кнопок LMG VK.
        accent = if (isDark) lerp(primary, onPrimary, 0.38f) else primary,
        accentRed = error,
        iconDefault = onSurface,
        iconMuted = onSurfaceVariant.copy(alpha = if (highContrast) 0.78f else 0.58f),
        sectionLabel = onSurfaceVariant.copy(alpha = if (highContrast) 0.82f else 0.62f),
        searchFieldBg = surfaceVariant.copy(alpha = if (highContrast) 0.92f else 0.72f),
        chipBg = primary.copy(alpha = if (highContrast) 0.14f else 0.07f),
        chipBorder = outline.copy(alpha = if (highContrast) 0.30f else 0.16f),
        bottomBarTint = surface,
        miniPlayerTint = primary.copy(alpha = if (highContrast) 0.12f else 0.06f),
        miniPlayerBorder = outline.copy(alpha = borderAlpha),
    )
}

/**
 * @param themeMode 0=Auto (Dynamic Color), 1=Dark, 2=Light
 * @param highContrast включить высококонтрастную палитру (меньше прозрачности).
 */
@Composable
fun LiquidMusicGlassTheme(
    themeMode: Int = 0,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (themeMode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val useDynamicColor = themeMode == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val materialScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        isDark -> LiquidDarkScheme
        else -> LiquidLightScheme
    }
    val liquidColors = if (useDynamicColor) {
        materialScheme.toDynamicLiquidColors(isDark = isDark, highContrast = highContrast)
    } else if (isDark) {
        if (highContrast) DarkLiquidColorsHighContrast else DarkLiquidColors
    } else {
        if (highContrast) LightLiquidColorsHighContrast else LightLiquidColors
    }

    CompositionLocalProvider(
        LocalLiquidColors provides liquidColors
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = LiquidTypography
        ) {
            // Bare Text() is UI/content text, so it inherits VK Sans Text.
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = VkSansText),
                content = content
            )
        }
    }
}

/**
 * Принудительно ТЁМНЫЙ контент независимо от выбранной темы приложения. Wave и
 * фулл-плеер всегда тёмные — их эффекты (аура/дым/AGSL) рассчитаны на тёмный фон.
 * Тема приложения (Светлая/Тёмная/Авто) меняет только контентные экраны.
 */
@Composable
fun ForceDarkContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLiquidColors provides DarkLiquidColors
    ) {
        MaterialTheme(
            colorScheme = LiquidDarkScheme,
            typography = LiquidTypography
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = VkSansText),
                content = content
            )
        }
    }
}
