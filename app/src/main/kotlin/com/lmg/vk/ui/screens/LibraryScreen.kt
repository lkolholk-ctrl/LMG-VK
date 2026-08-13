package com.lmg.vk.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.ProfileLibrarySearch
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.data.local.db.FavoriteTrackEntity
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlaylistSyncManager
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.glass.GlassKit
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.components.PlaylistNameDialog
import com.lmg.vk.ui.components.SectionTopBar
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.components.PlaylistPickerSheet
import com.lmg.vk.ui.components.TrackActionsSheet
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.viewmodel.LibraryViewModel
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException

private val AppleRed = Color(0xFFFC3C44)

private enum class LibraryView { MAIN, PLAYLISTS, RECENT, FAVORITES, DOWNLOADS, LOCAL_PLAYLISTS, IMPORTED, LOCAL_AUDIO }

private enum class FavoriteSort { DEFAULT, TITLE, ARTIST }
private enum class PlaylistSource { ALL, LOCAL, CLOUD }
private enum class PlaylistSort { DEFAULT, NAME, TRACK_COUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onOpenPlaylist: (String) -> Unit = {},
    onOpenLocalLibrary: () -> Unit = {},
    // Вход на экран «Загрузки». Дефолт — прежнее поведение (внутренний вид
    // LibraryView.DOWNLOADS), поэтому старые вызовы LibraryScreen не ломаются.
    onOpenDownloads: (() -> Unit)? = null,

