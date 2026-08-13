package com.lmg.vk.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lmg.vk.ui.glass.AlbumColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue

import androidx.compose.ui.graphics.toArgb
import com.lmg.vk.debug.DebugLog

/**
 * Apple Music стиль — статичный градиентный фон из палитры обложки.
 *
 * Важно для локального JUCE-воспроизведения: фон не декодирует bitmap. Раньше тут
 * было три AlbumArtImage + blur-слоя; для локальных треков каждый слой запускал
 * MediaMetadataRetriever/loadThumbnail, и открытие FullPlayer из уведомления могло
 * дать пачку тяжёлых декодов + GPU blur на первом кадре. Это забивало main/render
 * и приводило к ANR, а аудио в это время циклично повторяло последний блок.
 */
@Composable
fun AnimatedPlayerBackground(
    albumColors: AlbumColors,
    modifier: Modifier = Modifier
) {
    val baseVibrant = rememberSaturationBoost(albumColors.vibrant)
    val baseDominant = rememberSaturationBoost(albumColors.dominant)
    val baseLightVibrant = rememberSaturationBoost(albumColors.lightVibrant)

    LaunchedEffect(albumColors) {
        DebugLog.add(
            "ART BG input dominant=${albumColors.dominant.toArgb().hex()} " +
                "vibrant=${albumColors.vibrant.toArgb().hex()} " +
                "lightVibrant=${albumColors.lightVibrant.toArgb().hex()} " +
                "muted=${albumColors.muted.toArgb().hex()} " +
                "targets(dominant=${baseDominant.toArgb().hex()}," +
                "vibrant=${baseVibrant.toArgb().hex()}," +
                "lightVibrant=${baseLightVibrant.toArgb().hex()})",
        )
    }

    val boostedVibrant by animateColorAsState(
        targetValue = baseVibrant,
        animationSpec = tween(durationMillis = 1000),
        label = "boostedVibrant"
    )
    val boostedDominant by animateColorAsState(
        targetValue = baseDominant,
        animationSpec = tween(durationMillis = 1000),
        label = "boostedDominant"
    )
    // muted (и его boosted/base пара) убран целиком: он больше не участвует в
    // фоне, а живая animateColorAsState на 1000 мс ради нечитаемого значения —
    // лишняя работа на каждой смене трека.
    val boostedLightVibrant by animateColorAsState(
        targetValue = baseLightVibrant,
        animationSpec = tween(durationMillis = 1000),
        label = "boostedLightVibrant"
    )

    // Apple Music does not join independent top/bottom gradients. Its player
    // builds one full-screen bitmap from three overlapping transformed copies
    // of the artwork, then blurs that complete field. Re-decoding and blurring
    // three artwork bitmaps here caused ANRs on local tracks, so keep the same
    // geometry with the already extracted palette: three oversized, strongly
    // overlapping fields are painted in one pass. Their transparent ends live
    // outside the viewport, therefore no stop or layer boundary can form a
    // horizontal seam in the middle of the player.
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val radius = size.maxDimension * 1.18f
                val darkBase = lerp(boostedDominant, Color.Black, 0.58f)

                drawRect(darkBase)
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to boostedLightVibrant.copy(alpha = 0.82f),
                            0.52f to boostedVibrant.copy(alpha = 0.50f),
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.18f),
                        radius = radius,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to boostedVibrant.copy(alpha = 0.68f),
                            0.58f to boostedDominant.copy(alpha = 0.44f),
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.48f),
                        radius = radius * 1.04f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to boostedDominant.copy(alpha = 0.62f),
                            0.62f to boostedVibrant.copy(alpha = 0.34f),
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.92f),
                        radius = radius * 1.08f,
                    )
                )

                // One uninterrupted readability scrim. Unlike the old
                // 0.35/0.65/0.78 stop chain, its slope never changes midway.
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.02f),
                            Color.Black.copy(alpha = 0.36f),
                        )
                    )
                )
            }
    )
}

/**
 * Готовит цвет палитры под фон плеера: усиливает насыщенность И ЯРКОСТЬ.
 * Раньше бустилась только насыщенность (value не трогался) — тёмные обложки
 * давали «тухлый» фон. Теперь поднимаем и HSV-value с нижним порогом, чтобы
 * даже тёмный кавер давал ощутимое свечение.
 */
@Composable
private fun rememberSaturationBoost(
    color: Color,
    // Снижено 2.5/1.9 → 1.6/1.3: центральный экстрактор (AlbumColorExtractor.vivid)
    // теперь сам отдаёт сочные цвета, и прежний агрессивный локальный буст поверх
    // клипал в белый/бандинг. Держим лёгкий добор + пол яркости на тёмных обложках.
    satBoost: Float = 1.6f,
    valBoost: Float = 1.3f,
    valFloor: Float = 0.38f
): Color {
    return androidx.compose.runtime.remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[1] = (hsv[1] * satBoost).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * valBoost).coerceAtLeast(valFloor).coerceIn(0f, 1f)
        androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(hsv))
    }
}

private fun Int.hex(): String = "#%08X".format(this)
