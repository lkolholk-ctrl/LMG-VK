package com.lmg.vk.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.engine.backend.AlbumResponse
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.ui.components.DetailHeader
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.components.DetailTrackRow
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

    LaunchedEffect(albumId, reloadKey) {
        isLoading = true
        error = null
        album = null
        try {
            album = MusicBackend.getAlbum(albumId)
            isFollowing = album?.album?.isFollowing == true
            if (album == null) error = MusicBackend.lastError.value ?: "Album not found"
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
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = LiquidSurfaces.textPrimary(isDark),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Retry",
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
                                    PlayerController.play(context, playableTracks, 0)
                                }
                            },
                            onShuffle = {
                                if (playableTracks.isNotEmpty()) {
                                    PlayerController.play(context, playableTracks.shuffled(), 0)
                                }
                            },
                            canPlay = playableTracks.isNotEmpty(),
                        )
                    }

                    item {
                        AlbumActionsRow(
                            isDark = isDark,
                            isFollowing = isFollowing,
                            isAdding = followBusy,
                            canFollow = info?.canFollow == true && !followBusy,
                            canDownload = playableTracks.isNotEmpty(),
                            onAdd = {
                                if (!isFollowing && info?.canFollow == true) {
                                    scope.launch {
                                        followBusy = true
                                        if (MusicBackend.followAlbum(albumId)) {
                                            isFollowing = true
                                            Toast.makeText(context, "Album added", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Couldn't add album", Toast.LENGTH_SHORT).show()
                                        }
                                        followBusy = false
                                    }
                                }
                            },
                            onDownload = {
                                playableTracks.forEach { AudioDownloadManager.downloadTrack(context, it) }
                                Toast.makeText(
                                    context,
                                    "Caching ${playableTracks.size} tracks",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onQueue = {
                                playableTracks.forEach(PlayerController::addToQueue)
                                Toast.makeText(
                                    context,
                                    "Added ${playableTracks.size} tracks to queue",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }

                    info?.artistId?.takeIf { it.isNotBlank() }?.let { artistId ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(LiquidSurfaces.card(isDark))
                                    .liquidClickable(
                                        pressedScale = LiquidMotion.PressButton,
                                        onClick = { onNavigateToArtist(artistId) },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = info?.artist.orEmpty(),
                                    color = LiquidSurfaces.textPrimary(isDark),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                                )
                                Icon(
                                    Icons.Rounded.ArrowForward,
                                    contentDescription = "Open artist",
                                    tint = LiquidSurfaces.textSecondary(isDark),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }

                    itemsIndexed(albumTracks, key = { _, track -> track.id }) { index, track ->
                        DetailTrackRow(
                            position = index + 1,
                            title = track.title,
                            subtitle = null,
                            durationMs = track.durationMs,
                            // Обложка здесь одна на все треки — номер полезнее.
                            coverUrl = null,
                            isDark = isDark,
                            showDivider = index < albumTracks.lastIndex,
                            enabled = track.isAvailable,
                            onClick = {
                                val playableIndex = playableTracks.indexOfFirst { it.id == track.id }
                                if (playableIndex >= 0) {
                                    PlayerController.play(context, playableTracks, playableIndex)
                                }
                            }
                        )
                    }

                    item {
                        val metadata = buildList {
                            info?.plays?.takeIf { it > 0 }?.let { add("${formatCount(it)} plays") }
                            info?.createdAt?.takeIf { it > 0L }?.let { add("Created ${formatCatalogDate(it)}") }
                            info?.updatedAt?.takeIf { it > 0L }?.let { add("Updated ${formatCatalogDate(it)}") }
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
    }
}

@Composable
private fun AlbumActionsRow(
    isDark: Boolean,
    isFollowing: Boolean,
    isAdding: Boolean,
    canFollow: Boolean,
    canDownload: Boolean,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlbumActionButton(
            when {
                isFollowing -> "Added"
                isAdding -> "Adding…"
                else -> "Add"
            },
            if (isFollowing) Icons.Rounded.Check else Icons.Rounded.Add,
            (isFollowing || canFollow) && !isAdding,
            isDark,
            onAdd,
        )
        AlbumActionButton("Cache", Icons.Rounded.Download, canDownload, isDark, onDownload)
        AlbumActionButton("Queue", Icons.Rounded.QueueMusic, canDownload, isDark, onQueue)
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
    value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000f)
    value >= 1_000 -> "%.1fK".format(Locale.US, value / 1_000f)
    else -> value.toString()
}