    backdrop: LayerBackdrop? = null
) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // viewModel(), НЕ remember{} (P1, аудит): remember создавал VM мимо
    // ViewModelStore — два вечных Room-коллектора из init + БЕЗУСЛОВНЫЙ
    // syncWithCloud (полный сетевой обход лайков) на КАЖДЫЙ заход в таб.
    // applicationContext — чтобы retained-VM не держал Activity.
    val appContext = remember(context) { context.applicationContext }
    val viewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        initializer = { LibraryViewModel(appContext) }
    )

    var currentView by remember { mutableStateOf(LibraryView.MAIN) }
    var libraryQuery by remember { mutableStateOf("") }
    var favoriteSort by remember { mutableStateOf(FavoriteSort.DEFAULT) }
    var recentTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlistQuery by remember { mutableStateOf("") }
    var playlistSource by remember { mutableStateOf(PlaylistSource.ALL) }
    var playlistSort by remember { mutableStateOf(PlaylistSort.DEFAULT) }

    // Адаптив: в широком окне (телефон-альбом / планшет) сетка плейлистов
    // получает больше колонок, а вертикальные списки-строки центрируем узкой
    // колонкой ~600dp боковыми отступами, чтобы строки не растягивались.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // Альбом: скромный отступ, чтобы списки заполняли ширину (как шапка), а не
    // висели узкой колонкой по центру с пустыми боками (полевой фидбек).
    val wideSidePad = if (win.useSideBySide) 24.dp else 20.dp

    // Favorites state
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profileSearch by viewModel.profileSearch.collectAsState()
    val isProfileSearchLoading by viewModel.isProfileSearchLoading.collectAsState()
    val profileSearchError by viewModel.profileSearchError.collectAsState()
    val currentTrack by PlayerController.currentTrack.collectAsState()

    // Downloads state
    val db = remember { FavoriteTrackDatabase.getInstance(context) }
    val downloadedTracks by db.downloadsFlow.collectAsState(initial = emptyList())
    val downloadedTrackIds = remember(downloadedTracks) {
        downloadedTracks.mapTo(mutableSetOf()) {
            com.lmg.vk.engine.VkAudioIdentity.stableFullId(it.trackId)
        }
    }
    val currentTrackId = remember(currentTrack) {
        currentTrack?.id?.let(com.lmg.vk.engine.VkAudioIdentity::stableFullId)
    }

    // Imported state
    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    val activeAccountId by MusicAuth.profileId.collectAsState()
    var importedPlaylists by remember { mutableStateOf<List<com.lmg.vk.engine.backend.UserPlaylist>>(emptyList()) }
    var isPlaylistsLoading by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val localPlaylists by com.lmg.vk.engine.PlaylistManager.playlists.collectAsState()
    val playlistSyncState by PlaylistSyncManager.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val allPlaylistCells = remember(localPlaylists, importedPlaylists, activeAccountId) {
        val linkedRemoteIds = localPlaylists
            .filter { it.remoteOwnerId == activeAccountId }
            .mapNotNull { it.remoteId }
            .toSet()
        localPlaylists.map { p ->
            val syncedToActiveAccount = p.remoteId != null && p.remoteOwnerId == activeAccountId
            PlaylistCellData(
                key = "local_${p.id}",
                id = p.id,
                name = p.name,
                trackCount = p.tracks.size,
                covers = p.tracks.mapNotNull { it.coverUrl }.distinct().take(4),
                badge = if (syncedToActiveAccount) "Synced" else "Local",
                isImported = false,
                isCloud = syncedToActiveAccount,
            )
        } + importedPlaylists.filterNot { playlist ->
            playlist.id?.let { it in linkedRemoteIds } == true
        }.map { p ->
            PlaylistCellData(
                key = "cloud_${p.id}",
                id = p.id ?: "",
                name = p.name.orEmpty(),
                trackCount = p.trackCount ?: 0,
                covers = listOfNotNull(p.cover?.replace("1000x1000", "400x400")),
                badge = "VK",
                isImported = true,
                isCloud = true,
            )
        }
    }

    // Load cloud playlists when entering the Imported view or when logged in
    fun loadImportedPlaylists() {
        if (isLoggedIn) {
            scope.launch {
                isPlaylistsLoading = true
                try {
                    PlaylistSyncManager.sync()
                    val response = MusicBackend.getUserPlaylists(limit = 1000)
                    importedPlaylists = response.items
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Ошибка VK-сессии или сети не должна завершать UI-процесс.
                    // Сохраняем уже показанные плейлисты; повтор доступен кнопкой.
                } finally {
                    isPlaylistsLoading = false
                }
            }
        }
    }

    fun refreshLibrary() {
        viewModel.syncWithCloud()
        loadImportedPlaylists()
    }

    // Загрузки: если хост дал маршрут — уходим на отдельный экран (у него свой
    // бэкстек и системная кнопка «назад»), иначе остаёмся на прежнем внутреннем
    // виде. Одна точка, чтобы оба входа (таб и карточка) не разъехались.
    fun openDownloads() {
        if (onOpenDownloads != null) {
            onOpenDownloads()
        } else {
            currentView = LibraryView.DOWNLOADS
        }
    }

    LaunchedEffect(isLoggedIn, activeAccountId) {
        importedPlaylists = emptyList()
        loadImportedPlaylists()
    }

    LaunchedEffect(libraryQuery, isLoggedIn, activeAccountId) {
        viewModel.searchCurrentProfile(if (isLoggedIn) libraryQuery else "")
    }

    LaunchedEffect(currentView) {
        if (currentView == LibraryView.MAIN || currentView == LibraryView.RECENT) {
            recentTracks = runCatching {
                val ids = AppDatabase.getInstance(context).playbackHistoryDao()
                    .getRecentTrackIds(100)
                    .distinct()
                MusicBackend.getBatchTrackMeta(ids).getOrThrow().items
                    .filter { it.isSuccess }
                    .map {
                        Track(
                            id = it.trackId ?: it.id,
                            title = it.title.orEmpty(),
                            artist = it.artist.orEmpty(),
                            albumName = "",
                            uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
                            durationMs = it.durationMs,
                            albumId = it.collectionId?.hashCode()?.toLong() ?: -1L,
                            coverUrl = it.cover,
                        )
                    }
            }.getOrDefault(emptyList())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        when (currentView) {
            LibraryView.MAIN -> {
                var downloadsSize by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(downloadedTracks) {
                    downloadsSize = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val bytes = downloadedTracks.sumOf { entity ->
                            com.lmg.vk.data.local.PublicDownloads.sizeBytes(context, entity.localPath)
                        }
                        when {
                            bytes <= 0L -> null
                            bytes < (1L shl 20) -> "%.0f KB".format(bytes / 1024.0)
                            bytes < (1L shl 30) -> "%.0f MB".format(bytes / 1048576.0)
                            else -> "%.1f GB".format(bytes / 1073741824.0)
                        }
                    }
                }

                val matchingFavorites = remember(favorites, libraryQuery) {
                    favorites.filter {
                        libraryQuery.isBlank() ||
                            it.title.contains(libraryQuery, ignoreCase = true) ||
                            it.artistName.orEmpty().contains(libraryQuery, ignoreCase = true)
                    }
                }
                val favoritePreview = remember(matchingFavorites) { matchingFavorites.take(5) }
                val recentPreview = remember(recentTracks) { recentTracks.take(4) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 178.dp),
                ) {
                    item(key = "library_header") {
                        SectionTopBar(
                            title = "Library",
                            subtitle = "${favorites.size} tracks · ${allPlaylistCells.size} playlists · ${downloadedTracks.size} offline",
                            isDark = lc.isDark,
                        )
                    }

                    item(key = "library_search_sync") {
                        LibrarySearchSyncRow(
                            query = libraryQuery,
                            onQueryChange = { libraryQuery = it },
                            showSync = isLoggedIn,
                            isSyncing = isSyncing || playlistSyncState.isSyncing,
                            onSync = ::refreshLibrary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }

                    if (isLoggedIn && libraryQuery.trim().length >= 2) {
                        item(key = "library_profile_search") {
                            ProfileLibrarySearchResults(
                                result = profileSearch,
                                isLoading = isProfileSearchLoading,
                                error = profileSearchError,
                                onTrackClick = { track ->
                                    val index = profileSearch?.tracks?.indexOfFirst { it.id == track.id } ?: -1
                                    if (index >= 0) {
                                        PlayerController.play(context, profileSearch!!.tracks, index)
                                    }
                                },
                                onPlaylistClick = onOpenPlaylist,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    }

                    item(key = "library_quick_sections") {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                            LibrarySectionTitle(title = "Your library")
                            Spacer(Modifier.height(10.dp))
                            LibraryQuickSections(
                                playlistCount = allPlaylistCells.size,
                                downloadCount = downloadedTracks.size,
                                downloadsSize = downloadsSize,
                                onPlaylists = { currentView = LibraryView.PLAYLISTS },
                                onAlbums = onOpenLocalLibrary,
                                onArtists = onOpenLocalLibrary,
                                onDownloads = ::openDownloads,
                            )
                        }
                    }

                    item(key = "library_recent_header") {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            LibrarySectionTitle(
                                title = "Recently played",
                                count = recentTracks.size.takeIf { it > 0 },
                                action = "See all",
                                onAction = { currentView = LibraryView.RECENT },
                            )
                        }
                    }

                    if (recentPreview.isEmpty()) {
                        item(key = "library_recent_empty") {
                            CompactLibraryEmptyHint(
                                text = "Tracks you play will appear here",
                                icon = lmgVector(LmgDrawables.HistoryBackwardOutline28),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    } else {
                        items(recentPreview, key = { "recent-preview-${it.id}" }) { track ->
                            RecentTrackItem(
                                track = track,
                                compact = true,
                                horizontalPadding = 20.dp,
                            ) {
                                val index = recentTracks.indexOfFirst { it.id == track.id }
                                if (index >= 0) PlayerController.play(context, recentTracks, index)
                            }
                        }
                    }

                    item(key = "library_favorites_header") {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            LibrarySectionTitle(
                                title = if (libraryQuery.isBlank()) "My tracks" else "Search results",
                                count = if (libraryQuery.isBlank()) favorites.size else matchingFavorites.size,
                                action = if (libraryQuery.isBlank()) "See all" else "Show all",
                                onAction = { currentView = LibraryView.FAVORITES },
                                quickActionIcon = lmgVector(LmgDrawables.ShuffleOutline24),
                                quickActionDescription = "Shuffle my tracks",
                                quickActionEnabled = favorites.isNotEmpty(),
                                onQuickAction = { viewModel.shuffleAndPlay(context) },
                            )
                        }
                    }

                    if (favoritePreview.isEmpty()) {
                        item(key = "library_favorites_empty") {
                            CompactLibraryEmptyHint(
                                text = if (libraryQuery.isBlank()) "Your favorite tracks will appear here" else "No tracks match your search",
                                icon = lmgVector(LmgDrawables.FavoriteOutline28),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    } else {
                        items(favoritePreview, key = { "favorite-preview-${it.trackId}" }) { track ->
                            LibraryTrackPreview(
                                track = track,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            ) {
                                viewModel.playTrack(context, track.cloudTrackId ?: track.trackId)
                            }
                        }
                    }
                }
            }

            LibraryView.PLAYLISTS -> {
                val visiblePlaylists = remember(
                    allPlaylistCells,
                    playlistQuery,
                    playlistSource,
                    playlistSort,
                ) {
                    allPlaylistCells
                        .filter {
                            playlistQuery.isBlank() ||
                                it.name.contains(playlistQuery, ignoreCase = true)
                        }
                        .filter {
                            when (playlistSource) {
                                PlaylistSource.ALL -> true
                                PlaylistSource.LOCAL -> !it.isCloud
                                PlaylistSource.CLOUD -> it.isCloud
                            }
                        }
                        .let { cells ->
                            when (playlistSort) {
                                PlaylistSort.DEFAULT -> cells
                                PlaylistSort.NAME -> cells.sortedBy { it.name.lowercase() }
                                PlaylistSort.TRACK_COUNT -> cells.sortedByDescending { it.trackCount }
                            }
                        }
                }
                var playlistToDelete by remember { mutableStateOf<PlaylistCellData?>(null) }
                val syncMessage = when {
                    playlistSyncState.isSyncing -> "Synchronizing local and VK playlists…"
                    playlistSyncState.error != null -> playlistSyncState.error
                    playlistSyncState.lastReport != null -> playlistSyncState.lastReport?.let {
                        buildString {
                            append("Synced · ${it.pushed} uploaded · ${it.pulled} downloaded")
                            if (it.failed > 0) append(" · ${it.failed} failed")
                            if (it.deleted > 0) append(" · ${it.deleted} deleted")
                            if (it.unsupportedTracks > 0) append(" · ${it.unsupportedTracks} local-only tracks")
                        }
                    }
                    else -> null
                }

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = if (win.useSideBySide)
                        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(160.dp)
                    else androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 178.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SectionTopBar(
                            title = "Playlists",
                            subtitle = "${allPlaylistCells.size} collections",
                            isDark = lc.isDark,
                            modifier = Modifier.requiredWidth(screenWidth),
                            onBack = { currentView = LibraryView.MAIN },
                            actions = {
                                if (isLoggedIn) {
                                    CompactLibraryAction(
                                        label = if (playlistSyncState.isSyncing) "Syncing…" else "Sync",
                                        icon = lmgVector(LmgDrawables.RefreshOutline28),
                                        enabled = !playlistSyncState.isSyncing,
                                        onClick = { loadImportedPlaylists() },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                CompactLibraryAction(
                                    label = "New playlist",
                                    icon = lmgVector(LmgDrawables.ListPlusOutline20),
                                    emphasized = true,
                                    onClick = { showCreatePlaylistDialog = true },
                                    modifier = Modifier.weight(1f),
                                )
                            },
                        )
                    }

                    syncMessage?.let { message ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = message,
                                color = if (playlistSyncState.error != null) AppleRed else lc.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        LibrarySearchField(
                            value = playlistQuery,
                            onValueChange = { playlistQuery = it },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        PlaylistControls(
                            source = playlistSource,
                            sort = playlistSort,
                            onSourceChange = { playlistSource = it },
                            onSortChange = {
                                playlistSort = when (playlistSort) {
                                    PlaylistSort.DEFAULT -> PlaylistSort.NAME
                                    PlaylistSort.NAME -> PlaylistSort.TRACK_COUNT
                                    PlaylistSort.TRACK_COUNT -> PlaylistSort.DEFAULT
                                }
                            },
                        )
                    }

                    if (visiblePlaylists.isEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                EmptyState("No playlists found", com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28)
                            }
                        }
                    } else {
                        items(visiblePlaylists.size, key = { visiblePlaylists[it].key }) { index ->
                            val cell = visiblePlaylists[index]
                            PlaylistCell(
                                data = cell,
                                onClick = { onOpenPlaylist(cell.id) },
                                onLongPress = { playlistToDelete = cell },
                            )
                        }
                    }
                }

                playlistToDelete?.let { cell ->
                    GlassDialog(
                        visible = true,
                        onDismiss = { playlistToDelete = null },
                        title = "Delete Playlist",
                        message = "Are you sure you want to delete '${cell.name}'?",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                        iconTint = Color(0xFFFF5252),
                        primaryButton = GlassDialogButton(
                            text = "Delete",
                            onClick = {
                                if (cell.isImported) {
                                    scope.launch {
                                        PlaylistSyncManager.deleteRemote(cell.id)
                                        loadImportedPlaylists()
                                    }
                                } else {
                                    scope.launch {
                                        PlaylistSyncManager.deleteEverywhere(cell.id)
                                        loadImportedPlaylists()
                                    }
                                }
                                playlistToDelete = null
                            },
                            backgroundColor = Color(0xFFFF5252),
                            textColor = Color.White,
                        ),
                        secondaryButton = GlassDialogButton(
                            text = "Cancel",
                            onClick = { playlistToDelete = null },
                            backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                            textColor = lc.textSecondary,
                        ),
                    )
                }
            }

            LibraryView.LOCAL_AUDIO -> {
                LocalAudioView(
                    context = context,
                    onBack = { currentView = LibraryView.MAIN }
                )
            }

            LibraryView.RECENT -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 178.dp),
                ) {
                    item(key = "recent_header") {
                        SectionTopBar(
                            title = "Recent",
                            subtitle = if (recentTracks.isEmpty()) "Your listening history" else "${recentTracks.size} recent tracks",
                            isDark = lc.isDark,
                            onBack = { currentView = LibraryView.MAIN },
                        )
                    }
                    if (recentTracks.isEmpty()) {
                        item(key = "recent_empty") {
                            Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                EmptyState("No listening history yet", com.lmg.vk.ui.icons.LmgGlyphs.HistoryBackwardOutline28)
                            }
                        }
                    } else {
                        items(recentTracks, key = { it.id }) { track ->
                            RecentTrackItem(track = track) {
                                val index = recentTracks.indexOfFirst { it.id == track.id }
                                if (index >= 0) PlayerController.play(context, recentTracks, index)
                            }
                        }
                    }
                }
            }

            LibraryView.FAVORITES -> {
                // Трек, для которого открыто меню «...» / выбор плейлиста.
                var actionsTrack by remember { mutableStateOf<Track?>(null) }
                var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }

                val matchingFavorites = remember(favorites, libraryQuery) {
                    favorites.filter {
                        libraryQuery.isBlank() ||
                            it.title.contains(libraryQuery, ignoreCase = true) ||
                            it.artistName.orEmpty().contains(libraryQuery, ignoreCase = true)
                    }
                }
                val displayedFavorites = remember(matchingFavorites, favoriteSort) {
                    when (favoriteSort) {
                        FavoriteSort.DEFAULT -> matchingFavorites
                        FavoriteSort.TITLE -> matchingFavorites.sortedBy { it.title.lowercase() }
                        FavoriteSort.ARTIST -> matchingFavorites.sortedBy { it.artistName.orEmpty().lowercase() }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 178.dp),
                ) {
                    item(key = "favorites_header") {
                        SectionTopBar(
                            title = "My tracks",
                            subtitle = if (libraryQuery.isBlank()) {
                                "${favorites.size} favorite tracks"
                            } else {
                                "${matchingFavorites.size} search results"
                            },
                            isDark = lc.isDark,
                            onBack = { currentView = LibraryView.MAIN },
                        )
                    }

                    item(key = "favorites_search_sync") {
                        LibrarySearchSyncRow(
                            query = libraryQuery,
                            onQueryChange = { libraryQuery = it },
                            showSync = true,
                            isSyncing = isSyncing,
                            onSync = { viewModel.syncWithCloud() },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }

                    item(key = "favorites_controls") {
                        MyTracksControlBar(
                            enabled = favorites.isNotEmpty(),
                            sort = favoriteSort,
                            onPlayAll = { viewModel.playAll(context) },
                            onShuffle = { viewModel.shuffleAndPlay(context) },
                            onSort = {
                                favoriteSort = when (favoriteSort) {
                                    FavoriteSort.DEFAULT -> FavoriteSort.TITLE
                                    FavoriteSort.TITLE -> FavoriteSort.ARTIST
                                    FavoriteSort.ARTIST -> FavoriteSort.DEFAULT
                                }
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }

                    if (displayedFavorites.isEmpty() && !isSyncing) {
                        item(key = "favorites_empty") {
                            Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                EmptyState(
                                    if (libraryQuery.isBlank()) "No favorites yet" else "No tracks match your search",
                                    com.lmg.vk.ui.icons.LmgGlyphs.Favorite28,
                                )
                            }
                        }
                    } else {
                        items(displayedFavorites, key = { it.trackId }) { track ->
                            val stableTrackIds = listOfNotNull(track.trackId, track.cloudTrackId)
                                .map(com.lmg.vk.engine.VkAudioIdentity::stableFullId)
                            Box(
                                modifier = if (win.useSideBySide) {
                                    Modifier.padding(horizontal = wideSidePad)
                                } else {
                                    Modifier
                                },
                            ) {
                                FavoriteTrackItem(
                                    track = track,
                                    isLiked = com.lmg.vk.engine.VkAudioIdentity
                                        .stableFullId(track.trackId) in favoriteIds,
                                    enabled = track.isAvailable,
                                    compact = win.useSideBySide,
                                    isCurrent = currentTrackId != null && currentTrackId in stableTrackIds,
                                    isDownloaded = stableTrackIds.any { it in downloadedTrackIds },
                                    onClick = {
                                        viewModel.playTrack(
                                            context,
                                            track.cloudTrackId ?: track.trackId,
                                        )
                                    },
                                    onMore = {
                                        actionsTrack = Track(
                                            id = track.cloudTrackId ?: track.trackId,
                                            title = track.title,
                                            artist = track.artistName.orEmpty(),
                                            albumName = track.albumTitle ?: "",
                                            uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
                                            durationMs = track.durationMs,
                                            albumId = track.collectionId?.hashCode()?.toLong() ?: -1L,
                                            coverUrl = track.imageUrl,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Меню трека и выбор плейлиста — те же компоненты, что в поиске
                // и на экранах альбома/артиста, чтобы действия по «...» были
                // одинаковыми во всём приложении.
                actionsTrack?.let { t ->
                    TrackActionsSheet(
                        track = t,
                        isFavorite = com.lmg.vk.engine.VkAudioIdentity
                            .stableFullId(t.id) in favoriteIds,
                        onToggleFavorite = {
                            scope.launch {
                                LibraryRepository.getInstance(context).toggleFavorite(t)
                            }
                        },
                        onAddToPlaylist = { playlistPickerTrack = t },
                        onDismiss = { actionsTrack = null },
                    )
                }
                playlistPickerTrack?.let { t ->
                    PlaylistPickerSheet(
                        playlists = localPlaylists,
                        onSelect = { playlist ->
                            val added = com.lmg.vk.engine.PlaylistManager.addTrack(playlist.id, t)
                            android.widget.Toast.makeText(
                                context,
                                if (added) "Добавлено в ${playlist.name}"
                                else "Уже в ${playlist.name}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            playlistPickerTrack = null
                        },
                        onDismiss = { playlistPickerTrack = null },
                    )
                }
            }

            LibraryView.DOWNLOADS -> {
                var showClearAllDialog by remember { mutableStateOf(false) }
                var trackToDelete by remember { mutableStateOf<com.lmg.vk.data.local.db.DownloadedTrackEntity?>(null) }
                val isDialogActive = trackToDelete != null || showClearAllDialog
                val migration by com.lmg.vk.data.local.DownloadsMigrator.progress.collectAsState()

                // ── Screen content (blurred when dialog is active) ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isDialogActive) Modifier.blur(16.dp) else Modifier),
                    contentPadding = PaddingValues(bottom = 178.dp),
                ) {
                    item(key = "library_downloads_header") {
                        SectionTopBar(
                            title = "Downloads",
                            subtitle = "${downloadedTracks.size} offline tracks",
                            isDark = lc.isDark,
                            onBack = { currentView = LibraryView.MAIN },
                            actions = {
                                if (downloadedTracks.isNotEmpty()) {
                                    Spacer(Modifier.weight(1f))
                                    CompactLibraryAction(
                                        label = "Clear all",
                                        icon = lmgVector(LmgDrawables.DeleteSavedOutline28),
                                        emphasized = true,
                                        tint = lc.accentRed,
                                        onClick = { showClearAllDialog = true },
                                    )
                                }
                            },
                        )
                    }

                    item(key = "library_downloads_tabs") {
                        LibraryTabs(
                            isDark = lc.isDark,
                            downloaded = true,
                            onLibrary = { currentView = LibraryView.MAIN },
                            onDownloaded = {},
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }

                    // Ненавязчивый прогресс одноразовой миграции скачанного в
                    // публичные Загрузки (сотни файлов — видно, что идёт работа).
                    migration?.let { (done, total) ->
                        item(key = "library_downloads_migration") {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                                Text(
                                    text = "Moving downloads to Downloads… $done/$total",
                                    color = LiquidTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { if (total > 0) done.toFloat() / total else 0f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    if (downloadedTracks.isEmpty()) {
                        item(key = "library_downloads_empty") {
                            Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                EmptyState("No downloaded tracks yet", com.lmg.vk.ui.icons.LmgGlyphs.DownloadOutline28)
                            }
                        }
                    } else {
                        items(downloadedTracks, key = { it.trackId }) { trackEntity ->
                            Box(
                                modifier = if (win.useSideBySide) {
                                    Modifier.padding(horizontal = wideSidePad)
                                } else {
                                    Modifier
                                },
                            ) {
                                DownloadedTrackItem(
                                    track = trackEntity,
                                    compact = win.useSideBySide,
                                    onClick = {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val tracks = downloadedTracks.map { entity ->
                                                Track(
                                                    id = entity.trackId,
                                                    title = entity.title,
                                                    artist = entity.artistName.orEmpty(),
                                                    albumName = entity.albumTitle ?: "",
                                                    // content:// (публичные Загрузки) или легаси-файл
                                                    uri = com.lmg.vk.data.local.PublicDownloads.toPlayableUri(entity.localPath),
                                                    durationMs = entity.durationMs,
                                                    albumId = entity.albumTitle?.hashCode()?.toLong() ?: -1L,
                                                    coverUrl = entity.localCoverPath ?: entity.imageUrl
                                                )
                                            }
                                            val startIndex = tracks.indexOfFirst { it.id == trackEntity.trackId }
                                            if (startIndex >= 0) {
                                                PlayerController.playLocalOnJuce(
                                                    context = context,
                                                    tracks = tracks,
                                                    startIndex = startIndex,
                                                    playbackContext = PlaybackContext.Downloads
                                                )
                                            }
                                        }
                                    },
                                    onDelete = { trackToDelete = trackEntity }
                                )
                            }
                        }
                    }
                }

                // ── Dialogs OUTSIDE the blurred content ──
                if (showClearAllDialog) {
                    GlassDialog(
                        visible = showClearAllDialog,
                        onDismiss = { showClearAllDialog = false },
                        title = "Clear All Downloads",
                        message = "This will permanently delete all ${downloadedTracks.size} downloaded tracks from your device and the database. This action cannot be undone.",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.DownloadOutline28,
                        iconTint = AppleRed,
                        primaryButton = GlassDialogButton(
                            text = "Clear All",
                            onClick = {
                                showClearAllDialog = false
                                scope.launch {
                                    AudioDownloadManager.clearAllDownloads(context)
                                }
                            },
                            backgroundColor = AppleRed,
                            textColor = Color.White
                        ),
                        secondaryButton = GlassDialogButton(
                            text = "Cancel",
                            onClick = { showClearAllDialog = false },
                            backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                            textColor = lc.textSecondary
                        )
                    )
                }

                trackToDelete?.let { track ->
                    GlassDialog(
                        visible = true,
                        onDismiss = { trackToDelete = null },
                        title = "Delete Offline Track",
                        message = "Remove '${track.title}' from your device storage?",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                        iconTint = Color(0xFFFF5252),
                        primaryButton = GlassDialogButton(
                            text = "Remove",
                            onClick = {
                                AudioDownloadManager.deleteDownloadedTrack(context, track.trackId)
                                trackToDelete = null
                            },
                            backgroundColor = Color(0xFFFF5252),
                            textColor = Color.White
                        ),
                        secondaryButton = GlassDialogButton(
                            text = "Cancel",
                            onClick = { trackToDelete = null },
                            backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                            textColor = lc.textSecondary
                        )
                    )
                }
            }

            LibraryView.LOCAL_PLAYLISTS -> {
                val localPlaylists by com.lmg.vk.engine.PlaylistManager.playlists.collectAsState()
                var playlistToDelete by remember { mutableStateOf<com.lmg.vk.engine.PlaylistManager.Playlist?>(null) }
                val isDialogActive = playlistToDelete != null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isDialogActive) Modifier.blur(16.dp) else Modifier)
                ) {
                    SubHeader("My Playlists", onBack = { currentView = LibraryView.MAIN }) {
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(lmgVector(LmgDrawables.ListPlusOutline20), null, tint = Color(0xFF30D158), modifier = Modifier.size(24.dp))
                        }
                    }

                    if (localPlaylists.isEmpty()) {
                        EmptyState("No playlists yet.\nCreate one to get started!", com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = if (win.useSideBySide) wideSidePad else 20.dp,
                                end = if (win.useSideBySide) wideSidePad else 20.dp,
                                bottom = 120.dp
                            )
                        ) {
                            items(localPlaylists, key = { it.id }) { playlist ->
                                LocalPlaylistRow(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.id) },
                                    onDelete = { playlistToDelete = playlist }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // ── Screen-level modal dialog for local playlist deletion ──
                playlistToDelete?.let { playlist ->
                    GlassDialog(
                        visible = true,
                        onDismiss = { playlistToDelete = null },
                        title = "Delete Playlist",
                        message = "Are you sure you want to delete '${playlist.name}'?",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                        iconTint = Color(0xFFFF5252),
                        primaryButton = GlassDialogButton(
                            text = "Delete",
                            onClick = {
                                scope.launch {
                                    PlaylistSyncManager.deleteEverywhere(playlist.id)
                                    loadImportedPlaylists()
                                }
                                playlistToDelete = null
                            },
                            backgroundColor = Color(0xFFFF5252),
                            textColor = Color.White
                        ),
                        secondaryButton = GlassDialogButton(
                            text = "Cancel",
                            onClick = { playlistToDelete = null },
                            backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                            textColor = lc.textSecondary
                        )
                    )
                }
            }

            LibraryView.IMPORTED -> {
                var importedPlaylistToDelete by remember { mutableStateOf<com.lmg.vk.engine.backend.UserPlaylist?>(null) }
                val isDialogActive = importedPlaylistToDelete != null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isDialogActive) Modifier.blur(16.dp) else Modifier)
                ) {
                    SubHeader("Imported Playlists", onBack = { currentView = LibraryView.MAIN }) {
                        if (isLoggedIn) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { loadImportedPlaylists() }) {
                                    Icon(com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28, null, tint = lc.iconMuted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    if (!isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28, null, tint = lc.iconMuted, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Sync Playlists", color = lc.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = VkSansDisplay)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Sign in to your account in the Profile tab to view and sync your VK playlists.",
                                    color = lc.textSecondary,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    } else if (isPlaylistsLoading && importedPlaylists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppleRed)
                        }
                    } else {
                        if (importedPlaylists.isEmpty()) {
                            EmptyState("No playlists yet.", com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28)
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    start = if (win.useSideBySide) wideSidePad else 20.dp,
                                    end = if (win.useSideBySide) wideSidePad else 20.dp,
                                    bottom = 120.dp
                                )
                            ) {
                                items(importedPlaylists, key = { it.id ?: "" }) { playlist ->
                                    ImportedPlaylistRow(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist.id ?: "") },
                                        onDelete = { importedPlaylistToDelete = playlist }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // ── Screen-level modal dialog for imported playlist deletion ──
                importedPlaylistToDelete?.let { playlist ->
                    GlassDialog(
                        visible = true,
                        onDismiss = { importedPlaylistToDelete = null },
                        title = "Delete Playlist",
                        message = "Are you sure you want to delete '${playlist.name}' from your backend library?",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                        iconTint = Color(0xFFFF5252),
                        primaryButton = GlassDialogButton(
                            text = "Delete",
                            onClick = {
                                scope.launch {
                                    PlaylistSyncManager.deleteRemote(playlist.id ?: "")
                                    loadImportedPlaylists()
                                }
                                importedPlaylistToDelete = null
                            },
                            backgroundColor = Color(0xFFFF5252),
                            textColor = Color.White
                        ),
                        secondaryButton = GlassDialogButton(
                            text = "Cancel",
                            onClick = { importedPlaylistToDelete = null },
                            backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                            textColor = lc.textSecondary
                        )
                    )
                }
            }
        }

        if (showCreatePlaylistDialog) {
            PlaylistNameDialog(
                title = "New playlist",
                confirmLabel = "Create",
                onConfirm = { name ->
                    com.lmg.vk.engine.PlaylistManager.create(name)
                    showCreatePlaylistDialog = false
                },
                onDismiss = { showCreatePlaylistDialog = false },
            )
        }

        errorMessage?.let { message ->
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message)
                viewModel.clearError()
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 96.dp),
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Components
// ═════════════════════════════════════════════════════════════════

@Composable
private fun LibraryTabs(
    isDark: Boolean,
    downloaded: Boolean,
    onLibrary: () -> Unit,
    onDownloaded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lc = LiquidTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        listOf("Library" to false, "Downloaded" to true).forEach { (title, tab) ->
            val selected = downloaded == tab
            Text(
                text = title,
                color = if (selected) lc.accent else lc.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (selected) lc.accent.copy(alpha = if (isDark) 0.18f else 0.12f)
                        else Color.Transparent,
                    )
                    .liquidClickable(onClick = if (tab) onDownloaded else onLibrary)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

private data class LibraryQuickItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconSize: androidx.compose.ui.unit.Dp = 19.dp,
    val onClick: () -> Unit,
)

@Composable
private fun LibrarySearchSyncRow(
    query: String,
    onQueryChange: (String) -> Unit,
    showSync: Boolean,
    isSyncing: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySearchField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
        )
        if (showSync) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (isSyncing) 0.58f else 1f)
                    .clip(CircleShape)
                    .background(LiquidSurfaces.card(LiquidTheme.colors.isDark))
                    .liquidClickable(
                        enabled = !isSyncing,
                        pressedScale = LiquidMotion.PressButton,
                        onClick = onSync,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = lmgVector(LmgDrawables.RefreshOutline28),
                    contentDescription = if (isSyncing) "Syncing tracks" else "Sync tracks",
                    tint = LiquidTheme.colors.accent,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionTitle(
    title: String,
    count: Int? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    quickActionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    quickActionDescription: String? = null,
    quickActionEnabled: Boolean = true,
    onQuickAction: () -> Unit = {},
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildString {
                append(title)
                count?.let { append(" · $it") }
            },
            color = lc.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = VkSansDisplay,
            modifier = Modifier.weight(1f),
        )
        quickActionIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .alpha(if (quickActionEnabled) 1f else 0.4f)
                    .clip(CircleShape)
                    .liquidClickable(
                        enabled = quickActionEnabled,
                        pressedScale = LiquidMotion.PressIcon,
                        onClick = onQuickAction,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = quickActionDescription,
                    tint = lc.accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        action?.let { label ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .liquidClickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = lc.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = lmgVector(LmgDrawables.ChevronRightSmallOutline24),
                    contentDescription = null,
                    tint = lc.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryQuickSections(
    playlistCount: Int,
    downloadCount: Int,
    downloadsSize: String?,
    onPlaylists: () -> Unit,
    onAlbums: () -> Unit,
    onArtists: () -> Unit,
    onDownloads: () -> Unit,
) {
    val items = listOf(
        LibraryQuickItem(
            title = "Playlists",
            subtitle = "$playlistCount saved",
            icon = lmgVector(LmgDrawables.PlaylistOutline28),
            onClick = onPlaylists,
        ),
        LibraryQuickItem(
            title = "Albums",
            subtitle = "Saved and local",
            icon = lmgVector(LmgDrawables.AlbumFilled12),
            iconSize = 16.dp,
            onClick = onAlbums,
        ),
        LibraryQuickItem(
            title = "Artists",
            subtitle = "& curators",
            icon = lmgVector(LmgDrawables.Users3Outline28),
            onClick = onArtists,
        ),
        LibraryQuickItem(
            title = "Downloads",
            subtitle = downloadsSize ?: "$downloadCount offline",
            icon = lmgVector(LmgDrawables.DownloadOutline28),
            onClick = onDownloads,
        ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            LibraryQuickTile(item = item, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LibraryQuickTile(
    item: LibraryQuickItem,
    modifier: Modifier = Modifier,
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = modifier
            .liquidClickable(
                pressedScale = LiquidMotion.PressButton,
                onClick = item.onClick,
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(LiquidSurfaces.card(lc.isDark)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = lc.accent,
                modifier = Modifier.size(item.iconSize),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.title,
            color = lc.textPrimary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            color = lc.textSecondary,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactLibraryEmptyHint(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(LiquidSurfaces.card(lc.isDark))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = lc.iconMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = lc.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun MyTracksControlBar(
    enabled: Boolean,
    sort: FavoriteSort,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactLibraryAction(
            label = "Play all",
            icon = lmgVector(LmgDrawables.Play28),
            emphasized = true,
            enabled = enabled,
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
        )
        CompactLibraryAction(
            label = "Shuffle",
            icon = lmgVector(LmgDrawables.ShuffleOutline24),
            enabled = enabled,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
        CompactLibraryAction(
            label = when (sort) {
                FavoriteSort.DEFAULT -> "Default"
                FavoriteSort.TITLE -> "Title"
                FavoriteSort.ARTIST -> "Artist"
            },
            icon = lmgVector(LmgDrawables.SortHorizontalOutline24),
            onClick = onSort,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactLibraryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val lc = LiquidTheme.colors
    val actionColor = tint ?: lc.accent
    Row(
        modifier = modifier
            .height(42.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LiquidSurfaces.card(lc.isDark)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = actionColor,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (emphasized) actionColor else lc.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lc = LiquidTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(color = lc.textPrimary, fontSize = 15.sp, fontFamily = VkSansText),
        cursorBrush = SolidColor(lc.accent),
        singleLine = true,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(LiquidSurfaces.card(lc.isDark))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28, null, tint = lc.iconMuted, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank()) Text("Search library", color = lc.textTertiary, fontSize = 15.sp)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun ProfileLibrarySearchResults(
    result: ProfileLibrarySearch?,
    isLoading: Boolean,
    error: String?,
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LiquidSurfaces.card(lc.isDark))
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "VK library",
            color = lc.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            isLoading -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = lc.accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("Searching your VK library…", color = lc.textSecondary, fontSize = 14.sp)
            }

            error != null -> Text(
                text = "Couldn't search VK library: $error",
                color = AppleRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            result != null && result.tracks.isEmpty() && result.playlists.isEmpty() -> Text(
                text = "Nothing found in your VK library",
                color = lc.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            result != null -> {
                result.playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = playlist.cover,
                            contentDescription = null,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(9.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                playlist.title,
                                color = lc.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Playlist · ${playlist.trackCount} tracks",
                                color = lc.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                        Icon(
                            com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
                            contentDescription = "Open playlist",
                            tint = lc.iconMuted,
                        )
                    }
                }
                if (result.playlists.isNotEmpty() && result.tracks.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = lc.textPrimary.copy(alpha = 0.08f),
                    )
                }
                result.tracks.forEach { track ->
                    RecentTrackItem(track = track) { onTrackClick(track) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistControls(
    source: PlaylistSource,
    sort: PlaylistSort,
    onSourceChange: (PlaylistSource) -> Unit,
    onSortChange: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaylistSource.entries.forEach { option ->
            val selected = source == option
            Text(
                text = when (option) {
                    PlaylistSource.ALL -> "All"
                    PlaylistSource.LOCAL -> "Local"
                    PlaylistSource.CLOUD -> "Cloud"
                },
                color = if (selected) lc.accent else lc.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) lc.accent.copy(alpha = 0.14f)
                        else LiquidSurfaces.card(lc.isDark),
                    )
                    .liquidClickable { onSourceChange(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .liquidClickable(onClick = onSortChange)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SortOutline28,
                contentDescription = null,
                tint = lc.accent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = when (sort) {
                    PlaylistSort.DEFAULT -> "Default"
                    PlaylistSort.NAME -> "A–Z"
                    PlaylistSort.TRACK_COUNT -> "By tracks"
                },
                color = lc.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** UI-модель ячейки локального или VK-плейлиста. */
private data class PlaylistCellData(
    val key: String,
    val id: String,
    val name: String,
    val trackCount: Int,
    val covers: List<String>,
    val badge: String?,
    val isImported: Boolean,
    val isCloud: Boolean,
)

@Composable
private fun PlaylistCell(
    data: PlaylistCellData,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(LiquidSurfaces.card(lc.isDark))
        ) {
            when {
                data.covers.size >= 4 -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            MosaicTile(data.covers[0], Modifier.weight(1f))
                            MosaicTile(data.covers[1], Modifier.weight(1f))
                        }
                        Row(Modifier.weight(1f)) {
                            MosaicTile(data.covers[2], Modifier.weight(1f))
                            MosaicTile(data.covers[3], Modifier.weight(1f))
                        }
                    }
                }
                data.covers.isNotEmpty() -> {
                    AsyncImage(
                        model = data.covers.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Icon(
                        com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28,
                        null,
                        tint = lc.iconMuted,
                        modifier = Modifier.size(40.dp).align(Alignment.Center)
                    )
                }
            }
            data.badge?.let { badge ->
                SourceBadge(
                    source = badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            data.name,
            color = lc.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${data.trackCount} tracks",
            color = lc.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MosaicTile(url: String, modifier: Modifier) {
    AsyncImage(
        model = url.replace("1000x1000", "300x300"),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxHeight()
    )
}

/** VK marker for remotely synced playlists. */
@Composable
private fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFF2787F5)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "VK",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Stage 7b — экран локальной музыки (MediaStore). Запрашивает READ_MEDIA_AUDIO
 * (13+) / READ_EXTERNAL_STORAGE, сканирует библиотеку, играет через Media3
 * (PlayerController.playFromList) — content:// URI проходит как локальный файл.
 */
@Composable
private fun LocalAudioView(
    context: Context,
    onBack: () -> Unit
) {
    val lc = LiquidTheme.colors
    val scope = rememberCoroutineScope()

    // Адаптив: в широком окне центрируем список треков узкой колонкой ~600dp.
    val laWin = com.lmg.vk.ui.rememberWindowInfo()
    val laSidePad = if (laWin.useSideBySide)
        (((laWin.widthDp - 600) / 2).coerceAtLeast(12)).dp else 12.dp

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun scan() {
        scope.launch {
            loading = true
            tracks = com.lmg.vk.data.local.LocalAudioRepository.load(context)
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) scan()
    }

    LaunchedEffect(Unit) {
        if (hasPermission) scan() else permissionLauncher.launch(permission)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubHeader("Local Audio", onBack = onBack) {
            if (tracks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        // Stage 8b — локальное аудио играем полностью через JUCE.
                        PlayerController.playLocalOnJuce(
                            context = context,
                            tracks = tracks.shuffled(),
                            startIndex = 0
                        )
                    }) {
                        Icon(com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        when {
            !hasPermission -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24, null, tint = lc.iconMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Allow access to your music", color = lc.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = VkSansDisplay)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Grant permission to scan and play audio stored on this device.",
                            color = lc.textSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        ActionButton("Grant Permission", com.lmg.vk.ui.icons.LmgGlyphs.ScanViewfinderOutline28, onClick = { permissionLauncher.launch(permission) })
                    }
                }
            }
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF9F0A))
                }
            }
            tracks.isEmpty() -> {
                EmptyState("No local audio found", com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = laSidePad, end = laSidePad, bottom = 120.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        LocalTrackRow(
                            track = track,
                            compact = laWin.useSideBySide,
                            onClick = {
                                val startIndex = tracks.indexOfFirst { it.id == track.id }
                                if (startIndex >= 0) {
                                    // Stage 8b — локальное аудио играем полностью через JUCE.
                                    PlayerController.playLocalOnJuce(
                                        context = context,
                                        tracks = tracks,
                                        startIndex = startIndex
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalTrackRow(
    track: Track,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 40.dp else 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(lc.glassTint),
            contentAlignment = Alignment.Center
        ) {
            // Иконка-заглушка снизу; обложка (если есть) рисуется поверх.
            Icon(com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24, null, tint = lc.iconMuted, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
            AsyncImage(
                model = track.albumArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = lc.textPrimary, fontSize = if (compact) 13.5.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SubHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val lc = LiquidTheme.colors
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 34.dp else 40.dp)
                .background(lc.glassTint, CircleShape)
                .clip(CircleShape)
                .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28, null, tint = lc.textPrimary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }

        Spacer(Modifier.width(if (compact) 12.dp else 16.dp))

        Text(
            text = title,
            fontSize = if (compact) 20.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = VkSansDisplay,
            color = lc.textPrimary,
            modifier = Modifier.weight(1f)
        )

        Row(content = actions)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = lc.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = VkSansDisplay,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(lc.glassTint, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", color = lc.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    val lc = LiquidTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, RoundedCornerShape(12.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = lc.textTertiary, fontSize = 14.sp)
    }
}

@Composable
private fun ImportedPlaylistRow(
    playlist: com.lmg.vk.engine.backend.UserPlaylist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val lc = LiquidTheme.colors
    val shape = RoundedCornerShape(28.dp)   // эталон радиуса — карточки настроек

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LiquidSurfaces.card(lc.isDark))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (!playlist.cover.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.cover.replace("1000x1000", "200x200"),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(lc.glassTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28,
                        null,
                        tint = lc.iconMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name.orEmpty(),
                color = lc.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.trackCount} tracks",
                color = lc.textSecondary,
                fontSize = 13.sp
            )
        }

        IconButton(onClick = onDelete) {
            Icon(com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28, null, tint = lc.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Row component for local playlists from PlaylistManager.
 */
@Composable
private fun LocalPlaylistRow(
    playlist: com.lmg.vk.engine.PlaylistManager.Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val lc = LiquidTheme.colors
    val shape = RoundedCornerShape(28.dp)   // эталон радиуса — карточки настроек

    // Get cover from first track if available
    val firstTrackWithCover = playlist.tracks.firstOrNull { !it.coverUrl.isNullOrBlank() }
    val coverUrl = firstTrackWithCover?.coverUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LiquidSurfaces.card(lc.isDark))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover / Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl.replace("1000x1000", "200x200"),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(lc.glassTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28,
                        null,
                        tint = lc.iconMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                color = lc.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.tracks.size} tracks",
                color = lc.textSecondary,
                fontSize = 13.sp
            )
        }

        IconButton(onClick = onDelete) {
            Icon(com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28, null, tint = lc.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Import Dialog
// ═════════════════════════════════════════════════════════════════

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lc = LiquidTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(lc.glassTint)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppleRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = lc.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LibraryTrackPreview(
    track: FavoriteTrackEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (track.isAvailable) 1f else 0.42f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = track.isAvailable, onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = null,
            coverUrl = track.imageUrl,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            placeholderIconSize = 21.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = lc.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (track.isAvailable) track.artistName.orEmpty()
                else "Unavailable · ${track.artistName.orEmpty()}",
                color = lc.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0L) {
            Text(formatDuration(track.durationMs), color = lc.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecentTrackItem(
    track: Track,
    compact: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onClick: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (track.isAvailable) 1f else 0.42f)
            .clickable(enabled = track.isAvailable, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = if (compact) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtImage(
            uri = null,
            contentDescription = null,
            coverUrl = track.coverUrl,
            modifier = Modifier.size(if (compact) 48.dp else 54.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            placeholderIconSize = if (compact) 20.dp else 23.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = lc.textPrimary,
                fontSize = if (compact) 14.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (track.isAvailable) track.artist else "Unavailable · ${track.artist}",
                color = lc.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0L) {
            Text(formatDuration(track.durationMs), color = lc.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FavoriteTrackItem(
    track: FavoriteTrackEntity,
    isLiked: Boolean,
    enabled: Boolean = true,
    isCurrent: Boolean = false,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onMore: () -> Unit,
    compact: Boolean = false
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val itemArtSize = if (compact) 42.dp else 50.dp
        val coverToDisplay = track.imageUrl?.takeIf { it.isNotBlank() }
            ?.replace("1000x1000", "300x300")
        Box(
            modifier = Modifier
                .size(itemArtSize)
                .then(
                    if (isCurrent) Modifier.border(1.5.dp, lc.accent, RoundedCornerShape(8.dp))
                    else Modifier,
                )
                .padding(if (isCurrent) 2.dp else 0.dp),
        ) {
            AlbumArtImage(
                uri = null,
                contentDescription = null,
                coverUrl = coverToDisplay,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop,
                placeholderIconSize = if (compact) 18.dp else 21.dp,
            )
            if (isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(LiquidSurfaces.card(lc.isDark)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = lmgVector(LmgDrawables.DownloadCheckOutline28),
                        contentDescription = "Downloaded",
                        tint = lc.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    GlassKit.ExplicitBadge()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.title,
                    color = if (isCurrent) lc.accent else lc.textPrimary,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = track.artistName?.takeIf { it.isNotBlank() }.let { name ->
                    when {
                        enabled -> name.orEmpty()
                        name != null -> "Недоступно · $name"
                        else -> "Недоступно"
                    }
                },
                color = lc.textSecondary,
                fontSize = if (compact) 11.5.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // «...» открывает общее меню трека, как на остальных экранах. Раньше
        // здесь стоял onToggleLike: кнопка с подписью «Опции» молча снимала
        // лайк, то есть УДАЛЯЛА трек из медиатеки одним нажатием, без
        // подтверждения и без возможности что-то ещё сделать.
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 36.dp)
                .clip(CircleShape)
                .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.MoreHorizontal28,
                contentDescription = "Опции",
                tint = lc.textTertiary,
                modifier = Modifier.size(if (compact) 19.dp else 22.dp)
            )
        }

        if (track.durationMs > 0) {
            Text(
                text = formatDuration(track.durationMs),
                color = lc.textSecondary,
                fontSize = if (compact) 11.sp else 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun DownloadedTrackItem(
    track: com.lmg.vk.data.local.db.DownloadedTrackEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    compact: Boolean = false
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val itemArtSize = if (compact) 44.dp else 56.dp
        val coverToLoad = track.localCoverPath?.takeIf { it.isNotBlank() }
            ?: track.imageUrl?.takeIf { it.isNotBlank() }
        AlbumArtImage(
            uri = null,
            contentDescription = null,
            coverUrl = coverToLoad,
            modifier = Modifier
                .size(itemArtSize)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = lc.textPrimary,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName.orEmpty(),
                color = lc.textSecondary,
                fontSize = if (compact) 11.5.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                contentDescription = null,
                tint = lc.textTertiary,
                modifier = Modifier.size(if (compact) 19.dp else 22.dp)
            )
        }

        if (track.durationMs > 0) {
            Text(
                text = formatDuration(track.durationMs),
                color = lc.textSecondary,
                fontSize = if (compact) 11.sp else 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val lc = LiquidTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lc.textTertiary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = lc.textSecondary,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ═════════════════════════════════════════════════════════════════
//  Cascade Ripple Animation — Playback Transition
// ═════════════════════════════════════════════════════════════════

/**
 * Wraps a track row with the Cascade Ripple playback animation.
 * When [isPlaying] becomes true, triggers:
 * 1. A soft color-tinted ripple from the center of the row
 * 2. A temporary micro-blur on the row container edges
 * 3. A smooth 4dp downward shift when items below are playing
 *
 * @param isPlaying Whether this track is currently the active playback target
 * @param itemIndex The index of this item in the list (for cascade shift calculation)
 * @param playingIndex The index of the currently playing item (-1 if none)
 * @param content The row content composable
 */
@Composable
private fun CascadeTrackRow(
    isPlaying: Boolean,
    itemIndex: Int,
    playingIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // ── Micro-shift for items below the playing track ──
    val shouldShift = playingIndex >= 0 && itemIndex > playingIndex
    val shiftY by animateDpAsState(
        targetValue = if (shouldShift) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "cascade_shift"
    )

    // ── Subtle blur when this item is the playing target ──
    val blurDp by animateDpAsState(
        targetValue = if (isPlaying) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "cascade_blur"
    )

    // ── Background glow alpha when playing ──
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.08f else 0f,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "cascade_glow"
    )

    Box(
        modifier = modifier
            .offset(y = shiftY)
            .then(if (blurDp.value > 0f) Modifier.blur(blurDp) else Modifier)
            .background(
                color = if (glowAlpha > 0f) AppleRed.copy(alpha = glowAlpha) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        content()
    }
}
