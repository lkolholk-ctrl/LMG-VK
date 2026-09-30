package com.lmg.vk.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A catalog artist can contain a tiny copy of an otherwise full-size release cover. */
internal object ArtistHeroArtworkResolver {
    suspend fun resolve(context: Context, original: String?, releaseCovers: List<String>): String? {
        if (original.isNullOrBlank()) return original
        val candidates = releaseCovers.filter { it.isNotBlank() && it != original }.distinct().take(12)
        if (candidates.isEmpty()) return original
        return withContext(Dispatchers.IO) {
            var bestUrl = original
            withTimeoutOrNull(8_000) {
                suspend fun load(url: String): Bitmap? {
                    val result = context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(url).size(1024).precision(Precision.EXACT).allowHardware(false)
                        .memoryCacheKey("artist-source-check:$url").build()) as? SuccessResult
                    return (result?.drawable as? BitmapDrawable)?.bitmap
                }
                val source = load(original) ?: return@withTimeoutOrNull
                // Large original portraits need no replacement or additional network work.
                if (minOf(source.width, source.height) >= 900) return@withTimeoutOrNull
                val reference = signature(source)
                var bestArea = source.width.toLong() * source.height
                for (url in candidates) {
                    val candidate = withTimeoutOrNull(1_500) { load(url) } ?: continue
                    val area = candidate.width.toLong() * candidate.height
                    if (area <= bestArea || !sameArtistArtwork(reference, signature(candidate))) continue
                    bestArea = area
                    bestUrl = url
                    if (minOf(candidate.width, candidate.height) >= 900) break
                }
            }
            bestUrl
        }
    }

    private fun signature(bitmap: Bitmap): IntArray {
        val sample = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        Canvas(sample).drawBitmap(bitmap, Rect(left, top, left + side, top + side), Rect(0, 0, 32, 32),
            Paint(Paint.FILTER_BITMAP_FLAG))
        return IntArray(32 * 32).also {
            sample.getPixels(it, 0, 32, 0, 0, 32, 32)
            sample.recycle()
        }
    }
}

/** Compare content, never choose an album simply because it belongs to the artist. */
internal fun sameArtistArtwork(a: IntArray, b: IntArray): Boolean {
    if (a.size != b.size || a.isEmpty()) return false
    var difference = 0L
    var minimum = 255
    var maximum = 0
    for (i in a.indices) {
        for (shift in intArrayOf(16, 8, 0)) {
            val x = (a[i] ushr shift) and 255
            val y = (b[i] ushr shift) and 255
            difference += kotlin.math.abs(x - y)
            minimum = minOf(minimum, x)
            maximum = maxOf(maximum, x)
        }
    }
    return maximum - minimum >= 40 && difference.toDouble() / (a.size * 3) <= 12.0
}
