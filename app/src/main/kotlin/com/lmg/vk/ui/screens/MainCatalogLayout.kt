package com.lmg.vk.ui.screens

internal enum class MainCatalogLayout {
    TRACK_LIST, TRACK_PAIRS, TRACK_TRIPLES, CARDS, LARGE_CARDS, GRID
}

internal fun mainCatalogLayout(layout: String, tracks: Boolean): MainCatalogLayout = when (layout) {
    "entity_double_grid", "vertical_grid", "dynamic_grid" -> MainCatalogLayout.GRID
    "large_slider", "music_chart_large_slider", "music_exclusive_slider",
    "recomms_slider", "infinite_large_slider", "crop_slider" -> MainCatalogLayout.LARGE_CARDS
    "slider", "infinite_slider" -> MainCatalogLayout.CARDS
    "double_stacked_slider", "double_stacked_slider_minimalistic_card",
    "double_stacked_list", "double_stacked_list_minimalistic_card", "double_list" ->
        if (tracks) MainCatalogLayout.TRACK_PAIRS else MainCatalogLayout.CARDS
    "triple_stacked_slider", "music_chart_triple_stacked_slider" ->
        if (tracks) MainCatalogLayout.TRACK_TRIPLES else MainCatalogLayout.CARDS
    else -> if (tracks) MainCatalogLayout.TRACK_LIST else MainCatalogLayout.CARDS
}
