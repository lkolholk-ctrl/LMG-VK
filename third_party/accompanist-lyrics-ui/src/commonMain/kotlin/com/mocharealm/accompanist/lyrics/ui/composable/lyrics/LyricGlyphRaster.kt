// LMG VK addition: fixed glyph coverage for fractional lift without rotation or baseline snapping.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.drawText
import kotlin.math.ceil

private class GlyphRaster(val image: ImageBitmap, val inset: Offset, val resolution: Float)

/** Draw-thread cache, shared across visible rows. Evicted images are left to GC for GPU safety. */
private object GlyphRasterCache {
    private data class Key(val input: TextLayoutInput, val glow: Boolean)
    private val entries = LinkedHashMap<Key, GlyphRaster>(64, 0.75f, true)
    private var bytes = 0L
    private const val MaxBytes = 8L * 1024 * 1024

    fun DrawScope.get(layout: TextLayoutResult, glow: Boolean): GlyphRaster {
        val key = Key(layout.layoutInput, glow)
        entries[key]?.let { return it }
        val trace = com.lmg.vk.debug.PlayerStartupTrace.begin(if (glow) "glyph_glow_miss" else "glyph_face_miss")
        try {
            val raster = if (glow) {
                val face = get(layout, false)
                val offset = IntArray(2)
                val paint = Paint().apply {
                    maskFilter = BlurMaskFilter(5f * layout.layoutInput.density.density * face.resolution,
                        BlurMaskFilter.Blur.NORMAL)
                }
                val mask = face.image.asAndroidBitmap().extractAlpha(paint, offset).asImageBitmap()
                GlyphRaster(mask, face.inset - Offset(offset[0] / face.resolution, offset[1] / face.resolution),
                    face.resolution)
            } else {
                // Supersample the face to preserve sharp edges during the existing 1.14x emphasis.
                val resolution = 2f
                val padding = ceil(maxOf(2f, layout.size.height * 0.15f))
                val width = ceil((layout.size.width + 2 * padding) * resolution).toInt().coerceAtLeast(1)
                val height = ceil((layout.size.height + 2 * padding) * resolution).toInt().coerceAtLeast(1)
                val image = ImageBitmap(width, height, ImageBitmapConfig.Alpha8)
                CanvasDrawScope().draw(this, layoutDirection, Canvas(image), Size(width.toFloat(), height.toFloat())) {
                    scale(resolution, resolution, Offset.Zero) {
                        drawText(layout, color = Color.White, topLeft = Offset(padding, padding),
                            shadow = androidx.compose.ui.graphics.Shadow.None)
                    }
                }
                GlyphRaster(image, Offset(padding, padding), resolution)
            }
            entries[key] = raster
            bytes += raster.image.asAndroidBitmap().allocationByteCount
            while (bytes > MaxBytes && entries.size > 1) {
                val iterator = entries.entries.iterator()
                val oldest = iterator.next().value.image
                bytes -= oldest.asAndroidBitmap().allocationByteCount
                iterator.remove()
            }
            return raster
        } finally {
            trace?.end()
        }
    }
}

internal fun DrawScope.drawStableLyricGlyph(
    layout: TextLayoutResult,
    origin: Offset,
    tint: ColorFilter,
    alpha: Float,
    shadowAlpha: Float
) {
    fun drawRaster(raster: GlyphRaster, opacity: Float) {
        withTransform({
            translate(origin.x - raster.inset.x, origin.y - raster.inset.y)
            scale(1f / raster.resolution, 1f / raster.resolution, Offset.Zero)
        }) {
            drawImage(raster.image, alpha = opacity, colorFilter = tint, filterQuality = FilterQuality.Low)
        }
    }
    if (shadowAlpha > 0.001f) {
        drawRaster(with(GlyphRasterCache) { get(layout, true) }, alpha * shadowAlpha)
    }
    drawRaster(with(GlyphRasterCache) { get(layout, false) }, alpha)
}
