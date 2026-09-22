package com.lmg.vk.artwork

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import java.net.URI
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.network.installVpnBypass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal data class MotionArtworkSource(val artwork: MotionArtwork, val dataSourceFactory: CacheDataSource.Factory)

internal object MotionArtworkRepository {
    private var videoCache: SimpleCache? = null
    private val memory = linkedMapOf<String, Pair<Long, MotionArtwork?>>()

    private fun streamingFactory(context: Context): CacheDataSource.Factory {
        val cache = synchronized(this) {
            videoCache ?: SimpleCache(File(context.cacheDir, "motion_stream_v2"),
                LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
                StandaloneDatabaseProvider(context.applicationContext)).also { videoCache = it }
        }
        return CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8_000).setReadTimeoutMs(15_000))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS).installVpnBypass().build()
    }

    suspend fun load(context: Context, query: ArtworkQuery): MotionArtworkSource? = withContext(Dispatchers.IO) {
        if (!query.usable) return@withContext null
        val directory = File(context.cacheDir, "motion_artwork_v1").apply { mkdirs() }
        val canonical = ItunesArtworkMatcher.lookupQuery(query)
        if (!canonical.usable) return@withContext null
        val key = hash("${canonical.title}\u0000${canonical.artist}" +
            canonical.album.takeIf(String::isNotBlank)?.let { "\u0000$it" }.orEmpty())
        val record = File(directory, "$key.json")
        try {
            synchronized(memory) { memory[key] }?.takeIf { it.first > System.currentTimeMillis() }?.let {
                return@withContext it.second?.let { artwork -> MotionArtworkSource(artwork, streamingFactory(context)) }
            }
            val saved = runCatching { Json.parseToJsonElement(record.readText()).jsonObject }.getOrNull()
            val savedBody = saved?.get("body")?.jsonPrimitive?.contentOrNull
            val savedArtwork = savedBody?.let { runCatching { MotionArtwork.parse(it, canonical) }.getOrNull() }
            val savedExpires = saved?.get("expires")?.jsonPrimitive?.longOrNull ?: 0L
            val savedVersion = saved?.get("lookupVersion")?.jsonPrimitive?.intOrNull ?: 0
            val valid = savedBody != null && ArtworkCachePolicy.canReuse(savedArtwork != null,
                savedExpires, savedVersion, System.currentTimeMillis())
            val body = if (valid) requireNotNull(savedBody) else {
                val url = MotionArtwork.requestUrl(canonical)
                getText(url.toString()).also { json ->
                    Json.parseToJsonElement(json).jsonObject
                    val metadata = MotionArtwork.parse(json, query)
                    val expires = System.currentTimeMillis() +
                        if (metadata == null) ArtworkCachePolicy.MISS_TTL_MS else TimeUnit.DAYS.toMillis(7)
                    record.writeText(JsonObject(mapOf("expires" to JsonPrimitive(expires),
                        "lookupVersion" to JsonPrimitive(ArtworkCachePolicy.LOOKUP_VERSION),
                        "body" to JsonPrimitive(json))).toString())
                }
            }
            val artwork = MotionArtwork.parse(body, query)
            val expires = if (valid) savedExpires
                else System.currentTimeMillis() +
                    if (artwork == null) ArtworkCachePolicy.MISS_TTL_MS else TimeUnit.DAYS.toMillis(7)
            synchronized(memory) {
                memory[key] = expires to artwork
                while (memory.size > 128) memory.remove(memory.keys.first())
            }
            directory.listFiles().orEmpty().filter { it.extension == "mp4" || it.extension == "part" }
                .forEach(File::delete)
            directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending(File::lastModified)
                .drop(256).forEach(File::delete)
            artwork?.let { MotionArtworkSource(it, streamingFactory(context)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            DebugLog.add("MOTION lookup failed ${error.javaClass.simpleName}")
            null
        }
    }

    suspend fun prefetch(context: Context, query: ArtworkQuery) = withContext(Dispatchers.IO) {
        val source = load(context, query) ?: return@withContext
        val hls = source.artwork.hls ?: return@withContext
        val jobContext = coroutineContext
        try {
            fun playlist(url: String) = DataSourceInputStream(source.dataSourceFactory.createDataSource(),
                DataSpec(Uri.parse(url))).use { HlsPlaylistParser().parse(Uri.parse(url), it) }
            val master = playlist(hls)
            val media = if (master is HlsMultivariantPlaylist) {
                val variant = master.variants.filter {
                    it.format.codecs?.contains("avc1.") == true && (it.format.roleFlags and C.ROLE_FLAG_TRICK_PLAY) == 0
                }.maxWithOrNull(compareBy<HlsMultivariantPlaylist.Variant> {
                    it.format.width.toLong() * it.format.height
                }.thenBy { it.format.peakBitrate }) ?: return@withContext
                jobContext.ensureActive()
                playlist(variant.url.toString()) as? HlsMediaPlaylist
            } else master as? HlsMediaPlaylist
            val segment = media?.segments?.firstOrNull() ?: return@withContext
            if (segment.fullSegmentEncryptionKeyUri != null || segment.drmInitData != null) return@withContext
            for (part in listOfNotNull(segment.initializationSegment, segment)) {
                jobContext.ensureActive()
                val length = part.byteRangeLength
                if (length > 12L * 1024 * 1024) continue
                val uri = URI(media.baseUri).resolve(part.url).toString()
                if (!uri.startsWith("https://")) continue
                CacheWriter(source.dataSourceFactory.createDataSource(),
                    DataSpec.Builder().setUri(uri).setPosition(part.byteRangeOffset)
                        .setLength(if (length == C.LENGTH_UNSET.toLong()) 12L * 1024 * 1024 else length).build(),
                    null) { _, _, _ -> jobContext.ensureActive() }.cache()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { DebugLog.add("MOTION prefetch ${error.javaClass.simpleName}") }
    }

    private suspend fun getText(url: String): String = fetch(url).use {
        if (!it.isSuccessful) throw IOException("Motion HTTP ${it.code}")
        val body = it.body ?: throw IOException("Empty motion response")
        val source = body.source()
        if (source.request(262_145)) throw IOException("Motion response too large")
        source.readUtf8()
    }

    private suspend fun fetch(url: String): Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url).build())
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

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
