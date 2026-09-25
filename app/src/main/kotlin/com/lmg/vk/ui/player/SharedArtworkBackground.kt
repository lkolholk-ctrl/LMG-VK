package com.lmg.vk.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.os.Process
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lmg.vk.ui.glass.ArtworkSourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.concurrent.Executors

private val artworkRenderDispatcher = Executors.newSingleThreadExecutor { task ->
    Thread({
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        task.run()
    }, "ArtworkBackground").apply { isDaemon = true }
}.asCoroutineDispatcher()

private object BackgroundArtworkCache {
    private val mutex = Mutex()
    private val bitmaps = LinkedHashMap<String, Bitmap?>()

    suspend fun load(context: Context, uri: Uri?, cover: String?, audio: Uri?, albumId: Long): Bitmap? = mutex.withLock {
        val source = ArtworkSourceResolver.resolve(uri, cover)
        val key = if (source?.coverUrl != null) source.cacheKey else "${source?.cacheKey}:$audio:$albumId"
        if (bitmaps.containsKey(key)) {
            com.lmg.vk.debug.PlayerStartupTrace.mark("backgroundArtwork=cacheHit")
            return@withLock bitmaps[key]
        }
        com.lmg.vk.debug.PlayerStartupTrace.mark("backgroundArtwork=cacheMiss")
        val bitmap = withContext(Dispatchers.IO) {
            val models = listOfNotNull(
                source?.model,
                if (albumId > 0 && source?.coverUrl == null) Uri.parse("content://media/external/audio/albumart/$albumId") else null
            )
            var loaded: Bitmap? = null
            for (model in models) {
                val result = context.imageLoader.execute(ImageRequest.Builder(context)
                    .data(model).size(256, 256).allowHardware(false).build())
                if (result is SuccessResult) {
                    loaded = result.drawable.toBitmap(256, 256, Bitmap.Config.ARGB_8888)
                    break
                }
            }
            if (loaded == null && audio != null && audio.scheme in listOf("content", "file")) {
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, audio)
                        retriever.embeddedPicture?.let { bytes ->
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            var sample = 1
                            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                                BitmapFactory.Options().apply { inSampleSize = sample })
                            if (decoded != null) {
                                loaded = Bitmap.createScaledBitmap(decoded, 256, 256, true)
                                if (loaded !== decoded) decoded.recycle()
                            }
                        }
                    } finally { retriever.release() }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* Missing embedded artwork uses the neutral background. */ }
            }
            loaded
        }
        bitmaps[key] = bitmap
        if (bitmaps.size > 3) bitmaps.remove(bitmaps.keys.first())
        bitmap
    }
}

/** FullPlayer owns this surface; lyrics and queue remain transparent over it. */
@Composable
fun SharedArtworkBackground(
    albumArtUri: Uri?,
    coverUrl: String? = null,
    audioFileUri: Uri? = null,
    albumId: Long = -1,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var artwork by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(albumArtUri, coverUrl, audioFileUri, albumId) {
        val trace = com.lmg.vk.debug.PlayerStartupTrace.begin("background_load")
        try {
            artwork = BackgroundArtworkCache.load(context, albumArtUri, coverUrl, audioFileUri, albumId)
        } finally {
            trace?.end()
        }
    }
    val latestArtwork = rememberUpdatedState(artwork)
    val latestEnabled = rememberUpdatedState(enabled)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val (width, height) = backgroundSurfaceSize(constraints.maxWidth, constraints.maxHeight,
            (density.density * 160).roundToInt())
        val frame = remember { mutableStateOf<ImageBitmap?>(null) }
        val elapsed = remember { longArrayOf(0L) }
        LaunchedEffect(width, height, lifecycle) {
            var renderer: ArtworkBackgroundRenderer? = null
            var previousBitmap: Bitmap? = null
            val schedule = ArtworkBackgroundFrameSchedule()
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    var previousTick = SystemClock.uptimeMillis()
                    while (isActive) {
                        val started = SystemClock.uptimeMillis()
                        val current = latestArtwork.value
                        val nextElapsed = elapsed[0] + started - previousTick
                        if (!schedule.needsFrame(current, latestEnabled.value, nextElapsed)) {
                            delay(100)
                            previousTick = SystemClock.uptimeMillis()
                            continue
                        }
                        elapsed[0] = nextElapsed
                        previousTick = started
                        val bitmap = withContext(artworkRenderDispatcher) {
                            val activeRenderer = renderer ?: run {
                                val trace = com.lmg.vk.debug.PlayerStartupTrace.begin("background_init")
                                try {
                                    ArtworkBackgroundRenderer(width, height).also { renderer = it }
                                } finally {
                                    trace?.end()
                                }
                            }
                            val trace = com.lmg.vk.debug.PlayerStartupTrace.begin("background_frame")
                            try {
                                activeRenderer.frame(current, elapsed[0])
                            } finally {
                                trace?.end()
                            }
                        }
                        if (bitmap !== previousBitmap) {
                            frame.value = bitmap.asImageBitmap()
                            previousBitmap = bitmap
                        }
                        schedule.rendered(elapsed[0])
                        val spent = SystemClock.uptimeMillis() - started
                        // Bound CPU duty on slower devices; never queue frames to catch up.
                        delay(maxOf(50L - spent, spent * 2, 1L))
                    }
                }
            } finally {
                withContext(NonCancellable + artworkRenderDispatcher) { renderer?.close() }
            }
        }
        // A separate display list keeps background invalidations local inside the backdrop capture.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val bitmap = frame.value
            if (bitmap == null) drawRect(Color(0xFF202227))
            else {
                // Crop the overscan after blur, matching the original 1.3x surface.
                val cropWidth = (bitmap.width / 1.3f).roundToInt().coerceAtLeast(1)
                val cropHeight = (bitmap.height / 1.3f).roundToInt().coerceAtLeast(1)
                drawImage(bitmap,
                    srcOffset = IntOffset((bitmap.width - cropWidth) / 2, (bitmap.height - cropHeight) / 2),
                    srcSize = IntSize(cropWidth, cropHeight),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low)
            }
        }
    }
}
