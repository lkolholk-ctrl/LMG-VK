package com.lmg.vk.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MainCatalogLayoutTest {
    @Test
    fun serverSliderRemainsCardsEvenWhenItContainsTracks() {
        assertEquals(MainCatalogLayout.CARDS, mainCatalogLayout("slider", tracks = true))
    }

    @Test
    fun largePlaylistSliderIsNotReducedToSmallCards() {
        assertEquals(MainCatalogLayout.LARGE_CARDS, mainCatalogLayout("large_slider", tracks = false))
        assertEquals(MainCatalogLayout.LARGE_CARDS, mainCatalogLayout("music_chart_large_slider", tracks = false))
    }

    @Test
    fun stackedTracksKeepTheServerRowCount() {
        assertEquals(MainCatalogLayout.TRACK_PAIRS, mainCatalogLayout("double_stacked_slider", tracks = true))
        assertEquals(MainCatalogLayout.TRACK_TRIPLES, mainCatalogLayout("triple_stacked_slider", tracks = true))
    }

    @Test
    fun ordinaryTrackListIsVertical() {
        assertEquals(MainCatalogLayout.TRACK_LIST, mainCatalogLayout("list", tracks = true))
        assertEquals(MainCatalogLayout.TRACK_LIST, mainCatalogLayout("music_chart_list", tracks = true))
    }

    @Test
    fun gridPreservesItsLayoutForPlaylists() {
        assertEquals(MainCatalogLayout.GRID, mainCatalogLayout("entity_double_grid", tracks = false))
    }
}
