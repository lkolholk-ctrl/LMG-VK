package com.lmg.vk.engine.backend

import org.junit.Assert.*
import org.junit.Test

class CatalogNavigationTest {
    @Test fun preservesEditorialAndQueryDestinations() {
        assertEquals("https://vk.com/music?z=audio_playlist-12_34_abcd", catalogWebUrl("/music?z=audio_playlist-12_34_abcd"))
        assertEquals("https://vk.com/@music-editorial", catalogWebUrl("https://vk.com/@music-editorial"))
        assertEquals("https://vk.com/music/artist/poppy", catalogWebUrl("vk://vk.com/music/artist/poppy"))
        assertEquals("https://example.org/article", catalogWebUrl("https://example.org/article"))
    }
    @Test fun rejectsNonWebActionsAndBadDestinations() {
        for (url in listOf("javascript:alert(1)", "file:///etc/passwd", "intent://music", "https://", "https://name@vk.com/music"))
            assertNull(catalogWebUrl(url))
    }
    @Test fun sectionNavigationIsVkOnlyAndKeepsOpaqueId() {
        assertEquals("main:abc/123", catalogSectionFromUrl("https://vk.com/music?section=main%3Aabc%2F123"))
        assertNull(catalogSectionFromUrl("https://example.org/music?section=main"))
        assertNull(catalogSectionFromUrl("https://vk.com/music?z=audio_playlist-1_2"))
    }
    @Test fun playButtonsOnlyTreatPlayableShortcutsAsPlaylists() {
        assertTrue(HomeItem("mine", "Мои треки", isCustom = true, musicOwnerId = 1L).canPlayCatalogShortcut())
        assertTrue(HomeItem("recent", "Недавнее", isCustom = true, catalogSectionId = "recent").canPlayCatalogShortcut())
        assertTrue(HomeItem("list", "Подборка", isCustom = true, catalogUrl = "/music/playlist/-1_2").canPlayCatalogShortcut())
        assertFalse(HomeItem("news", "Новости", isCustom = true, catalogUrl = "https://vk.com/@music-news").canPlayCatalogShortcut())
        assertFalse(HomeItem("foreign", "Страница", isCustom = true, catalogUrl = "https://example.org/audio_playlist-1_2").canPlayCatalogShortcut())
    }

    @Test fun collectionsAndRootTabsKeepTheirFullApiUrl() {
        for (url in listOf(
            "https://vk.com/audio?catalog=recent",
            "https://vk.com/audios123?section=explore",
            "https://vk.ru/music?section=general#collection",
            "https://vk.com/music/recommendations",
        )) assertEquals(url, catalogApiUrl(url))
        assertTrue(catalogUrlNeedsApi("/audio?catalog=recent"))
        assertTrue(catalogUrlNeedsApi("/audios123?section=explore"))
        assertNull(catalogSectionFromUrl("/music?section=general"))
        assertNull(catalogSectionFromUrl("/music?section=explore"))
    }

    @Test fun sharedPlaylistTargetWinsOverOuterCatalogTab() {
        val url = "/music?section=general&z=audio_playlist-1_2_ab1234"
        assertFalse(catalogUrlNeedsApi(url))
        assertNull(catalogSectionFromUrl(url))
    }

    @Test fun catalogApiRejectsForeignHostsAndNonMusicPages() {
        for (url in listOf(
            "https://vk.com.evil.org/music", "https://example.org/audio?catalog=recent",
            "https://vk.com@evil.org/music", "https://vk.com/@music-editorial",
            "https://vk.com/video-1_2", "https://vk.com/id123",
        )) assertNull(catalogApiUrl(url))
    }
}
