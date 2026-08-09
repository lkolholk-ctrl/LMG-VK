package com.lmg.vk.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lmg.vk.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val FALLBACK_DARK = 0xFF121212.toInt()
private val FALLBACK_MUTED = 0xFF1C1C1E.toInt()
private const val PALETTE_BITMAP_SIZE = 384
private const val MAX_PIXEL_SAMPLES = 4_096

data class AlbumColors(
    val dominant: Color = Color(FALLBACK_DARK),
    val darkMuted: Color = Color(FALLBACK_DARK),
    val darkVibrant: Color = Color(FALLBACK_DARK),
    val vibrant: Color = Color(FALLBACK_MUTED),
    val lightVibrant: Color = Color(FALLBACK_MUTED),
    val muted: Color = Color(FALLBACK_MUTED),
    val lightMuted: Color = Color(FALLBACK_MUTED),
    val accents: List<Color> = emptyList(),
)

/** Hue-сохраняющий подъём слишком тёмного цвета. */
private fun liftDarkColor(color: Int, minBrightness: Float = 0.15f): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color, hsv)
    if (hsv[2] < 0.02f) return Color(FALLBACK_DARK)
    hsv[2] = hsv[2].coerceAtLeast(minBrightness)
    return Color(AndroidColor.HSVToColor(hsv))
}

/** Усиливает цвет в HSV, не меняя hue. */
private fun vivid(c: Color, satMul: Float, valMul: Float, valFloor: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(c.toArgb(), hsv)
    hsv[1] = (hsv[1] * satMul).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * valMul).coerceAtLeast(valFloor).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun topAccents(palette: Palette, satMul: Float, count: Int): List<Color> =
    palette.swatches
        .filter { saturationOf(it.rgb) >= 0.25f && it.population >= 40 }
        .sortedByDescending { saturationOf(it.rgb) * (minOf(it.population, 500) / 500f) }
        .take(count)
        .map { vivid(Color(it.rgb), satMul = satMul, valMul = 1.05f, valFloor = 0.20f) }

private data class BitmapColorStats(
    val sampleCount: Int,
    val averageBrightness: Float,
    val averageChroma: Float,
    val p90Chroma: Float,
    val averageHslSaturation: Float,
    val coloredFraction: Float,
    val hueCoherence: Float,
    val representativeColor: Int?,
    val isMonochrome: Boolean,
)

private data class ExtractionResult(
    val colors: AlbumColors,
    val cacheable: Boolean,
)

private val paletteCache = object : LinkedHashMap<String, AlbumColors>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AlbumColors>) = size > 64
}

private fun cachedPalette(key: String): AlbumColors? =
    synchronized(paletteCache) { paletteCache[key] }

private fun putPalette(key: String, colors: AlbumColors) {
    synchronized(paletteCache) { paletteCache[key] = colors }
}

/**
 * Совместимый вход для остальных экранов. И UI, и extractor выбирают источник
 * через один [ArtworkSourceResolver]: cover URL имеет приоритет над URI.
 */
@Composable
fun rememberAlbumColors(uri: Uri?, coverUrl: String? = null): AlbumColors {
    val source = remember(uri, coverUrl) { ArtworkSourceResolver.resolve(uri, coverUrl) }
    return rememberAlbumColors(source)
}

/** Палитра именно того resolved artwork source, который отображает FullPlayer. */
@Composable
fun rememberAlbumColors(source: ResolvedArtworkSource?): AlbumColors {
    val context = LocalContext.current
    var colors by remember(source?.cacheKey) { mutableStateOf(AlbumColors()) }

    LaunchedEffect(source?.cacheKey) {
        if (source == null) {
            DebugLog.add("ART EXTRACT no-source -> FALLBACK_DARK/FALLBACK_MUTED")
            colors = AlbumColors()
            return@LaunchedEffect
        }

        DebugLog.add("ART EXTRACT request kind=${source.kind} source=${source.model}")
        cachedPalette(source.cacheKey)?.let { cached ->
            DebugLog.add("ART EXTRACT cache-hit key=${source.cacheKey} colors=${cached.debugString()}")
            colors = cached
            return@LaunchedEffect
        }

        val extraction = withContext(Dispatchers.IO) {
            runCatching { extractAlbumColors(context, source) }
                .getOrElse { error ->
                    DebugLog.add(
                        "ART EXTRACT failed source=${source.model} " +
                            "error=${error.javaClass.simpleName}:${error.message} -> FALLBACK",
                    )
                    ExtractionResult(AlbumColors(), cacheable = false)
                }
        }
        if (extraction.cacheable) putPalette(source.cacheKey, extraction.colors)
        colors = extraction.colors
    }

    return colors
}

