package com.lmg.vk.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.engine.backend.Album
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.engine.Track
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.ui.components.DetailHeader
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.components.DetailTrackRow
import com.lmg.vk.ui.components.TrackActionsSheet
import com.lmg.vk.ui.components.PlaylistNameDialog
import com.lmg.vk.ui.components.PlaylistPickerSheet
import com.lmg.vk.ui.components.formatTotalDuration
import com.lmg.vk.ui.components.toDetailThumb
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран плейлиста — на тех же общих частях, что альбом.
 *
 * Отличие от альбома: в строках показывается обложка трека, а не номер. В
 * плейлисте песни разные, и обложка узнаётся быстрее порядкового номера, тогда
 * как у альбома обложка одна на всех и номер полезнее.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    val scope = rememberCoroutineScope()

    var playlistInfo by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var loadedCount by remember { mutableStateOf(0) }
    var totalExpected by remember { mutableStateOf<Int?>(null) }
    var isFollowing by remember { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    var cacheRequested by remember { mutableStateOf(false) }
    var actionsTrack by remember { mutableStateOf<Track?>(null) }
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val libraryRepository = remember(context) { LibraryRepository.getInstance(context) }
    val favoriteIds by libraryRepository.favoriteIdsFlow.collectAsState(initial = emptySet())
    val downloadDb = remember(context) { FavoriteTrackDatabase.getInstance(context) }
    val downloadedTracks by downloadDb.downloadsFlow.collectAsState(initial = emptyList())
    val downloadProgress by AudioDownloadManager.downloadProgress.collectAsState()
    val isPremium by MusicAuth.isPremium.collectAsState()
    val managedPlaylists by PlaylistManager.playlists.collectAsState()

    // Локальные плейлисты живут в приложении, облачные — на сервере. Отличаются
    // по префиксу идентификатора.
    val isLocalPlaylist = playlistId.startsWith("pl_")
    val localPlaylist = remember(playlistId, managedPlaylists) {
        managedPlaylists.firstOrNull { it.id == playlistId }
    }

    LaunchedEffect(playlistId, reloadKey) {
        if (playlistId.isBlank()) {
            errorMsg = "Invalid playlist"
            return@LaunchedEffect
        }

        isLoading = true
        errorMsg = null
        tracks = emptyList()
        playlistInfo = null
        loadedCount = 0
        totalExpected = null
        isFollowing = false
        followBusy = false
        cacheRequested = false

        if (isLocalPlaylist) {
            if (localPlaylist == null) {
                errorMsg = "Playlist not found"
                isLoading = false
                return@LaunchedEffect
            }
            tracks = localPlaylist.toEngineTracks()
            loadedCount = tracks.size
            totalExpected = tracks.size
            isLoading = false
        } else {
            try {
                val allTracks = mutableListOf<Track>()
                var offset = 0
                val limit = 200
                var page = 0

                while (true) {
                    page++
                    val response =
                        MusicBackend.getUserPlaylistTracks(playlistId, limit = limit, offset = offset)
                    if (response == null) {
                        errorMsg = if (page == 1) {
                            "Failed to load playlist"
                        } else {
                            "Some tracks could not be loaded"
                        }
                        break
                    }

                    if (page == 1) {
                        val p = response.playlist
                        playlistInfo = p
                        isFollowing = p?.isFollowing == true
                        totalExpected = p?.trackCount
                    }

                    val pageTracks = response.tracks.mapNotNull { tr ->
                        val trackIdStr = tr.id.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val durationSec = tr.duration ?: 0L
                        // Часть источников отдаёт секунды, часть миллисекунды —
                        // различаем по величине.
                        val durationMs = if (durationSec < 10_000L) durationSec * 1000L else durationSec
                        Track(
                            id = trackIdStr,
                            title = tr.title.orEmpty(),
                            artist = tr.artist.orEmpty(),
                            albumName = "",
                            uri = Uri.parse("https://byicloud.online/track/$trackIdStr"),
                            durationMs = durationMs,
                            albumId = tr.collectionId?.hashCode()?.toLong()
                                ?: trackIdStr.hashCode().toLong(),
                            coverUrl = tr.cover.toDetailThumb(),
                            isAvailable = tr.isAvailable,
                        )
                    }

                    allTracks.addAll(pageTracks)
                    tracks = allTracks.distinctBy(Track::id)
                    loadedCount = tracks.size

                    if (response.tracks.size < limit) break
                    val expected = totalExpected
                    if (expected != null && allTracks.size >= expected) break
                    offset += limit
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(isLocalPlaylist, localPlaylist?.modifiedAt) {
        if (isLocalPlaylist && localPlaylist != null) {
            tracks = localPlaylist.toEngineTracks()
            loadedCount = tracks.size
            totalExpected = tracks.size
            errorMsg = null
            isLoading = false
        }
    }

    val name = remember(playlistId, playlistInfo, localPlaylist) {
        if (isLocalPlaylist) {
            localPlaylist?.name ?: "Playlist"
        } else {
            playlistInfo?.title ?: "Playlist"
        }
    }
    val playableTracks = remember(tracks) { tracks.filter { it.isAvailable } }
    val downloadedIds = remember(downloadedTracks) { downloadedTracks.map { it.trackId }.toSet() }
    val cachedCount = remember(playableTracks, downloadedIds) {
        playableTracks.count { it.id in downloadedIds }
    }
    val activeDownloadProgress = remember(playableTracks, downloadProgress) {
        playableTracks.mapNotNull { downloadProgress[it.id] }
    }
    val cacheProgress = remember(playableTracks, cachedCount, activeDownloadProgress) {
        when {
            playableTracks.isEmpty() -> 0f
            activeDownloadProgress.isNotEmpty() ->
                ((cachedCount + activeDownloadProgress.sum()) / playableTracks.size).coerceIn(0f, 1f)
            else -> cachedCount.toFloat() / playableTracks.size
        }
    }

    val listState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 320
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(LiquidSurfaces.sheet(isDark))) {
        when {
            isLoading && tracks.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                }

            errorMsg != null && tracks.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMsg.orEmpty(),
                            color = LiquidSurfaces.textSecondary(isDark),
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        RetryButton(isDark = isDark) { reloadKey++ }
                    }
                }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    item {
                        DetailHeader(
                            title = name,
                            // Обложки у плейлиста нет — берём обложку первого трека:
                            // пустой квадрат смотрелся бы как ошибка загрузки.
                            subtitle = if (isLocalPlaylist) {
                                "Your playlist"
                            } else {
                                playlistInfo?.artist?.takeIf(String::isNotBlank) ?: "VK Music"
                            },
                            facts = buildList {
                                if (tracks.isNotEmpty()) add("${tracks.size} songs")
                                val total = tracks.sumOf { it.durationMs }
                                if (total > 0) add(formatTotalDuration(total))
                                playlistInfo?.followers?.takeIf { it > 0 }?.let { add("${formatPlaylistCount(it)} followers") }
                            },
                            coverUrl = playlistInfo?.cover?.toDetailThumb()
                                ?: tracks.firstOrNull()?.coverUrl,
                            isDark = isDark,
                            onPlay = {
                                if (playableTracks.isNotEmpty()) PlayerController.play(context, playableTracks, 0)
                            },
                            onShuffle = {
                                if (playableTracks.isNotEmpty()) {
                                    PlayerController.play(context, playableTracks.shuffled(), 0)
                                }
                            }
                        )
                    }

                    item {
                        PlaylistActionsRow(
                            isDark = isDark,
                            isLocal = isLocalPlaylist,
                            isSynced = localPlaylist?.remoteId != null,
                            isOwned = playlistInfo?.isOwned == true,
                            isFollowing = isFollowing,
                            followEnabled = playlistInfo?.canFollow == true && !followBusy,
                            cacheEnabled = isPremium && playableTracks.isNotEmpty(),
                            queueEnabled = playableTracks.isNotEmpty(),
                            onAdd = {
                                if (!isLocalPlaylist && !isFollowing && playlistInfo?.canFollow == true) {
                                    scope.launch {
                                        followBusy = true
                                        if (MusicBackend.followPlaylist(playlistId)) isFollowing = true
                                        followBusy = false
                                    }
                                }
                            },
                            onCache = {
                                cacheRequested = true
                                playableTracks
                                    .filterNot { it.id in downloadedIds }
                                    .forEach { AudioDownloadManager.downloadTrack(context, it) }
                            },
                            onQueue = { playableTracks.forEach(PlayerController::addToQueue) },
                            onRename = { showRenameDialog = true },
                        )
                    }

                    if (cacheRequested || cachedCount > 0) {
                        item {
                            PlaylistCacheProgress(
                                cached = cachedCount,
                                total = playableTracks.size,
                                progress = cacheProgress,
                                isActive = activeDownloadProgress.isNotEmpty(),
                                isDark = isDark,
                            )
                        }
                    }

                    if (tracks.isEmpty() && !isLoading) {
                        item {
                            Text(
                                text = "This playlist is empty",
                                color = LiquidSurfaces.textSecondary(isDark),
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                            )
                        }
                    }

                    itemsIndexed(tracks, key = { index, track -> "${track.id}-$index" }) { index, track ->
                        DetailTrackRow(
                            position = index + 1,
                            title = track.title,
                            subtitle = track.artist,
                            durationMs = track.durationMs,
                            // В плейлисте песни разные — обложка узнаётся быстрее номера.
                            coverUrl = track.coverUrl,
                            isDark = isDark,
                            showDivider = index < tracks.lastIndex,
                            enabled = track.isAvailable,
                            onMore = if (track.isAvailable) {
                                { actionsTrack = track }
                            } else null,
                            onClick = {
                                val playableIndex = playableTracks.indexOfFirst { it.id == track.id }
                                if (playableIndex >= 0) {
                                    PlayerController.play(context, playableTracks, playableIndex)
                                }
                            }
                        )
                    }

                    if (isLoading && tracks.isNotEmpty()) {
                        item {
                            PlaylistLoadingMore(
                                loaded = loadedCount,
                                total = totalExpected,
                                isDark = isDark,
                            )
                        }
                    }

                    errorMsg?.takeIf { tracks.isNotEmpty() }?.let { partialError ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    partialError,
                                    color = LiquidSurfaces.textSecondary(isDark),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                RetryButton(isDark = isDark) { reloadKey++ }
                            }
                        }
                    }

                    playlistInfo?.let { info ->
                        item { PlaylistMetadata(info = info, isDark = isDark) }
                    }
                }
            }
        }

        DetailTopBar(
            title = name,
            showTitle = showTopBarTitle,
            isDark = isDark,
            onBack = onBack
        )

        actionsTrack?.let { selected ->
            val selectedIndex = tracks.indexOfFirst { it.id == selected.id }
            TrackActionsSheet(
                track = selected,
                isFavorite = selected.id in favoriteIds,
                onToggleFavorite = {
                    scope.launch { libraryRepository.toggleFavorite(selected) }
                },
                onCache = if (isPremium && selected.id !in downloadedIds) {
                    { AudioDownloadManager.downloadTrack(context, selected) }
                } else null,
                onAddToPlaylist = { playlistPickerTrack = selected },
                onRemoveFromPlaylist = if (isLocalPlaylist) {
                    { PlaylistManager.removeTrack(playlistId, selected.id) }
                } else null,
                onMoveUp = if (isLocalPlaylist && selectedIndex > 0) {
                    { PlaylistManager.moveTrack(playlistId, selectedIndex, selectedIndex - 1) }
                } else null,
                onMoveDown = if (isLocalPlaylist && selectedIndex in 0 until tracks.lastIndex) {
                    { PlaylistManager.moveTrack(playlistId, selectedIndex, selectedIndex + 1) }
                } else null,
                onDismiss = { actionsTrack = null },
            )
        }

        playlistPickerTrack?.let { selected ->
            PlaylistPickerSheet(
                playlists = managedPlaylists,
                onSelect = { playlist ->
                    val added = PlaylistManager.addTrack(playlist.id, selected)
                    Toast.makeText(
                        context,
                        if (added) "Added to ${playlist.name}" else "Already in ${playlist.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    playlistPickerTrack = null
                },
                onDismiss = { playlistPickerTrack = null },
            )
        }

        if (showRenameDialog && localPlaylist != null) {
            PlaylistNameDialog(
                title = "Rename playlist",
                initialName = localPlaylist.name,
                confirmLabel = "Rename",
                onConfirm = { newName ->
                    PlaylistManager.rename(playlistId, newName)
                    showRenameDialog = false
                },
                onDismiss = { showRenameDialog = false },
            )
        }
    }
}

