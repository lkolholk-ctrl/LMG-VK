package com.lmg.vk.ui.screens

import android.widget.ImageView
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.lmg.vk.R
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.mix.VkMixLottieStore
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileInputStream
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Таб «New» — главная выдача VK Music CatalogKit (`catalog.getAudioAuto`).
 * Локальные mood/recent/history карточки сюда не подмешиваются.
 */
@Composable
fun NewScreen(
    viewModel: HomeViewModel,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToMusicOwner: (Long) -> Unit = {},
    onOpenSnippets: () -> Unit = {},
) {
    val context = LocalContext.current
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()
    LaunchedEffect(viewModel, activeAccountId) {
        viewModel.loadHomeContent(force = activeAccountId != null)
    }
    // Список скрытых баннеров нужен до первой отрисовки блоков, иначе закрытый
    // баннер мигнёт при заходе на экран.
    LaunchedEffect(activeAccountId) {
        NewDismissedBanners.init(context, activeAccountId ?: 0L)
    }

    val homeContent by viewModel.homeContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.error.collectAsState()
    // Выдача табов и догруженные порции живут в VM: у блока с табами своих
    // элементов нет, а прокрутка за экран не должна сбрасывать ни выбор таба,
    // ни уже подгруженные страницы.
    val tabStates by viewModel.tabContent.collectAsState()
    val selectedTabs by viewModel.selectedTab.collectAsState()
    val blockPaging by viewModel.blockPaging.collectAsState()
    val loadingSignalBlocks by viewModel.loadingSignalBlocks.collectAsState()
    val isLoadingHomeSection by viewModel.isLoadingHomeSection.collectAsState()
    val isLoadingMoreHomeSection by viewModel.isLoadingMoreHomeSection.collectAsState()
    val homeSectionError by viewModel.homeSectionError.collectAsState()
    val selectedHomeSectionId by viewModel.selectedHomeSectionId.collectAsState()
    val catalogSectionState by viewModel.catalogSectionState.collectAsState()
    val homeBlocks = remember(homeContent) {
        homeContent?.blocks?.filter {
            // Блок табов элементов не несёт — его «содержимое» это сами табы.
            (it.items.isNotEmpty() || it.subsectionTabs.isNotEmpty() || it.signalInfo != null) &&
                // Служебные блоки VK (separator, placeholder, text и прочие) в
                // выдаче есть, но карточками не рисуются. Раньше они отсекались
                // ниже, уже при отрисовке, и попадали в счётчик «N разделов» —
                // число в шапке не совпадало с тем, что видно на экране.
                it.layoutName !in NEW_SKIPPED_LAYOUTS
        } ?: emptyList()
    }
    var sectionSheetBlock by remember { mutableStateOf<com.lmg.vk.engine.backend.HomeBlock?>(null) }

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
            homeItem.isStreamMix -> viewModel.startCatalogVkMix(
                context = context,
                mixId = homeItem.streamMixId.orEmpty(),
                title = homeItem.title,
                isTunable = homeItem.streamMixTunable,
                blockId = homeItem.catalogBlockId,
                entityId = homeItem.streamMixEntityId,
                sectionId = homeItem.streamMixSectionId,
                catalogItemId = homeItem.streamMixCatalogItemId,
                options = homeItem.streamMixOptions,
                resolveSettings = homeItem.streamMixResolveSettings,
            )
            homeItem.isRadio && homeItem.isAvailable -> PlayerController.playFromList(
                context = context,
                tracks = listOf(homeItem.toTrack()),
                playbackContext = homeItem.catalogBlockId
                    ?.takeIf(String::isNotBlank)
                    ?.let { PlaybackContext.Catalog(it) },
            )
            homeItem.isMusicOwner -> homeItem.musicOwnerId?.let(onNavigateToMusicOwner)
            !homeItem.catalogUrl.isNullOrBlank() -> {
                val uri = android.net.Uri.parse(homeItem.catalogUrl)
                val path = uri.path.orEmpty()
                val curatorId = Regex("""/music/curator/([-_a-zA-Z0-9]+)""")
                    .find(path)
                    ?.groupValues
                    ?.getOrNull(1)
                if (!curatorId.isNullOrBlank()) {
                    viewModel.openCatalogCurator(curatorId, homeItem.title)
                } else {
                    when (val target = com.lmg.vk.engine.VkLinkResolver.parseOffline(uri)) {
                        is com.lmg.vk.engine.VkLinkTarget.Artist ->
                            onNavigateToArtist(target.idOrDomain)
                        is com.lmg.vk.engine.VkLinkTarget.Album ->
                            onNavigateToAlbum(target.navId)
                        is com.lmg.vk.engine.VkLinkTarget.Playlist ->
                            onNavigateToPlaylist(target.navId)
                        is com.lmg.vk.engine.VkLinkTarget.OwnerAudio ->
                            onNavigateToMusicOwner(target.ownerId)
                        else -> Unit
                    }
                }
            }
            homeItem.isCustom -> Unit
            homeItem.isArtist -> onNavigateToArtist(homeItem.artistId ?: homeItem.id)
            homeItem.isPlaylist -> onNavigateToPlaylist(homeItem.collectionId ?: homeItem.id)
            homeItem.isAlbum -> onNavigateToAlbum(homeItem.collectionId ?: homeItem.id)
            else -> PlayerController.playFromList(
                context = context,
                tracks = listOf(homeItem.toTrack()),
                playbackContext = homeItem.catalogBlockId
                    ?.takeIf(String::isNotBlank)
                    ?.let { PlaybackContext.Catalog(it) },
            )
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
                NewScreenHeader(
                    compact = compact,
                    sectionTitle = homeContent?.sections
                        ?.firstOrNull { it.id == selectedHomeSectionId }
                        ?.title,
                    updatedAt = homeContent?.updatedAt,
                    isLoading = isLoading,
                    onRefresh = { viewModel.loadHomeContent(force = true) },
                )
            }

            if (homeContent?.sections.orEmpty().size > 1) {
                item(key = "root_sections") {
                    NewRootSectionTabs(
                        sections = homeContent?.sections.orEmpty(),
                        selectedId = selectedHomeSectionId,
                        enabled = !isLoadingHomeSection,
                        compact = compact,
                        onSelect = viewModel::selectHomeSection,
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                }
            }

            // При обновлении не прячем уже полученную VK-выдачу: тонкая полоса
            // даёт понять, что запрос идёт, но экран остаётся полезным.
            if (isLoading && homeBlocks.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(lc.accent.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.34f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(lc.accent),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Скелетоны только для подтверждённой VK catalog выдачи.
            if ((homeBlocks.isEmpty() && isLoading) || isLoadingHomeSection) {
                items(count = 2, key = { "skeleton_$it" }) {
                    NewSectionSkeleton()
                    Spacer(Modifier.height(sectionGap))
                }
            }

            homeSectionError?.let { message ->
                item(key = "section_error") {
                    NewInlineError(
                        message = message,
                        compact = compact,
                        onRetry = viewModel::retryHomeSection,
                    )
                    Spacer(Modifier.height(sectionGap))
                }
            }

            loadError?.takeIf { homeBlocks.isNotEmpty() && !isLoading }?.let { message ->
                item {
                    NewInlineError(
                        message = message,
                        compact = compact,
                        onRetry = { viewModel.loadHomeContent(force = true) },
                    )
                }
            }

            loadError?.takeIf { homeBlocks.isEmpty() && !isLoading }?.let { message ->
                item {
                    NewLoadError(
                        message = message,
                        compact = compact,
                        onRetry = { viewModel.loadHomeContent(force = true) },
                    )
                }
            }

            // Экран не остаётся пустым даже если VK временно вернул пустой
            // каталог: пользователь может повторить именно VK-запрос.
            if (homeBlocks.isEmpty() && !isLoading && loadError == null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            text = stringResource(R.string.catalog_empty),
                            color = lc.textSecondary,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = stringResource(R.string.retry_load),
                            color = lc.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.loadHomeContent(force = true) },
                        )
                    }
                }
            }

            // ── Блоки CatalogKit в исходном серверном порядке. ──
            if (!isLoadingHomeSection) homeBlocks.forEach { block ->
                item(key = "block_${block.id}") {
                    NewCatalogBlock(
                        block = block,
                        compact = compact,
                        rowGap = rowGap,
                        tabStates = tabStates,
                        selectedTabs = selectedTabs,
                        onSelectTab = viewModel::selectSubsectionTab,
                        onRetryTab = viewModel::retrySubsectionTab,
                        onItemClick = onItemClick,
                        onPlaySignal = { signal, seedTrackId ->
                            viewModel.startCatalogSignal(context, signal, seedTrackId)
                        },
                        onPlayBlock = { block, seedTrackId ->
                            viewModel.startCatalogBlock(context, block, seedTrackId)
                        },
                        onOpenSection = { sectionId, title ->
                            viewModel.openCatalogSection(sectionId, title)
                        },
                        loadingSignalBlocks = loadingSignalBlocks,
                        onOpenSheet = { sectionSheetBlock = it },
                        onOpenSnippets = onOpenSnippets,
                    )
                    Spacer(Modifier.height(sectionGap))
                }
            }


            if (!isLoadingHomeSection && !homeContent?.sectionNextFrom.isNullOrBlank()) {
                item(key = "section_next_${homeContent?.sectionNextFrom}") {
                    LaunchedEffect(homeContent?.selectedSectionId, homeContent?.sectionNextFrom) {
                        viewModel.loadMoreHomeSection()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLoadingMoreHomeSection) 18.dp else 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMoreHomeSection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = lc.accent,
                            )
                        }
                    }
                }
            }
        }
        sectionSheetBlock?.let { block ->
            NewSectionSheet(
                block = block,
                compact = compact,
                paging = blockPaging[block.id],
                onLoadMore = { viewModel.loadMoreBlockItems(block) },
                onRetryLoadMore = { viewModel.retryBlockItems(block) },
                onDismiss = { sectionSheetBlock = null },
                onPlayBlock = {
                    viewModel.startCatalogBlock(context, block)
                    sectionSheetBlock = null
                },
                onItemClick = { item ->
                    sectionSheetBlock = null
                    onItemClick(item)
                },
            )
        }
        if (catalogSectionState !is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Idle) {
            NewCatalogSectionSheet(
                state = catalogSectionState,
                compact = compact,
                onDismiss = viewModel::closeCatalogSection,
                onRetry = viewModel::retryCatalogSection,
                onLoadMore = viewModel::loadMoreCatalogSection,
                onItemClick = { item ->
                    viewModel.closeCatalogSection()
                    onItemClick(item)
                },
            )
        }
    }
}

