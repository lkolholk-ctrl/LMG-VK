package com.lmg.vk.engine

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppCacheDirectoriesTest {
    @Test fun cleanupPathsExcludeDownloadsAndUserLyrics() {
        val cache = File("/app/cache")
        val files = File("/app/files")
        val roots = CacheCategory.entries.flatMap { cacheCategoryDirectories(cache, files, it) }
        val protected = listOf("downloads/song.mp3", "offline_motion/media/video", "mylyrics/song.lrc")
        protected.forEach { path ->
            val saved = File(files, path).toPath()
            assertFalse(roots.any { saved.startsWith(it.toPath()) })
        }
        assertTrue(File(files, "lyrics/ttml") in cacheCategoryDirectories(cache, files, CacheCategory.LYRICS))
        assertFalse(roots.contains(cache))
        assertFalse(roots.contains(files))
    }

    @Test fun managedCacheDirectoriesAreNotDeletedAsOrdinaryFiles() {
        val roots = CacheCategory.entries.flatMap {
            cacheCategoryDirectories(File("/app/cache"), File("/app/files"), it)
        }.map { it.name }
        assertFalse(roots.contains("media3_cache"))
        assertFalse(roots.contains("motion_stream_v2"))
        assertFalse(roots.contains("coil_cache"))
    }
}
