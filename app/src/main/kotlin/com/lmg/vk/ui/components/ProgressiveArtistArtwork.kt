package com.lmg.vk.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.size.Precision
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.lmg.vk.ui.glass.AlbumArtImage

/** Cached small bitmap layers: no live backdrop sampling, also works on Android 10/11. */
@Composable
fun ProgressiveArtistArtwork(url: String?, name: String, isDark: Boolean, drawBase: Boolean = true, artworkHeight: Dp? = null) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageHeight = artworkHeight?.coerceAtMost(maxHeight) ?: maxHeight
        val contentRatio = imageHeight.value / maxWidth.value.coerceAtLeast(1f)
        val ratio = maxHeight.value / maxWidth.value.coerceAtLeast(1f)
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { imageHeight.roundToPx() }.coerceAtLeast(1)
        val imageModifier = Modifier.fillMaxWidth().height(imageHeight)
        if (drawBase) {
            val cover = com.lmg.vk.ui.glass.ArtworkSourceResolver.realCoverOrNull(url)
            if (cover == null) AlbumArtImage(uri = null, coverUrl = null, contentDescription = name,
                modifier = imageModifier, contentScale = ContentScale.Crop)
            else {
                val base = remember(cover, widthPx, heightPx, context) {
                    ImageRequest.Builder(context).data(cover).size(widthPx, heightPx).precision(Precision.EXACT)
                        .memoryCacheKey("artist-hero-v3:$cover:$widthPx:$heightPx").crossfade(false).build()
                }
                AsyncImage(base, contentDescription = name, modifier = imageModifier, contentScale = ContentScale.Crop)
            }
        }
        if (!url.isNullOrBlank()) {
            listOf(2, 6).forEach { radius ->
                val request = remember(url, radius, ratio, contentRatio, context) {
                    ImageRequest.Builder(context).data(url).size(600).allowHardware(false).crossfade(false)
                        .transformations(ArtistBlurTransformation(radius, ratio, contentRatio)).build()
                }
                // Alpha is baked into the cached bitmap. No offscreen destination-in
                // layer can erase or darken the sharp portrait underneath it.
                AsyncImage(request, contentDescription = null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize())
            }
        }
        val background = DetailStyle.background(isDark)
        val fadeStart = imageHeight.value / maxHeight.value * if (isDark) .64f else .94f
        val fade = remember(background, fadeStart) {
            Array(33) { i ->
                val y = i / 32f
                val t = ((y - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
                val alpha = t * t * (3f - 2f * t)
                y to background.copy(alpha = alpha)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*fade)))
    }
}

/** Three separable box passes approximate a Gaussian at a fixed, small working size. */
internal class ArtistBlurTransformation(private val radius: Int, private val aspect: Float = 1f, private val contentAspect: Float = aspect) : Transformation {
    override val cacheKey = "lmg-artist-progressive-v3-$radius-$aspect-$contentAspect"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = minOf(256, input.width)
        val height = (width * aspect).toInt().coerceAtLeast(1)
        val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val contentHeight = (width * contentAspect).toInt().coerceIn(1, height)
        val scale = maxOf(width.toFloat() / input.width, contentHeight.toFloat() / input.height)
        val left = (width - input.width * scale) / 2f
        val top = (contentHeight - input.height * scale) / 2f
        // Extend the lower edge through the controls without stretching the portrait.
        val shader = android.graphics.BitmapShader(input, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, scale); postTranslate(left, top) })
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        android.graphics.Canvas(small).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        var pixels = IntArray(width * height)
        var buffer = IntArray(pixels.size)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== input) small.recycle()
        fun pass(horizontal: Boolean) {
            val length = if (horizontal) width else height
            val lines = if (horizontal) height else width
            val count = radius * 2 + 1
            fun index(line: Int, position: Int) = if (horizontal) line * width + position else position * width + line
            for (line in 0 until lines) {
                var a = 0; var r = 0; var g = 0; var b = 0
                fun accumulate(position: Int, sign: Int) {
                    val pixel = pixels[index(line, position.coerceIn(0, length - 1))]
                    a += (pixel ushr 24) * sign; r += ((pixel ushr 16) and 255) * sign
                    g += ((pixel ushr 8) and 255) * sign; b += (pixel and 255) * sign
                }
                for (i in -radius..radius) accumulate(i, 1)
                for (position in 0 until length) {
                    buffer[index(line, position)] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                    accumulate(position - radius, -1); accumulate(position + radius + 1, 1)
                }
            }
            val swap = pixels; pixels = buffer; buffer = swap
        }
        repeat(3) { pass(true); pass(false) }
        val start = if (radius == 2) .70f else .84f
        val end = if (radius == 2) .92f else 1f
        for (y in 0 until height) {
            val opacity = ((y.toFloat() / (contentHeight - 1).coerceAtLeast(1) - start) / (end - start)).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val i = y * width + x
                val alpha = ((pixels[i] ushr 24) * opacity).toInt()
                pixels[i] = (pixels[i] and 0x00FFFFFF) or (alpha shl 24)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
