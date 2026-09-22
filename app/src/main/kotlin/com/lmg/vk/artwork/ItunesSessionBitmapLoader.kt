package com.lmg.vk.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class ItunesSessionBitmapLoader(
    context: Context,
    private val scope: CoroutineScope,
    private val delegate: BitmapLoader,
) : BitmapLoader by delegate {
    private val context = context.applicationContext

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val query = ArtworkQuery(metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(),
            metadata.durationMs ?: 0L, metadata.albumTitle?.toString().orEmpty())
        if (!query.usable) return delegate.loadBitmapFromMetadata(metadata)
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val cover = ItunesArtworkRepository.find(context, query)
                val bitmap = cover?.let {
                    val result = context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(it).size(512).allowHardware(false).build())
                    (result.drawable as? BitmapDrawable)?.bitmap
                }
                if (bitmap != null) future.set(bitmap)
                else {
                    val fallback = delegate.loadBitmapFromMetadata(metadata)
                    if (fallback == null) future.setException(java.io.IOException("No track artwork"))
                    else {
                        future.addListener({ if (future.isCancelled) fallback.cancel(true) }, MoreExecutors.directExecutor())
                        future.set(fallback.get())
                    }
                }
            } catch (cancelled: CancellationException) {
                future.cancel(false)
            } catch (error: Exception) {
                future.setException(error)
            }
        }
        job.invokeOnCompletion { if (it is CancellationException) future.cancel(false) }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }
}
