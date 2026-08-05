package com.lmg.vk.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.viewmodel.HomeViewModel

/**
 * Таб «New» — главная выдача VK Music CatalogKit (`catalog.getAudioAuto`).
 * Локальные mood/recent/history карточки сюда не подмешиваются.
 */
@Composable
fun NewScreen(
    viewModel: HomeViewModel,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.loadHomeContent() }

    val homeContent by viewModel.homeContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.error.collectAsState()
    val homeBlocks = remember(homeContent) {
        homeContent?.blocks?.filter { it.items.isNotEmpty() } ?: emptyList()
    }

    val lc = LiquidTheme.colors
    // Широкое окно (телефон-альбом ИЛИ планшет): ограничиваем ширину списка
    // и центрируем — плоские строки/карусели не растягиваются на весь экран.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    // Альбом/планшет: делаем всё компактнее (шрифты/карточки/отступы ~20-30%),
    // как в LandscapeHome. В портрете compact=false → всё как было.
    val compact = win.useSideBySide
    val sectionGap = if (compact) 18.dp else 28.dp
    val rowGap = if (compact) 10.dp else 14.dp
    val onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit = { homeItem ->
        when {
            homeItem.isCustom -> Unit
            homeItem.isArtist -> onNavigateToArtist(homeItem.artistId ?: homeItem.id)
            homeItem.isAlbum -> onNavigateToAlbum(homeItem.collectionId ?: homeItem.id)
            else -> PlayerController.playFromList(context, listOf(homeItem.toTrack()))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(lc.settingsBackground)) {
        LazyColumn(
            modifier = if (win.useSideBySide)
                Modifier.fillMaxHeight().widthIn(max = 900.dp).fillMaxWidth().align(Alignment.TopCenter)
            else Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 178.dp)
        ) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item {
                Text(
                    text = "New",
                    color = lc.textPrimary,
                    fontSize = if (compact) 20.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFontFamily,
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = if (compact) 8.dp else 12.dp,
                        bottom = if (compact) 10.dp else 16.dp
                    )
                )
            }

            // Скелетоны только для подтверждённой VK catalog выдачи.
            if (homeBlocks.isEmpty() && isLoading) {
                items(count = 2, key = { "skeleton_$it" }) {
                    NewSectionSkeleton()
                    Spacer(Modifier.height(sectionGap))
                }
            }

            loadError?.takeIf { homeBlocks.isEmpty() && !isLoading }?.let { message ->
                item {
                    Text(
                        text = message,
                        color = lc.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }

            // Экран не остаётся пустым даже если VK временно вернул пустой
            // каталог: пользователь может повторить именно VK-запрос.
            if (homeBlocks.isEmpty() && !isLoading && loadError == null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            text = "В каталоге VK пока нет блоков",
                            color = lc.textSecondary,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Повторить загрузку",
                            color = lc.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { viewModel.loadHomeContent(force = true) },
                        )
                    }
                }
            }

            // ── Блоки CatalogKit в исходном серверном порядке. ──
            homeBlocks.forEach { block ->
                item(key = "block_${block.id}") {
                    val title = block.title.takeUnless {
                        it == "VK Музыка" && block.layoutName.isNotBlank()
                    }
                    when {
                        block.layoutName in NEW_HERO_LAYOUTS -> {
                            block.items.firstOrNull()?.let { homeItem ->
                                NewHeroBanner(homeItem, compact, onClick = { onItemClick(homeItem) })
                            }
                        }

                        block.layoutName in NEW_TRACK_LIST_LAYOUTS -> {
                            NewSectionHeader(title, compact)
                            NewTrackColumns(
                                blockId = block.id,
                                homeItems = block.items,
                                compact = compact,
                                showRank = block.layoutName.startsWith("music_chart"),
                                onItemClick = onItemClick,
                            )
                        }

                        else -> {
                            NewSectionHeader(title, compact)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(rowGap)
                            ) {
                                items(block.items, key = { "${block.id}_${it.id}" }) { homeItem ->
                                    if (block.type == "curators" || block.id.contains("curator", ignoreCase = true)) {
                                        NewCuratorCard(
                                            title = homeItem.title,
                                            coverUrl = homeItem.cover,
                                            compact = compact,
                                        )
                                    } else {
                                        NewTrackCard(
                                            title = homeItem.title,
                                            subtitle = homeItem.subtitle ?: homeItem.displayArtist,
                                            coverUrl = homeItem.cover,
                                            compact = compact,
                                            showRank = block.layoutName.startsWith("music_chart"),
                                            rank = homeItem.rank,
                                            enabled = !homeItem.isTrack || homeItem.isAvailable,
                                            onClick = { onItemClick(homeItem) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(sectionGap))
                }
            }
        }
    }
}

/** Пульсирующий плейсхолдер секции: плашка заголовка + ряд карточек 140dp. */
@Composable
private fun NewSectionSkeleton() {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "newSkeleton")
        .animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(650),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "newSkeletonPulse"
        )
    val base = if (LiquidTheme.colors.isDark) Color(0xFF1A1A1A) else Color(0xFFEAEAEF)
    Column {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .size(width = 130.dp, height = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(base.copy(alpha = pulse))
        )
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) {
                Column {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(base.copy(alpha = pulse))
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(base.copy(alpha = pulse))
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 11.dp)
                            .clip(RoundedCornerShape(50))
                            .background(base.copy(alpha = pulse))
                    )
                }
            }
        }
    }
}

