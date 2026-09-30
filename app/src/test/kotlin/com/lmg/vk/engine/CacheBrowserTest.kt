package com.lmg.vk.engine

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class CacheBrowserTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun deletingOneLyricsFilePreservesOtherLyricsAndAuthoredText() {
        val cache = temporary.newFolder("cache")
        val files = temporary.newFolder("files")
        val roots = cacheCategoryDirectories(cache, files, CacheCategory.LYRICS)
        roots.forEach { it.mkdirs() }
        val chosen = File(roots.first(), "one.lrc").apply { writeText("first") }
        val retained = File(roots.first(), "two.lrc").apply { writeText("second") }
        val authored = File(files, "mylyrics/own.lrc").apply { parentFile.mkdirs(); writeText("own") }
        deleteCacheFile(chosen, roots)
        deleteCacheFile(chosen, roots) // Already evicted entries are harmless.
        assertFalse(chosen.exists())
        assertEquals("second", retained.readText())
        assertEquals("own", authored.readText())
        assertThrows(IllegalArgumentException::class.java) { deleteCacheFile(authored, roots) }
    }

    @Test fun directoryAndTraversalCannotDeleteOtherStorage() {
        val root = temporary.newFolder("lrclib")
        val protected = temporary.newFile("download.mp3").apply { writeText("audio") }
        assertThrows(IllegalArgumentException::class.java) { deleteCacheFile(File(root, "../download.mp3"), listOf(root)) }
        assertThrows(IllegalArgumentException::class.java) { deleteCacheFile(root, listOf(root)) }
        assertEquals("audio", protected.readText())
    }

    @Test fun symlinkCannotDeleteAuthoredLyrics() {
        val root = temporary.newFolder("lrclib")
        val protected = temporary.newFile("own.lrc").apply { writeText("own") }
        val link = File(root, "link.lrc")
        Files.createSymbolicLink(link.toPath(), protected.toPath())
        assertThrows(IllegalArgumentException::class.java) { deleteCacheFile(link, listOf(root)) }
        assertEquals("own", protected.readText())
    }

    @Test fun trustedAppStorageMayHaveAndroidStyleDirectoryAlias() {
        val actual = temporary.newFolder("actual")
        val alias = File(temporary.root, "app-storage-alias")
        Files.createSymbolicLink(alias.toPath(), actual.toPath())
        File(actual, "song.lrc").writeText("lyrics")
        deleteCacheFile(File(alias, "song.lrc"), listOf(alias))
        assertFalse(File(actual, "song.lrc").exists())
    }

    @Test fun mergeLegacyCopiesButKeepOtherProvidersAndRecordingsSeparate() {
        val apple = CacheEntry("current", "Песня", "Артист", "Apple TTML", 10, listOf("current"), recording = "200000")
        val legacy = apple.copy(id = "legacy", refs = listOf("legacy"))
        val anotherRecording = apple.copy(id = "long", refs = listOf("long"), recording = "240000")
        val lrclib = apple.copy(id = "lrclib", refs = listOf("lrclib"), source = "LRCLIB")
        val merged = mergeCacheEntries(CacheCategory.LYRICS, listOf(apple, legacy, anotherRecording, lrclib))
        assertEquals(3, merged.size)
        val chosen = merged.single { "current" in it.refs }
        assertEquals(listOf("current", "legacy"), chosen.refs)
        assertEquals(20L, chosen.bytes)
        assertFalse("long" in chosen.refs)
        assertFalse("lrclib" in chosen.refs)
    }

    @Test fun searchCombinesTitleArtistAndProviderInAnyOrder() {
        val entry = CacheEntry("id", "По-другому", "Лолита, Коста Лакоста", "Apple TTML", 50, listOf("path"))
        assertTrue(entry.matches("  ЛАКОСТА по-другому Apple  "))
        assertTrue(entry.matches(""))
        assertFalse(entry.matches("Лолита LRCLIB"))
    }
}