@Composable
private fun PlaylistActionsRow(
    isDark: Boolean,
    isLocal: Boolean,
    isSynced: Boolean,
    isOwned: Boolean,
    isFollowing: Boolean,
    followEnabled: Boolean,
    cacheEnabled: Boolean,
    queueEnabled: Boolean,
    onAdd: () -> Unit,
    onCache: () -> Unit,
    onQueue: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlaylistActionButton(
            title = when {
                isSynced -> "Synced"
                isLocal -> "Local"
                isOwned -> "Yours"
                isFollowing -> "Added"
                else -> "Add"
            },
            icon = if (isLocal || isOwned || isFollowing) Icons.Rounded.Check else Icons.Rounded.Add,
            enabled = !isLocal && !isOwned && !isFollowing && followEnabled,
            isDark = isDark,
            onClick = onAdd,
        )
        PlaylistActionButton("Cache", Icons.Rounded.Download, cacheEnabled, isDark, onCache)
        PlaylistActionButton("Queue", Icons.Rounded.QueueMusic, queueEnabled, isDark, onQueue)
        if (isLocal) {
            PlaylistActionButton("Rename", Icons.Rounded.Edit, true, isDark, onRename)
        }
    }
}

private fun PlaylistManager.Playlist.toEngineTracks(): List<Track> = tracks.map { track ->
    Track(
        id = track.id,
        title = track.title,
        artist = track.artist,
        albumName = "",
        uri = Uri.parse("https://byicloud.online/track/${track.id}"),
        durationMs = track.durationMs,
        albumId = track.id.hashCode().toLong(),
        coverUrl = track.coverUrl,
    )
}

