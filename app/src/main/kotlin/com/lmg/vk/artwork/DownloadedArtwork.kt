package com.lmg.vk.artwork

import android.content.Context
import coil.imageLoader
import com.lmg.vk.network.installVpnBypass
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import com.lmg.vk.audio.Id3Utils
import com.lmg.vk.audio.Mp3TagWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class DownloadedArtwork(val url: String, val bytes: ByteArray, val localPath: String?, val preferred: Boolean) {
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
                    val bytes = readCached(context, url) ?: if (url.startsWith("/")) {
                        val local = File(url)
                        if (local.length() !in 1..Mp3TagWriter.MAX_COVER_BYTES.toLong()) continue
                        local.readBytes()
                    } else fetch(url)
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
                    return@withContext DownloadedArtwork(url, bytes, localPath, url == preferred)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
            null
        }

        private val http by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).installVpnBypass().build() }

        private suspend fun fetch(url: String): ByteArray {
            val call = http.newCall(Request.Builder().url(url).build())
            val response = suspendCancellableCoroutine<Response> { c ->
                c.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (c.isActive) c.resumeWithException(e) }
                    override fun onResponse(call: Call, response: Response) {
                        c.resume(response) { _, value, _ -> value.close() }
                    }
                })
            }
            return response.use {
                if (!it.isSuccessful) throw IOException("Cover HTTP ${it.code}")
                val body = it.body ?: throw IOException("Empty cover")
                val source = body.source()
                if (source.request(Mp3TagWriter.MAX_COVER_BYTES.toLong() + 1)) throw IOException("Cover too large")
                source.readByteArray().also { bytes ->
                    if (body.contentLength() >= 0 && body.contentLength() != bytes.size.toLong()) throw IOException("Truncated cover")
                }
            }
        }

        @OptIn(coil.annotation.ExperimentalCoilApi::class)
        private fun readCached(context: Context, key: String): ByteArray? =
            context.imageLoader.diskCache?.openSnapshot(key)?.use { snapshot ->
                val file = snapshot.data.toFile()
                if (file.length() !in 1..Mp3TagWriter.MAX_COVER_BYTES.toLong()) null else file.readBytes()
            }
    }
}
