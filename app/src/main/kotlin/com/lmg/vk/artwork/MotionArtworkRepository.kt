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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class MotionArtworkSource(val artwork: MotionArtwork, val dataSourceFactory: CacheDataSource.Factory)

internal object MotionArtworkRepository {
    private var videoCache: SimpleCache? = null

    suspend fun cacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        synchronized(this@MotionArtworkRepository) {
            videoCache?.cacheSpace ?: File(context.cacheDir, "motion_stream_v2")
                .walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        synchronized(this@MotionArtworkRepository) {
            val current = videoCache
            if (current != null) {
                current.keys.toList().forEach(current::removeResource)
            } else {
                val dir = File(context.cacheDir, "motion_stream_v2")
                if (dir.exists()) SimpleCache.delete(dir, StandaloneDatabaseProvider(context.applicationContext))
            }
        }
    }

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
    suspend fun load(context: Context, query: ArtworkQuery, trackId: String? = null): MotionArtworkSource? = withContext(Dispatchers.IO) {
        try {
            OfflineMotionStore.find(context, query, trackId)?.let { return@withContext it }
            AppleArtworkRepository.find(context, query)?.motion?.let {
                MotionArtworkSource(it, streamingFactory(context))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            DebugLog.add("MOTION lookup failed ${error.javaClass.simpleName}: ${error.message}")
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

}
