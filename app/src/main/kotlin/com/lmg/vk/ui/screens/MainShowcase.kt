package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeItem
import com.lmg.vk.engine.backend.canPlayCatalogShortcut
import com.lmg.vk.engine.backend.catalogMatchPercent
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay

internal val LocalMainMixSettings = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalMainTuneRecommendations = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalMainPlaylistPlayer = staticCompositionLocalOf<(HomeItem, String?) -> Unit> { { _, _ -> } }

@Composable
internal fun MainShowcase(
    block: HomeBlock,
    compact: Boolean,
    kind: MainShowcaseKind,
    onOpen: (HomeItem) -> Unit,
) {
    val playPlaylist = LocalMainPlaylistPlayer.current
    val onPlay: (HomeItem) -> Unit = { item ->
        if (item.isPlaylist || item.isAlbum || item.canPlayCatalogShortcut()) playPlaylist(item, null) else onOpen(item)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileWidth = ((maxWidth - 52.dp) / 2).coerceIn(140.dp, 220.dp)
        when (kind) {
            MainShowcaseKind.PRIMARY_MIX -> MainPersonalMix(block.items.first(), compact, onPlay)
            MainShowcaseKind.SHORTCUTS, MainShowcaseKind.ARTIST_MIXES -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(block.items, key = { it.id }) { item ->
                    MainCircleItem(item, if (compact) 96.dp else 112.dp, onOpen, onPlay)
                }
            }
            MainShowcaseKind.MIX_GRID -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(mainGridColumns(block), key = { it.first().id }) { column ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        column.forEach { item -> MainMixTile(item, tileWidth, onPlay) }
                    }
                }
            }
            MainShowcaseKind.POSTERS -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(block.items, key = { it.id }) { item -> MainPoster(item, tileWidth, onOpen, onPlay) }
            }
            MainShowcaseKind.RECOMMENDATIONS -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(block.items.filter { it.recommendation != null }, key = { it.id }) { item ->
                    MainRecommendation(item, (maxWidth - 110.dp).coerceIn(270.dp, 340.dp), compact, onOpen)
                }
            }
            MainShowcaseKind.NONE -> Unit
        }
    }
}

