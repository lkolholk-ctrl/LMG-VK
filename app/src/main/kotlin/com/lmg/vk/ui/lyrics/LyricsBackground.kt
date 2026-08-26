package com.lmg.vk.ui.lyrics

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors

private data class BackdropArt(
    val albumArtUri: Uri?,
    val coverUrl: String?,
    val audioFileUri: Uri?,
    val albumId: Long,
)

@Composable
fun AppleBackdrop(
    albumArtUri: Uri?,
    coverUrl: String? = null,
    audioFileUri: Uri? = null,
    albumId: Long = -1L,
    albumColors: AlbumColors?,
    intense: Boolean,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val srcSize = if (intense) 7.dp else 20.dp
    val saturation = if (intense) 3.5f else 2.5f
    val saturatedFilter = remember(saturation) {
        val am = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
        ColorFilter.colorMatrix(ColorMatrix(am.getArray()))
    }

    val artState = remember(albumArtUri, coverUrl, audioFileUri, albumId) {
        BackdropArt(albumArtUri, coverUrl, audioFileUri, albumId)
    }

    val spin = rememberInfiniteTransition(label = "appleBackdropSpin")
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

    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    val srcPx = with(density) { srcSize.toPx() }
    val scaleK = if (hostSize.width > 0 && srcPx > 0f) hostSize.width * 1.3f / srcPx else 0f
    val hostW = hostSize.width.toFloat()
    val hostH = hostSize.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { hostSize = it }
    ) {
        Crossfade(
            targetState = artState,
            animationSpec = tween(durationMillis = 1000),
            modifier = Modifier.fillMaxSize(),
            label = "appleBackdropCrossfade"
        ) { state ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (scaleK > 0f && hostW > 0f) {
                    BackdropLayer(state, saturatedFilter, srcSize, scaleK, rotA, 0f, 0f)
                    BackdropLayer(state, saturatedFilter, srcSize, scaleK, rotB, -0.95f * hostW, -0.70f * hostH)
                    BackdropLayer(state, saturatedFilter, srcSize, scaleK, rotC, -0.50f * hostW, 0.70f * hostH)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)))
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f)))
    }
}

@Composable
private fun BackdropLayer(
    state: BackdropArt,
    filter: ColorFilter,
    srcSize: Dp,
    scaleK: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
) {
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scaleK
                scaleY = scaleK
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        Box(
            modifier = Modifier
                .size(srcSize)
                .graphicsLayer { rotationZ = rotation }
                .blur(8.dp)
        ) {
            AlbumArtImage(
                uri = state.albumArtUri,
                coverUrl = state.coverUrl,
                audioFileUri = state.audioFileUri,
                albumId = state.albumId,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

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
    AppleBackdrop(
        albumArtUri = albumArtUri,
        coverUrl = coverUrl,
        audioFileUri = audioFileUri,
        albumId = albumId,
        albumColors = albumColors,
        intense = false,
        modifier = modifier
    )
}

fun boostedCoverColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(1f)
    hsv[2] = (hsv[2] * 1.35f).coerceIn(0.42f, 0.96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
