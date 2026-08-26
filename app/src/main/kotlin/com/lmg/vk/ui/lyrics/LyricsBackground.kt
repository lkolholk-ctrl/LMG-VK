package com.lmg.vk.ui.lyrics

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors

@Composable
fun LyricsBackground(
    albumArtUri: Uri?,
    coverUrl: String? = null,
    audioFileUri: Uri? = null,
    albumId: Long = -1L,
    albumColors: AlbumColors,
    saturationBoost: Float = LyricsTimeProcessor.SATURATION_BOOST,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = boostedCoverColor(albumColors.vibrant),
        animationSpec = tween(durationMillis = 1000),
        label = "lyricsBgColor"
    )

    data class ArtState(
        val albumArtUri: Uri?,
        val coverUrl: String?,
        val audioFileUri: Uri?,
        val albumId: Long
    )
    val artState = remember(albumArtUri, coverUrl, audioFileUri, albumId) {
        ArtState(albumArtUri, coverUrl, audioFileUri, albumId)
    }

    val saturatedFilter = remember {
        val am = android.graphics.ColorMatrix().apply { setSaturation(2.5f) }
        ColorFilter.colorMatrix(ColorMatrix(am.getArray()))
    }
    val degraded = com.lmg.vk.ui.PerfMonitor.degraded
    val bgBlur = (if (degraded) 6 else LyricsTimeProcessor.BACKGROUND_BLUR_DP).dp

    val spin = rememberInfiniteTransition(label = "lyricsArtSpin")
    val rotA by spin.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 120_000, easing = LinearEasing)),
        label = "rotA"
    )
    val rotB by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 90_000, easing = LinearEasing)),
        label = "rotB"
    )
    val rotC by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 70_000, easing = LinearEasing)),
        label = "rotC"
    )

    @Composable
    fun rotatingArt(angle: () -> Float) {
        AlbumArtImage(
            uri = artState.albumArtUri,
            coverUrl = artState.coverUrl,
            audioFileUri = artState.audioFileUri,
            albumId = artState.albumId,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = saturatedFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.9f
                    scaleY = 1.9f
                    rotationZ = angle()
                }
        )
    }

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        Crossfade(
            targetState = artState,
            animationSpec = tween(durationMillis = 1000),
            modifier = Modifier.fillMaxSize(),
            label = "lyricsBackgroundCrossfade"
        ) { state ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (degraded) {
                    AlbumArtImage(
                        uri = state.albumArtUri,
                        coverUrl = state.coverUrl,
                        audioFileUri = state.audioFileUri,
                        albumId = state.albumId,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = saturatedFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.4f
                                scaleY = 1.4f
                            }
                            .blur(bgBlur)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(bgBlur)
                    ) {
                        rotatingArt { rotA }
                        rotatingArt { rotB }
                        rotatingArt { rotC }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.10f)))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.55f),
                            0.16f to Color.Black.copy(alpha = 0.06f),
                            0.45f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.60f)
                        )
                    )
                )
        )
    }
}

fun boostedCoverColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(1f)
    hsv[2] = (hsv[2] * 1.35f).coerceIn(0.42f, 0.96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
