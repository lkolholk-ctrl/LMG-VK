package com.lmg.vk.engine.lyrics.apple

import com.lmg.vk.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

interface AppleTtmlClient {
    suspend fun fetch(
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null
    ): Result<String>
}

class DefaultAppleTtmlClient(
    private val proxyBaseUrl: String = "http://50.117.3.97:8777",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .build()
) : AppleTtmlClient {

    override suspend fun fetch(
        title: String,
        artist: String,
        durationMs: Long,
        language: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (title.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Title cannot be blank"))
        }

        val url = runCatching {
            "$proxyBaseUrl/v2/lyrics/ttml".toHttpUrl().newBuilder()
                .addQueryParameter("title", title.trim())
                .addQueryParameter("artist", artist.trim())
                .apply {
                    if (!language.isNullOrBlank()) {
                        addQueryParameter("lang", language.trim())
                    } else {
                        addQueryParameter("lang", "all")
                    }
                    if (durationMs > 0L) {
                        addQueryParameter("duration", (durationMs / 1000L).toString())
                    }
                }
                .build()
        }.getOrElse {
            return@withContext Result.failure(it)
        }

        DebugLog.add("apple client fetch title='$title' artist='$artist' durSec=${durationMs / 1000L}")

        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/plain, application/xml, application/ttml+xml, */*")
                .header("User-Agent", "LMG-VK/1.1")
                .build()

            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    DebugLog.add("apple client fail error=${e.javaClass.simpleName}: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    try {
                        response.use { resp ->
                            if (!resp.isSuccessful) {
                                DebugLog.add("apple client error status=$code")
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(IOException("HTTP error $code")))
                                }
                                return
                            }
                            val body = resp.body?.string().orEmpty()
                            val isTtml = body.trimStart().startsWith("<tt", ignoreCase = true)
                            DebugLog.add("apple client success status=$code bytes=${body.length} isTtml=$isTtml")
                            if (!isTtml || body.isBlank()) {
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(IOException("Invalid non-TTML response")))
                                }
                                return
                            }
                            if (continuation.isActive) {
                                continuation.resume(Result.success(body))
                            }
                        }
                    } catch (e: Throwable) {
                        DebugLog.add("apple client exception=${e.message}")
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(e))
                        }
                    }
                }
            })
        }
    }
}