/**
 * Одна секция каталога: заголовок + содержимое по `layoutName`.
 *
 * Вынесено из [NewScreen] отдельным composable, потому что выдача таба
 * `subsection_tabs` — это тоже полноценные блоки CatalogKit со своими
 * layout'ами, и рисовать их надо тем же кодом. Иначе внутри табов пришлось бы
 * держать вторую, урезанную вёрстку.
 *
 * [allowTabs] закрывает рекурсию: блок с табами внутри выдачи другого таба
 * рисовать не начинаем — VK такую вложенность не строит, а стек она бы съела.
 */
@Composable
private fun NewCatalogBlock(
    block: com.lmg.vk.engine.backend.HomeBlock,
    compact: Boolean,
    rowGap: androidx.compose.ui.unit.Dp,
    tabStates: Map<String, com.lmg.vk.engine.backend.CatalogTabState>,
    selectedTabs: Map<String, String>,
    onSelectTab: (String, String) -> Unit,
    onRetryTab: (String) -> Unit,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
    onPlaySignal: (com.lmg.vk.engine.backend.HomeSignalInfo, String?) -> Unit,
    onPlayBlock: (com.lmg.vk.engine.backend.HomeBlock, String?) -> Unit,
    onOpenSection: (String, String) -> Unit,
    loadingSignalBlocks: Set<String>,
    onOpenSheet: (com.lmg.vk.engine.backend.HomeBlock) -> Unit,
    onOpenSnippets: () -> Unit = {},
    allowTabs: Boolean = true,
) {
    val context = LocalContext.current
    val title = block.title.takeUnless {
        it == stringResource(R.string.vk_music_brand) && block.layoutName.isNotBlank()
    }
    val openBlock: () -> Unit = {
        val sectionId = block.actions.openSectionId
            ?: block.signalInfo?.openSectionId
        if (!sectionId.isNullOrBlank()) {
            onOpenSection(
                sectionId,
                block.actions.openSectionTitle ?: block.signalInfo?.title ?: block.title,
            )
        } else {
            onOpenSheet(block)
        }
    }
    val playBlock: (() -> Unit)? = block.actions.playBlockId
        ?.takeIf(String::isNotBlank)
        ?.let { { onPlayBlock(block, null) } }
    val isBlockLoading = block.actions.playBlockId
        ?.let(loadingSignalBlocks::contains) == true
    val canOpenBlock = !block.actions.openSectionId.isNullOrBlank() ||
        !block.signalInfo?.openSectionId.isNullOrBlank() ||
        !block.nextFrom.isNullOrBlank()
    when {
        block.signalInfo != null -> {
            val signal = block.signalInfo
            NewSignalCard(
                block = block,
                compact = compact,
                isLoading = signal.playBlockId?.let(loadingSignalBlocks::contains) == true,
                onPlay = { seedTrackId -> onPlaySignal(signal, seedTrackId) },
                onOpen = openBlock.takeIf { !signal.openSectionId.isNullOrBlank() },
            )
        }

        block.isAudioCatalogBlock() -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            NewTrackColumns(
                blockId = block.id,
                homeItems = block.items,
                compact = compact,
                showRank = block.layoutName.startsWith("music_chart"),
                onItemClick = onItemClick,
            )
        }

        block.isPeopleCatalogBlock() -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp),
            ) {
                items(block.items, key = { "${block.id}_${it.id}" }) { item ->
                    NewCuratorCard(item, compact) { onItemClick(item) }
                }
            }
        }

        block.isCollectionCatalogBlock() || block.isRadioCatalogBlock() -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                itemsIndexed(block.items, key = { _, item -> "${block.id}_${item.id}" }) { _, item ->
                    NewTrackCard(
                        title = item.title,
                        subtitle = item.subtitle ?: item.artist
                            ?: if (item.isRadio) stringResource(R.string.vk_radio) else item.displayArtist,
                        coverUrl = item.cover,
                        compact = compact,
                        enabled = item.isInteractive && item.isAvailable,
                        onClick = {
                            if (item.isRadio) {
                                val stations = block.items.filter { it.isRadio && it.isAvailable }
                                val startIndex = stations.indexOfFirst { it.id == item.id }
                                if (startIndex >= 0) {
                                    PlayerController.playFromList(
                                        context = context,
                                        tracks = stations.map { it.toTrack() },
                                        startIndex = startIndex,
                                        playbackContext = PlaybackContext.Catalog(block.id),
                                    )
                                }
                            } else {
                                onItemClick(item)
                            }
                        },
                    )
                }
            }
        }

        block.layoutName == "close_catalog_banner" -> {
            // Закрытый баннер не рисуем вовсе — вместе с заголовком,
            // иначе на экране останется пустая секция.
            if (!NewDismissedBanners.isDismissed(block.id)) {
                NewSectionHeader(
                    title = title,
                    compact = compact,
                    itemCount = block.items.size,
                    showOpenButton = canOpenBlock,
                    onClick = openBlock,
                )
                block.items.firstOrNull()?.let { homeItem ->
                    NewCloseableBanner(
                        item = homeItem,
                        compact = compact,
                        onClick = { onItemClick(homeItem) },
                        onDismiss = { NewDismissedBanners.dismiss(block.id) },
                    )
                }
            }
        }

        block.layoutName == "subsection_tabs" -> {
            if (allowTabs && block.subsectionTabs.isNotEmpty()) {
                // Заголовок без «показать все»: у блока табов своих элементов нет,
                // открывать в шторке нечего — там оказался бы пустой список.
                NewSectionHeader(
                    title = title,
                    compact = compact,
                    itemCount = 0,
                    showOpenButton = false,
                )
                NewSubsectionTabs(
                    block = block,
                    compact = compact,
                    rowGap = rowGap,
                    tabStates = tabStates,
                    selectedTabs = selectedTabs,
                    onSelectTab = onSelectTab,
                    onRetryTab = onRetryTab,
                    onItemClick = onItemClick,
                    onPlaySignal = onPlaySignal,
                    onPlayBlock = onPlayBlock,
                    onOpenSection = onOpenSection,
                    loadingSignalBlocks = loadingSignalBlocks,
                    onOpenSheet = onOpenSheet,
                )
            }
        }

        // Точка входа в полноэкранную ленту сниппетов. У VK это отдельный
        // фрагмент-фид (`C1718e`), а не карусель, поэтому баннер здесь —
        // только «дверь»: и заголовок, и сами карточки открывают
        // SnippetsScreen, который грузит `audio.getSnippets` сам. Отдельные
        // элементы никуда больше не ведут намеренно: в выдаче это превью той
        // же ленты, а не ссылки на разные подборки.
        block.layoutName == "snippets_banner" -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                onClick = onOpenSnippets,
            )
            if (block.items.size > 1) {
                NewBannerRow(
                    blockId = block.id,
                    items = block.items,
                    compact = compact,
                    rowGap = rowGap,
                    onItemClick = { onOpenSnippets() },
                )
            } else {
                block.items.firstOrNull()?.let { homeItem ->
                    NewHeroBanner(homeItem, compact, onClick = onOpenSnippets)
                }
            }
        }

        block.layoutName in NEW_HERO_LAYOUTS -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            // У «слайдерных» баннеров элементов больше одного, и
            // раньше все кроме первого просто терялись. Один
            // баннер по-прежнему рисуется во всю ширину.
            if (block.items.size > 1) {
                NewBannerRow(
                    blockId = block.id,
                    items = block.items,
                    compact = compact,
                    rowGap = rowGap,
                    onItemClick = onItemClick,
                )
            } else {
                block.items.firstOrNull()?.let { homeItem ->
                    NewHeroBanner(homeItem, compact, onClick = { onItemClick(homeItem) })
                }
            }
        }

        block.layoutName in NEW_TRACK_LIST_LAYOUTS -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            NewTrackColumns(
                blockId = block.id,
                homeItems = block.items,
                compact = compact,
                showRank = block.layoutName.startsWith("music_chart"),
                onItemClick = onItemClick,
            )
        }

        block.layoutName in NEW_LARGE_SLIDER_LAYOUTS -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                items(block.items, key = { "${block.id}_${it.id}" }) { homeItem ->
                    NewLargeCard(
                        item = homeItem,
                        compact = compact,
                        showRank = block.layoutName.startsWith("music_chart"),
                        onClick = { onItemClick(homeItem) },
                    )
                }
            }
        }

        block.layoutName in NEW_GRID_LAYOUTS -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            NewDoubleGrid(
                blockId = block.id,
                items = block.items,
                compact = compact,
                rowGap = rowGap,
                onItemClick = onItemClick,
            )
        }

        block.layoutName == "audio_content_card_extended_slider" -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                items(block.items, key = { "${block.id}_${it.id}" }) { homeItem ->
                    NewExtendedCard(
                        item = homeItem,
                        compact = compact,
                        onClick = { onItemClick(homeItem) },
                    )
                }
            }
        }

        block.layoutName in NEW_SKIPPED_LAYOUTS -> Unit

        else -> {
            NewSectionHeader(
                title = title,
                compact = compact,
                itemCount = block.items.size,
                showOpenButton = canOpenBlock,
                onPlay = playBlock,
                playShuffled = block.actions.shuffled,
                isPlayLoading = isBlockLoading,
                onClick = openBlock,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                items(block.items, key = { "${block.id}_${it.id}" }) { homeItem ->
                    if (block.type == "curators" || block.id.contains("curator", ignoreCase = true)) {
                        NewCuratorCard(
                            item = homeItem,
                            compact = compact,
                            onClick = { onItemClick(homeItem) },
                        )
                    } else {
                        NewTrackCard(
                            title = homeItem.title,
                            subtitle = homeItem.subtitle
                                ?: homeItem.artist
                                ?: if (homeItem.isCustom) stringResource(R.string.vk_music_brand) else homeItem.displayArtist,
                            coverUrl = homeItem.cover,
                            compact = compact,
                            // `slider` — самый частый layout VK Музыки, им отдаётся
                            // основная часть подборок. У VK карточка здесь заметно
                            // крупнее мелких плиток, поэтому обычная карусель тоже
                            // должна быть широкой, иначе весь экран выглядит как
                            // одинаковая мелкая сетка.
                            wide = block.layoutName == "slider" || block.layoutName.isBlank(),
                            showRank = block.layoutName.startsWith("music_chart"),
                            rank = homeItem.rank,
                            enabled = homeItem.isInteractive &&
                                (!homeItem.isTrack || homeItem.isAvailable),
                            dimWhenDisabled = !homeItem.isInteractive,
                            onClick = { onItemClick(homeItem) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Official VK Music Signal entity, presented in LMG's neutral surface style.
 * The cover/text/current month are server fields; play resolves the action's
 * catalog source instead of treating the preview track as a one-item queue.
 */
@Composable
private fun NewSignalCard(
    block: com.lmg.vk.engine.backend.HomeBlock,
    compact: Boolean,
    isLoading: Boolean,
    onPlay: (String?) -> Unit,
    onOpen: (() -> Unit)?,
) {
    val signal = block.signalInfo ?: return
    val lc = LiquidTheme.colors
    val preview = block.items.firstOrNull()
    val coverSize = if (compact) 84.dp else 104.dp
    val canPlay = !signal.playBlockId.isNullOrBlank() && !isLoading

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
            .background(LiquidSurfaces.card(lc.isDark))
            .padding(if (compact) 14.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImage(
                uri = null,
                contentDescription = signal.title.ifBlank { stringResource(R.string.signal_fallback) },
                coverUrl = signal.cover,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(coverSize)
                    .clip(RoundedCornerShape(if (compact) 16.dp else 20.dp))
                    .clickable(enabled = onOpen != null || canPlay) {
                        if (onOpen != null) onOpen() else onPlay(null)
                    },
                placeholderIconSize = if (compact) 28.dp else 32.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (compact) 12.dp else 16.dp),
            ) {
                signal.currentMonth.takeIf(String::isNotBlank)?.let { month ->
                    Text(
                        text = month,
                        color = lc.accent,
                        fontSize = if (compact) 10.5.sp else 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AppFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = signal.title.ifBlank { stringResource(R.string.signal_fallback) },
                    color = lc.textPrimary,
                    fontSize = if (compact) 17.sp else 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = VkSansDisplay,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (signal.currentMonth.isBlank()) 0.dp else 3.dp),
                )
                signal.subtitle.takeIf(String::isNotBlank)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = lc.textSecondary,
                        fontSize = if (compact) 11.5.sp else 13.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = AppFontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(lc.accent.copy(alpha = if (canPlay) 0.18f else 0.08f))
                    .clickable(enabled = canPlay) { onPlay(null) },
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 19.dp else 22.dp),
                        strokeWidth = 2.dp,
                        color = lc.accent,
                    )
                } else {
                    Icon(
                        imageVector = if (signal.shuffled) {
                            com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28
                        } else {
                            com.lmg.vk.ui.icons.LmgGlyphs.Play28
                        },
                        contentDescription = if (signal.shuffled) stringResource(R.string.shuffle_signal) else stringResource(R.string.listen_signal),
                        tint = if (signal.playBlockId.isNullOrBlank()) lc.textSecondary else lc.accent,
                        modifier = Modifier.size(if (compact) 21.dp else 24.dp),
                    )
                }
            }
        }

        preview?.let { item ->
            Spacer(Modifier.height(if (compact) 12.dp else 14.dp))
            NewTrackRow(
                item = item,
                rank = null,
                compact = compact,
                onClick = { onPlay(item.trackId ?: item.id) },
            )
        }
    }
}

/** Root Catalog2 sections: one selected showcase, as in the official client. */
@Composable
private fun NewRootSectionTabs(
    sections: List<com.lmg.vk.engine.backend.HomeCatalogSection>,
    selectedId: String?,
    enabled: Boolean,
    compact: Boolean,
    onSelect: (String) -> Unit,
) {
    val lc = LiquidTheme.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections, key = { it.id }) { section ->
            val selected = section.id == selectedId
            Text(
                text = section.title,
                color = if (selected) lc.accent else lc.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontFamily = AppFontFamily,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) lc.accent.copy(alpha = 0.14f)
                        else LiquidSurfaces.card(lc.isDark),
                    )
                    .clickable(enabled = enabled && !selected) { onSelect(section.id) }
                    .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 9.dp),
            )
        }
    }
}

