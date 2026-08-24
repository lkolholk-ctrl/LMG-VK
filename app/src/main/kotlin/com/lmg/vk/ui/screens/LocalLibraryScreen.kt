package com.lmg.vk.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lmg.vk.R
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.data.local.LocalLibraryIndexer
import com.lmg.vk.data.local.LocalLibraryStore
import com.lmg.vk.data.local.db.AlbumAgg
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.data.local.db.ArtistAgg
import com.lmg.vk.data.local.db.LocalTrackEntity
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.components.SectionTopBar
import com.lmg.vk.ui.theme.LiquidColors
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import kotlinx.coroutines.delay

private fun albumArt(albumId: Long): Uri = Uri.parse("content://media/external/audio/albumart/$albumId")

private fun fmtDuration(ms: Long): String {
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

// ══════════════════════════════════════════════════════════════════════════════
//  Главный экран: поиск + Артисты / Альбомы / Треки
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LocalLibraryScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit
) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).localTracksDao() }

    LaunchedEffectIndex(context)
    val status by LocalLibraryIndexer.status.collectAsState()
    val progress by LocalLibraryIndexer.progress.collectAsState()

    var tab by remember { mutableStateOf(0) }              // 0=артисты 1=альбомы 2=треки
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<LocalLibraryStore.SearchResults?>(null) }

    // ── множественный выбор (вкладка «Треки») для массового редактирования тегов ──
    val selected = remember { mutableStateMapOf<String, LocalTrackEntity>() }
    var selectionMode by remember { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selected.clear() }

    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(250)                                          // debounce
        results = LocalLibraryStore.search(context, query)
    }
    // выходим из режима выбора при смене вкладки или входе в поиск
    androidx.compose.runtime.LaunchedEffect(tab, query) {
        if (tab != 2 || query.isNotBlank()) exitSelection()
    }

    BackHandler(enabled = bulkOpen) { bulkOpen = false }
    BackHandler(enabled = selectionMode && !bulkOpen) { exitSelection() }

    Box(Modifier.fillMaxSize().background(lc.settingsBackground)) {
        val listHeader: @Composable () -> Unit = {
            if (selectionMode) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(Modifier.height(12.dp))
                    SelectionHeader(selected.size, lc, onClose = { exitSelection() })
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                LocalLibraryBrowseHeader(
                    query = query,
                    selectedTab = tab,
                    lc = lc,
                    onBack = onBack,
                    onQueryChange = { query = it },
                    onClearQuery = { query = "" },
                    onSelectTab = { tab = it },
                )
            }
        }

        if (query.isBlank()) {
            when (tab) {
                0 -> ArtistsList(dao, lc, onOpenArtist, listHeader)
                1 -> AlbumsGrid(dao, lc, onOpenAlbum, listHeader)
                else -> TracksTab(
                    dao, lc, context, selectionMode, selected,
                    header = listHeader,
                    onLongPress = { e -> selectionMode = true; selected[e.id] = e },
                    onToggle = { e -> if (selected.containsKey(e.id)) selected.remove(e.id) else selected[e.id] = e },
                    onSelectAllVisible = { list -> list.forEach { selected[it.id] = it } },
                    onClearVisible = { exitSelection() }
                )
            }
        } else {
            SearchResultsView(results, lc, context, onOpenArtist, onOpenAlbum, listHeader)
        }

        // нижняя панель действий в режиме выбора
        if (selectionMode && selected.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(LiquidSurfaces.card(lc.isDark))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.selected_count, selected.size), color = lc.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(lc.accent)
                        .liquidClickable(pressedScale = LiquidMotion.PressButton) { bulkOpen = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(lmgVector(LmgDrawables.ListPenOutline20), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_tags), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (status == LocalLibraryIndexer.Status.SCANNING && !selectionMode) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    .clip(CircleShape).background(LiquidSurfaces.card(lc.isDark)).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.indexing_library, progress), color = lc.textSecondary, fontSize = 13.sp)
            }
        }

        // оверлей массового редактирования
        if (bulkOpen) {
            Box(Modifier.fillMaxSize()) {
                BulkTagEditScreen(
                    tracks = selected.values.map { LocalLibraryStore.toTrack(it) },
                    onBack = { bulkOpen = false; exitSelection() }
                )
            }
        }
    }
}

