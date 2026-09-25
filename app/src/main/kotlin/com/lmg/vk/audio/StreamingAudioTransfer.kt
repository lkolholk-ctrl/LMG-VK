package com.lmg.vk.audio

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/** Streaming execute keeps a whole playlist out of the Java heap. Partial responses never succeed. */
internal object StreamingAudioTransfer {
    suspend fun download(client: HttpClient, url: String, file: File, progress: (Float) -> Unit) {
        try {
            client.prepareGet(url).execute { response ->
                if (response.status.value != 200) throw IOException("Audio HTTP ${response.status.value}")
                val type = response.headers["Content-Type"].orEmpty().lowercase()
                if (type.startsWith("text/") || "json" in type || "mpegurl" in type)
                    throw IOException("Audio endpoint returned $type")
                val expected = response.headers["Content-Length"]?.toLongOrNull() ?: -1
                val channel = response.bodyAsChannel()
                var count = 0L
                try {
                    file.outputStream().buffered(64 * 1024).use { output ->
                        val bytes = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = channel.readAvailable(bytes, 0, bytes.size)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(bytes, 0, read); count += read
                            if (expected > 0) progress((count.toFloat() / expected).coerceIn(0f, 1f))
                        }
                    }
                    requireComplete(count, expected)
                } finally { channel.cancel(null) }
            }
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun requireComplete(count: Long, expected: Long) {
        if (count <= 0 || expected >= 0 && count != expected) throw IOException("Incomplete audio: $count / $expected bytes")
    }
}