private suspend fun extractAlbumColors(
    context: Context,
    source: ResolvedArtworkSource,
): ExtractionResult {
    val bitmap = loadArtworkBitmap(context, source)
        ?: return ExtractionResult(AlbumColors(), cacheable = false)

    DebugLog.add(
        "ART BITMAP loaded source=${source.model} size=${bitmap.width}x${bitmap.height} " +
            "config=${bitmap.config}",
    )

    val (palette, stats) = try {
        withContext(Dispatchers.Default) {
            val pixelStats = analyzeBitmap(bitmap)
            val generatedPalette = Palette.from(bitmap)
                .maximumColorCount(24)
                .generate()
            generatedPalette to pixelStats
        }
    } finally {
        bitmap.recycle()
    }

    logPalette(source, palette, stats)

    if (stats.isMonochrome) {
        fun gray(value: Float): Color {
            val channel = (value.coerceIn(0f, 1f) * 255).toInt()
            return Color(channel, channel, channel)
        }

        val hi = 0.60f + stats.averageBrightness * 0.25f
        val lo = 0.15f + stats.averageBrightness * 0.10f
        val result = AlbumColors(
            dominant = gray(lo + 0.06f),
            darkMuted = gray(lo),
            darkVibrant = gray(lo + 0.03f),
            vibrant = gray(hi),
            lightVibrant = gray(hi + 0.12f),
            muted = gray(lo + 0.10f),
            lightMuted = gray(hi + 0.06f),
        )
        DebugLog.add("ART OUTPUT monochrome=true colors=${result.debugString()}")
        return ExtractionResult(result, cacheable = true)
    }

    val representative = stats.representativeColor
    val candidates = listOfNotNull(
        palette.vibrantSwatch,
        palette.darkVibrantSwatch,
        palette.lightVibrantSwatch,
        palette.dominantSwatch,
        palette.mutedSwatch,
    )
    // Порог Vibrant не снижаем: если Palette не выделил хороший swatch,
    // используем representative color прямой выборки пикселей.
    val bestSwatch = candidates
        .filter { brightnessOf(it.rgb) > 0.05f && saturationOf(it.rgb) >= 0.18f }
        .maxByOrNull {
            saturationOf(it.rgb) *
                (0.55f + 0.45f * (minOf(it.population, 500) / 500f))
        }

    val rawDominant = preferChromatic(
        primary = palette.dominantSwatch?.rgb,
        representative = representative,
        fallback = FALLBACK_DARK,
    )
    val rawVibrant = bestSwatch?.rgb
        ?: representative
        ?: palette.vibrantSwatch?.rgb
        ?: rawDominant
    val rawMuted = preferChromatic(
        primary = palette.mutedSwatch?.rgb,
        representative = representative,
        fallback = rawDominant,
    )
    val rawDarkMuted = preferChromatic(
        primary = palette.darkMutedSwatch?.rgb,
        representative = representative,
        fallback = rawDominant,
    )
    val rawDarkVibrant = preferChromatic(
        primary = palette.darkVibrantSwatch?.rgb,
        representative = representative,
        fallback = rawVibrant,
    )
    val rawLightVibrant = preferChromatic(
        primary = palette.lightVibrantSwatch?.rgb,
        representative = representative,
        fallback = rawVibrant,
    )
    val rawLightMuted = preferChromatic(
        primary = palette.lightMutedSwatch?.rgb,
        representative = representative,
        fallback = rawMuted,
    )

    DebugLog.add(
        "ART RAW dominant=${rawDominant.hex()} vibrant=${rawVibrant.hex()} " +
            "muted=${rawMuted.hex()} darkMuted=${rawDarkMuted.hex()} " +
            "darkVibrant=${rawDarkVibrant.hex()} lightVibrant=${rawLightVibrant.hex()} " +
            "lightMuted=${rawLightMuted.hex()} fallbackUsed=" +
            "${listOf(rawDominant, rawVibrant, rawMuted).any { it == FALLBACK_DARK || it == FALLBACK_MUTED }}",
    )

    val result = AlbumColors(
        dominant = vivid(liftDarkColor(rawDominant, 0.10f), 1.50f, 1.10f, 0.18f),
        darkMuted = vivid(liftDarkColor(rawDarkMuted, 0.10f), 1.60f, 0.72f, 0.12f),
        darkVibrant = vivid(liftDarkColor(rawDarkVibrant, 0.10f), 1.50f, 0.78f, 0.16f),
        vibrant = vivid(liftDarkColor(rawVibrant, 0.12f), 1.55f, 1.15f, 0.22f),
        lightVibrant = vivid(liftDarkColor(rawLightVibrant, 0.15f), 1.45f, 1.15f, 0.26f),
        muted = vivid(liftDarkColor(rawMuted, 0.10f), 1.45f, 0.88f, 0.16f),
        lightMuted = vivid(liftDarkColor(rawLightMuted, 0.15f), 1.30f, 1.10f, 0.30f),
        accents = topAccents(palette, satMul = 1.40f, count = 3),
    )
    DebugLog.add("ART OUTPUT monochrome=false colors=${result.debugString()}")
    return ExtractionResult(result, cacheable = true)
}

