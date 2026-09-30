package com.lmg.vk.engine

import android.content.Context
import coil.imageLoader
import com.lmg.vk.artwork.AppleArtworkRepository
import com.lmg.vk.artwork.ItunesArtworkRepository
import com.lmg.vk.artwork.MotionArtworkRepository
import com.lmg.vk.ui.lyrics.LoadedLyricsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class CacheCategory { LYRICS, ARTWORK, MOTION, AUDIO }

/** Explicit cache paths only: never downloads, offline_motion or user-authored mylyrics. */
internal fun cacheCategoryDirectories(cacheDir: File, filesDir: File, category: CacheCategory): List<File> =
    when (category) {
        CacheCategory.LYRICS -> listOf(File(cacheDir, "lrclib"),
            File(filesDir, "lyrics/ttml"), File(filesDir, "lyrics/apple_ttml"),
            File(filesDir, "lyrics/lmg_lyrics_plus"))
        CacheCategory.ARTWORK -> listOf("image_cache", "itunes_artwork_v3", "apple_artwork_v3")
            .map { File(cacheDir, it) }
        // SimpleCache and Coil are managed through their own APIs.
        else -> emptyList()
    }

object AppCacheManager {
    private val lock = Mutex()

    suspend fun sizes(context: Context): Map<CacheCategory, Long> = withContext(Dispatchers.IO) {
        lock.withLock {
            CacheCategory.entries.associateWith { category ->
                when (category) {
                    CacheCategory.AUDIO -> MediaCacheManager.getCacheSizeBytes(context)
                    CacheCategory.MOTION -> MotionArtworkRepository.cacheSize(context)
                    else -> cacheCategoryDirectories(context.cacheDir, context.filesDir, category)
                        .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } +
                        if (category == CacheCategory.ARTWORK) context.imageLoader.diskCache?.size ?: 0L else 0L
                }
            }
        }
    }

    /** Null selects all four media caches. Active playback may cache fresh data afterwards. */
    suspend fun clear(context: Context, category: CacheCategory?) = withContext(Dispatchers.IO) {
        lock.withLock {
            for (selected in category?.let(::listOf) ?: CacheCategory.entries) {
                when (selected) {
                    CacheCategory.AUDIO -> MediaCacheManager.clearCache(context)
                    CacheCategory.MOTION -> MotionArtworkRepository.clearCache(context)
                    CacheCategory.LYRICS -> {
                        LoadedLyricsStore.clear()
                        LyricsParser.trimCache()
                    }
                    CacheCategory.ARTWORK -> {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                        AppleArtworkRepository.clearMemoryCache()
                        ItunesArtworkRepository.clearMemoryCache()
                    }
                }
                cacheCategoryDirectories(context.cacheDir, context.filesDir, selected).forEach { dir ->
                    // Keep the directory so concurrent cache writers can continue normally.
                    dir.listFiles()?.forEach { child ->
                        if (!child.deleteRecursively() && child.exists()) throw IOException("Cache cleanup failed")
                    }
                }
            }
        }
    }
}
