package com.lmg.vk.ui.lyrics

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lmg.vk.R

// Select the variable font's actual ExtraBold outlines, rather than synthetic bold.
@OptIn(ExperimentalTextApi::class)
internal val LyricsFontFamily = FontFamily(
    Font(
        resId = R.font.golos_text,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)
