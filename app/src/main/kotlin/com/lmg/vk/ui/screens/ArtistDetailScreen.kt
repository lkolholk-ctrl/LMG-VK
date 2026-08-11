package com.lmg.vk.ui.screens

import android.content.Intent
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.backend.ArtistAlbum
import com.lmg.vk.engine.backend.ArtistLink
import com.lmg.vk.engine.backend.ArtistResponse
import com.lmg.vk.engine.backend.ArtistVideo
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.ui.components.releaseTypeLabel
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.viewmodel.ArtistCommunitiesViewModel
import com.lmg.vk.ui.viewmodel.ArtistCommunity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch


/** Обложки каталога приходят огромными; для карточек это лишний трафик и память. */
private fun String?.toThumb(): String? = this
    ?.replace("1000x1000", "600x600")
    ?.replace("1500x1500", "600x600")
    ?.replace("300x300", "600x600")

/**
 * Экран артиста.
 *
 * Порядок разделов привычный: сначала то, ради чего сюда заходят (послушать
 * прямо сейчас), затем свежий релиз, дискография и только потом окружение
 * артиста. Подача своя — живая шапка, личный блок и разный вес разделов вместо
 * ровного списка одинаковых каруселей.
 */
@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    /**
     * Открыть экран сообщества. Передаётся ОТРИЦАТЕЛЬНЫЙ owner_id — та же
     * конвенция, что у `NavRoutes.group()` и `GroupViewModel.load()`.
     * Дефолт пустой намеренно: до связывания в NavHost карточки просто не
     * реагируют на тап, а не падают.
     */
    onOpenGroup: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val scope = rememberCoroutineScope()

    var artist by remember { mutableStateOf<ArtistResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isFollowed by remember(artistId) { mutableStateOf(false) }
    var isFollowBusy by remember(artistId) { mutableStateOf(false) }
    var isMixBusy by remember(artistId) { mutableStateOf(false) }
    var reloadKey by remember(artistId) { mutableStateOf(0) }

    // Сообщества артиста грузим отдельно от MusicBackend.getArtist: там блок
    // `groups` теряется (сообщества сваливаются в officialPages без различения
    // «своё/похожие», а layout `owner_cell` не читается вообще).
    val communitiesViewModel: ArtistCommunitiesViewModel = viewModel()
    val artistCommunities by communitiesViewModel.state.collectAsState()
    LaunchedEffect(artistId, reloadKey) { communitiesViewModel.load(artistId) }

    LaunchedEffect(artistId, reloadKey) {
        isLoading = true
        error = null
        artist = null
        try {
            val result = MusicBackend.getArtist(artistId)
            if (result == null) {
                error = MusicBackend.lastError.value ?: "Artist not found"
            } else {
                artist = result
                isFollowed = result.isFollowed
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    // Быстро показываем topSongs, а полный список и полную дискографию догружаем
    // отдельными VK-запросами. Поэтому главный экран артиста не ждёт сотни треков,
    // но See all и счётчики используют уже настоящий каталог.
    var artistTracks by remember(artistId) {
        mutableStateOf<List<com.lmg.vk.engine.Track>>(emptyList())
    }
    var artistTracksLoading by remember(artistId) { mutableStateOf(false) }
    var artistReleases by remember(artistId) {
        mutableStateOf<List<ArtistAlbum>>(emptyList())
    }

    LaunchedEffect(artist?.id) {
        val art = artist
        if (art == null) {
            artistTracks = emptyList()
            artistTracksLoading = false
            return@LaunchedEffect
        }
        artistTracks = art.topSongs.map { it.toTrack() }.distinctBy { it.id }
        artistTracksLoading = true
        val allTracks = MusicBackend.getArtistAllTracks(art.id).distinctBy { it.id }
        if (allTracks.isNotEmpty()) artistTracks = allTracks
        artistTracksLoading = false
    }

    LaunchedEffect(artist?.id) {
        val art = artist
        if (art == null) {
            artistReleases = emptyList()
            return@LaunchedEffect
        }
        artistReleases = MusicBackend.getArtistReleases(art.id).distinctBy { it.id }
    }

    // Личный блок: сколько раз слушали именно этого артиста и что чаще всего.
    // Такого на карточке артиста нет ни у одного стриминга, а данные у нас свои.
    var playCount by remember { mutableStateOf(0) }
    var favouriteTrackTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistTracks) {
        playCount = 0
        favouriteTrackTitle = null
        if (artistTracks.isEmpty()) return@LaunchedEffect
        try {
            val stats = AppDatabase.getInstance(context).playbackHistoryDao().getAllTrackStats(500)
            val byId = artistTracks.associateBy { it.id }
            val mine = stats.filter { byId.containsKey(it.trackId) }
            playCount = mine.sumOf { it.playCount }
            favouriteTrackTitle = mine.maxByOrNull { it.playCount }
                ?.takeIf { it.playCount > 0 }
                ?.let { byId[it.trackId]?.title }
        } catch (_: Exception) {
            playCount = 0
        }
    }

    val topSongs = remember(artist) { artist?.topSongs.orEmpty() }
    val playableArtistTracks = remember(artistTracks) { artistTracks.filter { it.isAvailable } }

    // Дискографию делим по типу релиза: сборники и концертные записи слушают
    // иначе, чем студийные альбомы, и в общей куче они только мешают искать.
    val allReleases = remember(artist, artistReleases) {
        (
            artistReleases +
                artist?.albums.orEmpty() +
                artist?.singles.orEmpty()
            ).distinctBy { it.id }
    }
    val compilations = remember(allReleases) {
        // VK называет сборник `collection`; `compilation` — написание Apple-каталога.
        // Проверяем оба: теперь в `type` доезжает настоящий вид релиза от VK, и по
        // одному лишь `compilation` секция всегда оставалась бы пустой.
        allReleases.filter {
            it.type?.contains("compilation", ignoreCase = true) == true ||
                it.type?.contains("collection", ignoreCase = true) == true
        }
    }
    val liveAlbums = remember(allReleases) {
        allReleases.filter {
            it.type?.contains("live", ignoreCase = true) == true ||
                it.title.contains("(Live", ignoreCase = true)
        }
    }
    val singles = remember(allReleases) {
        allReleases.filter { it.isSingleOrEpUi() }
    }
    val albums = remember(allReleases, compilations, liveAlbums, singles) {
        val excluded = (compilations + liveAlbums + singles).map { it.id }.toSet()
        allReleases.filterNot { it.id in excluded }
    }
    val appearsOn = remember(artist) { artist?.appearsOn.orEmpty().distinctBy { it.id } }
    val playlists = remember(artist) { artist?.playlists.orEmpty().distinctBy { it.id } }
    val linkedArtists = remember(artist) { artist?.linkedArtists.orEmpty().distinctBy { it.id } }
    val similar = remember(artist, linkedArtists) {
        val linkedIds = linkedArtists.map { it.id }.toSet()
        artist?.similarArtists.orEmpty().distinctBy { it.id }.filterNot { it.id in linkedIds }
    }

    var showAllSongs by remember { mutableStateOf(false) }
    var showAllVideos by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Имя в панели показываем только когда шапка ушла: пока артист виден крупно,
    // дублировать его незачем.
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 320
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark))) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = error.orEmpty(), color = colors.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidSurfaces.card(colors.isDark))
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
                            tint = LiquidSurfaces.textPrimary(colors.isDark),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Retry",
                            color = LiquidSurfaces.textPrimary(colors.isDark),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            else -> {
                val art = artist
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    item {
                        ArtistHeaderWithSheet(
                            name = art?.name.orEmpty(),
                            genre = art?.genre,
                            imageUrl = (art?.image ?: art?.cover).toThumb(),
                            videoUrl = art?.editorialVideoUrl,
                            isDark = colors.isDark,
                            onPlay = {
                                if (playableArtistTracks.isNotEmpty()) {
                                    PlayerController.play(
                                        context,
                                        playableArtistTracks,
                                        0,
                                        playbackContext = PlaybackContext.Artist(artistId),
                                    )
                                }
                            },
                            onShuffle = {
                                if (playableArtistTracks.isNotEmpty()) {
                                    PlayerController.play(
                                        context,
                                        playableArtistTracks.shuffled(),
                                        0,
                                        playbackContext = PlaybackContext.Artist(artistId),
                                    )
                                }
                            }
                        )
                    }

                    art?.let { artistInfo ->
                        item {
                            ArtistActionsStrip(
                                isDark = colors.isDark,
                                isFollowed = isFollowed,
                                isMixBusy = isMixBusy,
                                followEnabled = (artistInfo.canFollow || isFollowed) && !isFollowBusy,
                                onMix = {
                                    scope.launch {
                                        isMixBusy = true
                                        val mixSource = MusicBackend.getArtistMixSource(
                                            artistInfo.id,
                                            artistInfo.mixId,
                                        )
                                        val mix = mixSource?.tracks.orEmpty().filter { it.isAvailable }
                                        if (mixSource != null && mix.isNotEmpty()) {
                                            PlayerController.play(
                                                context,
                                                mix,
                                                0,
                                                playbackContext = PlaybackContext.VkMix(mixSource.session),
                                            )
                                        } else {
                                            Toast.makeText(context, "Artist mix is unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                        isMixBusy = false
                                    }
                                },
                                onFollow = {
                                    val target = !isFollowed
                                    scope.launch {
                                        isFollowBusy = true
                                        if (MusicBackend.setArtistFollowed(artistInfo.id, target)) {
                                            isFollowed = target
                                            Toast.makeText(
                                                context,
                                                if (target) "Artist followed" else "Artist unfollowed",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            Toast.makeText(context, "Couldn't update follow", Toast.LENGTH_SHORT).show()
                                        }
                                        isFollowBusy = false
                                    }
                                },
                                onShare = {
                                    val url = "https://vk.com/artist/${artistInfo.id.removePrefix("vk_")}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(intent, artistInfo.name))
                                },
                            )
                        }
                    }

                    art?.let { artistInfo ->
                        val songCount = maxOf(artistTracks.size, topSongs.size)
                        val releaseCount = allReleases.size
                        val videoCount = artistInfo.videos.size
                        if (songCount > 0 || releaseCount > 0 || playlists.isNotEmpty() || videoCount > 0) {
                            item {
                                ArtistCatalogSummary(
                                    songs = songCount,
                                    songsAreMinimum = artistTracks.size >= ARTIST_TRACK_COUNT_PLUS_THRESHOLD,
                                    releases = releaseCount,
                                    playlists = playlists.size,
                                    videos = videoCount,
                                    isDark = colors.isDark,
                                )
                            }
                        }
                    }

                    if (playCount > 0) {
                        item {
                            PersonalStrip(
                                playCount = playCount,
                                favouriteTrack = favouriteTrackTitle,
                                textPrimary = LiquidSurfaces.textPrimary(colors.isDark),
                                textSecondary = LiquidSurfaces.textSecondary(colors.isDark),
                                isDark = colors.isDark
                            )
                        }
                    }

                    art?.latestRelease?.let { latest ->
                        item { SectionHeaderThemed(colors.isDark, "Latest release") }
                        item {
                            LatestReleaseCard(
                                album = latest,
                                textPrimary = LiquidSurfaces.textPrimary(colors.isDark),
                                textSecondary = LiquidSurfaces.textSecondary(colors.isDark),
                                isDark = colors.isDark,
                                onClick = { onNavigateToAlbum(latest.id) }
                            )
                        }
                    }

                    if (topSongs.isNotEmpty()) {
                        item {
                            SectionHeaderWithLink(
                                isDark = colors.isDark,
                                title = "Top songs",
                                // Полный каталог открывается отдельным окном и не
                                // превращает основную страницу в список из сотен строк.
                                linkLabel = "See all",
                                onLinkClick = { showAllSongs = true }
                            )
                        }

                        item {
                            // Колонки по пять с горизонтальной прокруткой: так за
                            // экран влезает вдвое больше песен, чем простым списком,
                            // и видно, что список продолжается.
                            val songs = artistTracks.ifEmpty { topSongs.map { it.toTrack() } }
                            LazyRow(
                                contentPadding = PaddingValues(
                                    horizontal = LiquidMetrics.ScreenPadding
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val pages = songs.take(25).chunked(5)
                                items(pages.size) { pageIndex ->
                                    Column(modifier = Modifier.fillParentMaxWidth(0.88f)) {
                                        pages[pageIndex].forEachIndexed { rowIndex, track ->
                                            val position = pageIndex * 5 + rowIndex
                                            TopSongRow(
                                                position = position + 1,
                                                title = track.title,
                                                subtitle = track.artist.ifBlank { track.albumName },
                                                coverUrl = track.coverUrl,
                                                isExplicit = track.isExplicit,
                                                durationMs = track.durationMs,
                                                enabled = track.isAvailable,
                                                textPrimary = LiquidSurfaces.textPrimary(colors.isDark),
                                                textSecondary = LiquidSurfaces.textSecondary(colors.isDark),
                                                onClick = {
                                                    val playableSongs = songs.filter { it.isAvailable }
                                                    val playableIndex = playableSongs.indexOfFirst { it.id == track.id }
                                                    if (playableIndex >= 0) {
                                                        PlayerController.play(
                                                            context,
                                                            playableSongs,
                                                            playableIndex,
                                                            playbackContext = PlaybackContext.Artist(artistId),
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

                    if (albums.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Albums") }
                        item {
                            AlbumRow(albums, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    if (singles.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Singles & EPs") }
                        item {
                            AlbumRow(singles, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    if (compilations.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Compilations") }
                        item {
                            AlbumRow(compilations, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    if (liveAlbums.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Live albums") }
                        item {
                            AlbumRow(liveAlbums, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Playlists") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LiquidMetrics.ScreenPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    Column(modifier = Modifier.width(160.dp)) {
                                        AlbumArtImage(
                                            uri = null,
                                            coverUrl = playlist.cover.toThumb(),
                                            contentDescription = playlist.title,
                                            modifier = Modifier
                                                .size(160.dp)
                                                .shadow(
                                                    elevation = LiquidMetrics.CoverElevation,
                                                    shape = LiquidMetrics.CardShape,
                                                    ambientColor = LiquidSurfaces.shadowTint(colors.isDark),
                                                    spotColor = LiquidSurfaces.shadowTint(colors.isDark)
                                                )
                                                .clip(LiquidMetrics.CardShape)
                                                .liquidClickable(
                                                    pressedScale = LiquidMotion.PressButton,
                                                    onClick = { onNavigateToPlaylist(playlist.id) }
                                                ),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = playlist.title,
                                            color = colors.textPrimary,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (similar.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Similar artists") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LiquidMetrics.ScreenPadding),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(similar, key = { it.id }) { other ->
                                    Column(
                                        modifier = Modifier.width(96.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AlbumArtImage(
                                            uri = null,
                                            coverUrl = other.cover.toThumb(),
                                            contentDescription = other.displayName,
                                            modifier = Modifier
                                                .size(96.dp)
                                                .clip(CircleShape)
                                                .liquidClickable(
                                                    pressedScale = LiquidMotion.PressButton,
                                                    onClick = { onNavigateToArtist(other.id) }
                                                ),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = other.displayName,
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (appearsOn.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Participates in releases") }
                        item {
                            AlbumRow(appearsOn, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    art?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                        item { SectionHeaderThemed(colors.isDark, "About ${art.name}") }
                        item {
                            Text(
                                text = bio,
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(horizontal = LiquidMetrics.ScreenPadding),
                            )
                        }
                    }

                    if (linkedArtists.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, "Links") }
                        items(linkedArtists, key = { "linked-artist-${it.id}" }) { linked ->
                            ArtistLinkRow(
                                title = linked.displayName,
                                subtitle = "Artist",
                                cover = linked.cover,
                                isDark = colors.isDark,
                                onClick = { onNavigateToArtist(linked.id) },
                            )
                        }
                    }

                    art?.links.orEmpty().let { links ->
                        val concerts = links.filter { it.matches("concert", "ticket", "концерт", "билет") }
                        val merch = links.filter { it.matches("merch", "shop", "store", "мерч", "магазин") }
                        val other = links.filterNot { it in concerts || it in merch }
                        if (concerts.isNotEmpty()) {
                            item { SectionHeaderThemed(colors.isDark, "Concerts") }
                            items(concerts, key = { "concert-${it.id}" }) { link ->
                                ArtistLinkRow(link.title, link.subtitle, link.cover, colors.isDark)
                            }
                        }
                        if (merch.isNotEmpty()) {
                            item { SectionHeaderThemed(colors.isDark, "Merch") }
                            items(merch, key = { "merch-${it.id}" }) { link ->
                                ArtistLinkRow(link.title, link.subtitle, link.cover, colors.isDark)
                            }
                        }
                        if (other.isNotEmpty()) {
                            item { SectionHeaderThemed(colors.isDark, "Information") }
                            item {
                                CompactInformationGrid(
                                    links = other,
                                    isDark = colors.isDark,
                                )
                            }
                        }
                    }

                    art?.officialPages.orEmpty().let { pages ->
                        val profiles = pages.filterNot { it.isCommunity }
                        if (profiles.isNotEmpty()) {
                            item { SectionHeaderThemed(colors.isDark, "Official profiles") }
                            items(profiles, key = { "profile-${it.id}" }) { page ->
                                ArtistLinkRow(page.name, page.subtitle, page.cover, colors.isDark)
                            }
                        }
                    }

                    // Сообщества из блока страницы артиста. Раздельно: своя
                    // официальная страница (VK помечает её layout'ом
                    // `owner_cell`) и похожие сообщества. Блок появляется
                    // только когда VK реально что-то отдал — пустоту не рисуем.
                    artistCommunities.own?.let { ownCommunity ->
                        item { SectionHeaderThemed(colors.isDark, "Official community") }
                        item {
                            ArtistCommunityRow(
                                community = ownCommunity,
                                isDark = colors.isDark,
                                onClick = onOpenGroup,
                            )
                        }
                    }

                    if (artistCommunities.similar.isNotEmpty()) {
                        item {
                            // Заголовок берём тот, что прислал VK; свой текст —
                            // только если блок пришёл без header'а.
                            SectionHeaderThemed(
                                colors.isDark,
                                artistCommunities.similarTitle ?: "Similar communities",
                            )
                        }
                        item {
                            ArtistCommunityCarousel(
                                communities = artistCommunities.similar,
                                onClick = onOpenGroup,
                            )
                        }
                    }

                    if (art?.videos.orEmpty().isNotEmpty()) {
                        item {
                            SectionHeaderWithLink(
                                isDark = colors.isDark,
                                title = "Music videos",
                                linkLabel = "See all",
                                onLinkClick = { showAllVideos = true },
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LiquidMetrics.ScreenPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(art?.videos.orEmpty(), key = { it.id }) { video ->
                                    ArtistVideoCard(video.title, video.cover, video.duration, colors.isDark)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Панель поверх шапки: кнопка «назад» нужна всегда, имя подхватывается
        // только после прокрутки.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (showTopBarTitle) {
                        LiquidSurfaces.sheet(colors.isDark)
                    } else {
                        Color.Transparent
                    }
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (showTopBarTitle) {
                            LiquidSurfaces.card(colors.isDark)
                        } else {
                            LiquidSurfaces.glassFill
                        }
                    )
                    .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                    contentDescription = "Back",
                    tint = if (showTopBarTitle) {
                        LiquidSurfaces.textPrimary(colors.isDark)
                    } else {
                        Color.White
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = artist?.name.orEmpty(),
                color = LiquidSurfaces.textPrimary(colors.isDark),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (showTopBarTitle) 1f else 0f)
            )
        }

        if (showAllSongs) {
            ArtistTracksDialog(
                artistName = artist?.name.orEmpty(),
                tracks = artistTracks,
                isLoading = artistTracksLoading,
                isDark = colors.isDark,
                onPlay = { index ->
                    val playable = artistTracks.filter { it.isAvailable }
                    val selected = artistTracks.getOrNull(index)
                    val playableIndex = playable.indexOfFirst { it.id == selected?.id }
                    if (playableIndex >= 0) {
                        PlayerController.play(
                            context,
                            playable,
                            playableIndex,
                            playbackContext = PlaybackContext.Artist(artistId),
                        )
                    }
                },
                onDismiss = { showAllSongs = false },
            )
        }

        if (showAllVideos) {
            ArtistVideosDialog(
                artistName = artist?.name.orEmpty(),
                videos = artist?.videos.orEmpty(),
                isDark = colors.isDark,
                onDismiss = { showAllVideos = false },
            )
        }
    }
}

@Composable
private fun ArtistTracksDialog(
    artistName: String,
    tracks: List<Track>,
    isLoading: Boolean,
    isDark: Boolean,
    onPlay: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LiquidSurfaces.sheet(isDark)),
        ) {
            ArtistListDialogTopBar(
                title = artistName,
                subtitle = "${tracks.size}${if (tracks.size >= ARTIST_TRACK_COUNT_PLUS_THRESHOLD) "+" else ""} songs",
                isDark = isDark,
                onBack = onDismiss,
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = LiquidTheme.colors.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                itemsIndexed(
                    tracks,
                    key = { index, track -> "artist-all-${track.id}-$index" },
                ) { index, track ->
                    Column(modifier = Modifier.padding(horizontal = LiquidMetrics.ScreenPadding)) {
                        TopSongRow(
                            position = index + 1,
                            title = track.title,
                            subtitle = track.artist.ifBlank { track.albumName },
                            coverUrl = track.coverUrl,
                            isExplicit = track.isExplicit,
                            durationMs = track.durationMs,
                            enabled = track.isAvailable,
                            textPrimary = LiquidSurfaces.textPrimary(isDark),
                            textSecondary = LiquidSurfaces.textSecondary(isDark),
                            onClick = { onPlay(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistVideosDialog(
    artistName: String,
    videos: List<ArtistVideo>,
    isDark: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LiquidSurfaces.sheet(isDark)),
        ) {
            ArtistListDialogTopBar(
                title = artistName,
                subtitle = "${videos.size} videos",
                isDark = isDark,
                onBack = onDismiss,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = LiquidMetrics.ScreenPadding,
                    top = 12.dp,
                    end = LiquidMetrics.ScreenPadding,
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridItems(videos, key = { it.id }) { video ->
                    ArtistVideoCard(
                        title = video.title,
                        cover = video.cover,
                        duration = video.duration,
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistListDialogTopBar(
    title: String,
    subtitle: String,
    isDark: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiquidSurfaces.sheet(isDark))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LiquidSurfaces.card(isDark))
                .liquidClickable(
                    pressedScale = LiquidMotion.PressButton,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                contentDescription = "Back",
                tint = LiquidSurfaces.textPrimary(isDark),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LiquidSurfaces.textPrimary(isDark),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = LiquidSurfaces.textSecondary(isDark),
                fontSize = 11.sp,
            )
        }
    }
}

private fun ArtistAlbum.isSingleOrEpUi(): Boolean {
    val releaseType = type.orEmpty()
    return releaseType.contains("single", ignoreCase = true) ||
        releaseType.equals("ep", ignoreCase = true) ||
        releaseType.contains("extended_play", ignoreCase = true)
}

/**
 * Шапка: видео-заставка артиста, если каталог её отдал, иначе фото.
 *
 * Видео идёт без звука и по кругу — это фон, а не проигрывание: звук поверх
 * музыки недопустим, а один проход выглядел бы как сбой.
 */
@Composable
private fun ArtistHeader(
    name: String,
    genre: String?,
    imageUrl: String?,
    videoUrl: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxWidth().height(LiquidMetrics.HeaderHeight)) {
        // Фон, имя и кнопки двигаются одним куском. Параллакс здесь пробовался и
        // был убран: фон уезжал медленнее содержимого, и при прокрутке шапка
        // расползалась — фотография отдельно, подписи с кнопками отдельно.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            if (!videoUrl.isNullOrBlank()) {
                val exoPlayer = remember(videoUrl) {
                    ExoPlayer.Builder(context).build().apply {
                        volume = 0f
                        repeatMode = Player.REPEAT_MODE_ONE
                        playWhenReady = true
                        setMediaItem(MediaItem.fromUri(videoUrl))
                        prepare()
                    }
                }
                DisposableEffect(videoUrl) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AlbumArtImage(
                    uri = null,
                    coverUrl = imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Затемнение снизу: имя поверх светлого кадра иначе не читается.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = LiquidMetrics.ScreenPadding,
                    end = LiquidMetrics.ScreenPadding,
                    // Ровно столько, чтобы кнопки не ушли под край наезжающего
                    // листа: больше — и блок повиснет в пустоте посреди шапки.
                    bottom = LiquidMetrics.SheetOverlap + 8.dp
                )
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = LiquidMetrics.TitleHuge,
                fontWeight = LiquidMetrics.TitleHugeWeight,
                letterSpacing = LiquidMetrics.TitleHugeSpacing,
                lineHeight = 44.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!genre.isNullOrBlank()) {
                Text(
                    text = genre,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderButton("Play", com.lmg.vk.ui.icons.LmgGlyphs.Play28, filled = true, onClick = onPlay)
                HeaderButton("Shuffle", com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28, filled = false, onClick = onShuffle)
            }
        }
    }
}

/**
 * Кнопка действия в шапке.
 *
 * Главная — сплошная белая с тёмным текстом: под ней фотография, и только
 * плотная заливка гарантирует читаемость на любом кадре. Вторая — стеклянная,
 * чтобы не спорить с главной за внимание.
 */
@Composable
private fun RowScope.HeaderButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (filled) Color.Black else Color.White
    Row(
        modifier = Modifier
            .weight(1f)
            .height(LiquidMetrics.ActionButtonHeight)
            .shadow(
                // Главной кнопке тень нужнее: она белая и лежит на светлых кадрах,
                // без отрыва от фона её край теряется.
                elevation = if (filled) LiquidMetrics.ButtonElevation else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(CircleShape)
            .background(if (filled) Color.White else LiquidSurfaces.glassAction)
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = LiquidMetrics.ActionLabel,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** То, чего нет у стримингов: сколько именно ВЫ слушали этого артиста. */
@Composable
private fun PersonalStrip(
    playCount: Int,
    favouriteTrack: String?,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 16.dp)
            .clip(LiquidMetrics.CardShape)
            .background(LiquidSurfaces.card(isDark))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = "You played this artist $playCount times",
            color = textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (!favouriteTrack.isNullOrBlank()) {
            Text(
                text = "Most played: $favouriteTrack",
                color = textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Шапка вместе с верхушкой листа.
 *
 * Обе части живут в одном элементе списка намеренно: если лист сдвигать
 * отдельным элементом, уезжает только он, а следующие остаются на месте — между
 * ними появляется пустая полоса. Здесь наезд рисуется внутри общего контейнера,
 * поэтому части всегда держатся друг за друга.
 */
@Composable
private fun ArtistHeaderWithSheet(
    name: String,
    genre: String?,
    imageUrl: String?,
    videoUrl: String?,
    isDark: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        ArtistHeader(
            name = name,
            genre = genre,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            onPlay = onPlay,
            onShuffle = onShuffle
        )
        SheetTop(
            isDark = isDark,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Верхушка листа контента: наезжает на шапку и скруглена сверху.
 *
 * Приём из макета — за счёт наезда шапка воспринимается подложкой, а не первым
 * элементом списка, и переход к контенту читается без разделителя.
 */
@Composable
private fun SheetTop(isDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LiquidMetrics.SheetShape)
            .background(LiquidSurfaces.sheet(isDark))
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(LiquidSurfaces.grabber(isDark))
        )
    }
}

/** Заголовок раздела со ссылкой справа — «See all» и подобные. */
@Composable
private fun SectionHeaderWithLink(
    isDark: Boolean,
    title: String,
    linkLabel: String,
    onLinkClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = LiquidMetrics.ScreenPadding,
                end = LiquidMetrics.ScreenPadding,
                top = LiquidMetrics.SectionGap,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = LiquidMetrics.SectionTitle,
            fontWeight = LiquidMetrics.SectionTitleWeight,
            letterSpacing = LiquidMetrics.SectionTitleSpacing,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = linkLabel,
            color = LiquidSurfaces.textSecondary(isDark),
            fontSize = LiquidMetrics.LinkLabel,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(LiquidMetrics.Pill)
                .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onLinkClick)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SectionHeaderThemed(isDark: Boolean, title: String) {
    Text(
        text = title,
        color = LiquidSurfaces.textPrimary(isDark),
        fontSize = LiquidMetrics.SectionTitle,
        fontWeight = LiquidMetrics.SectionTitleWeight,
        letterSpacing = LiquidMetrics.SectionTitleSpacing,
        modifier = Modifier.padding(
            start = LiquidMetrics.ScreenPadding,
            end = LiquidMetrics.ScreenPadding,
            top = LiquidMetrics.SectionGap,
            bottom = 12.dp
        )
    )
}

@Composable
private fun ArtistActionsStrip(
    isDark: Boolean,
    isFollowed: Boolean,
    isMixBusy: Boolean,
    followEnabled: Boolean,
    onMix: () -> Unit,
    onFollow: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtistActionButton(
            if (isMixBusy) "Loading…" else "Artist mix",
            com.lmg.vk.ui.icons.LmgGlyphs.MusicNoteWaveOutline28,
            !isMixBusy,
            isDark,
            onMix,
        )
        ArtistActionButton(
            if (isFollowed) "Following" else "Follow",
            if (isFollowed) com.lmg.vk.ui.icons.LmgGlyphs.Favorite28 else com.lmg.vk.ui.icons.LmgGlyphs.FavoriteOutline28,
            followEnabled,
            isDark,
            onFollow,
        )
        ArtistActionButton("Share", com.lmg.vk.ui.icons.LmgGlyphs.ShareOutline28, true, isDark, onShare)
    }
}

@Composable
private fun ArtistCatalogSummary(
    songs: Int,
    songsAreMinimum: Boolean,
    releases: Int,
    playlists: Int,
    videos: Int,
    isDark: Boolean,
) {
    val stats = listOf(
        songs to "Songs",
        releases to "Releases",
        playlists to "Playlists",
        videos to "Videos",
    ).filter { it.first > 0 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(LiquidSurfaces.card(isDark))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEach { (value, label) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (label == "Songs" && songsAreMinimum) "$value+" else value.toString(),
                    color = LiquidSurfaces.textPrimary(isDark),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    label,
                    color = LiquidSurfaces.textSecondary(isDark),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CompactInformationGrid(
    links: List<ArtistLink>,
    isDark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding)
            .clip(RoundedCornerShape(18.dp))
            .background(LiquidSurfaces.card(isDark))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        links.chunked(2).forEach { rowLinks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowLinks.forEach { link ->
                    CompactInformationItem(link = link, isDark = isDark)
                }
                if (rowLinks.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.CompactInformationItem(
    link: ArtistLink,
    isDark: Boolean,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(13.dp))
            .background(LiquidSurfaces.sheet(isDark).copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtImage(
            uri = null,
            coverUrl = link.cover.toThumb(),
            contentDescription = null,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                link.title,
                color = LiquidSurfaces.textPrimary(isDark),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            link.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    subtitle,
                    color = LiquidSurfaces.textSecondary(isDark),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ArtistActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(18.dp))
            .background(LiquidSurfaces.card(isDark))
            .liquidClickable(enabled = enabled, pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = LiquidSurfaces.textPrimary(isDark), modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistLinkRow(
    title: String,
    subtitle: String?,
    cover: String?,
    isDark: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) {
                    Modifier.liquidClickable(
                        pressedScale = LiquidMotion.PressButton,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtImage(
            uri = null,
            coverUrl = cover.toThumb(),
            contentDescription = title,
            modifier = Modifier.size(52.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = LiquidSurfaces.textPrimary(isDark),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = LiquidSurfaces.textSecondary(isDark),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Официальное сообщество артиста — строкой, а не карточкой в карусели.
 *
 * Почему иначе, чем «похожие»: оно всегда одно, и это не «ещё вариант», а
 * страница самого артиста. Строка на всю ширину читается как заявление, а не
 * как элемент выбора, и рядом влезает подпись о подписке.
 */
@Composable
private fun ArtistCommunityRow(
    community: ArtistCommunity,
    isDark: Boolean,
    onClick: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(LiquidSurfaces.card(isDark))
            .liquidClickable(
                pressedScale = LiquidMotion.PressButton,
                onClick = { onClick(community.ownerId) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtImage(
            uri = null,
            coverUrl = community.cover.toThumb(),
            contentDescription = community.name,
            modifier = Modifier.size(52.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = community.name,
                color = LiquidSurfaces.textPrimary(isDark),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Подписку показываем только когда VK её подтвердил: `is_followed`
            // приходит не всегда, и «не подписаны» было бы домыслом.
            if (community.isFollowed) {
                Text(
                    text = "Вы подписаны",
                    color = LiquidSurfaces.textSecondary(isDark),
                    fontSize = 12.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Похожие сообщества — горизонтальная карусель круглых аватаров, как у
 * кураторов на экране New: их всегда несколько и они равнозначны.
 */
@Composable
private fun ArtistCommunityCarousel(
    communities: List<ArtistCommunity>,
    onClick: (Long) -> Unit,
) {
    val colors = LiquidTheme.colors
    // На узком экране аватары мельче, иначе в карусель влезает меньше двух с
    // половиной карточек и она перестаёт читаться как список.
    val compact = !com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    val size = if (compact) 76.dp else 94.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = LiquidMetrics.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(communities, key = { "artist-community-${it.id}" }) { community ->
            Column(
                modifier = Modifier.width(size),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AlbumArtImage(
                    uri = null,
                    coverUrl = community.cover.toThumb(),
                    contentDescription = community.name,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressButton,
                            onClick = { onClick(community.ownerId) },
                        ),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = community.name,
                    color = colors.textPrimary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistVideoCard(
    title: String,
    cover: String?,
    duration: Long,
    isDark: Boolean,
    modifier: Modifier = Modifier.width(238.dp),
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (onClick != null) {
                        Modifier.liquidClickable(
                            pressedScale = LiquidMotion.PressButton,
                            onClick = onClick,
                        )
                    } else Modifier
                ),
        ) {
            AlbumArtImage(
                uri = null,
                coverUrl = cover.toThumb(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (duration > 0L) {
                Text(
                    text = "%d:%02d".format(duration / 60, duration % 60),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = title,
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val ARTIST_TRACK_COUNT_PLUS_THRESHOLD = 200

private fun ArtistLink.matches(vararg markers: String): Boolean {
    val haystack = "$title ${subtitle.orEmpty()} $url".lowercase()
    return markers.any { it.lowercase() in haystack }
}

@Composable
private fun TopSongRow(
    position: Int,
    title: String,
    subtitle: String,
    coverUrl: String?,
    isExplicit: Boolean = false,
    durationMs: Long = 0L,
    enabled: Boolean = true,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(18.dp))
            .liquidClickable(enabled = enabled, pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$position",
            color = textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.width(28.dp)
        )
        AlbumArtImage(
            uri = null,
            coverUrl = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .size(LiquidMetrics.TrackCoverSize)
                .clip(LiquidMetrics.CoverShapeSmall),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExplicit) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(textSecondary.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "E",
                            color = textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = if (enabled) title else "$title · Недоступно",
                    color = textPrimary,
                    fontSize = LiquidMetrics.RowTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = LiquidMetrics.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (durationMs > 0L) {
            val totalSec = durationMs / 1000L
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            Text(
                text = String.format(java.util.Locale.US, "%d:%02d", minutes, seconds),
                color = textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/** Свежий релиз крупно: у знакомого артиста его ищут первым делом. */
@Composable
private fun LatestReleaseCard(
    album: ArtistAlbum,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding)
            // Тень ставится ДО обрезки: после clip она обрезалась бы вместе с
            // формой и не была бы видна вовсе.
            .shadow(
                elevation = LiquidMetrics.CardElevation,
                shape = LiquidMetrics.CardShape,
                // В тёмной теме чёрная тень на тёмном фоне не читается — берём
                // подсветку посветлее, иначе карточка выглядит плоской.
                ambientColor = LiquidSurfaces.shadowTint(isDark),
                spotColor = LiquidSurfaces.shadowTint(isDark)
            )
            .clip(LiquidMetrics.CardShape)
            .background(LiquidSurfaces.card(isDark))
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(LiquidMetrics.CardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtImage(
            uri = null,
            coverUrl = album.cover.toThumb(),
            contentDescription = album.title,
            modifier = Modifier
                .size(LiquidMetrics.ReleaseCoverSize)
                .shadow(
                    elevation = LiquidMetrics.CoverElevation,
                    shape = LiquidMetrics.CoverShape,
                    ambientColor = LiquidSurfaces.shadowTint(isDark),
                    spotColor = LiquidSurfaces.shadowTint(isDark)
                )
                .clip(LiquidMetrics.CoverShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = album.title,
                color = textPrimary,
                fontSize = LiquidMetrics.CardTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(releaseTypeLabel(album.type), album.year).joinToString(" · "),
                color = textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AlbumRow(
    albums: List<ArtistAlbum>,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onNavigateToAlbum: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LiquidMetrics.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(modifier = Modifier.width(150.dp)) {
                AlbumArtImage(
                    uri = null,
                    coverUrl = album.cover.toThumb(),
                    contentDescription = album.title,
                    modifier = Modifier
                        .size(150.dp)
                        .shadow(
                            elevation = LiquidMetrics.CoverElevation,
                            shape = LiquidMetrics.CardShape,
                            ambientColor = LiquidSurfaces.shadowTint(isDark),
                            spotColor = LiquidSurfaces.shadowTint(isDark)
                        )
                        .clip(LiquidMetrics.CardShape)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressButton,
                            onClick = { onNavigateToAlbum(album.id) }
                        ),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.title,
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                album.year?.let {
                    Text(text = it, color = textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