/**
 * Загружает artwork тем же Coil ImageLoader, которым пользуется AsyncImage.
 * Это даёт тот же disk/memory cache, OkHttp, прокси и декодер формата.
 */
private suspend fun loadArtworkBitmap(
    context: Context,
    source: ResolvedArtworkSource,
): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(source.model)
        .size(PALETTE_BITMAP_SIZE, PALETTE_BITMAP_SIZE)
        .allowHardware(false)
        .build()

    return when (val result = context.imageLoader.execute(request)) {
        is SuccessResult -> {
            DebugLog.add(
                "ART BITMAP Coil success source=${source.model} dataSource=${result.dataSource}",
            )
            result.drawable.softwareCopy()
        }
        is ErrorResult -> {
            DebugLog.add(
                "ART BITMAP Coil error source=${source.model} " +
                    "error=${result.throwable.javaClass.simpleName}:${result.throwable.message} -> FALLBACK",
            )
            null
        }
    }
}

private fun android.graphics.drawable.Drawable.softwareCopy(): Bitmap? = runCatching {
    val bitmap = (this as? BitmapDrawable)?.bitmap
    if (bitmap != null) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        val width = intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(PALETTE_BITMAP_SIZE)
            ?: PALETTE_BITMAP_SIZE
        val height = intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(PALETTE_BITMAP_SIZE)
            ?: PALETTE_BITMAP_SIZE
        toBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}.getOrNull()

/**
 * Настоящий grayscale определяется по пикселям, а не по наличию VibrantSwatch.
 * Нейтральными считаются только изображения, у которых одновременно малы
 * средняя chroma, 90-й перцентиль chroma и доля цветных пикселей. Единый слабый
 * hue (пастель, кремовый, mint) сохраняется через hueCoherence.
 */
private fun analyzeBitmap(bitmap: Bitmap): BitmapColorStats {
    val pixelCount = bitmap.width.toLong() * bitmap.height.toLong()
    val step = sqrt((pixelCount.toDouble() / MAX_PIXEL_SAMPLES).coerceAtLeast(1.0))
        .toInt()
        .coerceAtLeast(1)
    val chromaValues = ArrayList<Float>(MAX_PIXEL_SAMPLES)

    var samples = 0
    var brightnessSum = 0f
    var chromaSum = 0f
    var hslSaturationSum = 0f
    var coloredPixels = 0
    var weightedR = 0f
    var weightedG = 0f
    var weightedB = 0f
    var representativeWeight = 0f
    var hueX = 0.0
    var hueY = 0.0
    var hueWeight = 0.0

    val hsv = FloatArray(3)
    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            val color = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(color) / 255f
            if (alpha < 0.50f) continue

            val r = AndroidColor.red(color) / 255f
            val g = AndroidColor.green(color) / 255f
            val b = AndroidColor.blue(color) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val chroma = max - min
            val lightness = (max + min) / 2f
            val hslSaturation = if (chroma <= 0.0001f) {
                0f
            } else {
                (chroma / (1f - abs(2f * lightness - 1f)).coerceAtLeast(0.0001f))
                    .coerceIn(0f, 1f)
            }

            samples++
            brightnessSum += (r + g + b) / 3f
            chromaSum += chroma
            // HSL у почти белого JPEG-шума может показать высокую saturation
            // при разнице каналов всего 1–2/255. Взвешиваем её реальной chroma.
            hslSaturationSum += hslSaturation * (chroma / 0.05f).coerceIn(0f, 1f)
            chromaValues += chroma
            if (chroma >= 0.035f && hslSaturation >= 0.06f) coloredPixels++

            // Почти белое/чёрное не должно заглушать небольшой цветной рисунок.
            if (max >= 0.035f && min <= 0.985f) {
                val weight = alpha * (0.02f + chroma * 3f + hslSaturation * 0.75f)
                weightedR += r * weight
                weightedG += g * weight
                weightedB += b * weight
                representativeWeight += weight
            }

            if (chroma >= 0.012f) {
                AndroidColor.RGBToHSV(
                    (r * 255f).toInt(),
                    (g * 255f).toInt(),
                    (b * 255f).toInt(),
                    hsv,
                )
                val weight = (chroma * (0.25f + hslSaturation)).toDouble()
                val radians = Math.toRadians(hsv[0].toDouble())
                hueX += cos(radians) * weight
                hueY += sin(radians) * weight
                hueWeight += weight
            }
        }
    }

    if (samples == 0) {
        return BitmapColorStats(0, 0f, 0f, 0f, 0f, 0f, 0f, null, isMonochrome = true)
    }

    chromaValues.sort()
    val p90Index = ((chromaValues.size - 1) * 0.90f).toInt().coerceIn(chromaValues.indices)
    val averageChroma = chromaSum / samples
    val p90Chroma = chromaValues[p90Index]
    val averageHslSaturation = hslSaturationSum / samples
    val coloredFraction = coloredPixels.toFloat() / samples
    val hueCoherence = if (hueWeight > 0.0) {
        (sqrt(hueX * hueX + hueY * hueY) / hueWeight).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val coherentTint = hueCoherence >= 0.70f && averageChroma >= 0.018f && p90Chroma >= 0.025f
    val isMonochrome = averageChroma < 0.025f &&
        p90Chroma < 0.055f &&
        averageHslSaturation < 0.09f &&
        coloredFraction < 0.08f &&
        !coherentTint

    val representative = if (representativeWeight > 0f) {
        AndroidColor.rgb(
            ((weightedR / representativeWeight) * 255f).toInt().coerceIn(0, 255),
            ((weightedG / representativeWeight) * 255f).toInt().coerceIn(0, 255),
            ((weightedB / representativeWeight) * 255f).toInt().coerceIn(0, 255),
        )
    } else {
        null
    }

    return BitmapColorStats(
        sampleCount = samples,
        averageBrightness = brightnessSum / samples,
        averageChroma = averageChroma,
        p90Chroma = p90Chroma,
        averageHslSaturation = averageHslSaturation,
        coloredFraction = coloredFraction,
        hueCoherence = hueCoherence,
        representativeColor = representative,
        isMonochrome = isMonochrome,
    )
}

