package com.lmg.vk.artwork

import android.content.Context
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.lyrics.apple.AppleLyricsConfig
import com.lmg.vk.network.installVpnBypass
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Shared metadata cache and in-flight requests for cover, player and prefetch callers. */
internal object AppleArtworkRepository {
    fun clearMemoryCache() = synchronized(lock) { cache.clear() }
    private data class Entry(val value: AppleArtwork?, val expires: Long)
    private data class Flight(val task: Deferred<AppleArtwork?>, var users: Int)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val cache = object : LinkedHashMap<ArtworkQuery, Entry>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ArtworkQuery, Entry>?) = size > 256
    }
    private val flights = mutableMapOf<ArtworkQuery, Flight>()
    private val gate = Semaphore(3)
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).installVpnBypass().build()
    }

    suspend fun find(context: Context, request: ArtworkQuery): AppleArtwork? {
        val query = ItunesArtworkMatcher.lookupQuery(request)
        if (!query.usable) return null
        val flight = synchronized(lock) {
            cache[query]?.takeIf { it.expires > System.currentTimeMillis() }?.let { return it.value }
            flights[query]?.also { it.users++ } ?: Flight(scope.async(start = CoroutineStart.LAZY) {
                load(context.applicationContext, query)
            }, 1).also { flights[query] = it }
        }
        return try { flight.task.await() } finally {
            synchronized(lock) {
                if (--flight.users == 0) {
                    if (flights[query] === flight) flights.remove(query)
                    flight.task.cancel()
                }
            }
        }
    }

    private suspend fun load(context: Context, query: ArtworkQuery): AppleArtwork? {
        val url = MotionArtwork.requestUrl(query).toString()
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val dir = File(context.cacheDir, "apple_artwork_v3").apply { mkdirs() }
        val file = File(dir, "$key.json")
        com.lmg.vk.engine.CacheCatalog.remember(context, file.absolutePath, query.title, query.artist)
        val saved = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
        val expires = saved?.get("expires")?.jsonPrimitive?.longOrNull ?: 0
        if (expires > System.currentTimeMillis()) {
            val body = saved?.get("body")?.jsonPrimitive?.contentOrNull
            if (body != null) {
                val parsed = runCatching { AppleArtwork.parse(body, query) }
                if (parsed.isSuccess) {
                    val value = parsed.getOrNull()
                    synchronized(lock) { cache[query] = Entry(value, expires) }
                    DebugLog.add("ARTWORK cache=disk title='${query.title}' cover=${value?.cover != null} motion=${value?.motion != null}")
                    return value
                }
            }
        }
        val start = System.nanoTime()
        val body = gate.withPermit {
            try { fetch(url) } catch (e: IOException) {
                if (e is HttpFailure && e.code !in listOf(408, 429, 500, 502, 503, 504)) throw e
                delay(750)
                fetch(url)
            }
        }
        // Malformed responses, authentication and network failures never become cached misses.
        val value = AppleArtwork.parse(body, query)
        val until = System.currentTimeMillis() + if (value == null) ArtworkCachePolicy.MISS_TTL_MS
            else TimeUnit.DAYS.toMillis(1)
        synchronized(lock) { cache[query] = Entry(value, until) }
        runCatching {
            file.writeText(JsonObject(mapOf("expires" to JsonPrimitive(until), "body" to JsonPrimitive(body))).toString())
            dir.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(512).forEach(File::delete)
        }
        DebugLog.add("ARTWORK cache=network title='${query.title}' cover=${value?.cover != null} motion=${value?.motion != null} elapsedMs=${(System.nanoTime() - start) / 1_000_000}")
        return value
    }

    private class HttpFailure(val code: Int) : IOException("Artwork HTTP $code")

    private suspend fun fetch(url: String): String {
        val call = http.newCall(Request.Builder().url(url)
            .header("X-API-Key", AppleLyricsConfig.API_KEY).header("Accept", "application/json").build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        return response.use {
            if (!it.isSuccessful) throw HttpFailure(it.code)
            val source = it.body?.source() ?: throw IOException("Empty artwork response")
            if (source.request(262_145)) throw IOException("Artwork response too large")
            source.readUtf8()
        }
    }
}
