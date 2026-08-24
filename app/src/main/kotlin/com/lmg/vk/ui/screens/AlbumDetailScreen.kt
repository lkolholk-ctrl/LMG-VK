package com.lmg.vk.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.backend.AlbumResponse
import com.lmg.vk.engine.backend.MiniArtist
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.ui.components.DetailHeader
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.components.DetailTrackRow
import com.lmg.vk.ui.components.TrackActionsSheet
import com.lmg.vk.ui.components.PlaylistPickerSheet
import com.lmg.vk.ui.components.formatTotalDuration
import com.lmg.vk.ui.components.releaseTypeLabel
import com.lmg.vk.ui.components.toDetailThumb
import com.lmg.vk.ui.components.vkMainColor
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран альбома.
 *
 * Разметка собрана из общих частей — тех же, что у плейлиста и остальных
 * подборок. Отличие альбома одно: в строках показывается номер, а не обложка,
 * потому что обложка здесь одна на все треки.
 */
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    val scope = rememberCoroutineScope()

    var album by remember { mutableStateOf<AlbumResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isFollowing by remember(albumId) { mutableStateOf(false) }
    var followBusy by remember(albumId) { mutableStateOf(false) }
    var reloadKey by remember(albumId) { mutableStateOf(0) }
    var actionsTrack by remember(albumId) { mutableStateOf<Track?>(null) }
    var playlistPickerTrack by remember(albumId) { mutableStateOf<Track?>(null) }
    var artistChooser by remember(albumId) { mutableStateOf<List<MiniArtist>?>(null) }
    val editablePlaylists by PlaylistManager.playlists.collectAsState()

    val libraryRepository = remember(context) { LibraryRepository.getInstance(context) }
    val favoriteIds by libraryRepository.favoriteIdsFlow.collectAsState(initial = emptySet())
    val downloadDb = remember(context) { FavoriteTrackDatabase.getInstance(context) }
    val downloadedTracks by downloadDb.downloadsFlow.collectAsState(initial = emptyList())
    val downloadProgress by AudioDownloadManager.downloadProgress.collectAsState()
    val isPremium by MusicAuth.isPremium.collectAsState()
    val activeAccountId by MusicAuth.profileId.collectAsState()

    LaunchedEffect(albumId, reloadKey, activeAccountId) {
        isLoading = true
        error = null
        album = null
        try {
            album = MusicBackend.getAlbum(albumId)
            isFollowing = album?.album?.isFollowing == true
            if (album == null) error = MusicBackend.lastError.value ?: context.getString(R.string.album_not_found)
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    val albumTracks = remember(album) {
        album?.tracks?.map { it.toTrack() }?.distinctBy { it.id } ?: emptyList()
    }
    val playableTracks = remember(albumTracks) { albumTracks.filter { it.isAvailable } }

    /**
     * Строки списка треков: у многодискового релиза между дисками стоит заголовок.
     *
     * Нумерация внутри каждого диска своя, с единицы, — как на самом релизе. До
     * этого двойной альбом выглядел одним списком, где номера доходили до 30, хотя
     * на обороте у обоих дисков нумерация начинается заново.
     *
     * `discNumber` живёт в модели каталога `AlbumTrack`, а не в [Track], поэтому по
     * дискам группируем ДО преобразования: у Track такого поля нет и плееру оно не
     * нужно — там важен только порядок.
     */
    val trackRows = remember(album, albumTracks) {
        val discById = album?.tracks.orEmpty()
            .mapNotNull { source -> source.discNumber?.let { source.id to it } }
            .toMap()
        val discs = albumTracks.mapNotNull { discById[it.id] }.distinct()
        if (discs.size < 2) {
            albumTracks.mapIndexed { index, track -> AlbumTrackRow(track, index + 1, null) }
        } else {
            var lastDisc = 0
            var position = 0
            albumTracks.map { track ->
                // Трек без номера части считаем продолжением предыдущего диска:
                // обрывать нумерацию из-за одного пропуска хуже, чем продолжить.
                val disc = discById[track.id] ?: lastDisc
                val header = if (disc != lastDisc) {
                    lastDisc = disc
                    position = 0
                    disc
                } else {
                    null
                }
                position++
                AlbumTrackRow(track, position, header)
            }
        }
    }
    val albumArtists = remember(album) {
        val info = album?.album
        info?.artists.orEmpty()
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .ifEmpty {
                info?.artistId
                    ?.takeIf(String::isNotBlank)
                    ?.let { listOf(MiniArtist(id = it, name = info.artist)) }
                    .orEmpty()
            }
    }
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
    val cacheLabel = when {
        playableTracks.isNotEmpty() && cachedCount == playableTracks.size -> stringResource(R.string.cached_label)
        activeDownloadProgress.isNotEmpty() -> "${(cacheProgress * 100).toInt()}%"
        else -> stringResource(R.string.action_cache)
    }

    var albumPlayCount by remember(albumId) { mutableStateOf(0) }
    var favouriteAlbumTrack by remember(albumId) { mutableStateOf<String?>(null) }
    LaunchedEffect(albumTracks, activeAccountId) {
        albumPlayCount = 0
        favouriteAlbumTrack = null
        if (albumTracks.isEmpty()) return@LaunchedEffect
        runCatching {
            val stats = AppDatabase.getInstance(context).playbackHistoryDao()
                .getAllTrackStats(AppDatabase.activeAccountId(), 500)
            val byId = albumTracks.associateBy { it.id }
            val albumStats = stats.filter { it.trackId in byId }
            albumPlayCount = albumStats.sumOf { it.playCount }
            favouriteAlbumTrack = albumStats.maxByOrNull { it.playCount }
                ?.takeIf { it.playCount > 0 }
                ?.let { byId[it.trackId]?.title }
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
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = error.orEmpty(),
                        color = LiquidSurfaces.textSecondary(isDark),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidSurfaces.card(isDark))
                            .liquidClickable(
                                pressedScale = LiquidMotion.PressButton,
                                onClick = { reloadKey++ },
                            )
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                            contentDescription = null,
                            tint = LiquidSurfaces.textPrimary(isDark),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(R.string.action_retry),
                            color = LiquidSurfaces.textPrimary(isDark),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            else -> {
                val info = album?.album
                LazyColumn(
                    state = listState,
                    // Ограничение ширины для планшетов и ландшафта: без него строка
                    // растягивается на весь экран, и номер с длительностью
                    // оказываются в разных его концах.
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    item {
                        DetailHeader(
                            title = info?.title.orEmpty(),
                            subtitle = info?.artist.orEmpty(),
                            facts = buildList {
                                // Вид релиза первым: «Single · 2024» читается сразу,
                                // а раньше сингл вообще подписывался как плейлист.
                                releaseTypeLabel(info?.type)?.let(::add)
                                info?.genre?.takeIf { it.isNotBlank() }?.let(::add)
                                info?.year?.takeIf { it.isNotBlank() }?.let(::add)
                                if (albumTracks.isNotEmpty()) add("${albumTracks.size} songs")
                                val unavailable = albumTracks.count { !it.isAvailable }
                                if (unavailable > 0) add("$unavailable unavailable")
                                // Общее время каталог не отдаёт — считаем по трекам.
                                val total = playableTracks.sumOf { it.durationMs }
                                if (total > 0) add(formatTotalDuration(total))
                            },
                            coverUrl = info?.cover.toDetailThumb(),
                            isDark = isDark,
                            onPlay = {
                                if (playableTracks.isNotEmpty()) {
                                    PlayerController.play(
                                        context,
                                        playableTracks,
                                        0,
                                        playbackContext = PlaybackContext.Album(albumId),
                                    )
                                }
                            },
                            onShuffle = {
                                if (playableTracks.isNotEmpty()) {
                                    PlayerController.play(
                                        context,
                                        playableTracks.shuffled(),
                                        0,
                                        playbackContext = PlaybackContext.Album(albumId),
                                    )
                                }
                            },
                            canPlay = playableTracks.isNotEmpty(),
                            mainColor = vkMainColor(info?.mainColor),
                        )
                    }

                    item {
                        AlbumActionsRow(
                            isDark = isDark,
                            isFollowing = isFollowing,
                            isAdding = followBusy,
                            canFollow = info?.canFollow == true && !followBusy,
                            cacheLabel = cacheLabel,
                            canDownload = isPremium && playableTracks.isNotEmpty() && cachedCount < playableTracks.size,
                            canQueue = playableTracks.isNotEmpty(),
                            onAdd = {
                                if (!isFollowing && info?.canFollow == true) {
                                    scope.launch {
                                        followBusy = true
                                        if (MusicBackend.followAlbum(albumId)) {
                                            isFollowing = true
                                            Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, R.string.album_add_failed, Toast.LENGTH_SHORT).show()
                                        }
                                        followBusy = false
                                    }
                                }
                            },
                            onDownload = {
                                playableTracks.forEach { AudioDownloadManager.downloadTrack(context, it) }
                                Toast.makeText(
                                    context,
                                    context.resources.getQuantityString(R.plurals.caching_tracks, playableTracks.size, playableTracks.size),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onQueue = {
                                playableTracks.forEach(PlayerController::addToQueue)
                                Toast.makeText(
                                    context,
                                    context.resources.getQuantityString(R.plurals.added_to_queue, playableTracks.size, playableTracks.size),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onShare = {
                                val shareId = info?.id?.takeIf { it.isNotBlank() } ?: albumId
                                val text = buildString {
                                    append(info?.title.orEmpty())
                                    info?.artist?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
                                    append("\nhttps://vk.com/music/album/").append(shareId.removePrefix("vk_"))
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, info?.title.orEmpty()))
                            },
                        )
                    }

                    if (albumArtists.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(LiquidSurfaces.card(isDark))
                                    .liquidClickable(
                                        pressedScale = LiquidMotion.PressButton,
                                        onClick = {
                                            if (albumArtists.size == 1) {
                                                onNavigateToArtist(albumArtists.first().id.orEmpty())
                                            } else {
                                                artistChooser = albumArtists
                                            }
                                        },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = info?.artist.orEmpty().ifBlank {
                                        albumArtists.joinToString(", ") { it.displayName }
                                    },
                                    color = LiquidSurfaces.textPrimary(isDark),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                                )
                                Icon(
                                    com.lmg.vk.ui.icons.LmgGlyphs.ArrowRightOutline28,
                                    contentDescription = stringResource(R.string.open_artist),
                                    tint = LiquidSurfaces.textSecondary(isDark),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }

                    if (albumPlayCount > 0) {
                        item {
                            AlbumPersonalStrip(
                                playCount = albumPlayCount,
                                favouriteTrack = favouriteAlbumTrack,
                                isDark = isDark,
                            )
                        }
                    }

                    itemsIndexed(trackRows, key = { _, row -> row.track.id }) { index, row ->
                        row.discHeader?.let { disc ->
                            Text(
                                text = stringResource(R.string.disc_title, disc),
                                color = LiquidSurfaces.textSecondary(isDark),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = if (index == 0) 4.dp else 18.dp,
                                    bottom = 6.dp,
                                ),
                            )
                        }
                        val track = row.track
                        DetailTrackRow(
                            position = row.position,
                            title = track.title,
                            subtitle = null,
                            durationMs = track.durationMs,
                            // Обложка здесь одна на все треки — номер полезнее.
                            coverUrl = null,
                            isDark = isDark,
                            // Перед заголовком следующего диска разделитель лишний:
                            // границу и без него видно, а вместе они дают двойную линию.
                            showDivider = index < trackRows.lastIndex &&
                                trackRows[index + 1].discHeader == null,
                            enabled = track.isAvailable,
                            onMore = if (track.isAvailable) {
                                { actionsTrack = track }
                            } else null,
                            onClick = {
                                val playableIndex = playableTracks.indexOfFirst { it.id == track.id }
                                if (playableIndex >= 0) {
                                    PlayerController.play(
                                        context,
                                        playableTracks,
                                        playableIndex,
                                        playbackContext = PlaybackContext.Album(albumId),
                                    )
                                }
                            }
                        )
                    }

                    item {
                        val metadata = buildList {
                            info?.releaseDate?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.released_date, it)) }
                            info?.plays?.takeIf { it > 0 }?.let { add(stringResource(R.string.plays_count, formatCount(it))) }
                            info?.followers?.takeIf { it > 0 }?.let { add(stringResource(R.string.followers_count, formatCount(it))) }
                            info?.createdAt?.takeIf { it > 0L }?.let { add(stringResource(R.string.created_at, formatCatalogDate(it))) }
                            info?.updatedAt?.takeIf { it > 0L }?.let { add(stringResource(R.string.updated_at, formatCatalogDate(it))) }
                        }
                        if (metadata.isNotEmpty() || !info?.description.isNullOrBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                info?.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = LiquidSurfaces.textPrimary(isDark), fontSize = 14.sp)
                                }
                                metadata.forEach {
                                    Text(it, color = LiquidSurfaces.textSecondary(isDark), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        DetailTopBar(
            title = album?.album?.title.orEmpty(),
            showTitle = showTopBarTitle,
            isDark = isDark,
            onBack = onBack
        )

        actionsTrack?.let { selected ->
            TrackActionsSheet(
                track = selected,
                isFavorite = com.lmg.vk.engine.VkAudioIdentity.stableFullId(selected.id) in favoriteIds,
                onToggleFavorite = {
                    scope.launch { libraryRepository.toggleFavorite(selected, "other") }
                },
                onCache = if (isPremium && selected.id !in downloadedIds) {
                    { AudioDownloadManager.downloadTrack(context, selected) }
                } else null,
                onAddToPlaylist = { playlistPickerTrack = selected },
                onDismiss = { actionsTrack = null },
            )
        }

        playlistPickerTrack?.let { selected ->
            PlaylistPickerSheet(
                playlists = editablePlaylists,
                onSelect = { playlist ->
                    val added = PlaylistManager.addTrack(playlist.id, selected)
                    Toast.makeText(
                        context,
                        if (added) context.getString(R.string.added_to_playlist, playlist.name) else context.getString(R.string.already_in_playlist, playlist.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    playlistPickerTrack = null
                },
                onDismiss = { playlistPickerTrack = null },
            )
        }

        artistChooser?.let { choices ->
            com.lmg.vk.ui.components.ArtistChooserDialog(
                artists = choices,
                onSelect = { selected ->
                    artistChooser = null
                    selected.id?.takeIf(String::isNotBlank)?.let(onNavigateToArtist)
                },
                onDismiss = { artistChooser = null },
            )
        }
    }
}

/**
 * Строка списка треков альбома: сам трек, его номер и — только у первого трека
 * диска — заголовок «Disc N».
 *
 * Номер держим здесь, а не берём индексом в списке: у двойника нумерация на
 * каждом диске начинается заново, и индекс дал бы 16-й трек там, где на релизе
 * первый трек второго диска.
 */
private data class AlbumTrackRow(
    val track: Track,
    val position: Int,
    val discHeader: Int?,
)

@Composable
private fun AlbumActionsRow(
    isDark: Boolean,
    isFollowing: Boolean,
    isAdding: Boolean,
    canFollow: Boolean,
    cacheLabel: String,
    canDownload: Boolean,
    canQueue: Boolean,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlbumActionButton(
            when {
                isFollowing -> stringResource(R.string.in_library)
                isAdding -> stringResource(R.string.adding_short)
                else -> stringResource(R.string.action_add)
            },
            if (isFollowing) com.lmg.vk.ui.icons.LmgGlyphs.BookmarkCheckOutline28
            else lmgVector(LmgDrawables.BookmarkAddOutline28),
            (isFollowing || canFollow) && !isAdding,
            isDark,
            onAdd,
        )
        AlbumActionButton(cacheLabel, com.lmg.vk.ui.icons.LmgGlyphs.DownloadOutline28, canDownload, isDark, onDownload)
        AlbumActionButton(stringResource(R.string.action_queue), com.lmg.vk.ui.icons.LmgGlyphs.ListPlayOutline28, canQueue, isDark, onQueue)
        AlbumActionButton(stringResource(R.string.action_share), com.lmg.vk.ui.icons.LmgGlyphs.ShareOutline28, true, isDark, onShare)
    }
}

@Composable
private fun AlbumPersonalStrip(
    playCount: Int,
    favouriteTrack: String?,
    isDark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(LiquidSurfaces.card(isDark))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.album_played_times, playCount, playCount),
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        favouriteTrack?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.most_played_track, it),
                color = LiquidSurfaces.textSecondary(isDark),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.AlbumActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

private fun formatCatalogDate(seconds: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(seconds * 1000L))

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1f млн".format(value / 1_000_000f)
    value >= 1_000 -> "%.1f тыс.".format(value / 1_000f)
    else -> value.toString()
}