private fun preferChromatic(primary: Int?, representative: Int?, fallback: Int): Int {
    val candidate = primary ?: return representative ?: fallback
    val sampled = representative ?: return candidate
    return if (chromaOf(candidate) < maxOf(0.018f, chromaOf(sampled) * 0.35f)) sampled else candidate
}

private fun logPalette(
    source: ResolvedArtworkSource,
    palette: Palette,
    stats: BitmapColorStats,
) {
    DebugLog.add(
        "ART STATS source=${source.model} samples=${stats.sampleCount} " +
            "avgChroma=${stats.averageChroma.fmt()} p90Chroma=${stats.p90Chroma.fmt()} " +
            "avgHslSat=${stats.averageHslSaturation.fmt()} " +
            "coloredFraction=${stats.coloredFraction.fmt()} hueCoherence=${stats.hueCoherence.fmt()} " +
            "representative=${stats.representativeColor?.hex()} isMonochrome=${stats.isMonochrome}",
    )
    DebugLog.add(
        "ART PALETTE dominant=${palette.dominantSwatch?.rgb?.hex()} " +
            "vibrant=${palette.vibrantSwatch?.rgb?.hex()} muted=${palette.mutedSwatch?.rgb?.hex()} " +
            "lightVibrant=${palette.lightVibrantSwatch?.rgb?.hex()} " +
            "lightMuted=${palette.lightMutedSwatch?.rgb?.hex()} swatches=${palette.swatches.size}",
    )
    palette.swatches.sortedByDescending { it.population }.forEachIndexed { index, swatch ->
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        DebugLog.add(
            "ART SWATCH[$index] rgb=${swatch.rgb.hex()} pop=${swatch.population} " +
                "h=${hsl[0].fmt()} hslSat=${hsl[1].fmt()} light=${hsl[2].fmt()}",
        )
    }
}

private fun AlbumColors.debugString(): String =
    "dominant=${dominant.toArgb().hex()},darkMuted=${darkMuted.toArgb().hex()}," +
        "darkVibrant=${darkVibrant.toArgb().hex()},vibrant=${vibrant.toArgb().hex()}," +
        "lightVibrant=${lightVibrant.toArgb().hex()},muted=${muted.toArgb().hex()}," +
        "lightMuted=${lightMuted.toArgb().hex()}"

private fun brightnessOf(color: Int): Float {
    val r = AndroidColor.red(color) / 255f
    val g = AndroidColor.green(color) / 255f
    val b = AndroidColor.blue(color) / 255f
    return (r + g + b) / 3f
}

private fun chromaOf(color: Int): Float {
    val r = AndroidColor.red(color) / 255f
    val g = AndroidColor.green(color) / 255f
    val b = AndroidColor.blue(color) / 255f
    return maxOf(r, g, b) - minOf(r, g, b)
}

private fun saturationOf(color: Int): Float {
    val max = maxOf(AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color)) / 255f
    return if (max > 0f) chromaOf(color) / max else 0f
}

private fun Int.hex(): String = "#%08X".format(this)
private fun Float.fmt(): String = "%.3f".format(this)
