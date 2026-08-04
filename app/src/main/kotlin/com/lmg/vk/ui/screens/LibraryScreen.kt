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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lmg.vk.R
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
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.components.PlaylistNameDialog
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.viewmodel.LibraryViewModel
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

    // Downloads state
    val db = remember { FavoriteTrackDatabase.getInstance(context) }
    val downloadedTracks by db.downloadsFlow.collectAsState(initial = emptyList())

    // Imported state
    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    var importedPlaylists by remember { mutableStateOf<List<com.lmg.vk.engine.backend.UserPlaylist>>(emptyList()) }
    var isPlaylistsLoading by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val localPlaylists by com.lmg.vk.engine.PlaylistManager.playlists.collectAsState()
    val playlistSyncState by PlaylistSyncManager.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val allPlaylistCells = remember(localPlaylists, importedPlaylists) {
        val linkedRemoteIds = localPlaylists.mapNotNull { it.remoteId }.toSet()
        localPlaylists.map { p ->
            PlaylistCellData(
                key = "local_${p.id}",
                id = p.id,
                name = p.name,
                trackCount = p.tracks.size,
                covers = p.tracks.mapNotNull { it.coverUrl }.distinct().take(4),
                badge = if (p.remoteId != null) "Synced" else "Local",
                isImported = false,
                isCloud = p.remoteId != null,
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

    LaunchedEffect(isLoggedIn) {
        loadImportedPlaylists()
    }

    LaunchedEffect(libraryQuery, isLoggedIn) {
        viewModel.searchCurrentProfile(if (isLoggedIn) libraryQuery else "")
    }

    LaunchedEffect(currentView) {
        if (currentView == LibraryView.RECENT) {
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
                // ── Вариант A (редизайн): системные разделы одной карточкой 28dp
                // с живыми сабтайтлами, ниже — сетка плейлистов 2 колонки с
                // мозаикой обложек. My Playlists и Imported слиты в одну сетку
                // (импортные — с бейджем источника), Local Audio + Медиатека —
                // один раздел «On this device». ──
                // Размер загрузок на диске — фоном, чтобы не трогать main.
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

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    // В альбоме/на планшете больше колонок под плейлисты (было 2);
                    // full-span заголовки/системная карточка тянутся во всю ширину.
                    columns = if (win.useSideBySide)
                        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 160.dp)
                    else androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 178.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column {
                            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Library",
                                    fontSize = if (win.useSideBySide) 26.sp else 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lc.textPrimary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).weight(1f)
                                )
                                if (isSyncing || playlistSyncState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = lc.accent,
                                        strokeWidth = 2.dp,
                                    )
                                } else if (isLoggedIn) {
                                    IconButton(onClick = ::refreshLibrary) {
                                        Icon(Icons.Filled.Refresh, "Refresh library", tint = lc.iconMuted)
                                    }
                                }
                            }
                        }
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        LibraryTabs(
                            isDark = lc.isDark,
                            downloaded = false,
                            onLibrary = {},
                            onDownloaded = { currentView = LibraryView.DOWNLOADS },
                        )
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        LibrarySearchField(
                            value = libraryQuery,
                            onValueChange = { libraryQuery = it },
                        )
                    }

                    if (isLoggedIn && libraryQuery.trim().length >= 2) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
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
                            )
                        }
                    }

                    // ── Системные разделы: одна карточка, строки с живым контентом ──
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(lc.cardSurface)
                        ) {
                            MenuCard("Recent", "Recently played", Icons.Rounded.History, lc.accent, { currentView = LibraryView.RECENT }, win.useSideBySide)
                            SystemRowDivider(compact = win.useSideBySide)
                            MenuCard(
                                "Playlists",
                                "${allPlaylistCells.size} playlists",
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                lc.accent,
                                { currentView = LibraryView.PLAYLISTS },
                                win.useSideBySide,
                            )
                            SystemRowDivider(compact = win.useSideBySide)
                            MenuCard("Albums", "Saved and local albums", Icons.Rounded.Album, lc.accent, onOpenLocalLibrary, win.useSideBySide)
                            SystemRowDivider(compact = win.useSideBySide)
                            MenuCard("Artists & curators", "Artists in your library", Icons.Rounded.Person, lc.accent, onOpenLocalLibrary, win.useSideBySide)
                            SystemRowDivider(compact = win.useSideBySide)
                            MenuCard(
                                title = "Downloaded music",
                                subtitle = downloadsSize
                                    ?.let { "${downloadedTracks.size} tracks · $it" }
                                    ?: "${downloadedTracks.size} tracks",
                                icon = Icons.Default.Download,
                                tint = Color(0xFF29B6F6),
                                compact = win.useSideBySide,
                                onClick = { currentView = LibraryView.DOWNLOADS }
                            )
                        }
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (libraryQuery.isBlank()) {
                                    "My tracks · ${favorites.size}"
                                } else {
                                    "Search results · ${matchingFavorites.size}"
                                },
                                color = lc.textPrimary,
                                fontSize = if (win.useSideBySide) 17.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (libraryQuery.isBlank()) "See all" else "Show all",
                                color = lc.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .liquidClickable { currentView = LibraryView.FAVORITES }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            IconButton(
                                enabled = favorites.isNotEmpty(),
                                onClick = { viewModel.shuffleAndPlay(context) },
                            ) {
                                Icon(Icons.Default.Shuffle, "Shuffle my tracks", tint = lc.accent)
                            }
                        }
                    }

                    items(
                        count = favoritePreview.size,
                        key = { "favorite-preview-${favoritePreview[it].trackId}" },
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    ) { index ->
                        LibraryTrackPreview(favoritePreview[index]) {
                            viewModel.playTrack(context, favoritePreview[index].trackId)
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

                Column(modifier = Modifier.fillMaxSize()) {
                    SubHeader("Playlists", onBack = { currentView = LibraryView.MAIN }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlistSyncState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = lc.accent,
                                    strokeWidth = 2.dp,
                                )
                            } else if (isLoggedIn) {
                                IconButton(onClick = { loadImportedPlaylists() }) {
                                    Icon(Icons.Filled.Refresh, "Sync playlists", tint = lc.iconMuted)
                                }
                            }
                            IconButton(onClick = { showCreatePlaylistDialog = true }) {
                                Icon(Icons.Rounded.Add, "Add playlist", tint = lc.accent)
                            }
                        }
                    }
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
                    syncMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (playlistSyncState.error != null) AppleRed else lc.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LibrarySearchField(
                        value = playlistQuery,
                        onValueChange = { playlistQuery = it },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
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

                    if (visiblePlaylists.isEmpty()) {
                        EmptyState("No playlists found", Icons.AutoMirrored.Rounded.PlaylistPlay)
                    } else {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = if (win.useSideBySide)
                                androidx.compose.foundation.lazy.grid.GridCells.Adaptive(160.dp)
                            else androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 178.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
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
                }

                playlistToDelete?.let { cell ->
                    GlassDialog(
                        visible = true,
                        onDismiss = { playlistToDelete = null },
                        title = "Delete Playlist",
                        message = "Are you sure you want to delete '${cell.name}'?",
                        icon = Icons.Rounded.Close,
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
                Column(modifier = Modifier.fillMaxSize()) {
                    SubHeader("Recent", onBack = { currentView = LibraryView.MAIN })
                    if (recentTracks.isEmpty()) {
                        EmptyState("No listening history yet", Icons.Rounded.History)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 178.dp),
                        ) {
                            items(recentTracks, key = { it.id }) { track ->
                                RecentTrackItem(track = track) {
                                    val index = recentTracks.indexOfFirst { it.id == track.id }
                                    if (index >= 0) PlayerController.play(context, recentTracks, index)
                                }
                            }
                        }
                    }
                }
            }

            LibraryView.FAVORITES -> {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    SubHeader(
                        if (libraryQuery.isBlank()) "My tracks · ${favorites.size}"
                        else "Search results · ${matchingFavorites.size}",
                        onBack = { currentView = LibraryView.MAIN },
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AppleRed,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { viewModel.syncWithCloud() }) {
                                Icon(Icons.Filled.Refresh, null, tint = lc.iconMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    LibrarySearchField(
                        value = libraryQuery,
                        onValueChange = { libraryQuery = it },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = when (favoriteSort) {
                                FavoriteSort.DEFAULT -> "Default"
                                FavoriteSort.TITLE -> "By title"
                                FavoriteSort.ARTIST -> "By artist"
                            },
                            color = lc.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .liquidClickable {
                                    favoriteSort = when (favoriteSort) {
                                        FavoriteSort.DEFAULT -> FavoriteSort.TITLE
                                        FavoriteSort.TITLE -> FavoriteSort.ARTIST
                                        FavoriteSort.ARTIST -> FavoriteSort.DEFAULT
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }

                    // Play/Shuffle
                    if (favorites.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionButton("Play All", Icons.Default.PlayArrow, onClick = { viewModel.playAll(context) }, modifier = Modifier.weight(1f))
                            ActionButton("Shuffle", Icons.Default.Shuffle, onClick = { viewModel.shuffleAndPlay(context) }, modifier = Modifier.weight(1f))
                        }
                    }

                    // Content
                    if (displayedFavorites.isEmpty() && !isSyncing) {
                        EmptyState(
                            if (libraryQuery.isBlank()) "No favorites yet" else "No tracks match your search",
                            Icons.Default.Favorite,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = if (win.useSideBySide)
                                PaddingValues(start = wideSidePad, end = wideSidePad, bottom = 178.dp)
                            else PaddingValues(bottom = 178.dp)
                        ) {
                            items(displayedFavorites, key = { it.trackId }) { track ->
                                FavoriteTrackItem(
                                    track = track,
                                    isLiked = track.trackId in favoriteIds,
                                    enabled = track.isAvailable,
                                    compact = win.useSideBySide,
                                    onClick = { viewModel.playTrack(context, track.trackId) },
                                    onToggleLike = {
                                        scope.launch {
                                            val repo = LibraryRepository.getInstance(context)
                                            val t = Track(
                                                id = track.trackId,
                                                title = track.title,
                                                artist = track.artistName ?: "Unknown Artist",
                                                albumName = track.albumTitle ?: "",
                                                uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
                                                durationMs = track.durationMs,
                                                albumId = track.collectionId?.hashCode()?.toLong() ?: -1L,
                                                coverUrl = track.imageUrl
                                            )
                                            repo.toggleFavorite(t)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            LibraryView.DOWNLOADS -> {
                var showClearAllDialog by remember { mutableStateOf(false) }
                var trackToDelete by remember { mutableStateOf<com.lmg.vk.data.local.db.DownloadedTrackEntity?>(null) }
                val isDialogActive = trackToDelete != null || showClearAllDialog

                // ── Screen content (blurred when dialog is active) ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isDialogActive) Modifier.blur(16.dp) else Modifier)
                ) {
                    SubHeader(
                        title = "Downloads",
                        onBack = { currentView = LibraryView.MAIN },
                        actions = {
                            if (downloadedTracks.isNotEmpty()) {
                                IconButton(onClick = { showClearAllDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear all downloads",
                                        tint = AppleRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    )

                    LibraryTabs(
                        isDark = lc.isDark,
                        downloaded = true,
                        onLibrary = { currentView = LibraryView.MAIN },
                        onDownloaded = {},
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )

                    // Ненавязчивый прогресс одноразовой миграции скачанного в
                    // публичные Загрузки (сотни файлов — видно, что идёт работа).
                    val migration by com.lmg.vk.data.local.DownloadsMigrator.progress.collectAsState()
                    migration?.let { (done, total) ->
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

                    if (downloadedTracks.isEmpty()) {
                        EmptyState("No downloaded tracks yet", Icons.Default.Download)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = if (win.useSideBySide)
                                PaddingValues(start = wideSidePad, end = wideSidePad, bottom = 178.dp)
                            else PaddingValues(bottom = 178.dp)
                        ) {
                            items(downloadedTracks, key = { it.trackId }) { trackEntity ->
                                DownloadedTrackItem(
                                    track = trackEntity,
                                    compact = win.useSideBySide,
                                    onClick = {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val tracks = downloadedTracks.map { entity ->
                                                Track(
                                                    id = entity.trackId,
                                                    title = entity.title,
                                                    artist = entity.artistName ?: "Unknown Artist",
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
                        icon = Icons.Default.Download,
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
                        icon = Icons.Rounded.Close,
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
                            Icon(Icons.Rounded.Add, null, tint = Color(0xFF30D158), modifier = Modifier.size(24.dp))
                        }
                    }

                    if (localPlaylists.isEmpty()) {
                        EmptyState("No playlists yet.\nCreate one to get started!", Icons.AutoMirrored.Rounded.PlaylistPlay)
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
                        icon = Icons.Rounded.Close,
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
                                    Icon(Icons.Filled.Refresh, null, tint = lc.iconMuted, modifier = Modifier.size(20.dp))
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
                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = lc.iconMuted, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Sync Playlists", color = lc.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                            EmptyState("No playlists yet.", Icons.AutoMirrored.Rounded.PlaylistPlay)
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
                        icon = Icons.Rounded.Close,
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
        textStyle = TextStyle(color = lc.textPrimary, fontSize = 15.sp),
        cursorBrush = SolidColor(lc.accent),
        singleLine = true,
        decorationBox = { inner ->
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(lc.cardSurface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = lc.iconMuted, modifier = Modifier.size(21.dp))
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
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(lc.cardSurface)
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
                            Icons.Rounded.ChevronRight,
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) lc.accent.copy(alpha = 0.14f) else lc.cardSurface)
                    .liquidClickable { onSourceChange(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = when (sort) {
                PlaylistSort.DEFAULT -> "Default"
                PlaylistSort.NAME -> "A–Z"
                PlaylistSort.TRACK_COUNT -> "By tracks"
            },
            color = lc.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .liquidClickable(onClick = onSortChange)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    compact: Boolean = false,
    trailing: @Composable () -> Unit = {}
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 58.dp else 72.dp)
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 44.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        }

        Spacer(Modifier.width(if (compact) 12.dp else 14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = lc.textPrimary, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = lc.textSecondary, fontSize = if (compact) 12.sp else 13.sp)
        }

        trailing()

        Icon(Icons.Rounded.ChevronRight, null, tint = lc.textTertiary, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
    }
}

/** Разделитель строк внутри системной карточки (с отступом под иконку). */
@Composable
private fun SystemRowDivider(compact: Boolean = false) {
    val lc = LiquidTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (compact) 64.dp else 74.dp, end = 16.dp)
            .height(0.8.dp)
            .background(lc.textPrimary.copy(alpha = 0.06f))
    )
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
                .background(lc.cardSurface)
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
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
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
            fontWeight = FontWeight.Black,
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
                        Icon(Icons.Default.Shuffle, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        when {
            !hasPermission -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MusicNote, null, tint = lc.iconMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Allow access to your music", color = lc.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Grant permission to scan and play audio stored on this device.",
                            color = lc.textSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        ActionButton("Grant Permission", Icons.Default.PlayArrow, onClick = { permissionLauncher.launch(permission) })
                    }
                }
            }
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF9F0A))
                }
            }
            tracks.isEmpty() -> {
                EmptyState("No local audio found", Icons.Rounded.MusicNote)
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
            Icon(Icons.Rounded.MusicNote, null, tint = lc.iconMuted, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
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
                Icons.AutoMirrored.Rounded.ArrowBack, null, tint = lc.textPrimary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }

        Spacer(Modifier.width(if (compact) 12.dp else 16.dp))

        Text(
            text = title,
            fontSize = if (compact) 20.sp else 24.sp,
            fontWeight = FontWeight.Bold,
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
        Text(title, color = lc.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            .background(lc.cardSurface)   // серая подложка как в настройках
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
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
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
            Icon(Icons.Rounded.Close, null, tint = lc.textTertiary, modifier = Modifier.size(22.dp))
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
            .background(lc.cardSurface)   // серая подложка как в настройках
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
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
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
            Icon(Icons.Rounded.Close, null, tint = lc.textTertiary, modifier = Modifier.size(22.dp))
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
private fun LibraryTrackPreview(track: FavoriteTrackEntity, onClick: () -> Unit) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (track.isAvailable) 1f else 0.42f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = track.isAvailable, onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.imageUrl,
            contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
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
private fun RecentTrackItem(track: Track, onClick: () -> Unit) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (track.isAvailable) 1f else 0.42f)
            .clickable(enabled = track.isAvailable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = lc.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (track.isAvailable) track.artist else "Unavailable · ${track.artist}",
                color = lc.textSecondary,
                fontSize = 13.sp,
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
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
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
        val itemArtSize = if (compact) 44.dp else 56.dp
        val coverToDisplay = track.imageUrl?.takeIf { it.isNotBlank() }
            ?.replace("1000x1000", "300x300")
            ?: "https://vk.com/images/audio_row_placeholder.png"
        AsyncImage(
            model = coverToDisplay,
            contentDescription = null,
            modifier = Modifier
                .size(itemArtSize)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    GlassKit.ExplicitBadge()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.title,
                    color = lc.textPrimary,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = if (enabled) {
                    track.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                } else {
                    "Недоступно · ${track.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist"}"
                },
                color = lc.textSecondary,
                fontSize = if (compact) 11.5.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
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
            ?: "https://vk.com/images/audio_row_placeholder.png"
        AsyncImage(
            model = coverToLoad,
            contentDescription = null,
            modifier = Modifier
                .size(itemArtSize)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
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
                text = track.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                color = lc.textSecondary,
                fontSize = if (compact) 11.5.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Close,
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
