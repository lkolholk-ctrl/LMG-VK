package com.lmg.vk.artwork

import android.content.Context
import android.os.SystemClock
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lmg.vk.network.installVpnBypass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ItunesArtworkRepository {
    private data class Entry(val artwork: String?, val expires: Long, val originalQuality: Boolean = true,
        val lookupVersion: Int = ArtworkCachePolicy.LOOKUP_VERSION)
    private data class Flight(val result: Deferred<String?>, var users: Int)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val cache = object : LinkedHashMap<ArtworkQuery, Entry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ArtworkQuery, Entry>?) = size > 1_000
    }
    private val flights = mutableMapOf<ArtworkQuery, Flight>()
    private val networkGate = Mutex()
    private val candidates = ArtworkCandidateCache()
    private var nextRequest = 0L
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS).installVpnBypass().build()
    }

    fun cached(request: ArtworkQuery): ArtworkLoadState? {
        val query = ItunesArtworkMatcher.lookupQuery(request)
        return synchronized(lock) {
            cache[query]?.takeIf { it.expires > System.currentTimeMillis() }
                ?.let { ArtworkLoadState(true, it.artwork) }
        }
    }

    suspend fun prefetchSearch(term: String) = withContext(Dispatchers.IO) {
        val cleaned = ItunesArtworkMatcher.cleanMetadata(term)
        if (cleaned.isBlank()) return@withContext
        try {
            searchCandidates(cleaned)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
        Unit
    }

    suspend fun find(context: Context, request: ArtworkQuery): String? {
        val query = ItunesArtworkMatcher.lookupQuery(request)
        if (!query.usable) return null
        val flight = synchronized(lock) {
            cache[query]?.takeIf { it.expires > System.currentTimeMillis() }?.let { return it.artwork }
            flights[query]?.also { it.users++ } ?: Flight(
                scope.async(start = CoroutineStart.LAZY) { load(context.applicationContext, query) }, 1,
            ).also { flights[query] = it }
        }
        return try {
            flight.result.await()
        } finally {
            synchronized(lock) {
                flight.users--
                if (flight.users == 0) {
                    if (flights[query] === flight) flights.remove(query)
                    flight.result.cancel()
                }
            }
        }
    }

    private suspend fun load(context: Context, query: ArtworkQuery): String? = withContext(Dispatchers.IO) {
        val key = listOf(query.title, query.artist, query.durationMs.toString(), query.album)
            .joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "itunes_artwork_v2").apply { mkdirs() }
        val file = File(directory, "$hash.json")
        val stored = runCatching {
            val obj = Json.parseToJsonElement(file.readText()).jsonObject
            Entry(obj["artwork"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                obj["expires"]?.jsonPrimitive?.longOrNull ?: 0,
                obj["quality"]?.jsonPrimitive?.longOrNull == 1L,
                obj["lookupVersion"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0)
        }.getOrNull()
        if (stored != null && ArtworkCachePolicy.canReuse(stored.artwork != null, stored.expires,
                stored.lookupVersion, System.currentTimeMillis()) &&
            (stored.artwork == null || stored.originalQuality)) {
            synchronized(lock) { cache[query] = stored }
            return@withContext stored.artwork
        }
        var ttl = ArtworkCachePolicy.MISS_TTL_MS
        val selected = try {
            val artwork = stored?.takeIf { it.expires > System.currentTimeMillis() }?.artwork
                ?: search(query)?.artworkUrl
            if (artwork == null) null else {
                val workingUrl = ItunesArtworkQuality.urls(artwork).firstOrNull { url ->
                    context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(url).size(600).allowHardware(false).build()) is SuccessResult
                }
                if (workingUrl != null) {
                    ttl = TimeUnit.DAYS.toMillis(7)
                    workingUrl
                } else {
                    ttl = TimeUnit.MINUTES.toMillis(5)
                    null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ttl = TimeUnit.MINUTES.toMillis(5)
            null
        }
        val entry = Entry(selected, System.currentTimeMillis() + ttl)
        synchronized(lock) { cache[query] = entry }
        runCatching {
            file.writeText(JsonObject(mapOf("artwork" to JsonPrimitive(selected.orEmpty()),
                "lookupVersion" to JsonPrimitive(entry.lookupVersion),
                "expires" to JsonPrimitive(entry.expires), "quality" to JsonPrimitive(1))).toString())
            val files = directory.listFiles().orEmpty()
            if (files.size > 2_000) files.sortedBy(File::lastModified).take(files.size - 2_000).forEach(File::delete)
        }
        selected
    }

    private suspend fun search(query: ArtworkQuery): ItunesArtworkCandidate? {
        candidates.match(query)?.let { return it }
        return networkGate.withLock {
            candidates.match(query)?.let { return@withLock it }
            val term = "${query.artist} ${query.title}"
            ItunesArtworkMatcher.match(query, candidates.get(term) ?: requestCandidates(term).also {
                candidates.put(term, it)
            })
        }
    }

    private suspend fun searchCandidates(term: String): List<ItunesArtworkCandidate> = networkGate.withLock {
        candidates.get(term) ?: requestCandidates(term).also { candidates.put(term, it) }
    }

    private suspend fun requestCandidates(term: String): List<ItunesArtworkCandidate> {
        delay((nextRequest - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        nextRequest = SystemClock.elapsedRealtime() + 3_200
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("country", "US").addQueryParameter("media", "music")
            .addQueryParameter("entity", "song").addQueryParameter("limit", "25").build()
        val call = http.newCall(Request.Builder().url(url).build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use { response ->
            if (response.code == 429) nextRequest = SystemClock.elapsedRealtime() + 60_000
            if (!response.isSuccessful) throw java.io.IOException("iTunes HTTP ${response.code}")
            val body = response.body ?: throw java.io.IOException("Empty iTunes response")
            val root = Json.parseToJsonElement(body.string()).jsonObject
            val results = root["results"] as? JsonArray ?: throw java.io.IOException("Invalid iTunes response")
            val candidates = results.mapNotNull { value ->
                val item = value as? JsonObject ?: return@mapNotNull null
                fun text(key: String) = item[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text("kind") != "song") return@mapNotNull null
                ItunesArtworkCandidate(text("trackName"), text("artistName"),
                    item["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0,
                    text("collectionName"), text("artworkUrl100"), text("trackViewUrl"))
            }
            return candidates
        }
    }
}
