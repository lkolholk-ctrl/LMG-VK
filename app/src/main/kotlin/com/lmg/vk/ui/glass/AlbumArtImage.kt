package com.lmg.vk.ui.glass

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
 * - Цветная VK-заглушка с нотой, когда обложки нет или CDN её не отдал
 */
@Composable
fun AlbumArtImage(
    uri: Uri?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    audioFileUri: Uri? = null,
    albumId: Long = -1L,
    placeholderKey: String? = null,
    coverUrl: String? = null
) {
    // URL старой маленькой заглушки VK означает отсутствие обложки, а не картинку.
    if (!coverUrl.isNullOrBlank() && !coverUrl.isVkAudioPlaceholder()) {
        var coverLoadFailed by remember(coverUrl) { mutableStateOf(false) }
        if (coverLoadFailed) {
            PlaceholderArt(
                modifier = modifier,
                contentDescription = contentDescription,
                placeholderKey = placeholderKey ?: coverUrl,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                onError = { coverLoadFailed = true },
            )
        }
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
        PlaceholderArt(
            modifier = modifier,
            contentDescription = contentDescription,
            placeholderKey = placeholderKey ?: uri?.toString() ?: audioFileUri?.toString(),
        )
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

// Проверка заглушки VK переехала в TrackPlaceholderArt: она нужна не только
// здесь, но и в MediaSession с экстрактором цветов, а две копии одной константы
// уже привели к тому, что про заглушку знал ТОЛЬКО этот файл.
private fun String.isVkAudioPlaceholder(): Boolean =
    TrackPlaceholderArt.isVkPlaceholder(this)


@Composable
private fun PlaceholderArt(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholderKey: String? = null,
) {
    // Выбор стабилен и живёт в TrackPlaceholderArt — том же словаре, из которого
    // заглушку берут MediaSession и экстрактор цветов. Своя копия логики здесь
    // означала бы, что на экране одна картинка, а в уведомлении другая.
    val resourceId = remember(placeholderKey) { TrackPlaceholderArt.resourceFor(placeholderKey) }
    Image(
        painter = painterResource(resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
