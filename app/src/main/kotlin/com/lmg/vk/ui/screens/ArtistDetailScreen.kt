package com.lmg.vk.ui.screens

import android.content.Intent
import android.widget.Toast

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lmg.vk.ui.navigation.WindowDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lmg.vk.ui.components.DetailStyle
import com.lmg.vk.ui.components.detailClickable
import com.lmg.vk.R
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.backend.ArtistAlbum
import com.lmg.vk.engine.backend.ArtistLink
import com.lmg.vk.engine.backend.ArtistResponse
import com.lmg.vk.engine.backend.ArtistVideo
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.ui.components.releaseTypeLabel
import com.lmg.vk.ui.components.DetailActionButton
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.components.DetailCircleButton
import com.lmg.vk.ui.components.DetailMenuAction
import com.lmg.vk.ui.components.DetailMenuButton
import com.lmg.vk.ui.components.TrackActionsSheet
import com.lmg.vk.ui.components.ProgressiveArtistArtwork
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.VkSansDisplay
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
    val activeAccountId by MusicAuth.profileId.collectAsState()

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
    LaunchedEffect(artistId, reloadKey, activeAccountId) {
        communitiesViewModel.load(artistId, force = true)
    }

    LaunchedEffect(artistId, reloadKey, activeAccountId) {
        isLoading = true
        error = null
        artist = null
        try {
            val result = MusicBackend.getArtist(artistId)
            if (result == null) {
                error = MusicBackend.lastError.value ?: context.getString(R.string.artist_not_found)
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
    var artistTracksNextOffset by remember(artistId) { mutableStateOf<Int?>(null) }
    var artistTracksHasMore by remember(artistId) { mutableStateOf(false) }
    var artistTracksLoadError by remember(artistId) { mutableStateOf(false) }
    var artistTracksLastPageIds by remember(artistId) { mutableStateOf<List<String>?>(null) }
    var artistReleases by remember(artistId) {
        mutableStateOf<List<ArtistAlbum>>(emptyList())
    }

    suspend fun loadArtistTracksPage(art: ArtistResponse, reset: Boolean) {
        if (artistTracksLoading || (!reset && !artistTracksHasMore)) return
        val offset = if (reset) 0 else artistTracksNextOffset ?: return
        artistTracksLoading = true
        artistTracksLoadError = false
        if (reset) {
            artistTracksNextOffset = null
            artistTracksHasMore = false
            artistTracksLastPageIds = null
        }

        val page = MusicBackend.getArtistTracksPage(art.id, offset)
        if (artist?.id != art.id) return
        if (page == null) {
            // Keep the same offset: a network/API failure must remain retryable.
            artistTracksNextOffset = offset
            artistTracksHasMore = true
            artistTracksLoadError = true
            artistTracksLoading = false
            return
        }

        val pageIds = page.tracks.map { it.id }
        val repeatedPage = pageIds.isNotEmpty() && pageIds == artistTracksLastPageIds
        artistTracksLastPageIds = pageIds
        val seen = artistTracks.mapTo(HashSet()) { it.id }
        artistTracks = artistTracks + page.tracks.filter { seen.add(it.id) }
        artistTracksHasMore = page.hasMore && page.nextOffset != null && !repeatedPage
        artistTracksNextOffset = page.nextOffset.takeIf { artistTracksHasMore }
        artistTracksLoading = false
    }

    LaunchedEffect(artist?.id, activeAccountId) {
        val art = artist
        if (art == null) {
            artistTracks = emptyList()
            artistTracksLoading = false
            artistTracksNextOffset = null
            artistTracksHasMore = false
            artistTracksLoadError = false
            return@LaunchedEffect
        }
        artistTracks = art.topSongs.map { it.toTrack() }.distinctBy { it.id }
        loadArtistTracksPage(art, reset = true)
    }

    LaunchedEffect(artist?.id, activeAccountId) {
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

    LaunchedEffect(artistTracks, activeAccountId) {
        playCount = 0
        favouriteTrackTitle = null
        if (artistTracks.isEmpty()) return@LaunchedEffect
        try {
            val stats = AppDatabase.getInstance(context).playbackHistoryDao()
                .getAllTrackStats(AppDatabase.activeAccountId(), 500)
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

    var showArtistInformation by remember { mutableStateOf(false) }
    var showReleases by remember { mutableStateOf(false) }
    var showAllSongs by remember { mutableStateOf(false) }
    var showAllVideos by remember { mutableStateOf(false) }
    val followArtist: () -> Unit = {
                                    val target = !isFollowed
                                    scope.launch {
                                        isFollowBusy = true
                                        if (MusicBackend.setArtistFollowed(artist?.id ?: artistId, target)) {
                                            isFollowed = target
                                            Toast.makeText(
                                                context,
                                                if (target) R.string.artist_followed else R.string.artist_unfollowed,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            Toast.makeText(context, R.string.follow_update_failed, Toast.LENGTH_SHORT).show()
                                        }
                                        isFollowBusy = false
                                    }
                                }
    val listState = rememberLazyListState()
    // Имя в панели показываем только когда шапка ушла: пока артист виден крупно,
    // дублировать его незачем.
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 320
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DetailStyle.background(colors.isDark))) {
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
                            stringResource(R.string.action_retry),
                            color = LiquidSurfaces.textPrimary(colors.isDark),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            else -> {
                val art = artist
                val originalHero = art?.image ?: art?.cover
                val releaseCovers = remember(art) {
                    (listOfNotNull(art?.latestRelease) + art?.singles.orEmpty() + art?.albums.orEmpty())
                        .mapNotNull { it.cover }.distinct().take(12)
                }
                var resolvedHero by remember(originalHero) { mutableStateOf(originalHero) }
                LaunchedEffect(originalHero, releaseCovers) {
                    resolvedHero = com.lmg.vk.artwork.ArtistHeroArtworkResolver.resolve(context, originalHero, releaseCovers)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    item {
                        ArtistHero(
                            canPlay = playableArtistTracks.isNotEmpty(),
                            name = art?.name.orEmpty(),
                            genre = art?.genre,
                            imageUrl = resolvedHero,
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

                    art?.latestRelease?.let { latest ->
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.latest_release)) }
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
                                title = stringResource(R.string.top_songs),
                                // Полный каталог открывается отдельным окном и не
                                // превращает основную страницу в список из сотен строк.
                                linkLabel = stringResource(R.string.see_all),
                                onLinkClick = { showAllSongs = true }
                            )
                        }

                        item {
                            val songs = artistTracks.ifEmpty { topSongs.map { it.toTrack() } }
                            Column(Modifier.fillMaxWidth().padding(horizontal = DetailStyle.padding)) {
                                songs.take(5).forEachIndexed { index, track ->
                                    TopSongRow(track = track, position = index + 1, title = track.title,
                                        subtitle = track.artist.ifBlank { track.albumName }, coverUrl = track.coverUrl,
                                        isExplicit = track.isExplicit, durationMs = track.durationMs, enabled = track.isAvailable,
                                        textPrimary = DetailStyle.text(colors.isDark), textSecondary = DetailStyle.muted(colors.isDark),
                                        onClick = {
                                            val playable = songs.filter { it.isAvailable }
                                            val selectedIndex = playable.indexOfFirst { it.id == track.id }
                                            if (selectedIndex >= 0) PlayerController.play(context, playable, selectedIndex,
                                                playbackContext = PlaybackContext.Artist(artistId))
                                        })
                                    if (index < minOf(songs.size, 5) - 1) Box(Modifier.padding(start = 55.dp).fillMaxWidth().height(1.dp).background(DetailStyle.line(colors.isDark)))
                                }
                            }
                        }
                    }

                    if (albums.isNotEmpty()) {
                        item { SectionHeaderWithLink(colors.isDark, stringResource(R.string.section_albums),
                            stringResource(R.string.see_all), onLinkClick = { showReleases = true }) }
                        item {
                            AlbumRow(albums, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum, grid = true)
                        }
                    }

                    if (singles.isNotEmpty()) {
                        item { SectionHeaderWithLink(colors.isDark, stringResource(R.string.singles_eps),
                            stringResource(R.string.see_all), onLinkClick = { showReleases = true }) }
                        item {
                            AlbumRow(singles, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum, grid = true)
                        }
                    }

                    if (compilations.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.compilations)) }
                        item {
                            AlbumRow(compilations, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    if (liveAlbums.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.live_albums)) }
                        item {
                            AlbumRow(liveAlbums, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.playlists_title)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = DetailStyle.padding),
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
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.similar_artists)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = DetailStyle.padding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.participates_in_releases)) }
                        item {
                            AlbumRow(appearsOn, LiquidSurfaces.textPrimary(colors.isDark), LiquidSurfaces.textSecondary(colors.isDark), colors.isDark, onNavigateToAlbum)
                        }
                    }

                    art?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.about_artist, art.name)) }
                        item {
                            Text(
                                text = bio,
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(horizontal = DetailStyle.padding),
                            )
                        }
                    }

                    if (linkedArtists.isNotEmpty()) {
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.links)) }
                        items(linkedArtists, key = { "linked-artist-${it.id}" }) { linked ->
                            ArtistLinkRow(
                                title = linked.displayName,
                                subtitle = stringResource(R.string.artist_label),
                                cover = linked.cover,
                                isDark = colors.isDark,
                                onClick = { onNavigateToArtist(linked.id) },
                            )
                        }
                    }

                    if (art?.links.orEmpty().isNotEmpty() || playCount > 0) {
                        item {
                            Text(stringResource(R.string.information), color = DetailStyle.accent, fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = DetailStyle.padding, vertical = 16.dp)
                                    .detailClickable(onClick = { showArtistInformation = true }).padding(vertical = 12.dp))
                        }
                    }

                    art?.officialPages.orEmpty().let { pages ->
                        val profiles = pages.filterNot { it.isCommunity }
                        if (profiles.isNotEmpty()) {
                            item { SectionHeaderThemed(colors.isDark, stringResource(R.string.official_profiles)) }
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
                        item { SectionHeaderThemed(colors.isDark, stringResource(R.string.official_community)) }
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
                                artistCommunities.similarTitle ?: stringResource(R.string.similar_communities),
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
                                title = stringResource(R.string.music_videos),
                                linkLabel = stringResource(R.string.see_all),
                                onLinkClick = { showAllVideos = true },
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = DetailStyle.padding),
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

        DetailTopBar(artist?.name.orEmpty(), showTopBarTitle, colors.isDark, onBack,
            titleContent = {
                ArtistTitlePill(artist?.name.orEmpty(), artist?.image ?: artist?.cover, colors.isDark)
            },
            trailing = {
                artist?.let { artistInfo ->
                    ArtistMenuButton(
                        title = artistInfo.name,
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
                                    Toast.makeText(context, R.string.artist_mix_unavailable, Toast.LENGTH_SHORT).show()
                                }
                                isMixBusy = false
                            }
                        },
                        onFollow = followArtist,
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
            })
        if (showReleases) ArtistReleasesDialog(allReleases, colors.isDark,
            onOpen = { showReleases = false; onNavigateToAlbum(it) }, onDismiss = { showReleases = false })

        if (showArtistInformation) ArtistInformationDialog(playCount = playCount, favouriteTrack = favouriteTrackTitle,
            name = artist?.name.orEmpty(), links = artist?.links.orEmpty(), isDark = colors.isDark,
            onDismiss = { showArtistInformation = false })

        if (showAllSongs) {
            ArtistTracksDialog(
                artistName = artist?.name.orEmpty(),
                artistCover = artist?.image ?: artist?.cover,
                tracks = artistTracks,
                isLoading = artistTracksLoading,
                hasMore = artistTracksHasMore,
                loadError = artistTracksLoadError,
                pagingKey = artistTracksNextOffset,
                isDark = colors.isDark,
                onLoadMore = {
                    artist?.let { art ->
                        scope.launch { loadArtistTracksPage(art, reset = false) }
                    }
                },
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
    artistCover: String?,
    tracks: List<Track>,
    isLoading: Boolean,
    hasMore: Boolean,
    loadError: Boolean,
    pagingKey: Int?,
    isDark: Boolean,
    onLoadMore: () -> Unit,
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
                .background(DetailStyle.background(isDark)),
        ) {
            DetailTopBar(
                title = artistName, showTitle = true, isDark = isDark, onBack = onDismiss,
                titleContent = {
                    ArtistTitlePill(artistName, artistCover, isDark,
                        subtitle = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size))
                },
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
                    Column(modifier = Modifier.padding(horizontal = DetailStyle.padding)) {
                        TopSongRow(
                            track = track,
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
                if (hasMore || isLoading) {
                    item(key = "artist-next-page") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isLoading -> CircularProgressIndicator(
                                    color = LiquidTheme.colors.accent,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                                loadError && hasMore -> Text(
                                    text = stringResource(R.string.retry_load_more),
                                    color = LiquidTheme.colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(LiquidSurfaces.card(isDark))
                                        .liquidClickable(
                                            pressedScale = LiquidMotion.PressButton,
                                            onClick = onLoadMore,
                                        )
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                )
                                hasMore -> LaunchedEffect(pagingKey, tracks.size) {
                                    onLoadMore()
                                }
                            }
                        }
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
                .background(DetailStyle.background(isDark)),
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
                    start = DetailStyle.padding,
                    top = 12.dp,
                    end = DetailStyle.padding,
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
            .background(DetailStyle.background(isDark))
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
                contentDescription = stringResource(R.string.action_back),
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
private fun ArtistBackdrop(name: String, imageUrl: String?, videoUrl: String?, isDark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.clipToBounds()) {
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
                    modifier = Modifier.fillMaxWidth().height(390.dp)
                )
            }
            ProgressiveArtistArtwork(imageUrl, name, isDark, drawBase = videoUrl.isNullOrBlank(), artworkHeight = 390.dp)
    }
}

@Composable
private fun ArtistHeader(
    name: String,
    genre: String?,
    imageUrl: String?,
    videoUrl: String?,
) {
    val isDark = LiquidTheme.colors.isDark
    Box(modifier = Modifier.fillMaxWidth().height(390.dp)) {

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = DetailStyle.padding,
                    end = DetailStyle.padding,
                    bottom = if (isDark) 22.dp else 34.dp
                )
        ) {
            if (!genre.isNullOrBlank()) {
                Text(
                    text = genre.uppercase(),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp, letterSpacing = 1.7.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = name,
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = LiquidMetrics.TitleHugeWeight,
                fontFamily = VkSansDisplay,
                letterSpacing = LiquidMetrics.TitleHugeSpacing,
                lineHeight = 54.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )



        }
    }
}