@Composable
private fun NewSectionHeader(title: String?, compact: Boolean = false) {
    if (title.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = LiquidTheme.colors.textPrimary,
            fontSize = if (compact) 15.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(if (compact) 26.dp else 32.dp)
                .clip(RoundedCornerShape(50))
                .background(if (LiquidTheme.colors.isDark) Color(0xFF252525) else Color(0xFFE8E8ED)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "›",
                color = LiquidTheme.colors.textPrimary,
                fontSize = if (compact) 23.sp else 28.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun NewTrackCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    compact: Boolean = false,
    showRank: Boolean = false,
    rank: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val lc = LiquidTheme.colors
    val cardSize = if (compact) 110.dp else 140.dp
    Column(
        modifier = Modifier
            .width(cardSize)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.42f),
    ) {
        Box {
            AlbumArtImage(
                uri = null,
                contentDescription = title,
                coverUrl = coverUrl,
                placeholderKey = "$title\u0000$subtitle",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(cardSize).clip(RoundedCornerShape(12.dp)),
            )
            if (showRank && rank != null) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC111111)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(rank.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Text(
            text = title,
            color = lc.textPrimary,
            fontSize = if (compact) 12.5.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = lc.textSecondary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NewHeroBanner(
    item: com.lmg.vk.engine.backend.HomeItem,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val imageHeight = if (compact) 142.dp else 192.dp
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clickable(enabled = !item.isCustom, onClick = onClick),
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = item.title,
            coverUrl = item.cover,
            placeholderKey = "${item.title}\u0000${item.subtitle.orEmpty()}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            text = item.title,
            color = LiquidTheme.colors.textPrimary,
            fontSize = if (compact) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        (item.subtitle ?: item.artist)?.takeIf(String::isNotBlank)?.let { subtitle ->
            Text(
                text = subtitle,
                color = LiquidTheme.colors.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontFamily = AppFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun NewTrackColumns(
    blockId: String,
    homeItems: List<com.lmg.vk.engine.backend.HomeItem>,
    compact: Boolean,
    showRank: Boolean,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        items(homeItems.chunked(3), key = { column -> "${blockId}_${column.firstOrNull()?.id.orEmpty()}" }) { column ->
            Column(modifier = Modifier.width(if (compact) 250.dp else 292.dp)) {
                column.forEachIndexed { index, homeItem ->
                    NewTrackRow(
                        item = homeItem,
                        rank = if (showRank) homeItem.rank ?: (index + 1) else null,
                        compact = compact,
                        onClick = { onItemClick(homeItem) },
                    )
                    if (index != column.lastIndex) Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                }
            }
        }
    }
}

@Composable
private fun NewTrackRow(
    item: com.lmg.vk.engine.backend.HomeItem,
    rank: Int?,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val imageSize = if (compact) 48.dp else 58.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isTrack || item.isAvailable, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rank?.let {
            Text(
                text = it.toString(),
                color = LiquidTheme.colors.textSecondary,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(if (compact) 24.dp else 30.dp),
            )
        }
        AlbumArtImage(
            uri = null,
            contentDescription = item.title,
            coverUrl = item.cover,
            placeholderKey = "${item.title}\u0000${item.displayArtist}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(imageSize).clip(RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = if (compact) 9.dp else 11.dp)) {
            Text(
                text = item.title,
                color = LiquidTheme.colors.textPrimary,
                fontSize = if (compact) 12.5.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle ?: item.displayArtist,
                color = LiquidTheme.colors.textSecondary,
                fontSize = if (compact) 11.sp else 12.sp,
                fontFamily = AppFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (item.durationMs > 0L) {
            Text(
                text = formatNewDuration(item.durationMs),
                color = LiquidTheme.colors.textSecondary,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun NewCuratorCard(title: String, coverUrl: String?, compact: Boolean) {
    val size = if (compact) 76.dp else 94.dp
    Column(modifier = Modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        AlbumArtImage(
            uri = null,
            contentDescription = title,
            coverUrl = coverUrl,
            placeholderKey = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(50)),
        )
        Text(
            text = title,
            color = LiquidTheme.colors.textPrimary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

private fun formatNewDuration(durationMs: Long): String {
    val seconds = durationMs / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

private val NEW_HERO_LAYOUTS = setOf("banner", "promo_banners_slider", "snippets_banner")

private val NEW_TRACK_LIST_LAYOUTS = setOf(
    "triple_stacked_slider",
    "music_chart_triple_stacked_slider",
    "list",
    "listened_list",
    "music_chart_list",
    "small_list",
    "compact_list",
    "large_list",
    "double_list",
)

/** Maps a VK home item to the common player model without a third-party resolver URI. */
private fun com.lmg.vk.engine.backend.HomeItem.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = displayArtist,
    albumName = album.orEmpty(),
    uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
    durationMs = durationMs,
    albumId = collectionId?.hashCode()?.toLong() ?: id.hashCode().toLong(),
    coverUrl = cover,
    artists = artistId?.let {
        listOf(com.lmg.vk.engine.backend.MiniArtist(id = it, name = displayArtist))
    }.orEmpty(),
    isExplicit = isExplicit,
    source = source,
    genre = genre,
    isAvailable = isAvailable,
)
