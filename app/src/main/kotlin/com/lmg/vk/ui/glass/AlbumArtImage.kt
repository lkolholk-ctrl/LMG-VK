package com.lmg.vk.ui.glass

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Минимальный размер стороны чтобы считать обложку HQ */
private const val MIN_HQ_SIZE = 500
/** Верхняя граница декода: FullPlayer не должен поднимать 2048+ bitmap и выбивать RAM. */
private const val MAX_ART_SIZE = 1024

/**
 * Универсальный компонент для отображения обложки альбома.
 * Поддерживает:
 * - Локальные треки (MediaStore album art, embedded art)
 * - Онлайн треки (coverUrl из backend API через Coil)
 */
@Composable
fun AlbumArtImage(
    uri: Uri?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    audioFileUri: Uri? = null,
    albumId: Long = -1L,
    coverUrl: String? = null
) {
    // Онлайн-обложка: используем Coil AsyncImage
    if (!coverUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        return
    }


    val context = LocalContext.current
    var bitmap by remember(uri, audioFileUri, albumId) { mutableStateOf<ImageBitmap?>(null) }
    var loadFailed by remember(uri, audioFileUri, albumId) { mutableStateOf(false) }

    LaunchedEffect(uri, audioFileUri, albumId) {
        bitmap = null
        loadFailed = false
        if (uri == null && audioFileUri == null) {
            loadFailed = true
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            var best: Bitmap? = null

            fun consider(candidate: Bitmap?) {
                if (candidate == null) return
                val current = best
                if (current == null || candidate.width * candidate.height > current.width * current.height) {
                    if (current != null && current !== candidate) current.recycle()
                    best = candidate
                } else {
                    candidate.recycle()
                }
            }

            // 1) MMR — embedded picture из аудиофайла (обычно оригинал)
            audioFileUri?.let { fileUri ->
                try {
                    context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(pfd.fileDescriptor)
                            retriever.embeddedPicture?.let { bytes ->
                                consider(decodeSampledByteArray(bytes, MAX_ART_SIZE))
                            }
                        } finally {
                            retriever.release()
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2) loadThumbnail на audio content URI
            audioFileUri?.let { fileUri ->
                try {
                    val bmp = context.contentResolver
                        .loadThumbnail(fileUri, Size(MAX_ART_SIZE, MAX_ART_SIZE), null)
                    consider(bmp)
                } catch (_: Exception) {}
            }

            // 3) loadThumbnail на albums URI
            if (albumId > 0) {
                try {
                    val albumUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
                    )
                    val bmp = context.contentResolver
                        .loadThumbnail(albumUri, Size(MAX_ART_SIZE, MAX_ART_SIZE), null)
                    consider(bmp)
                } catch (_: Exception) {}
            }

            // 4) Legacy albumart URI
            uri?.let { artUri ->
                try {
                    context.contentResolver.openInputStream(artUri)?.use { stream ->
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inSampleSize = 2
                        }
                        BitmapFactory.decodeStream(stream, null, opts)
                            ?.let { consider(it) }
                    }
                } catch (_: Exception) {}
            }

            best?.asImageBitmap()
        }

        if (result != null) {
            bitmap = result
        } else {
            loadFailed = true
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = FilterQuality.High
        )
    } else if (loadFailed) {
        PlaceholderArt(modifier = modifier)
    }
}

private fun decodeSampledByteArray(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = sample.coerceAtLeast(1)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

@Composable
fun VkDefaultAudioCover(
    title: String = "",
    artist: String = "",
    modifier: Modifier = Modifier,
    iconSizeRatio: Float = 0.5f
) {
    val hash = (title.hashCode() * 31 + artist.hashCode())
    val gradientIndex = (hash and 0x7FFFFFFF) % VK_DEFAULT_GRADIENTS.size
    val (startColor, endColor) = VK_DEFAULT_GRADIENTS[gradientIndex]

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(startColor, endColor)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxSize(iconSizeRatio)
        )
    }
}

private val VK_DEFAULT_GRADIENTS = listOf(
    Color(0xFF0077FF) to Color(0xFF0044B3), // VK Classic Blue
    Color(0xFF7B2CBF) to Color(0xFF5A189A), // VK Royal Purple
    Color(0xFFFF2A6D) to Color(0xFFD6004C), // VK Sunset Pink
    Color(0xFF00B4D8) to Color(0xFF0077B6), // VK Cyan Wave
    Color(0xFFFF9E00) to Color(0xFFE85D04), // VK Neon Amber
    Color(0xFF10B981) to Color(0xFF047857), // VK Emerald Green
    Color(0xFF8B5CF6) to Color(0xFF6D28D9), // VK Deep Violet
    Color(0xFFEC4899) to Color(0xFFBE185D)  // VK Magenta Dream
)

private const val VK_OFFICIAL_PLACEHOLDER_URL = "https://vk.com/images/audio_row_placeholder.png"

@Composable
private fun PlaceholderArt(modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(VK_OFFICIAL_PLACEHOLDER_URL)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