/** То, чего нет у стримингов: сколько именно ВЫ слушали этого артиста. */


/** Seamless portrait followed by flat playback controls. */
@Composable
internal fun ArtistHero(
    canPlay: Boolean,
    name: String,
    genre: String?,
    imageUrl: String?,
    videoUrl: String?,
    isDark: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        ArtistBackdrop(name, imageUrl, videoUrl, isDark, Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxWidth()) {
        ArtistHeader(
            name = name,
            genre = genre,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
        )
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailActionButton(stringResource(R.string.action_play), LmgGlyphs.Play28, true, isDark, enabled = canPlay, onClick = onPlay)
            DetailActionButton(stringResource(R.string.action_shuffle), LmgGlyphs.ShuffleOutline28, false, isDark, enabled = canPlay, onClick = onShuffle)
        }
    }
}
}

/** Заголовок раздела со ссылкой справа — «See all» и подобные. */
@Composable
internal fun SectionHeaderWithLink(
    isDark: Boolean,
    title: String,
    linkLabel: String,
    onLinkClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DetailStyle.padding,
                end = DetailStyle.padding,
                top = DetailStyle.sectionGap,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = LiquidSurfaces.textPrimary(isDark),
            fontSize = 23.sp, lineHeight = 28.sp,
            fontWeight = LiquidMetrics.SectionTitleWeight,
            fontFamily = VkSansDisplay,
            letterSpacing = LiquidMetrics.SectionTitleSpacing,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = linkLabel,
            color = DetailStyle.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clip(LiquidMetrics.Pill)
                .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onLinkClick)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun SectionHeaderThemed(isDark: Boolean, title: String) {
    Text(
        text = title,
        color = LiquidSurfaces.textPrimary(isDark),
        fontSize = 23.sp, lineHeight = 28.sp,
        fontWeight = LiquidMetrics.SectionTitleWeight,
        fontFamily = VkSansDisplay,
        letterSpacing = LiquidMetrics.SectionTitleSpacing,
        modifier = Modifier.padding(
            start = DetailStyle.padding,
            end = DetailStyle.padding,
            top = DetailStyle.sectionGap,
            bottom = 12.dp
        )
    )
}

