package com.lmg.vk.ui.screens

import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeItem
import com.lmg.vk.engine.backend.HomeRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MainShowcaseLayoutTest {
    private fun mix(id: String) = HomeItem(id, id, isCustom = true, streamMixId = "common", streamMixCatalogItemId = id)

    @Test
    fun genreGridDoesNotBecomePersonalMixHero() {
        val block = HomeBlock("genres", "Жанры", "actions",
            items = listOf(mix("pop"), mix("rock")), layoutName = "horizontal_buttons")
        assertEquals(MainShowcaseKind.MIX_GRID, mainShowcaseKind(block))
    }

    @Test
    fun artistMixesRemainSeparatePosters() {
        val block = HomeBlock("artists", "Микс по артистам", "actions",
            items = listOf(mix("a"), mix("b")), layoutName = "slider")
        assertEquals(MainShowcaseKind.POSTERS, mainShowcaseKind(block))
    }

    @Test
    fun gridUsesServerRowsWithoutLosingAdditionalItems() {
        val block = HomeBlock("genres", "", "actions", listOf(mix("pop"), mix("rock"), mix("hiphop"), mix("electronic"), mix("indie")),
            gridLayout = listOf(listOf("pop", "rock"), listOf("hiphop", "electronic")))
        assertEquals(listOf(listOf("pop", "hiphop"), listOf("rock", "electronic"), listOf("indie")),
            mainGridColumns(block).map { column -> column.map { it.id } })
    }

    @Test
    fun recommendationsHavePriorityOverTheirPlaylistLayout() {
        val item = HomeItem("42_1", "Playlist", isPlaylist = true, recommendation = HomeRecommendation())
        assertEquals(MainShowcaseKind.RECOMMENDATIONS,
            mainShowcaseKind(HomeBlock("rec", "", "music_recommended_playlists", listOf(item), "recomms_slider")))
    }

    @Test
    fun algorithmsUseOverlayPostersAndShortcutsUseOneHorizontalRow() {
        val item = HomeItem("42_1", "Открытия", isPlaylist = true)
        assertEquals(MainShowcaseKind.POSTERS, mainShowcaseKind(HomeBlock("algo", "", "music_playlists", listOf(item), "recomms_slider")))
        assertEquals(MainShowcaseKind.SHORTCUTS, mainShowcaseKind(HomeBlock("shortcuts", "", "catalog_banners", listOf(item), "triple_stacked_slider")))
    }
    @Test
    fun artistPortraitsUseCirclesInsteadOfPosters() {
        val item = mix("artist").copy(foregroundCover = "https://vk.test/artist.jpg", streamMixResolveSettings = true)
        assertEquals(MainShowcaseKind.ARTIST_MIXES,
            mainShowcaseKind(HomeBlock("artist", "", "actions", listOf(item), "slider")))
    }

    @Test
    fun linkShortcutsDoNotBecomeThreeRows() {
        val item = HomeItem("catalog_link_my", "Мои треки", isCustom = true)
        assertEquals(MainShowcaseKind.SHORTCUTS,
            mainShowcaseKind(HomeBlock("shortcuts", "", "links", listOf(item), "triple_stacked_slider")))
    }
    @Test
    fun mainFeaturedCollectionsUseCirclesRegardlessOfServerLayout() {
        val mixedItems = listOf(
            HomeItem("catalog_link_library", "Мои треки", isCustom = true),
            HomeItem("catalog_banner_recent", "Недавнее", isCustom = true),
            HomeItem("12_34", "Плейлист", isPlaylist = true),
        )
        for (layout in listOf("large_list", "triple_stacked_slider", "promo_banners_slider", "banner")) {
            val block = HomeBlock("collections", "Подборки", "custom_items", mixedItems, layout)
            assertEquals(MainShowcaseKind.SHORTCUTS, catalogShowcaseKind(block, true, true))
            assertEquals(MainShowcaseKind.NONE, catalogShowcaseKind(block, false, true))
        }
    }

    @Test
    fun otherExploreSectionsKeepTheirOwnPresentation() {
        val block = HomeBlock("editorial", "Выбор редакции", "music_playlists",
            listOf(HomeItem("12_34", "Playlist", isPlaylist = true)), "slider")
        assertEquals(MainShowcaseKind.NONE, catalogShowcaseKind(block, false, false))
    }

    @Test
    fun featuredTitleDoesNotOverrideSpecialActionsOrMixes() {
        val link = HomeItem("catalog_link_1", "Подборка", isCustom = true)
        for (layout in listOf("subsection_tabs", "close_catalog_banner", "snippets_banner")) {
            assertEquals(MainShowcaseKind.NONE, catalogShowcaseKind(HomeBlock("x", "Подборки", "links", listOf(link), layout), true, true))
        }
        assertEquals(MainShowcaseKind.PRIMARY_MIX,
            catalogShowcaseKind(HomeBlock("mix", "Подборки", "stream_mixes", listOf(mix("mix"))), true, true))
    }
    @Test
    fun podcastsAreHiddenEvenWhenWrappedInEditorialContentCards() {
        val card = HomeItem("content_card_42_7", "Антон Беляев", isCustom = true)
        assertTrue(HomeBlock("podcasts", "Популярные подкасты", "audio_content_cards", listOf(card), "slider").isPodcastCatalogBlock())
        assertTrue(HomeBlock("podcasts", "Для вас", "podcasts", listOf(card)).isPodcastCatalogBlock())
        assertTrue(HomeBlock("podcasts", "Для вас", "catalog_banners", listOf(card), "podcast_banners_slider").isPodcastCatalogBlock())
        assertFalse(HomeBlock("editorial", "Собрано редакцией", "music_playlists", listOf(card), "slider").isPodcastCatalogBlock())
    }
}