/** Заголовок New с выбранной серверной витриной и временем обновления. */
@Composable
private fun NewScreenHeader(
    compact: Boolean,
    sectionTitle: String?,
    updatedAt: Long?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 12.dp,
                top = if (compact) 8.dp else 12.dp,
                bottom = if (compact) 10.dp else 16.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tab_new),
                    color = lc.textPrimary,
                    fontSize = if (compact) 20.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = VkSansDisplay,
                )
                if (!sectionTitle.isNullOrBlank() || updatedAt != null) {
                    Text(
                        text = buildString {
                            append(sectionTitle?.takeIf(String::isNotBlank) ?: stringResource(R.string.vk_music_catalog))
                            formatNewUpdatedAt(updatedAt)?.let {
                                append("  ·  ")
                                append(it)
                            }
                        },
                        color = lc.textSecondary,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontFamily = AppFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (lc.isDark) Color(0xFF242426) else Color(0xFFEDEDF2))
                    .clickable(enabled = !isLoading, onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                        strokeWidth = 2.dp,
                        color = lc.accent,
                    )
                } else {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                        contentDescription = stringResource(R.string.refresh_vk_catalog),
                        tint = lc.textPrimary,
                        modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewInlineError(
    message: String,
    compact: Boolean,
    onRetry: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (lc.isDark) Color(0xFF221B1C) else Color(0xFFFFF1F1))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = lc.textSecondary,
            fontSize = if (compact) 11.sp else 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.action_retry),
            color = lc.accent,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NewLoadError(
    message: String,
    compact: Boolean,
    onRetry: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (lc.isDark) Color(0xFF1D1D1F) else Color(0xFFF2F2F7))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.new_load_failed),
            color = lc.textPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
        )
        Text(
            text = message,
            color = lc.textSecondary,
            fontSize = if (compact) 12.sp else 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(lc.accent.copy(alpha = 0.16f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                contentDescription = null,
                tint = lc.accent,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.retry_load),
                color = lc.accent,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

private fun formatNewUpdatedAt(timestamp: Long?): String? {
    if (timestamp == null || timestamp <= 0L) return null
    return "обновлено ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))}"
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
private fun NewSectionHeader(
    title: String?,
    compact: Boolean = false,
    itemCount: Int = 0,
    /** Блок табов открывать в шторке нечего — там был бы пустой список. */
    showOpenButton: Boolean = true,
    onPlay: (() -> Unit)? = null,
    playShuffled: Boolean = false,
    isPlayLoading: Boolean = false,
    onClick: () -> Unit = {},
) {
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
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (compact) AppFontFamily else VkSansDisplay,
            modifier = Modifier.weight(1f),
        )
        if (onPlay != null) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(if (compact) 40.dp else 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(LiquidTheme.colors.accent.copy(alpha = 0.14f))
                    .clickable(enabled = !isPlayLoading, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlayLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                        strokeWidth = 2.dp,
                        color = LiquidTheme.colors.accent,
                    )
                } else {
                    Icon(
                        imageVector = if (playShuffled) {
                            com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28
                        } else {
                            com.lmg.vk.ui.icons.LmgGlyphs.Play28
                        },
                        contentDescription = if (playShuffled) stringResource(R.string.shuffle_section) else stringResource(R.string.listen_section),
                        tint = LiquidTheme.colors.accent,
                        modifier = Modifier.size(if (compact) 19.dp else 21.dp),
                    )
                }
            }
        }
        if (showOpenButton) {
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (LiquidTheme.colors.isDark) Color(0xFF252525) else Color(0xFFE8E8ED)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
                        contentDescription = if (itemCount > 0) stringResource(R.string.open_section_count, itemCount) else stringResource(R.string.open_section),
                        tint = LiquidTheme.colors.textPrimary,
                        modifier = Modifier.size(if (compact) 20.dp else 23.dp),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewSectionSheet(
    block: com.lmg.vk.engine.backend.HomeBlock,
    compact: Boolean,
    paging: com.lmg.vk.engine.backend.BlockPagingState?,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    onPlayBlock: () -> Unit,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    // Догруженные порции идут ПОСЛЕ исходных: серверный порядок выдачи
    // сохраняется, а VK отдаёт продолжение, а не новую сортировку.
    val allItems = remember(block, paging?.extraItems) {
        block.items + paging?.extraItems.orEmpty()
    }
    val playableItems = remember(allItems) {
        allItems.filter { it.isTrack && it.isAvailable }
    }
    // Пагинация возможна, только если сервер дал курсор — либо в самом блоке,
    // либо в последнем ответе. Иначе догружать нечего и просить нечего.
    val hasCursor = !block.nextFrom.isNullOrBlank() || !paging?.nextFrom.isNullOrBlank()
    val isPagingLoading = paging?.isLoading == true
    val pagingError = paging?.error
    val exhausted = paging?.exhausted == true
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Догрузка по прокрутке: как только до конца списка остаётся меньше 6
    // элементов — просим следующую порцию. Порог, а не «самый последний
    // элемент», чтобы подгрузка успевала до того, как список кончится.
    val shouldLoadMore by remember(allItems.size) {
        androidx.compose.runtime.derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= allItems.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore, hasCursor, isPagingLoading, exhausted, pagingError) {
        if (shouldLoadMore && hasCursor && !isPagingLoading && !exhausted && pagingError == null) {
            onLoadMore()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = lc.settingsBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = block.title.ifBlank { stringResource(R.string.vk_music_brand) },
                color = lc.textPrimary,
                fontSize = if (compact) 19.sp else 23.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VkSansDisplay,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 2.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("${allItems.size} элементов")
                        // Пока курсор жив, точное число элементов неизвестно —
                        // «+» честнее, чем выдавать первую порцию за весь список.
                        if (hasCursor && !exhausted) append("+")
                    },
                    color = lc.textSecondary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!block.actions.playBlockId.isNullOrBlank() || playableItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 40.dp else 44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(lc.accent.copy(alpha = 0.16f))
                            .clickable {
                                if (!block.actions.playBlockId.isNullOrBlank()) {
                                    onPlayBlock()
                                } else {
                                    PlayerController.playFromList(
                                        context = context,
                                        tracks = playableItems.map { it.toTrack() },
                                        playbackContext = PlaybackContext.Catalog(block.id),
                                    )
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (block.actions.shuffled) {
                                com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28
                            } else {
                                com.lmg.vk.ui.icons.LmgGlyphs.Play28
                            },
                            contentDescription = if (block.actions.shuffled) stringResource(R.string.action_shuffle) else stringResource(R.string.listen_all),
                            tint = lc.accent,
                            modifier = Modifier.size(if (compact) 19.dp else 21.dp),
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            ) {
                itemsIndexed(allItems, key = { _, item -> "sheet_${block.id}_${item.id}" }) { index, item ->
                    NewTrackRow(
                        item = item,
                        rank = if (block.layoutName.startsWith("music_chart")) item.rank ?: (index + 1) else null,
                        compact = compact,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        onClick = { onItemClick(item) },
                    )
                }
                // Подвал списка: спиннер догрузки, ошибка с повтором или явное
                // «это всё». Молча обрывать список нельзя — непонятно, кончился он
                // или сломалась подгрузка.
                if (isPagingLoading) {
                    item(key = "sheet_${block.id}_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = lc.accent,
                            )
                        }
                    }
                }
                pagingError?.let { message ->
                    item(key = "sheet_${block.id}_error") {
                        NewInlineError(
                            message = message,
                            compact = compact,
                            onRetry = onRetryLoadMore,
                        )
                    }
                }
                if (exhausted && paging?.extraItems?.isNotEmpty() == true) {
                    item(key = "sheet_${block.id}_end") {
                        Text(
                            text = stringResource(R.string.thats_all_from_vk),
                            color = lc.textSecondary,
                            fontSize = if (compact) 11.sp else 12.sp,
                            fontFamily = AppFontFamily,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Official `open_section` destination backed by `catalog.getSection`. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewCatalogSectionSheet(
    state: com.lmg.vk.ui.viewmodel.CatalogSectionUiState,
    compact: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
) {
    val lc = LiquidTheme.colors
    val title = when (state) {
        is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Loading -> state.title
        is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Ready -> state.title
        is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Failed -> state.title
        com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Idle -> stringResource(R.string.vk_music_brand)
    }.ifBlank { stringResource(R.string.vk_music_brand) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = lc.settingsBackground,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                text = title,
                color = lc.textPrimary,
                fontSize = if (compact) 19.sp else 23.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VkSansDisplay,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            when (state) {
                is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = lc.accent,
                    )
                }

                is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Failed -> NewLoadError(
                    message = state.message,
                    compact = compact,
                    onRetry = onRetry,
                )

                is com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Ready -> {
                    val entries = remember(state.blocks) {
                        state.blocks.flatMap { block ->
                            block.items.map { item -> block.id to item }
                        }
                    }
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val nearEnd by remember(entries.size) {
                        androidx.compose.runtime.derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= entries.size - 6
                        }
                    }
                    LaunchedEffect(nearEnd, state.nextFrom, state.isLoadingMore, state.error) {
                        if (nearEnd && !state.nextFrom.isNullOrBlank() &&
                            !state.isLoadingMore && state.error == null
                        ) {
                            onLoadMore()
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(
                            entries,
                            key = { index, entry -> "section_${entry.first}_${entry.second.id}_$index" },
                        ) { _, entry ->
                            NewTrackRow(
                                item = entry.second,
                                rank = null,
                                compact = compact,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                onClick = { onItemClick(entry.second) },
                            )
                        }
                        if (state.isLoadingMore) {
                            item(key = "catalog_section_loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = lc.accent,
                                    )
                                }
                            }
                        }
                        state.error?.let { message ->
                            item(key = "catalog_section_error") {
                                NewInlineError(message, compact, onRetry)
                            }
                        }
                    }
                }

                com.lmg.vk.ui.viewmodel.CatalogSectionUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun NewTrackCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    compact: Boolean = false,
    wide: Boolean = false,
    showRank: Boolean = false,
    rank: Int? = null,
    enabled: Boolean = true,
    dimWhenDisabled: Boolean = true,
    onClick: () -> Unit
) {
    val lc = LiquidTheme.colors
    val cardSize = when {
        compact && wide -> 138.dp
        compact -> 110.dp
        wide -> 172.dp
        else -> 140.dp
    }
    val canClick = enabled && !title.isBlank()
    Column(
        modifier = Modifier
            .width(cardSize)
            .clickable(enabled = canClick, onClick = onClick)
            .alpha(if (!enabled && dimWhenDisabled) 0.42f else 1f),
    ) {
        Box {
            AlbumArtImage(
                uri = null,
                contentDescription = title,
                coverUrl = coverUrl,
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
            .clickable(enabled = item.isInteractive, onClick = onClick),
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = item.title,
            coverUrl = item.cover,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val imageSize = if (compact) 48.dp else 58.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.isInteractive && (!item.isTrack || item.isAvailable),
                onClick = onClick,
            )
            .alpha(if (item.isTrack && !item.isAvailable) 0.42f else 1f),
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
                text = item.subtitle
                    ?: item.artist
                    ?: if (item.isCustom) stringResource(R.string.vk_music_brand) else item.displayArtist,
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

/**
 * Крупная карточка для «больших» слайдеров VK X: `large_slider`,
 * `music_chart_large_slider`, `music_exclusive_slider`, `recomms_slider`.
 *
 * Отличие от [NewTrackCard] не только в размере: обложка здесь широкая (4:3), а
 * не квадратная, поэтому в один экран попадает меньше карточек — именно этим VK
 * и выделяет такие блоки среди обычных каруселей.
 */
@Composable
private fun NewLargeCard(
    item: com.lmg.vk.engine.backend.HomeItem,
    compact: Boolean,
    showRank: Boolean,
    onClick: () -> Unit,
) {
    val lc = LiquidTheme.colors
    val cardWidth = if (compact) 190.dp else 248.dp
    val imageHeight = if (compact) 140.dp else 184.dp
    val enabled = item.isInteractive && (!item.isTrack || item.isAvailable)
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(enabled = enabled && item.title.isNotBlank(), onClick = onClick)
            .alpha(if (!enabled && item.isInteractive) 0.42f else 1f),
    ) {
        Box {
            AlbumArtImage(
                uri = null,
                contentDescription = item.title,
                coverUrl = item.cover,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(cardWidth)
                    .height(imageHeight)
                    .clip(RoundedCornerShape(14.dp)),
            )
            item.streamMixAnimationUrl?.takeIf(String::isNotBlank)?.let { animationUrl ->
                NewMixBackgroundAnimation(
                    url = animationUrl,
                    animationId = item.streamMixCatalogItemId ?: item.streamMixId.orEmpty(),
                    modifier = Modifier
                        .width(cardWidth)
                        .height(imageHeight)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
            if (showRank && item.rank != null) {
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC111111)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.rank.toString(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
        Text(
            text = item.title,
            color = lc.textPrimary,
            fontSize = if (compact) 13.sp else 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        (item.subtitle ?: item.artist ?: item.displayArtist.takeIf { it.isNotBlank() })
            ?.let { subtitle ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = lc.textSecondary,
                    fontSize = if (compact) 11.5.sp else 12.5.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

private class NewMixAnimationViewState {
    var sourcePath: String? = null
    var sourceFile: File? = null
    var remoteUrl: String = ""
    var animationId: String = ""
    var exportedPath: String? = null
}

/** Official VK renders `background_animation_url` as a looping remote Lottie. */
@Composable
private fun NewMixBackgroundAnimation(
    url: String,
    animationId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceFile by produceState<File?>(initialValue = null, url, animationId) {
        value = withContext(Dispatchers.IO) {
            VkMixLottieStore.getOrDownload(
                context = context.applicationContext,
                optionId = "background_$animationId",
                url = url,
            )
        }
    }
    val file = sourceFile ?: return

    AndroidView(
        factory = { viewContext ->
            LottieAnimationView(viewContext).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                repeatCount = LottieDrawable.INFINITE
                setSafeMode(true)
                tag = NewMixAnimationViewState()
                setFailureListener { error ->
                    val state = tag as? NewMixAnimationViewState
                    state?.sourceFile?.let(VkMixLottieStore::quarantine)
                    setImageDrawable(null)
                    DebugLog.add(
                        "VK MIX background failed: id=${state?.animationId.orEmpty()}, " +
                            error.javaClass.simpleName,
                    )
                }
                addLottieOnCompositionLoadedListener {
                    val state = tag as? NewMixAnimationViewState ?: return@addLottieOnCompositionLoadedListener
                    repeatCount = LottieDrawable.INFINITE
                    playAnimation()
                    val path = state.sourcePath ?: return@addLottieOnCompositionLoadedListener
                    val localFile = state.sourceFile ?: return@addLottieOnCompositionLoadedListener
                    if (state.exportedPath != path) {
                        state.exportedPath = path
                        scope.launch(Dispatchers.IO) {
                            VkMixLottieStore.exportValidated(
                                context = viewContext.applicationContext,
                                optionId = "background_${state.animationId}",
                                url = state.remoteUrl,
                                source = localFile,
                            )
                        }
                    }
                }
            }
        },
        update = { view ->
            val state = (view.tag as? NewMixAnimationViewState)
                ?: NewMixAnimationViewState().also { view.tag = it }
            state.remoteUrl = url
            state.animationId = animationId
            state.sourceFile = file
            if (state.sourcePath != file.absolutePath) {
                state.sourcePath = file.absolutePath
                view.cancelAnimation()
                view.setNewMixAnimation(file)
            }
        },
        onRelease = { view ->
            view.cancelAnimation()
            view.removeAllLottieOnCompositionLoadedListener()
            view.setFailureListener(null)
            view.setImageDrawable(null)
        },
        modifier = modifier,
    )
}

private fun LottieAnimationView.setNewMixAnimation(file: File) {
    val zipped = FileInputStream(file).use { input ->
        input.read() == 0x50 && input.read() == 0x4B
    }
    val cacheKey = "vk_mix_background_${file.nameWithoutExtension}"
    if (zipped) {
        setAnimation(ZipInputStream(FileInputStream(file)), cacheKey)
    } else {
        setAnimation(FileInputStream(file), cacheKey)
    }
}

/**
 * Карточка расширенного аудио-контента (`audio_content_card_extended_slider`).
 * У VK это блок с описанием, поэтому подзаголовок здесь живёт в две строки, а
 * обложка квадратная — карточка вытянута вертикально.
 */
@Composable
private fun NewExtendedCard(
    item: com.lmg.vk.engine.backend.HomeItem,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val lc = LiquidTheme.colors
    val cardWidth = if (compact) 152.dp else 196.dp
    val enabled = item.isInteractive && (!item.isTrack || item.isAvailable)
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(enabled = enabled && item.title.isNotBlank(), onClick = onClick)
            .alpha(if (!enabled && item.isInteractive) 0.42f else 1f),
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = item.title,
            coverUrl = item.cover,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(cardWidth).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
        Text(
            text = item.title,
            color = lc.textPrimary,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        (item.subtitle ?: item.artist ?: item.displayArtist.takeIf { it.isNotBlank() })
            ?.let { subtitle ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = lc.textSecondary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

/**
 * Сетка из двух рядов для `entity_double_grid` и `categories_grid`.
 *
 * Прокрутка горизонтальная, но элементы идут парами по вертикали — так VK
 * показывает жанры и подборки. Разбиваем список на столбцы по два, чтобы
 * порядок читался слева-вниз-вправо, как в оригинале.
 */
@Composable
private fun NewDoubleGrid(
    blockId: String,
    items: List<com.lmg.vk.engine.backend.HomeItem>,
    compact: Boolean,
    rowGap: androidx.compose.ui.unit.Dp,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
) {
    val columns = remember(blockId, items) { items.chunked(2) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        items(columns, key = { column -> "${blockId}_grid_${column.first().id}" }) { column ->
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                column.forEach { item ->
                    NewGridTile(item = item, compact = compact, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

/** Плитка сетки: обложка слева, подписи справа — компактнее вертикальной карточки. */
@Composable
private fun NewGridTile(
    item: com.lmg.vk.engine.backend.HomeItem,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val lc = LiquidTheme.colors
    val artSize = if (compact) 52.dp else 62.dp
    val tileWidth = if (compact) 208.dp else 252.dp
    val enabled = item.isInteractive && (!item.isTrack || item.isAvailable)
    Row(
        modifier = Modifier
            .width(tileWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && item.title.isNotBlank(), onClick = onClick)
            .alpha(if (!enabled && item.isInteractive) 0.42f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = item.title,
            coverUrl = item.cover,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(artSize).clip(RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = item.title,
                color = lc.textPrimary,
                fontSize = if (compact) 12.5.sp else 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (item.subtitle ?: item.artist ?: item.displayArtist.takeIf { it.isNotBlank() })
                ?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = lc.textSecondary,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontFamily = AppFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
        }
    }
}

/**
 * Карусель широких баннеров — для «слайдерных» вариантов hero-семейства
 * (`promo_banners_slider`, `podcast_banners_slider`, `crop_slider`).
 *
 * Ширина карточки чуть меньше экрана: край следующего баннера видно, и это
 * подсказывает, что блок листается. Тот же приём у VK.
 */
@Composable
private fun NewBannerRow(
    blockId: String,
    items: List<com.lmg.vk.engine.backend.HomeItem>,
    compact: Boolean,
    rowGap: androidx.compose.ui.unit.Dp,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
) {
    val lc = LiquidTheme.colors
    val bannerWidth = if (compact) 268.dp else 320.dp
    val bannerHeight = if (compact) 126.dp else 152.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        items(items, key = { "${blockId}_banner_${it.id}" }) { item ->
            val enabled = item.isInteractive
            Column(
                modifier = Modifier
                    .width(bannerWidth)
                    .clickable(enabled = enabled, onClick = { onItemClick(item) }),
            ) {
                AlbumArtImage(
                    uri = null,
                    contentDescription = item.title,
                    coverUrl = item.cover,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(bannerWidth)
                        .height(bannerHeight)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Text(
                    text = item.title,
                    color = lc.textPrimary,
                    fontSize = if (compact) 13.5.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
                (item.subtitle ?: item.artist)?.takeIf(String::isNotBlank)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = lc.textSecondary,
                        fontSize = if (compact) 11.5.sp else 12.5.sp,
                        fontFamily = AppFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Закрываемый баннер каталога (`close_catalog_banner`).
 *
 * Отличие от обычного hero — крестик, и закрытие должно пережить перезапуск:
 * баннер, который возвращается после каждого старта, раздражает сильнее, чем
 * помогает. Поэтому id закрытых блоков хранится в [NewDismissedBanners].
 */
@Composable
private fun NewCloseableBanner(
    item: com.lmg.vk.engine.backend.HomeItem,
    compact: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lc = LiquidTheme.colors
    val imageHeight = if (compact) 142.dp else 192.dp
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        Column(modifier = Modifier.clickable(enabled = item.isInteractive, onClick = onClick)) {
            AlbumArtImage(
                uri = null,
                contentDescription = item.title,
                coverUrl = item.cover,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Text(
                text = item.title,
                color = lc.textPrimary,
                fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AppFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, end = 40.dp),
            )
            (item.subtitle ?: item.artist)?.takeIf(String::isNotBlank)?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = lc.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, end = 40.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x99111111))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                contentDescription = stringResource(R.string.action_hide),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Табы подразделов (`subsection_tabs`).
 *
 * Теперь по-настоящему: у каждого таба своя выдача, приходящая отдельным
 * запросом. Идентификатор таба — `replacement_id` из `actions[0].options`
 * (НЕ `section_id`, как предполагала спека — см. [HomeSubsectionTab]), а по
 * какому методу за ним идти, решает `parseCatalogTabRequest`.
 *
 * Выдача таба — это полноценные блоки CatalogKit, поэтому рисуем их тем же
 * [NewCatalogBlock]; собственной вёрстки у табов нет.
 *
 * Начальный таб — тот, у которого сервер поставил `selected` (так же ищет его
 * VK X в `AbstractC1574e`), иначе первый. Загрузку запускаем сразу: пустое место
 * под табами выглядело бы как сломанный блок.
 */
@Composable
private fun NewSubsectionTabs(
    block: com.lmg.vk.engine.backend.HomeBlock,
    compact: Boolean,
    rowGap: androidx.compose.ui.unit.Dp,
    tabStates: Map<String, com.lmg.vk.engine.backend.CatalogTabState>,
    selectedTabs: Map<String, String>,
    onSelectTab: (String, String) -> Unit,
    onRetryTab: (String) -> Unit,
    onItemClick: (com.lmg.vk.engine.backend.HomeItem) -> Unit,
    onPlaySignal: (com.lmg.vk.engine.backend.HomeSignalInfo, String?) -> Unit,
    onPlayBlock: (com.lmg.vk.engine.backend.HomeBlock, String?) -> Unit,
    onOpenSection: (String, String) -> Unit,
    loadingSignalBlocks: Set<String>,
    onOpenSheet: (com.lmg.vk.engine.backend.HomeBlock) -> Unit,
) {
    val lc = LiquidTheme.colors
    val tabs = block.subsectionTabs
    val defaultTab = remember(block.id, tabs) {
        (tabs.firstOrNull { it.selected } ?: tabs.first()).replacementId
    }
    val active = selectedTabs[block.id] ?: defaultTab
    // Первый заход: выдачи ещё нет ни для одного таба — просим дефолтный.
    LaunchedEffect(block.id, active) { onSelectTab(block.id, active) }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs, key = { "${block.id}_tab_${it.replacementId}" }) { tab ->
            val isActive = tab.replacementId == active
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) lc.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelectTab(block.id, tab.replacementId) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = tab.title,
                    color = if (isActive) lc.accent else lc.textSecondary,
                    fontSize = if (compact) 12.5.sp else 13.5.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    fontFamily = AppFontFamily,
                    maxLines = 1,
                )
            }
        }
    }

    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
    when (val state = tabStates[active]) {
        is com.lmg.vk.engine.backend.CatalogTabState.Ready -> {
            state.blocks.forEachIndexed { index, tabBlock ->
                if (index > 0) Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
                NewCatalogBlock(
                    block = tabBlock,
                    compact = compact,
                    rowGap = rowGap,
                    tabStates = tabStates,
                    selectedTabs = selectedTabs,
                    onSelectTab = onSelectTab,
                    onRetryTab = onRetryTab,
                    onItemClick = onItemClick,
                    onPlaySignal = onPlaySignal,
                    onPlayBlock = onPlayBlock,
                    onOpenSection = onOpenSection,
                    loadingSignalBlocks = loadingSignalBlocks,
                    onOpenSheet = onOpenSheet,
                    // Вложенные табы внутри таба не рисуем: VK такую структуру не
                    // строит, а рекурсия здесь ничем не ограничена.
                    allowTabs = false,
                )
            }
        }

        is com.lmg.vk.engine.backend.CatalogTabState.Failed -> NewInlineError(
            message = state.message,
            compact = compact,
            onRetry = { onRetryTab(active) },
        )

        // null трактуем как «запрос вот-вот уйдёт» (LaunchedEffect выше) — рисуем
        // тот же скелетон, что и при Loading, чтобы блок не мигал пустотой.
        else -> NewSubsectionSkeleton(compact = compact)
    }
}

/** Плейсхолдер выдачи таба: ряд карточек в размер обычной карусели. */
@Composable
private fun NewSubsectionSkeleton(compact: Boolean) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "newTabSkeleton")
        .animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(650),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "newTabSkeletonPulse"
        )
    val base = if (LiquidTheme.colors.isDark) Color(0xFF1A1A1A) else Color(0xFFEAEAEF)
    val cardSize = if (compact) 138.dp else 172.dp
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        repeat(3) {
            Column {
                Box(
                    modifier = Modifier
                        .size(cardSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(base.copy(alpha = pulse))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = cardSize * 0.7f, height = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(base.copy(alpha = pulse))
                )
            }
        }
    }
}

@Composable
private fun NewCuratorCard(
    item: com.lmg.vk.engine.backend.HomeItem,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val size = if (compact) 76.dp else 94.dp
    Column(
        modifier = Modifier
            .width(size)
            .clickable(enabled = item.isInteractive, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = item.title,
            coverUrl = item.cover,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(50)),
        )
        Text(
            text = item.title,
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

private fun com.lmg.vk.engine.backend.HomeBlock.isAudioCatalogBlock(): Boolean =
    type in setOf("music_audios", "audios", "charts", "recommendations") ||
        (items.isNotEmpty() && items.all { it.isTrack })

private fun com.lmg.vk.engine.backend.HomeBlock.isPeopleCatalogBlock(): Boolean =
    type in setOf("artist", "artists", "curator", "curators", "music_owners", "groups") ||
        (items.isNotEmpty() && items.all { it.isArtist || it.isMusicOwner })

private fun com.lmg.vk.engine.backend.HomeBlock.isCollectionCatalogBlock(): Boolean =
    type in setOf(
        "music_playlists",
        "music_recommended_playlists",
        "playlists",
        "albums",
    ) || (items.isNotEmpty() && items.all { it.isAlbum || it.isPlaylist })

private fun com.lmg.vk.engine.backend.HomeBlock.isRadioCatalogBlock(): Boolean =
    type in setOf("radiostations", "radio") || items.any { it.isRadio }

private val NEW_HERO_LAYOUTS = setOf(
    "banner",
    "promo_banners_slider",
    "snippets_banner",
    // Батч 2: то же семейство широких баннеров, отдельной вёрстки не требуют.
    // `crop_slider` у VK — обрезанная по высоте карусель, визуально это баннер.
    "podcast_banners_slider",
    "crop_slider",
    // Баннеры-подсказки и офферы из реестра 8.185 — та же широкая вёрстка.
    "help_hint_banner",
    "assistant_banner",
    "small_banner_offer",
)

/**
 * «Большие» слайдеры VK X — крупная карточка 4:3 вместо квадрата.
 * Имена подтверждены реестром `Catalog2LayoutJsonAdapter` из VK X,
 * см. docs/vkx-port/06-new-section.md.
 */
private val NEW_LARGE_SLIDER_LAYOUTS = setOf(
    "large_slider",
    "music_chart_large_slider",
    "music_exclusive_slider",
    "recomms_slider",
    "audio_stream_mix",
    "audio_stream_mix_interactive",
    // Из реестра официального клиента 8.185 (197 layout против 43 у VK X).
    // Зацикленные карусели: вёрстка та же, отличие только в поведении прокрутки,
    // которое мы всё равно не копируем.
    "infinite_large_slider",
    "infinite_promo_banners_slider",
    "infinite_podcast_banners_slider",
    "podcasts_extended_slider",
    "artist_merch_slider",
)

/** Блоки, которые VK показывает сеткой в два ряда, а не одной строкой. */
private val NEW_GRID_LAYOUTS = setOf(
    "entity_double_grid",
    "categories_grid",
    "categories_list",
    "podcast_category_genre_buttons",
    "horizontal_buttons",
    // Из реестра 8.185.
    "vertical_grid",
    "dynamic_grid",
    "horizontal_buttons_with_scroll",
    "horizontal_button_stack",
    "chips",
)

/**
 * Технические блоки-разделители из реестра VK X: собственного содержимого не
 * несут, а нарисованные как карточки выглядели бы мусором. `header*` отсекается
 * раньше, в `MusicBackend` (заголовок применяется к следующему блоку).
 */
private val NEW_SKIPPED_LAYOUTS = setOf(
    "separator",
    // separator_compact — настоящее имя в VK 8.185. Прежнее in_block_separator
    // в официальном клиенте отсутствует (0 вхождений), то есть разделитель
    // этого вида не отсекался и рисовался пустой карточкой.
    "separator_compact",
    "placeholder",
    "empty",
    "none",
    "placeholder_big",
    "placeholder_small",
    "text",
    "music_newsfeed_title",
    "playable_item_in_progress",
)

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
    // Из реестра 8.185: стопки по 2 (у нас был только triple) и списки,
    // которые VK присылает, а мы не рисовали — блок терялся молча.
    "double_stacked_slider",
    "double_stacked_slider_minimalistic_card",
    "double_stacked_list",
    "double_stacked_list_minimalistic_card",
    "stacked_list",
    "featured_list",
)

/** Maps a VK home item to the common player model without a third-party resolver URI. */
private fun com.lmg.vk.engine.backend.HomeItem.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = displayArtist,
    albumName = album.orEmpty(),
    uri = if (isRadio) {
        android.net.Uri.parse(radioStreamUrl.orEmpty())
    } else {
        com.lmg.vk.engine.VkAudioIdentity.playbackUri()
    },
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
