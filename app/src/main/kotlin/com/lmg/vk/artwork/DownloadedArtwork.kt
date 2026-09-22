package com.lmg.vk.artwork

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lmg.vk.audio.Id3Utils
import com.lmg.vk.audio.Mp3TagWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class DownloadedArtwork(val url: String, val bytes: ByteArray, val localPath: String?) {
    companion object {
        suspend fun load(
            context: Context,
            query: ArtworkQuery,
            fallback: String?,
            destination: File,
        ): DownloadedArtwork? = withContext(Dispatchers.IO) {
            val preferred = ItunesArtworkRepository.find(context, query)
            for (url in listOfNotNull(preferred, fallback?.takeIf(String::isNotBlank)).distinct()) {
                try {
                    var bytes = readCached(context, url)
                    if (bytes == null) {
                        val result = context.imageLoader.execute(ImageRequest.Builder(context)
                            .data(url).size(600).allowHardware(false).build())
                        if (result !is SuccessResult) continue
                        bytes = readCached(context, result.diskCacheKey ?: url)
                    }
                    if (bytes == null) continue
                    if (Id3Utils.sniffImageMime(bytes) == null) continue
                    val localPath = runCatching {
                        destination.parentFile?.mkdirs()
                        val temporary = File.createTempFile("cover-", ".tmp", destination.parentFile)
                        try {
                            temporary.writeBytes(bytes)
                            Files.move(temporary.toPath(), destination.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            destination.absolutePath
                        } finally {
                            temporary.delete()
                        }
                    }.getOrNull()
                    return@withContext DownloadedArtwork(url, bytes, localPath)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
            null
        }

        @OptIn(coil.annotation.ExperimentalCoilApi::class)
        private fun readCached(context: Context, key: String): ByteArray? =
            context.imageLoader.diskCache?.openSnapshot(key)?.use { snapshot ->
                val file = snapshot.data.toFile()
                if (file.length() !in 1..Mp3TagWriter.MAX_COVER_BYTES.toLong()) null else file.readBytes()
            }
    }
}
