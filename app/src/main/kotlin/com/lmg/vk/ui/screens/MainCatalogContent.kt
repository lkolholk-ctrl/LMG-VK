package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeItem
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay

internal val LocalVkMainCatalog = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewCatalogRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, content = content)
}

@Composable
internal fun MainCatalogItems(
    block: HomeBlock,
    compact: Boolean,
    onItemClick: (HomeItem) -> Unit,
) {
    val layout = mainCatalogLayout(block.layoutName, block.items.all { it.isTrack })
    val ranked = block.layoutName.startsWith("music_chart")
    when (layout) {
        MainCatalogLayout.TRACK_LIST -> Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            block.items.forEachIndexed { index, item ->
                NewTrackRow(item, if (ranked) item.rank ?: index + 1 else null, compact, onClick = { onItemClick(item) })
            }
        }
        MainCatalogLayout.TRACK_PAIRS, MainCatalogLayout.TRACK_TRIPLES -> {
            val rows = if (layout == MainCatalogLayout.TRACK_PAIRS) 2 else 3
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnWidth = (maxWidth - 64.dp).coerceIn(240.dp, 380.dp)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(block.items.chunked(rows), key = { _, items -> items.first().id }) { columnIndex, items ->
                        Column(Modifier.width(columnWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items.forEachIndexed { rowIndex, item ->
                                NewTrackRow(
                                    item, if (ranked) item.rank ?: columnIndex * rows + rowIndex + 1 else null,
                                    compact, onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
        MainCatalogLayout.GRID -> NewDoubleGrid(block.id, block.items, compact, 14.dp, onItemClick)
        MainCatalogLayout.CARDS, MainCatalogLayout.LARGE_CARDS -> LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(block.items, key = { _, item -> item.id }) { index, item ->
                NewTrackCard(
                    title = item.title,
                    subtitle = item.subtitle ?: item.displayArtist,
                    coverUrl = item.cover,
                    artworkQuery = if (item.isTrack) com.lmg.vk.artwork.ArtworkQuery(item.title, item.displayArtist, item.durationMs, item.album.orEmpty()) else null,
                    compact = compact,
                    wide = true,
                    showRank = ranked,
                    rank = item.rank ?: index + 1,
                    enabled = item.isAvailable,
                    artworkSize = if (layout == MainCatalogLayout.LARGE_CARDS) {
                        if (compact) 190.dp else 232.dp
                    } else null,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}
