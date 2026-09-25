package com.lmg.vk.artwork

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

/** Complete downloads, outside cacheDir. Playback uses a factory with NO network upstream. */
internal object OfflineMotionStore {
    private val writer = Mutex()
    private var cache: SimpleCache? = null
    private fun root(context: Context) = File(context.filesDir, "offline_motion").apply { mkdirs() }
    private fun index(context: Context) = OfflineMotionIndex(File(root(context), "index"))
    private fun cache(context: Context): SimpleCache = synchronized(this) {
        cache ?: SimpleCache(File(root(context), "media"), NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext)).also { cache = it }
    }
    private fun factory(context: Context, network: Boolean) = CacheDataSource.Factory()
        .setCache(cache(context)).setUpstreamDataSourceFactory(if (network)
            DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(15_000) else null)

    suspend fun find(context: Context, query: ArtworkQuery, trackId: String? = null): MotionArtworkSource? =
        withContext(Dispatchers.IO) {
            index(context).find(trackId, query)?.let { MotionArtworkSource(it, factory(context, false)) }
        }

    suspend fun save(context: Context, trackId: String, query: ArtworkQuery, artwork: MotionArtwork?) =
        withContext(Dispatchers.IO) {
            if (artwork == null) return@withContext
            writer.withLock {
                if (index(context).find(trackId, query) != null) return@withLock
                if (root(context).usableSpace < 128L * 1024 * 1024) throw IOException("Not enough space for offline motion")
                val storedCache = cache(context)
                val previousKeys = storedCache.keys.toSet()
                var complete = false
                try {
                val upstream = factory(context, true)
                val hls = artwork.hls
                if (hls == null) {
                    val downloader = ProgressiveDownloader(MediaItem.fromUri(artwork.playbackUrl), upstream, Executor { it.run() })
                    try {
                        withTimeoutOrNull(180_000) { runInterruptible { downloader.download { _, _, _ ->
                            if (root(context).usableSpace < 32L * 1024 * 1024) throw IOException("Not enough disk space")
                        } }; true } ?: throw IOException("Motion download timed out")
                        currentCoroutineContext().ensureActive()
                        index(context).commit(trackId, query, artwork.copy(preview = null))
                        complete = true
                    } finally { downloader.cancel() }
                    return@withLock
                }
                fun playlist(url: String) = DataSourceInputStream(upstream.createDataSource(), DataSpec(Uri.parse(url))).use {
                    HlsPlaylistParser().parse(Uri.parse(url), it)
                }
                val master = runInterruptible { playlist(hls) }
                val variant = if (master is HlsMultivariantPlaylist) master.variants.filter {
                    it.format.codecs?.contains("avc1") == true && (it.format.roleFlags and C.ROLE_FLAG_TRICK_PLAY) == 0
                }.maxWithOrNull(compareBy<HlsMultivariantPlaylist.Variant> {
                    it.format.width.toLong() * it.format.height
                }.thenBy { it.format.peakBitrate })?.url?.toString()
                    ?: throw IOException("Motion has no AVC rendition") else hls
                val media = (if (variant == hls) master else runInterruptible { playlist(variant) }) as? HlsMediaPlaylist
                    ?: throw IOException("Invalid motion playlist")
                if (!media.hasEndTag || media.segments.isEmpty() || media.durationUs > 300_000_000L)
                    throw IOException("Motion must be a finite clip")
                if (media.segments.any { it.drmInitData != null }) throw IOException("Unsupported protected motion")
                // Direct media playlist fixes the rendition: offline playback cannot choose an uncached variant.
                val downloader = HlsDownloader(MediaItem.fromUri(variant), upstream, Executor { it.run() })
                try {
                    withTimeoutOrNull(180_000) { runInterruptible { downloader.download { _, _, _ ->
                        if (root(context).usableSpace < 32L * 1024 * 1024) throw IOException("Not enough disk space")
                    } }; true } ?: throw IOException("Motion download timed out")
                    currentCoroutineContext().ensureActive()
                    index(context).commit(trackId, query, artwork.copy(hls = variant, mp4 = null, preview = null))
                    complete = true
                } finally { downloader.cancel() }
                } finally {
                    // Keep resources owned by completed downloads. Remove only this attempt's new keys.
                    if (!complete) (storedCache.keys - previousKeys).forEach { storedCache.removeResource(it) }
                }
            }
        }

    suspend fun remove(context: Context, trackId: String) = withContext(Dispatchers.IO) {
        writer.withLock {
            val index = index(context)
            val removed = index.remove(trackId) ?: return@withLock
            if (!index.containsUrl(removed.playbackUrl)) runInterruptible {
                if (removed.hls != null)
                    HlsDownloader(MediaItem.fromUri(removed.playbackUrl), factory(context, false), Executor { it.run() }).remove()
                else ProgressiveDownloader(MediaItem.fromUri(removed.playbackUrl), factory(context, false), Executor { it.run() }).remove()
            }
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        writer.withLock {
            index(context).clear()
            cache(context).keys.toList().forEach { cache(context).removeResource(it) }
        }
    }
}
