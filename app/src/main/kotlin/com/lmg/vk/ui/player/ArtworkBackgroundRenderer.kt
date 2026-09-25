package com.lmg.vk.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.animation.core.CubicBezierEasing

/** Worker-owned surfaces: no decode, blur or large image allocation on the UI thread. */
internal class ArtworkBackgroundRenderer(private val width: Int, private val height: Int) : AutoCloseable {
    private val surface = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val artworkLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(surface)
    private val layerCanvas = Canvas(artworkLayer)
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
    }
    private val blendPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels = IntArray(width * height)
    private val blur = ArtworkBlur(width, height)
    private val transition = CubicBezierEasing(0f, 0f, 0.3f, 1f)
    private var artwork: Bitmap? = null
    private var previous: Bitmap? = null
    private var initialized = false
    private var changedAt = 0L
    private var lastFrame: Bitmap? = null

    fun frame(next: Bitmap?, elapsedMs: Long): Bitmap {
        if (initialized && next == null && artwork == null && previous == null) {
            lastFrame?.let { return it }
        }
        if (!initialized || next !== artwork) {
            previous = artwork
            artwork = next
            changedAt = elapsedMs
            initialized = true
        }
        val fraction = transition.transform(((elapsedMs - changedAt) / 1_000f).coerceIn(0f, 1f))
        if (fraction >= 1f) {
            drawArtwork(artwork, elapsedMs)
            canvas.drawBitmap(artworkLayer, 0f, 0f, blendPaint.apply { alpha = 255 })
            previous = artwork
        } else {
            drawArtwork(previous, elapsedMs)
            canvas.drawBitmap(artworkLayer, 0f, 0f, blendPaint.apply { alpha = 255 })
            if (fraction > 0f) {
                drawArtwork(artwork, elapsedMs)
                canvas.drawBitmap(artworkLayer, 0f, 0f, blendPaint.apply { alpha = (fraction * 255).toInt() })
            }
        }
        canvas.drawColor(Color.argb(77, 0, 0, 0))
        canvas.drawColor(Color.argb(26, 255, 255, 255))
        surface.getPixels(pixels, 0, width, 0, 0, width, height)
        blur.apply(pixels)
        // Published frames are immutable and never recycled while Compose may still draw them.
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).also { lastFrame = it }
    }

    private fun drawArtwork(bitmap: Bitmap?, elapsedMs: Long) {
        artworkLayer.eraseColor(Color.rgb(24, 26, 30))
        if (bitmap == null) return
        val extent = maxOf(width, height) * 1.3f
        val scale = extent / minOf(bitmap.width, bitmap.height)
        val left = -(bitmap.width * scale - width) / 2f
        val top = -(bitmap.height * scale - height) / 2f
        for (layer in 0..2) {
            val rotation = artworkRotation(elapsedMs, layer)
            matrix.setScale(scale, scale)
            matrix.postRotate(rotation, bitmap.width * scale / 2f, bitmap.height * scale / 2f)
            matrix.postTranslate(left, top)
            when (layer) {
                1 -> matrix.postTranslate(-0.95f * width, -0.7f * height)
                2 -> {
                    matrix.postTranslate(-0.5f * width, 0.7f * height)
                    matrix.postRotate(rotation, width / 2f, height / 2f)
                }
            }
            layerCanvas.drawBitmap(bitmap, matrix, paint)
        }
    }

    override fun close() {
        surface.recycle()
        artworkLayer.recycle()
    }
}