@Composable
private fun LocalLibraryBrowseHeader(
    query: String,
    selectedTab: Int,
    lc: LiquidColors,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectTab: (Int) -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(modifier = Modifier.requiredWidth(screenWidth)) {
        SectionTopBar(
            title = stringResource(R.string.on_this_device),
            subtitle = stringResource(R.string.local_library_subtitle),
            isDark = lc.isDark,
            onBack = onBack,
        )
        Spacer(Modifier.height(12.dp))
        SearchField(
            query = query,
            lc = lc,
            onChange = onQueryChange,
            onClear = onClearQuery,
        )
        Spacer(Modifier.height(12.dp))
        Segments(selectedTab, lc, onSelectTab)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SelectionHeader(count: Int, lc: LiquidColors, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7), CircleShape)
                .clip(CircleShape).liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Icon(com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28, null, tint = lc.iconDefault, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(if (count == 0) stringResource(R.string.select_tracks) else stringResource(R.string.selected_count, count),
            color = lc.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = VkSansDisplay)
    }
}

@Composable
private fun LaunchedEffectIndex(context: android.content.Context) {
    androidx.compose.runtime.LaunchedEffect(Unit) { LocalLibraryIndexer.ensureIndexed(context) }
}

// ── Списки (главный экран) ───────────────────────────────────────────────────
@Composable
private fun ArtistsList(
    dao: com.lmg.vk.data.local.db.LocalTracksDao,
    lc: LiquidColors,
    onOpenArtist: (String) -> Unit,
    header: @Composable () -> Unit,
) {
    val flow = remember { dao.artists() }
    val artists by flow.collectAsState(initial = emptyList())
    // Адаптив: в широком окне центрируем список узкой колонкой ~600dp.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val sidePad = if (win.useSideBySide) 24.dp else 12.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "local_artists_header") { header() }
        items(artists, key = { it.name }) { artist ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
                ArtistRow(artist, lc, compact = win.useSideBySide) { onOpenArtist(artist.name) }
            }
        }
    }
}

@Composable
private fun AlbumsGrid(
    dao: com.lmg.vk.data.local.db.LocalTracksDao,
    lc: LiquidColors,
    onOpenAlbum: (Long, String) -> Unit,
    header: @Composable () -> Unit,
) {
    val flow = remember { dao.albums() }
    val albums by flow.collectAsState(initial = emptyList())
    // В альбоме/на планшете больше колонок под обложки (было фиксированные 2).
    val win = com.lmg.vk.ui.rememberWindowInfo()
    LazyVerticalGrid(
        columns = if (win.useSideBySide) GridCells.Adaptive(minSize = 150.dp) else GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(
            key = "local_albums_header",
            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
        ) { header() }
        items(albums, key = { it.albumId }) { al -> AlbumCard(al, lc, compact = win.useSideBySide) { onOpenAlbum(al.albumId, al.name) } }
    }
}

