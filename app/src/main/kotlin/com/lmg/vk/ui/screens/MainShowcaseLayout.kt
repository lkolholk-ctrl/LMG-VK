package com.lmg.vk.ui.screens

import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeItem

internal enum class MainShowcaseKind { NONE, PRIMARY_MIX, SHORTCUTS, MIX_GRID, ARTIST_MIXES, POSTERS, RECOMMENDATIONS }

internal fun catalogShowcaseKind(
    block: HomeBlock,
    isMainCatalog: Boolean,
    isFeaturedSection: Boolean,
): MainShowcaseKind {
    if (block.items.isEmpty() || block.layoutName in setOf("subsection_tabs", "close_catalog_banner", "snippets_banner")) {
        return MainShowcaseKind.NONE
    }
    if (isMainCatalog && isFeaturedSection && block.signalInfo == null && block.items.none { it.isStreamMix || it.recommendation != null }) {
        return MainShowcaseKind.SHORTCUTS
    }
    return if (isMainCatalog) mainShowcaseKind(block) else MainShowcaseKind.NONE
}

internal fun mainShowcaseKind(block: HomeBlock): MainShowcaseKind {
    if (block.items.isEmpty()) return MainShowcaseKind.NONE
    if (block.items.any { it.recommendation != null }) return MainShowcaseKind.RECOMMENDATIONS
    if ((block.type == "catalog_banners" || block.items.all { it.id.startsWith("catalog_banner_") } ||
            (block.items.all { it.id.startsWith("catalog_link_") } && block.layoutName in setOf(
                "triple_stacked_slider", "double_stacked_slider", "slider", "list", "compact_list",
            ))) && block.layoutName !in setOf("banner", "close_catalog_banner")) {
        return MainShowcaseKind.SHORTCUTS
    }
    if (block.items.all { it.isStreamMix }) {
        if (block.items.any { !it.foregroundCover.isNullOrBlank() }) return MainShowcaseKind.ARTIST_MIXES
        if (block.items.size == 1 && block.items.first().streamMixId == "common" &&
            !block.items.first().streamMixResolveSettings) return MainShowcaseKind.PRIMARY_MIX
        if (block.gridLayout.isNotEmpty() || block.layoutName in setOf(
                "horizontal_buttons", "horizontal_buttons_with_scroll", "horizontal_button_stack",
                "categories_grid", "categories_list", "entity_double_grid", "chips",
            ) || block.layoutStyle in setOf("small", "compact") ||
            block.items.all { it.catalogStyle in setOf("small", "compact") } ||
            block.items.all { it.streamMixResolveSettings && it.foregroundCover.isNullOrBlank() }
        ) return MainShowcaseKind.MIX_GRID
        return MainShowcaseKind.POSTERS
    }
    if (block.layoutName == "recomms_slider") return MainShowcaseKind.POSTERS
    return MainShowcaseKind.NONE
}

internal fun mainGridColumns(block: HomeBlock): List<List<HomeItem>> {
    if (block.gridLayout.isEmpty()) return block.items.chunked(2)
    val byId = block.items.flatMap { item ->
        listOfNotNull(item.id, item.streamMixCatalogItemId).map { it to item }
    }.toMap()
    val rows = block.gridLayout.map { row -> row.map { byId[it] } }
    val columns = (0 until (rows.maxOfOrNull { it.size } ?: 0)).map { column ->
        rows.mapNotNull { it.getOrNull(column) }
    }.filter { it.isNotEmpty() }
    val used = columns.flatten().map { it.id }.toSet()
    return columns + block.items.filter { it.id !in used }.chunked(2)
}

internal fun HomeBlock.isPodcastCatalogBlock(): Boolean =
    type.contains("podcast", ignoreCase = true) ||
        layoutName.contains("podcast", ignoreCase = true) ||
        title.contains("подкаст", ignoreCase = true) ||
        title.contains("podcast", ignoreCase = true) ||
        (items.isNotEmpty() && items.all { it.id.startsWith("podcast_") })