@Composable
private fun MainCircleItem(item: HomeItem, width: Dp, onOpen: (HomeItem) -> Unit, onPlay: (HomeItem) -> Unit) {
    val colors = LiquidTheme.colors
    Column(Modifier.width(width).clickable { onOpen(item) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width)) {
            AlbumArtImage(
                uri = null, coverUrl = item.foregroundCover?.takeIf(String::isNotBlank) ?: item.cover,
                contentDescription = item.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
            Box(Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape)
                .background(colors.accent).border(3.dp, colors.settingsBackground, CircleShape)
                .clickable(enabled = item.isInteractive) { onPlay(item) }, contentAlignment = Alignment.Center) {
                Icon(LmgGlyphs.Play28, stringResource(R.string.main_play_named, item.title),
                    tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
        Text(item.title, color = colors.textPrimary, fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        (item.subtitle ?: item.artist)?.takeIf(String::isNotBlank)?.let {
            Text(it, color = colors.textSecondary, fontFamily = AppFontFamily, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun MainArtwork(item: HomeItem, modifier: Modifier) {
    Box(modifier.background(LiquidTheme.colors.accent.copy(alpha = 0.28f))) {
        item.cover?.takeIf(String::isNotBlank)?.let {
            AlbumArtImage(uri = null, coverUrl = it, contentDescription = null,
                modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        item.streamMixAnimationUrl?.takeIf(String::isNotBlank)?.let {
            NewMixBackgroundAnimation(it, item.streamMixCatalogItemId ?: item.id, Modifier.matchParentSize())
        }
        item.foregroundCover?.takeIf(String::isNotBlank)?.let {
            AlbumArtImage(uri = null, coverUrl = it, contentDescription = null,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.BottomCenter),
                contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun MainPersonalMix(item: HomeItem, compact: Boolean, onPlay: (HomeItem) -> Unit) {
    val colors = LiquidTheme.colors
    val onSettings = LocalMainMixSettings.current
    Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 218.dp else 264.dp).clickable { onPlay(item) }) {
        BundledCatalogLottie(R.raw.lmg_mix_waves, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to colors.settingsBackground, 0.23f to Color.Transparent,
            0.72f to Color.Transparent, 1f to colors.settingsBackground,
        )))
        Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, color = colors.textPrimary, fontFamily = VkSansDisplay, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 24.sp else 28.sp, textAlign = TextAlign.Center)
            (item.artist ?: item.subtitle)?.takeIf(String::isNotBlank)?.let {
                Text(it, color = colors.textPrimary.copy(alpha = 0.75f), fontFamily = AppFontFamily,
                    fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.clip(CircleShape).background(colors.accent)
                    .clickable { onPlay(item) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(LmgGlyphs.Play28, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.main_mix_listen), color = Color.White, fontFamily = AppFontFamily)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                    Icon(LmgGlyphs.Filter24, stringResource(R.string.tune_vkmix), tint = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun MainMixTile(item: HomeItem, width: Dp, onPlay: (HomeItem) -> Unit) {
    Box(Modifier.width(width).height(56.dp).clip(RoundedCornerShape(14.dp)).clickable { onPlay(item) }) {
        MainArtwork(item, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.1f)))
        Row(Modifier.align(Alignment.Center).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(LmgGlyphs.Play28, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(item.title, color = Color.White, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MainPoster(item: HomeItem, width: Dp, onOpen: (HomeItem) -> Unit, onPlay: (HomeItem) -> Unit) {
    Box(Modifier.width(width).height(width * 1.14f).clip(RoundedCornerShape(14.dp)).clickable { onOpen(item) }) {
        MainArtwork(item, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent))))
        Column(Modifier.padding(14.dp).padding(bottom = 52.dp)) {
            Text(item.title, color = Color.White, fontFamily = VkSansDisplay, fontWeight = FontWeight.Bold,
                fontSize = 27.sp, lineHeight = 29.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            (item.subtitle ?: item.artist)?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = 0.88f), fontFamily = AppFontFamily, fontSize = 13.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        MainPlayButton(Modifier.align(Alignment.BottomEnd).padding(14.dp), item.title) { onPlay(item) }
    }
}

@Composable
private fun MainRecommendation(item: HomeItem, width: Dp, compact: Boolean, onOpen: (HomeItem) -> Unit) {
    val recommendation = item.recommendation ?: return
    val play = LocalMainPlaylistPlayer.current
    val colors = LiquidTheme.colors
    Column(Modifier.width(width).clip(RoundedCornerShape(12.dp))
        .border(1.dp, colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        .background(LiquidSurfaces.card(colors.isDark)).clickable { onOpen(item) }) {
        Box(Modifier.fillMaxWidth().height(140.dp).clickable { onOpen(item) }) {
            MainArtwork(item, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                catalogMatchPercent(recommendation.percentage)?.let {
                    Text("$it%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                recommendation.percentageTitle?.let {
                    Text(" · $it", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 18.dp, end = 78.dp)) {
                Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                recommendation.ownerName?.let {
                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AlbumArtImage(uri = null, coverUrl = recommendation.ownerAvatar, contentDescription = null,
                            modifier = Modifier.size(18.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Text(it, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            MainPlayButton(Modifier.align(Alignment.BottomEnd).padding(14.dp), item.title) { play(item, null) }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            recommendation.tracks.take(3).forEach { track ->
                NewTrackRow(track.copy(duration = null), null, true, onClick = { play(item, track.id) })
            }
        }
    }
}

@Composable
private fun MainPlayButton(modifier: Modifier, title: String, onClick: () -> Unit) {
    Box(modifier.size(48.dp).clip(CircleShape).background(Color.White).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(LmgGlyphs.Play28, stringResource(R.string.main_play_named, title), tint = Color.Black, modifier = Modifier.size(27.dp))
    }
}

@Composable
internal fun MainShowAll(label: String = stringResource(R.string.main_show_more), onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LiquidTheme.colors.textPrimary, fontFamily = AppFontFamily, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Icon(LmgGlyphs.ChevronRightOutline24, null, tint = LiquidTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}
