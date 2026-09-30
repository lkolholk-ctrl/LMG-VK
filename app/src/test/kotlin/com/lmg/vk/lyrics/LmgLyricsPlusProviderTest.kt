package com.lmg.vk.lyrics

import com.lmg.vk.engine.CacheCategory
import com.lmg.vk.engine.cacheCategoryDirectories
import com.lmg.vk.engine.lyrics.*
import com.lmg.vk.ui.lyrics.AccompanistLyricsAdapter
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LmgLyricsPlusProviderTest {
    @Test fun qqTtmlKeepsItsSourceAndWordTimingsInTheRenderer() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
            <body dur="3.000"><div><p begin="1.000" end="3.000"><span begin="1.000" end="2.000">Hello </span><span begin="2.000" end="3.000">world</span></p></div></body></tt>"""
        val content = LmgLyricsPlusProvider.content(raw)
        assertEquals(raw, content.value)
        assertEquals("LMG Lyrics Plus", content.sourceId.lyricsSourceTitle())
        assertNotEquals(LyricsSource.LYRICS_PLUS.id, content.sourceId)
        assertNotEquals(LyricsSource.APPLE_TTML.id, content.sourceId)
        val line = AccompanistLyricsAdapter.convert(content).synced.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(1000, line.start)
        assertEquals(3000, line.end)
    }

    @Test fun separateCacheIsIncludedInLyricsCleanup() {
        val files = File("/app/files")
        val apple = LocalTtmlStore.cacheDir(files, LyricsSource.APPLE_TTML)
        val lmg = LocalTtmlStore.cacheDir(files, LyricsSource.LMG_LYRICS_PLUS)
        assertNotEquals(apple, lmg)
        val cleanup = cacheCategoryDirectories(File("/app/cache"), files, CacheCategory.LYRICS)
        assertTrue(apple in cleanup)
        assertTrue(lmg in cleanup)
    }
}
