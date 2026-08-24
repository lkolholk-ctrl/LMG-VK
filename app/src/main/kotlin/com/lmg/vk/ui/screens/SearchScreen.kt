package com.lmg.vk.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.ui.glass.GlassKit
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.SearchItem
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.components.PlaylistPickerSheet
import com.lmg.vk.ui.components.TrackActionsSheet
import com.lmg.vk.ui.components.WrapRow
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import com.lmg.vk.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

private val SearchAccent = Color(0xFFFC3C44)

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    bottomContentPadding: Dp = 32.dp,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val activeAccountId by MusicAuth.profileId.collectAsState()
    val historyKey = "queries_v2_account_${activeAccountId ?: 0L}"

    val viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadMoreError by viewModel.loadMoreError.collectAsState()
    val pagingKey by viewModel.pagingKey.collectAsState()

    // Адаптив: поле поиска и сегменты остаются во всю ширину, а списки
    // результатов/истории в широком окне (альбом/планшет) центрируем узкой
    // колонкой ~600dp боковыми отступами — длинные строки во всю ширину плохи.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val resultsSidePad = if (win.useSideBySide) 24.dp else 0.dp
    // В широком окне (телефон-альбом / планшет) уменьшаем шрифты/обложки/высоты
    // ~на 25%, чтобы контент не выглядел портретно-крупным на невысоком экране.
    val compact = win.useSideBySide

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var history by remember(activeAccountId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(activeAccountId) {
        history = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val scoped = prefs.getString(historyKey, null)
            if (scoped != null) {
                scoped.split('\n').filter { it.isNotBlank() }
            } else {
                val legacy = prefs.getString("queries_v2", null)?.split('\n')
                    ?.filter { it.isNotBlank() }
                    ?: prefs.getStringSet("queries", emptySet()).orEmpty().toList().take(8)
                if (activeAccountId != null && legacy.isNotEmpty()) {
                    prefs.edit()
                        .putString(historyKey, legacy.joinToString("\n"))
                        .remove("queries_v2")
                        .remove("queries")
                        .apply()
                }
                legacy
            }
        }
    }
    fun saveQuery(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        // Дедуп без учёта регистра, свежий — наверх, максимум 8.
        val updated = (listOf(t) + history.filter { !it.equals(t, ignoreCase = true) }).take(8)
        if (updated == history) return
        history = updated
        prefs.edit()
            .putString(historyKey, updated.joinToString("\n"))
            .apply()
    }
    fun clearHistory() {
        prefs.edit().remove(historyKey).apply()
        history = emptyList()
    }

    // Save query when search completes with results
    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty() && query.isNotBlank()) {
            saveQuery(query)
        }
    }

    val tracks = searchResults.filter { it.isTrack }
    val albums = searchResults.filter { it.isAlbum }
    val artists = searchResults.filter { it.isArtist }

    // Долгий тап по треку → контекст-меню (в очередь / поделиться).
    var actionsTrack by remember { mutableStateOf<Track?>(null) }
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    val editablePlaylists by PlaylistManager.playlists.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(LiquidTheme.colors.settingsBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 40.dp)
                        .clip(CircleShape)
                        .background(LiquidTheme.colors.glassTint)
                        .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                        contentDescription = stringResource(R.string.action_back),
                        tint = LiquidTheme.colors.textPrimary,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.tab_search),
                    fontWeight = FontWeight.Bold,
                    fontFamily = VkSansDisplay,
                    fontSize = if (compact) 22.sp else 32.sp,
                    color = LiquidTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search field — пилюля с подсветкой при фокусе + Cancel рядом.
            val isDark = LiquidTheme.colors.isDark
            val searchBarBg = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF2F2F7)
            val accentColor = SearchAccent
            var searchFocused by remember { mutableStateOf(false) }
            val focusBorder by animateColorAsState(
                targetValue = if (searchFocused) accentColor.copy(alpha = 0.65f) else Color.Transparent,
                animationSpec = tween(200),
                label = "focusBorder"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 38.dp else 44.dp)
                    .clip(CircleShape)
                    .background(searchBarBg)
                    .border(1.5.dp, focusBorder, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28,
                    contentDescription = null,
                    tint = LiquidTheme.colors.iconMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    textStyle = TextStyle(
                        color = LiquidTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = VkSansText,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { searchFocused = it.isFocused },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            hideKeyboard()
                            viewModel.searchNow()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_placeholder),
                                    color = LiquidTheme.colors.textTertiary,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f))
                            .liquidClickable(pressedScale = LiquidMotion.PressIcon) {
                                viewModel.clearQuery()
                                hideKeyboard()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                            contentDescription = null,
                            tint = LiquidTheme.colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Cancel — появляется при фокусе/вводе, сбрасывает поиск.
            AnimatedVisibility(visible = searchFocused || query.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .liquidClickable(pressedScale = LiquidMotion.PressButton) {
                            viewModel.clearQuery()
                            hideKeyboard()
                        }
                )
            }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // ─── IDLE STATE: Categories + History ───
                androidx.compose.animation.AnimatedVisibility(
                    visible = query.isBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = resultsSidePad, end = resultsSidePad, bottom = bottomContentPadding)
                    ) {
                        if (history.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.recent_searches),
                                        color = LiquidTheme.colors.sectionLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Clear",
                                        color = SearchAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.liquidClickable(pressedScale = LiquidMotion.PressButton) { clearHistory() }
                                    )
                                }
                            }
                            // Чипы-пилюли в несколько рядов: компактнее списка,
                            // тап — искать сразу. WrapRow (свой Layout), НЕ
                            // androidx FlowRow — его сигнатура плавает между
                            // версиями foundation → NoSuchMethodError на рендере
                            // (поймано полевым дампом краша поиска).
                            item(key = "hist_chips") {
                                WrapRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    history.forEach { item ->
                                        HistoryChip(
                                            query = item,
                                            onClick = {
                                                hideKeyboard()
                                                viewModel.setQuery(item)
                                                viewModel.searchNow()
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "search_welcome") {
                                SearchWelcomeState(compact = compact)
                            }
                        }
                    }
                }

                // ─── ACTIVE SEARCH: Results ───
                androidx.compose.animation.AnimatedVisibility(
                    visible = query.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                        when {
                            isLoading -> {
                                // Шиммер-скелетоны: сразу видно структуру будущих
                                // результатов, а не крутилку в пустоте.
                                SearchSkeleton()
                            }
                            error != null -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF2F2F7))
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.something_went_wrong),
                                            color = LiquidTheme.colors.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = error ?: stringResource(R.string.unknown_error),
                                            color = LiquidTheme.colors.textTertiary,
                                            fontSize = 13.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Retry",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(SearchAccent)
                                                .liquidClickable(pressedScale = LiquidMotion.PressButton) { viewModel.searchNow() }
                                                .padding(horizontal = 24.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    contentPadding = PaddingValues(start = resultsSidePad, end = resultsSidePad, bottom = bottomContentPadding)
                                ) {
                                    // Artists section
                                    if (artists.isNotEmpty()) {
                                        item(key = "artists_label") {
                                            SearchSectionLabel(stringResource(R.string.section_artists), artists.size, compact)
                                        }
                                        item(key = "artists_row") {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                itemsIndexed(
                                                    items = artists,
                                                    key = { index, artist -> "artist_${index}_${artist.id}" }
                                                ) { _, artist ->
                                                    ArtistChip(
                                                        artist = artist,
                                                        compact = compact,
                                                        onClick = {
                                                            hideKeyboard()
                                                            onNavigateToArtist(artist.id)
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }

                                    // Albums section
                                    if (albums.isNotEmpty()) {
                                        item(key = "albums_label") {
                                            SearchSectionLabel(stringResource(R.string.section_albums), albums.size, compact)
                                        }
                                        item(key = "albums_row") {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                itemsIndexed(
                                                    items = albums,
                                                    key = { index, album -> "album_${index}_${album.id}" }
                                                ) { _, album ->
                                                    AlbumCard(
                                                        album = album,
                                                        compact = compact,
                                                        onClick = {
                                                            hideKeyboard()
                                                            onNavigateToAlbum(album.id)
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }

                                    // Tracks section
                                    if (tracks.isNotEmpty()) {
                                        item(key = "tracks_label") {
                                            SearchSectionLabel(stringResource(R.string.section_songs), tracks.size, compact)
                                        }
                                        val playableTracks = tracks.filter { it.isAvailable }.map { it.toTrack() }
                                        itemsIndexed(
                                            items = tracks,
                                            key = { index, track -> "track_${index}_${track.id}" }
                                        ) { _, item ->
                                            SearchResultRow(
                                                title = item.title,
                                                subtitle = if (item.isAvailable) item.displayArtist
                                                    else stringResource(R.string.unavailable_artist_prefix, item.displayArtist),
                                                icon = com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24,
                                                coverUrl = item.cover,
                                                isExplicit = item.isExplicit,
                                                isCustom = item.isCustom,
                                                enabled = item.isAvailable,
                                                compact = compact,
                                                durationMs = item.durationMs,
                                                onClick = {
                                                    hideKeyboard()
                                                    val startIdx = playableTracks.indexOfFirst { it.id == item.id }
                                                    if (startIdx >= 0) {
                                                        PlayerController.playFromList(
                                                            context = context,
                                                            tracks = playableTracks,
                                                            startIndex = startIdx,
                                                            autoRefillType = "search",
                                                            autoRefillId = query,
                                                            autoRefillName = query
                                                        )
                                                    }
                                                },
                                                onLongClick = if (item.isAvailable) {
                                                    {
                                                        hideKeyboard()
                                                        actionsTrack = playableTracks
                                                            .firstOrNull { it.id == item.id }
                                                    }
                                                } else {
                                                    null
                                                }
                                            )
                                        }
                                    }

                                    if (hasMore || isLoadingMore || loadMoreError != null) {
                                        item(key = "search_next_page") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                when {
                                                    isLoadingMore -> CircularProgressIndicator(
                                                        color = SearchAccent,
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                    loadMoreError != null -> Text(
                                                        text = stringResource(R.string.retry_load_more),
                                                        color = SearchAccent,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(LiquidTheme.colors.glassTint)
                                                            .liquidClickable(
                                                                pressedScale = LiquidMotion.PressButton,
                                                                onClick = viewModel::loadMore,
                                                            )
                                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                                    )
                                                    hasMore -> LaunchedEffect(pagingKey, searchResults.size) {
                                                        viewModel.loadMore()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp)
                                                    .padding(top = 24.dp)
                                                    .clip(RoundedCornerShape(28.dp))
                                                    .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF2F2F7))
                                                    .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.nothing_found),
                                                    color = LiquidTheme.colors.textPrimary,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = stringResource(R.string.search_no_results, query),
                                                    color = LiquidTheme.colors.textTertiary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }

        // Контекст-меню трека (долгий тап по строке результата).
        actionsTrack?.let { t ->
            TrackActionsSheet(
                track = t,
                onAddToPlaylist = { playlistPickerTrack = t },
                onDismiss = { actionsTrack = null },
            )
        }
        playlistPickerTrack?.let { track ->
            PlaylistPickerSheet(
                playlists = editablePlaylists,
                onSelect = { playlist ->
                    val added = PlaylistManager.addTrack(playlist.id, track)
                    Toast.makeText(
                        context,
                        if (added) context.getString(R.string.added_to_playlist, playlist.name)
                        else context.getString(R.string.already_in_playlist, playlist.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    playlistPickerTrack = null
                },
                onDismiss = { playlistPickerTrack = null },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  UI Components
// ═══════════════════════════════════════════════════════════

@Composable
private fun ArtistChip(
    artist: SearchItem,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val chipSize = if (compact) 56.dp else 72.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (compact) 64.dp else 80.dp)
            .liquidClickable { onClick() }
    ) {
        if (artist.cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = artist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(chipSize)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(chipSize)
                    .clip(CircleShape)
                    .background(if (LiquidTheme.colors.isDark) Color(0xFF2A2A2A) else Color(0xFFF2F2F7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28,
                    contentDescription = null,
                    tint = LiquidTheme.colors.iconMuted,
                    modifier = Modifier.size(if (compact) 26.dp else 32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
        Text(
            text = artist.title.takeIf { it.isNotBlank() } ?: artist.displayArtist,
            color = LiquidTheme.colors.textPrimary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AlbumCard(
    album: SearchItem,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val cardSize = if (compact) 108.dp else 140.dp
    Column(
        modifier = Modifier
            .width(cardSize)
            .liquidClickable { onClick() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.cover)
                .crossfade(true)
                .build(),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
        Text(
            text = album.title,
            color = LiquidTheme.colors.textPrimary,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = album.displayArtist,
            color = LiquidTheme.colors.textSecondary,
            fontSize = if (compact) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Чип-пилюля недавнего запроса (тап = искать сразу). */
@Composable
private fun HistoryChip(
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (LiquidTheme.colors.isDark) Color(0xFF1A1A1A) else Color(0xFFF2F2F7))
            .liquidClickable(pressedScale = LiquidMotion.PressButton) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.HistoryBackwardOutline28,
            contentDescription = null,
            tint = LiquidTheme.colors.iconMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = query,
            color = LiquidTheme.colors.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Спокойное стартовое состояние, когда истории поиска ещё нет. */
@Composable
private fun SearchWelcomeState(compact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = if (compact) 28.dp else 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 58.dp else 72.dp)
                .clip(CircleShape)
                .background(SearchAccent.copy(alpha = if (LiquidTheme.colors.isDark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28,
                contentDescription = null,
                tint = SearchAccent,
                modifier = Modifier.size(if (compact) 25.dp else 31.dp),
            )
        }
        Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
        Text(
            text = stringResource(R.string.search_vk_music),
            color = LiquidTheme.colors.textPrimary,
            fontSize = if (compact) 17.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (compact) VkSansText else VkSansDisplay,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_find_hint),
            color = LiquidTheme.colors.textTertiary,
            fontSize = if (compact) 12.sp else 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Шиммер-скелетоны результатов: пульсирующие пилюли на месте будущих строк. */
@Composable
private fun SearchSkeleton() {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
        .animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(650),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "skeletonPulse"
        )
    val base = if (LiquidTheme.colors.isDark) Color(0xFF1A1A1A) else Color(0xFFEAEAEF)
    Column(modifier = Modifier.fillMaxSize()) {
        // Плашка на месте заголовка секции
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .size(width = 110.dp, height = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(base.copy(alpha = pulse))
        )
        repeat(7) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(50))
                    .background(base.copy(alpha = pulse))
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    coverUrl: String?,
    isExplicit: Boolean = false,
    isCustom: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    durationMs: Long = 0L,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val artSize = if (compact) 38.dp else 48.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .padding(horizontal = 16.dp)
            .height(if (compact) 52.dp else 64.dp)
            .clip(RoundedCornerShape(50))   // строки-пилюли, как в настройках
            .background(if (LiquidTheme.colors.isDark) Color(0xFF1A1A1A) else Color(0xFFF2F2F7))
            .combinedClickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (LiquidTheme.colors.isDark) Color(0xFF2A2A2A) else Color(0xFFF2F2F7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LiquidTheme.colors.iconMuted,
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = LiquidTheme.colors.textPrimary,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    GlassKit.ExplicitBadge()
                }
                if (isCustom) {
                    Spacer(modifier = Modifier.width(6.dp))
                    GlassKit.VerifiedBadge()
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = LiquidTheme.colors.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Длительность справа. 0 (нет данных) — НЕ показываем, никаких «0:00».
        if (durationMs > 0L) {
            Spacer(modifier = Modifier.width(8.dp))
            val totalSec = durationMs / 1000
            Text(
                text = "%d:%02d".format(totalSec / 60, totalSec % 60),
                color = LiquidTheme.colors.textTertiary,
                fontSize = if (compact) 11.sp else 12.sp
            )
        }
    }
}

@Composable
private fun SearchSectionLabel(text: String, count: Int, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 15.sp else 20.sp,
            fontFamily = if (compact) VkSansText else VkSansDisplay,
            color = LiquidTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = LiquidTheme.colors.textSecondary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (LiquidTheme.colors.isDark) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.06f)
                )
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}
