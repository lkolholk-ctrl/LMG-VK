package com.lmg.vk.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.VkAudioIdentity
import com.lmg.vk.ui.components.DetailTrackRow
import com.lmg.vk.ui.components.SectionTopBar
import com.lmg.vk.ui.components.SectionTopBarAction
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.viewmodel.VkHistoryViewModel

@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    viewModel: VkHistoryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var pendingRemoval by remember { mutableStateOf<Track?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(listState, state.accountId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 5) viewModel.loadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidSurfaces.sheet(colors.isDark)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "vk_history_header") {
                SectionTopBar(
                    title = "История VK",
                    subtitle = when {
                        state.accountId == null -> "Войдите в аккаунт VK"
                        state.tracks.isEmpty() -> "Недавние прослушивания"
                        else -> "${state.tracks.size} треков"
                    },
                    isDark = colors.isDark,
                    onBack = onBack,
                    actions = if (state.accountId != null) {
                        {
                            SectionTopBarAction(
                                label = "Обновить",
                                icon = LmgGlyphs.RefreshOutline28,
                                filled = false,
                                enabled = !state.isLoading,
                                onClick = viewModel::refresh,
                            )
                        }
                    } else null,
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            when {
                state.isLoading -> item(key = "vk_history_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = colors.iconMuted,
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }

                state.accountId == null -> item(key = "vk_history_auth") {
                    HistoryMessage("История VK доступна после входа в аккаунт")
                }

                state.tracks.isEmpty() && state.error != null -> item(key = "vk_history_error") {
                    HistoryMessage(state.error.orEmpty())
                }

                state.tracks.isEmpty() -> item(key = "vk_history_empty") {
                    HistoryMessage("В истории пока ничего нет")
                }

                else -> {
                    itemsIndexed(
                        items = state.tracks,
                        key = { _, track -> VkAudioIdentity.stableFullId(track.id) },
                    ) { index, track ->
                        val removing = VkAudioIdentity.stableFullId(track.id) in state.removingIds
                        DetailTrackRow(
                            position = index + 1,
                            title = track.title,
                            subtitle = track.artist,
                            durationMs = track.durationMs,
                            coverUrl = track.coverUrl,
                            isDark = colors.isDark,
                            showDivider = index < state.tracks.lastIndex,
                            enabled = !removing,
                            onMore = { pendingRemoval = track },
                            onClick = {
                                if (track.isAvailable) {
                                    val playable = state.tracks.filter(Track::isAvailable)
                                    val playableIndex = playable.indexOfFirst {
                                        VkAudioIdentity.stableFullId(it.id) ==
                                            VkAudioIdentity.stableFullId(track.id)
                                    }
                                    if (playableIndex >= 0) {
                                        PlayerController.playFromList(context, playable, playableIndex)
                                    }
                                }
                            },
                        )
                    }

                    if (state.isLoadingMore) {
                        item(key = "vk_history_loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = colors.iconMuted,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    } else if (state.error != null) {
                        item(key = "vk_history_more_error") {
                            TextButton(
                                onClick = {
                                    if (state.nextFrom != null) viewModel.loadMore() else viewModel.refresh()
                                },
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            ) {
                                Text("${state.error}. Повторить")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Убрать из истории?") },
            text = { Text("${track.artist} — ${track.title}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        viewModel.remove(track)
                    },
                ) {
                    Text("Убрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun HistoryMessage(text: String) {
    val colors = LiquidTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}
