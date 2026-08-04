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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cancelHomeLoad()
        }
    }
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

            // ── Home-блоки (popular / new_releases / recommendations …) ──
            homeBlocks.forEach { block ->
                item(key = "block_${block.id}") {
                    NewSectionHeader(block.title, compact)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(rowGap)
                    ) {
                        items(block.items, key = { "${block.id}_${it.id}" }) { homeItem ->
                            NewTrackCard(
                                title = homeItem.title,
                                subtitle = homeItem.subtitle ?: homeItem.displayArtist,
                                coverUrl = homeItem.cover,
                                compact = compact,
                                enabled = !homeItem.isTrack || homeItem.isAvailable,
                                onClick = {
                                    when {
                                        homeItem.isArtist -> onNavigateToArtist(homeItem.artistId ?: homeItem.id)
                                        homeItem.isAlbum -> onNavigateToAlbum(homeItem.collectionId ?: homeItem.id)
                                        else -> PlayerController.playFromList(context, listOf(homeItem.toTrack()))
                                    }
                                }
                            )
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
private fun NewSectionHeader(title: String, compact: Boolean = false) {
    Text(
        text = title,
        color = LiquidTheme.colors.textPrimary,
        fontSize = if (compact) 15.sp else 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = AppFontFamily,
        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = if (compact) 8.dp else 12.dp)
    )
}

@Composable
private fun NewTrackCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    compact: Boolean = false,
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
        AlbumArtImage(
            uri = null,
            contentDescription = title,
            coverUrl = coverUrl,
            placeholderKey = "$title\u0000$subtitle",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(cardSize).clip(RoundedCornerShape(12.dp)),
        )
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
