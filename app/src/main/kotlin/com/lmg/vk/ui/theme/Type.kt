package com.lmg.vk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lmg.vk.R

// ═══════════════════════════════════════════════════════════
//  VK Sans — два самостоятельных семейства с реальными весами.
// ═══════════════════════════════════════════════════════════

val VkSansText = FontFamily(
    Font(R.font.vk_sans_text_light, FontWeight.Light),
    Font(R.font.vk_sans_text_regular, FontWeight.Normal),
    Font(R.font.vk_sans_text_medium, FontWeight.Medium),
    Font(R.font.vk_sans_text_demibold, FontWeight.SemiBold),
    Font(R.font.vk_sans_text_bold, FontWeight.Bold),
)

val VkSansDisplay = FontFamily(
    Font(R.font.vk_sans_display_light, FontWeight.Light),
    Font(R.font.vk_sans_display_regular, FontWeight.Normal),
    Font(R.font.vk_sans_display_medium, FontWeight.Medium),
    Font(R.font.vk_sans_display_demibold, FontWeight.SemiBold),
    Font(R.font.vk_sans_display_bold, FontWeight.Bold),
)

/** Compatibility name for existing content/UI call sites. */
val AppFontFamily = VkSansText

// ═══════════════════════════════════════════════════════════
//  Typography — Display only for expressive large text; Text for UI/content.
// ═══════════════════════════════════════════════════════════

val LiquidTypography = Typography(
    // Display styles — large hero text
    displayLarge = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.02).sp
    ),
    displayMedium = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.02).sp
    ),
    displaySmall = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).sp
    ),

    // Headline styles — section headers
    headlineLarge = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).sp
    ),

    // Title styles — card titles, list headers
    titleLarge = TextStyle(
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).sp
    ),
    titleMedium = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).sp
    ),
    titleSmall = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.02).sp
    ),

    // Body styles — primary readable text
    bodyLarge = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).sp
    ),
    bodySmall = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.01).sp
    ),

    // Label styles — captions, badges, metadata
    labelLarge = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).sp
    ),
    labelMedium = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.01).sp
    ),
    labelSmall = TextStyle(
        fontFamily = VkSansText,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.01).sp
    )
)