/** Compact artist identity in the collapsed navigation bar. */
@Composable
internal fun ArtistTitlePill(name: String, coverUrl: String?, isDark: Boolean, subtitle: String? = null) {
    val shape = RoundedCornerShape(percent = 50)
    Row(Modifier.heightIn(min = 44.dp).clip(shape)
        .background(if (isDark) Color(0xFF303030) else Color(0xFFF4F4F5))
        .border(1.dp, if (isDark) Color(0xFF505050) else Color(0xFFD5D5D8), shape)
        .padding(start = 7.dp, top = 7.dp, end = 13.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlbumArtImage(uri = null, coverUrl = coverUrl, contentDescription = null,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
            placeholderIconSize = 18.dp, contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, color = DetailStyle.text(isDark), fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Normal, softWrap = true)
            if (subtitle != null) Text(subtitle, color = DetailStyle.muted(isDark),
                fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
internal fun ArtistMenuButton(
    title: String,
    isFollowed: Boolean,
    isMixBusy: Boolean,
    followEnabled: Boolean,
    onMix: () -> Unit,
    onFollow: () -> Unit,
    onShare: () -> Unit,
) {
    DetailMenuButton(title, listOf(
            DetailMenuAction(stringResource(R.string.artist_mix), LmgGlyphs.MusicNoteWaveOutline28, !isMixBusy, onMix),
            DetailMenuAction(stringResource(if (isFollowed) R.string.following else R.string.follow), LmgGlyphs.Favorite28, followEnabled, onFollow),
            DetailMenuAction(stringResource(R.string.action_share), LmgGlyphs.ShareOutline28, onClick = onShare),
    ), circular = true)
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
            .padding(horizontal = DetailStyle.padding, vertical = 4.dp)
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
            .padding(horizontal = DetailStyle.padding)
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
                    text = stringResource(R.string.subscribed_to_community),
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
        contentPadding = PaddingValues(horizontal = DetailStyle.padding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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


private fun ArtistLink.matches(vararg markers: String): Boolean {
    val haystack = "$title ${subtitle.orEmpty()} $url".lowercase()
    return markers.any { it.lowercase() in haystack }
}

@Composable
internal fun TopSongRow(
    track: Track? = null,
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
    var menuOpen by remember(track?.id) { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    if (menuOpen && track != null) TrackActionsSheet(track, anchor = menuAnchor, onDismiss = { menuOpen = false })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(18.dp))
            .liquidClickable(enabled = enabled, pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtImage(
            uri = null,
            coverUrl = coverUrl,
            artworkQuery = com.lmg.vk.artwork.ArtworkQuery(title, subtitle, durationMs),
            contentDescription = title,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(11.dp))
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
                    fontSize = 15.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 12.sp, lineHeight = 16.sp,
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
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (track != null) Box(Modifier.size(44.dp).onGloballyPositioned { menuAnchor = it.boundsInWindow() }
            .liquidClickable(enabled = enabled, onClick = { menuOpen = true }), contentAlignment = Alignment.Center) {
            Icon(LmgGlyphs.MoreHorizontal28, stringResource(R.string.track_actions), tint = textSecondary, modifier = Modifier.size(22.dp))
        }

    }
}

/** Свежий релиз крупно: у знакомого артиста его ищут первым делом. */
@Composable
internal fun LatestReleaseCard(
    album: ArtistAlbum,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DetailStyle.padding)
        .detailClickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtImage(uri = null, coverUrl = album.cover.toThumb(), contentDescription = album.title,
            modifier = Modifier.size(94.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            album.date?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = DetailStyle.accent, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Text(album.title, color = DetailStyle.text(isDark), fontFamily = VkSansDisplay,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(releaseTypeLabel(album.type), album.year).joinToString(" · "),
                color = DetailStyle.muted(isDark), fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Icon(lmgVector(LmgDrawables.ChevronRightOutline24), null, tint = DetailStyle.muted(isDark), modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun AlbumRow(
    albums: List<ArtistAlbum>,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onNavigateToAlbum: (String) -> Unit,
    grid: Boolean = false
) {
    if (grid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DetailStyle.padding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            albums.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { album ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .liquidClickable(onClick = { onNavigateToAlbum(album.id) })) {
                            AlbumArtImage(uri = null, coverUrl = album.cover.toThumb(), contentDescription = album.title,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                            Text(album.title, color = textPrimary, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            album.year?.let { Text(it, color = textSecondary, fontSize = 12.sp) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = DetailStyle.padding),
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

/** Native discography, using only releases and years returned by the catalogue. */
@Composable
private fun ArtistReleasesDialog(releases: List<ArtistAlbum>, isDark: Boolean, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf(0) }
    val visible = remember(releases, filter) {
        releases.filter { filter == 0 || (if (filter == 2) it.isSingleOrEpUi() else !it.isSingleOrEpUi()) }
            .sortedByDescending { it.year?.toIntOrNull() ?: 0 }.groupBy { it.year.orEmpty() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(DetailStyle.background(isDark))) {
            DetailTopBar(stringResource(R.string.releases_title), true, isDark, onDismiss)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(stringResource(R.string.detail_filter_all), stringResource(R.string.section_albums), stringResource(R.string.singles_eps)).forEachIndexed { index, label ->
                    Text(label, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp, color = if (filter == index) DetailStyle.background(isDark) else DetailStyle.text(isDark),
                        modifier = Modifier.weight(1f).clip(CircleShape)
                            .background(if (filter == index) DetailStyle.text(isDark) else DetailStyle.surface(isDark))
                            .border(1.dp, if (filter == index) Color.White.copy(alpha = .25f) else LiquidSurfaces.divider(isDark), CircleShape)
                            .liquidClickable(onClick = { filter = index }).padding(horizontal = 12.dp, vertical = 13.dp))
                }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.detail_no_releases), color = LiquidSurfaces.textSecondary(isDark),
                        fontSize = 14.sp, modifier = Modifier.padding(vertical = 32.dp))
                }
                visible.forEach { (year, albums) ->
                    if (year.isNotBlank()) item(key = "year:$year", span = { GridItemSpan(maxLineSpan) }) {
                        Text(year, color = LiquidSurfaces.textPrimary(isDark), fontSize = 25.sp, fontFamily = VkSansDisplay, fontWeight = FontWeight.Bold)
                    }
                    gridItems(albums, key = { it.id }) { album ->
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).liquidClickable(onClick = { onOpen(album.id) })) {
                            AlbumArtImage(uri = null, coverUrl = album.cover.toThumb(), contentDescription = album.title,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                            Text(album.title, color = LiquidSurfaces.textPrimary(isDark), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            releaseTypeLabel(album.type)?.let { Text(it, color = LiquidSurfaces.textSecondary(isDark), fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistInformationDialog(name: String, links: List<ArtistLink>, isDark: Boolean, playCount: Int, favouriteTrack: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(DetailStyle.background(isDark))) {
            DetailTopBar(name, true, isDark, onDismiss)
            LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)) {
                if (playCount > 0) item {
                    Text(pluralStringResource(R.plurals.artist_played_times, playCount, playCount), color = DetailStyle.text(isDark), fontSize = 15.sp)
                    favouriteTrack?.let { Text(stringResource(R.string.most_played_track, it), color = DetailStyle.muted(isDark), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)) }
                }
                items(links.distinctBy { it.url }, key = { it.url }) { link ->
                    Column(Modifier.fillMaxWidth().detailClickable(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.url))) }
                    }).padding(vertical = 12.dp)) {
                        Text(link.title, color = DetailStyle.text(isDark), fontSize = 15.sp)
                        link.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = DetailStyle.muted(isDark), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