@Composable
private fun RowScope.PlaylistActionButton(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(16.dp))
            .background(LiquidSurfaces.card(isDark))
            .liquidClickable(enabled = enabled, pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = LiquidSurfaces.textPrimary(isDark), modifier = Modifier.size(18.dp))
        Text(
            title,
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun PlaylistCacheProgress(
    cached: Int,
    total: Int,
    progress: Float,
    isActive: Boolean,
    isDark: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = when {
                total > 0 && cached >= total -> "Playlist cached"
                isActive -> "Caching playlist · $cached/$total"
                else -> "Cached tracks · $cached/$total"
            },
            color = LiquidSurfaces.textSecondary(isDark),
            fontSize = 12.sp,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun PlaylistLoadingMore(loaded: Int, total: Int?, isDark: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(
            text = total?.let { "Loading tracks · $loaded/$it" } ?: "Loading tracks · $loaded",
            color = LiquidSurfaces.textSecondary(isDark),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun RetryButton(isDark: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LiquidSurfaces.card(isDark))
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Refresh, null, tint = LiquidSurfaces.textPrimary(isDark), modifier = Modifier.size(17.dp))
        Text(
            "Retry",
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun PlaylistMetadata(info: Album, isDark: Boolean) {
    val lines = buildList {
        info.plays?.takeIf { it > 0 }?.let { add("${formatPlaylistCount(it)} plays") }
        info.followers?.takeIf { it > 0 }?.let { add("${formatPlaylistCount(it)} followers") }
        info.createdAt?.takeIf { it > 0L }?.let { add("Created ${formatPlaylistDate(it)}") }
        info.updatedAt?.takeIf { it > 0L }?.let { add("Updated ${formatPlaylistDate(it)}") }
    }
    if (lines.isEmpty() && info.description.isNullOrBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        info.description?.takeIf(String::isNotBlank)?.let {
            Text(it, color = LiquidSurfaces.textPrimary(isDark), fontSize = 14.sp)
        }
        lines.forEach {
            Text(it, color = LiquidSurfaces.textSecondary(isDark), fontSize = 12.sp)
        }
    }
}

private fun formatPlaylistDate(seconds: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(seconds * 1000L))

private fun formatPlaylistCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000f)
    value >= 1_000 -> "%.1fK".format(Locale.US, value / 1_000f)
    else -> value.toString()
}