@Composable
private fun TracksTab(
    dao: com.lmg.vk.data.local.db.LocalTracksDao,
    lc: LiquidColors,
    context: android.content.Context,
    selectionMode: Boolean,
    selected: Map<String, LocalTrackEntity>,
    header: @Composable () -> Unit,
    onLongPress: (LocalTrackEntity) -> Unit,
    onToggle: (LocalTrackEntity) -> Unit,
    onSelectAllVisible: (List<LocalTrackEntity>) -> Unit,
    onClearVisible: () -> Unit
) {
    var sort by remember { mutableStateOf(0) } // 0 title,1 artist,2 album,3 added,4 duration
    val flow = remember(sort) {
        when (sort) {
            1 -> dao.tracksByArtist(); 2 -> dao.tracksByAlbum()
            3 -> dao.tracksByDateAdded(); 4 -> dao.tracksByDuration(); else -> dao.tracksByTitle()
        }
    }
    val tracks by flow.collectAsState(initial = emptyList())
    // Адаптив: в широком окне центрируем список треков узкой колонкой ~600dp
    // (полоса сортировки сверху остаётся во всю ширину).
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val sidePad = if (win.useSideBySide) 24.dp else 12.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (selectionMode) 96.dp else 120.dp),
    ) {
        item(key = "local_tracks_header") { header() }
        item(key = "local_tracks_sort") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectionMode) {
                    SortChip(stringResource(R.string.select_all_count, tracks.size), false, lc) { onSelectAllVisible(tracks) }
                    SortChip(stringResource(R.string.deselect), false, lc) { onClearVisible() }
                }
                val labels = listOf(
                    stringResource(R.string.sort_title),
                    stringResource(R.string.sort_artist),
                    stringResource(R.string.section_albums),
                    stringResource(R.string.sort_added),
                    stringResource(R.string.sort_duration)
                )
                labels.forEachIndexed { i, t -> SortChip(t, sort == i, lc) { sort = i } }
            }
        }
        itemsIndexed(tracks) { index, e ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
                SelectableTrackRow(
                    e, lc,
                    selectionMode = selectionMode,
                    selectedNow = selected.containsKey(e.id),
                    compact = win.useSideBySide,
                    onClick = { if (selectionMode) onToggle(e) else LocalLibraryStore.play(context, tracks, index) },
                    onLongPress = { onLongPress(e) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableTrackRow(
    e: LocalTrackEntity, lc: LiquidColors,
    selectionMode: Boolean, selectedNow: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit, onLongPress: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selectedNow) lc.glassTint else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = onClick, onLongClick = onLongPress
            )
            .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            if (selectedNow) {
                Icon(lmgVector(LmgDrawables.CheckCircleOn28), null, tint = lc.accent, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
            } else {
                Box(Modifier.size(if (compact) 20.dp else 24.dp).clip(CircleShape).border(2.dp, lc.iconMuted, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
        }
        ArtBox(albumArt(e.albumId), if (compact) 40.dp else 48.dp, 8.dp, lc, com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = lc.textPrimary, fontSize = if (compact) 13.5.sp else 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.artist} · ${fmtDuration(e.durationMs)}", color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Поиск ────────────────────────────────────────────────────────────────────
@Composable
private fun SearchResultsView(
    results: LocalLibraryStore.SearchResults?,
    lc: LiquidColors,
    context: android.content.Context,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    header: @Composable () -> Unit,
) {
    val r = results
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val sidePad = if (win.useSideBySide) 24.dp else 12.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "local_search_header") { header() }
        when {
            r == null -> item(key = "local_search_loading") {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.searching_short), color = lc.textTertiary, fontSize = 14.sp)
                }
            }

            r.artists.isEmpty() && r.albums.isEmpty() && r.tracks.isEmpty() -> item(key = "local_search_empty") {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.nothing_found), color = lc.textTertiary, fontSize = 14.sp)
                }
            }

            else -> {
                if (r.artists.isNotEmpty()) {
                    item { Box(Modifier.padding(horizontal = sidePad)) { SectionLabel(stringResource(R.string.section_artists), lc) } }
                    items(r.artists, key = { "a_" + it.name }) { artist ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
                            ArtistRow(artist, lc, compact = win.useSideBySide) { onOpenArtist(artist.name) }
                        }
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item { Box(Modifier.padding(horizontal = sidePad)) { SectionLabel(stringResource(R.string.section_albums), lc) } }
                    items(r.albums, key = { "al_" + it.albumId }) { album ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
                            AlbumRow(album, lc, compact = win.useSideBySide) { onOpenAlbum(album.albumId, album.name) }
                        }
                    }
                }
                if (r.tracks.isNotEmpty()) {
                    item { Box(Modifier.padding(horizontal = sidePad)) { SectionLabel(stringResource(R.string.section_songs), lc) } }
                    itemsIndexed(r.tracks) { index, track ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
                            TrackRow(track, lc, compact = win.useSideBySide) {
                                LocalLibraryStore.play(context, r.tracks, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Экран артиста: его альбомы + все треки
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LocalArtistDetailScreen(artistName: String, onBack: () -> Unit, onOpenAlbum: (Long, String) -> Unit) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).localTracksDao() }
    val albumsFlow = remember(artistName) { dao.albumsOfArtist(artistName) }
    val tracksFlow = remember(artistName) { dao.tracksOfArtist(artistName) }
    val albums by albumsFlow.collectAsState(initial = emptyList())
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    // Адаптив: в широком окне центрируем контент узкой колонкой ~600dp.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val sidePad = if (win.useSideBySide) 20.dp else 0.dp

    Box(Modifier.fillMaxSize().background(lc.settingsBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = sidePad, end = sidePad, bottom = 120.dp)) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleBack(lc, onBack)
                    Spacer(Modifier.width(14.dp))
                    Text(artistName, color = lc.textPrimary, fontSize = if (win.useSideBySide) 19.sp else 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PlayAllButton(lc) { LocalLibraryStore.play(context, tracks, 0) }
                }
                Spacer(Modifier.height(12.dp))
                if (albums.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.section_albums), lc)
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(albums, key = { it.albumId }) { al ->
                            Box(Modifier.width(if (win.useSideBySide) 118.dp else 140.dp).animateItem()) { AlbumCard(al, lc, compact = win.useSideBySide) { onOpenAlbum(al.albumId, al.name) } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionLabel(stringResource(R.string.all_tracks), lc)
                }
            }
            itemsIndexed(tracks) { index, e -> TrackRow(e, lc, paddingH = 12.dp, compact = win.useSideBySide) { LocalLibraryStore.play(context, tracks, index) } }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Экран альбома: треки по порядку
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LocalAlbumDetailScreen(albumId: Long, albumName: String, onBack: () -> Unit) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).localTracksDao() }
    val tracksFlow = remember(albumId) { dao.tracksOfAlbum(albumId) }
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    val artist = tracks.firstOrNull()?.artist ?: ""
    val year = tracks.firstOrNull()?.year ?: 0
    // Адаптив: в широком окне центрируем контент узкой колонкой ~600dp.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val sidePad = if (win.useSideBySide) 20.dp else 0.dp

    Box(Modifier.fillMaxSize().background(lc.settingsBackground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = sidePad, end = sidePad, bottom = 120.dp)) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleBack(lc, onBack)
                    Spacer(Modifier.weight(1f))
                    PlayAllButton(lc) { LocalLibraryStore.play(context, tracks, 0) }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ArtBox(albumArt(albumId), if (win.useSideBySide) 140.dp else 180.dp, 16.dp, lc, com.lmg.vk.ui.icons.LmgGlyphs.AlbumFilled12)
                }
                Spacer(Modifier.height(14.dp))
                Text(albumName, color = lc.textPrimary, fontSize = if (win.useSideBySide) 18.sp else 20.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                val sub = buildString {
                    append(artist)
                    if (year > 0) append(" · $year")
                    append(" · ${tracks.size} tracks")
                }
                Text(sub, color = lc.textSecondary, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            itemsIndexed(tracks) { index, e ->
                AlbumTrackRow(e, index + 1, lc, compact = win.useSideBySide) { LocalLibraryStore.play(context, tracks, index) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Переиспользуемые элементы
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ArtistRow(a: ArtistAgg, lc: LiquidColors, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtBox(albumArt(a.anyAlbumId), if (compact) 40.dp else 48.dp, 24.dp, lc, com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, color = lc.textPrimary, fontSize = if (compact) 13.5.sp else 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.albums_tracks_count, a.albumCount, a.trackCount), color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 12.sp)
        }
    }
}

@Composable
private fun AlbumRow(al: AlbumAgg, lc: LiquidColors, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtBox(albumArt(al.albumId), if (compact) 40.dp else 48.dp, 8.dp, lc, com.lmg.vk.ui.icons.LmgGlyphs.AlbumFilled12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(al.name, color = lc.textPrimary, fontSize = if (compact) 13.5.sp else 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(al.artist, color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumCard(al: AlbumAgg, lc: LiquidColors, compact: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .liquidClickable(onClick = onClick).padding(4.dp)
    ) {
        ArtBox(albumArt(al.albumId), null, 12.dp, lc, com.lmg.vk.ui.icons.LmgGlyphs.AlbumFilled12, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(al.name, color = lc.textPrimary, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(al.artist, color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(e: LocalTrackEntity, lc: LiquidColors, paddingH: Dp = 8.dp, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = paddingH, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtBox(albumArt(e.albumId), if (compact) 40.dp else 48.dp, 8.dp, lc, com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = lc.textPrimary, fontSize = if (compact) 13.5.sp else 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.artist} · ${fmtDuration(e.durationMs)}", color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumTrackRow(e: LocalTrackEntity, num: Int, lc: LiquidColors, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$num", color = lc.textTertiary, fontSize = if (compact) 12.sp else 13.sp, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = lc.textPrimary, fontSize = if (compact) 13.5.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmtDuration(e.durationMs), color = lc.textSecondary, fontSize = if (compact) 11.5.sp else 12.sp)
    }
}

@Composable
private fun ArtBox(
    uri: Uri, size: Dp?, corner: Dp, lc: LiquidColors,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val base = if (size != null) modifier.size(size) else modifier
    Box(base.clip(RoundedCornerShape(corner)).background(lc.glassTint), contentAlignment = Alignment.Center) {
        Icon(fallback, null, tint = lc.iconMuted, modifier = Modifier.size(22.dp))
        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable
private fun SectionLabel(text: String, lc: LiquidColors) {
    Text(text, color = lc.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Segments(selected: Int, lc: LiquidColors, onSelect: (Int) -> Unit) {
    val items = listOf(
        stringResource(R.string.section_artists),
        stringResource(R.string.section_albums),
        stringResource(R.string.section_songs)
    )
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, t ->
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(
                        if (selected == i) lc.accent else LiquidSurfaces.card(lc.isDark),
                    )
                    .liquidClickable(pressedScale = LiquidMotion.PressButton) { onSelect(i) }
                    .padding(vertical = if (compact) 7.dp else 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t, color = if (selected == i) Color.White else lc.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SortChip(text: String, selected: Boolean, lc: LiquidColors, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(
            if (selected) lc.accent else LiquidSurfaces.card(lc.isDark),
        )
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else lc.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchField(query: String, lc: LiquidColors, onChange: (String) -> Unit, onClear: () -> Unit) {
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(if (compact) 38.dp else 44.dp).clip(CircleShape)
            .background(LiquidSurfaces.card(lc.isDark)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28, null, tint = lc.iconMuted, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(
                color = lc.textPrimary,
                fontSize = if (compact) 14.sp else 16.sp,
                fontFamily = VkSansText,
            ),
            cursorBrush = SolidColor(lc.accent), modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text(stringResource(R.string.search_local_hint), color = lc.textTertiary, fontSize = if (compact) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28, null, tint = lc.iconMuted,
                modifier = Modifier.size(20.dp).liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onClear))
        }
    }
}

@Composable
private fun PlayAllButton(lc: LiquidColors, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(lc.accent)
            .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(com.lmg.vk.ui.icons.LmgGlyphs.Play28, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CircleBack(lc: LiquidColors, onClick: () -> Unit) {
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    Box(
        Modifier.size(if (compact) 34.dp else 40.dp).background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7), CircleShape)
            .clip(CircleShape).liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28, null, tint = lc.iconDefault, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
    }
}
