package com.lmg.vk.engine

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.util.concurrent.Callable
import kotlin.math.max

/**
 * BitmapLoader для MediaSession с жёстким пределом размера результата.
 *
 * Сетевые/content/file URI обрабатывает штатный media3 loader. Локальные
 * `android.resource://package/id` декодируются через Resources напрямую: это
 * работает и для сжатых drawable WebP, которые нельзя открыть через
 * `openRawResourceFd`.
 */
@OptIn(UnstableApi::class)
class BoundedArtworkBitmapLoader(
    context: Context,
    private val maximumOutputDimension: Int = 512,
) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get()
    private val delegate = DataSourceBitmapLoader(
        executor,
        DefaultDataSource.Factory(appContext),
        /* options = */ null,
        maximumOutputDimension,
    )

    init {
        require(maximumOutputDimension > 0) { "maximumOutputDimension must be positive" }
    }

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE) {
            return delegate.loadBitmap(uri)
        }
        return executor.submit(Callable { decodeLocalResource(uri) })
    }

    private fun decodeLocalResource(uri: Uri): Bitmap {
        val packageName = uri.authority
        if (!packageName.isNullOrEmpty() && packageName != appContext.packageName) {
            throw IOException("Artwork resource belongs to another package: $packageName")
        }
        val resourceId = uri.pathSegments.singleOrNull()?.toIntOrNull()
            ?: throw IOException("Invalid artwork resource URI: $uri")

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(appContext.resources, resourceId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unable to read artwork resource bounds: $uri")
        }

        var sampleSize = 1
        var largerDimension = max(bounds.outWidth, bounds.outHeight)
        while (largerDimension > maximumOutputDimension) {
            sampleSize *= 2
            largerDimension /= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(appContext.resources, resourceId, options)
            ?: throw IOException("Unable to decode artwork resource: $uri")
    }
}
