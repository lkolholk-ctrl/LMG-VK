package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.VkProfileRepository
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.components.DetailTrackRow
import com.lmg.vk.ui.components.formatTotalDuration
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme

/**
 * Аудиозаписи владельца как ОТДЕЛЬНЫЙ экран навигации — вход по ссылке
 * (`vk.com/audios123`, `vk.com/id123`, короткое имя; см.
 * [com.lmg.vk.engine.VkLinkResolver]).
 *
 * Почему обёртка, а не второй экран: разметка та же, что у подэкрана профиля,
 * различается только источник состояния. У профиля состояние уже загружено к
 * моменту показа, здесь же экран открывается по ссылке и грузит себя сам.
 *
 * Состояние берётся из того же [VkProfileRepository.ownerAudio], что и у профиля:
 * это единственный держатель пагинации ([VkProfileRepository.loadMoreOwnerAudio]
 * ходит именно в него), и второй источник пришлось бы синхронизировать с первым.
 * Плата — экран один за раз, но открыть два сразу и нельзя.
 */
@Composable
fun OwnerAudioRoute(
    ownerId: Long,
    onBack: () -> Unit,
) {
    val state by VkProfileRepository.ownerAudio.collectAsState()

    // Грузим на входе и при смене владельца. Если в репозитории уже лежит ЭТОТ
    // владелец (возврат по бэкстеку), повторный запрос не нужен.
    LaunchedEffect(ownerId) {
        if (VkProfileRepository.ownerAudio.value?.ownerId != ownerId) {
            VkProfileRepository.openOwnerAudioById(ownerId)
        }
    }

    // Уходя с экрана, состояние гасим: иначе профиль, открытый следом, показал бы
    // подэкраном чужое аудио, оставшееся от ссылки.
    DisposableEffect(ownerId) {
        onDispose { VkProfileRepository.closeOwnerAudio() }
    }

    // До первого ответа репозитория показываем свой каркас с тем же id: подставлять
    // чужое состояние (вдруг там остался прежний владелец) нельзя.
    val shown = state?.takeIf { it.ownerId == ownerId }
        ?: VkProfileRepository.OwnerAudioState(ownerId = ownerId, isLoading = true)

    OwnerAudioScreen(state = shown, onBack = onBack)
}

/**
 * Аудиозаписи друга или сообщества — открывается поверх профиля.
 *
 * Треки берутся напрямую из `audio.get` по чужому `owner_id`; играются тем же
 * путём, что и всё остальное (кэш [MusicBackend] → [PlayerController]).
 * Если владелец закрыл музыку, VK отвечает ошибкой доступа — показываем это
 * текстом, а не пустым списком.
 */
@Composable
fun OwnerAudioScreen(
    state: VkProfileRepository.OwnerAudioState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    val listState = rememberLazyListState()

    // Треки конвертируются один раз на изменение списка: маппинг заодно кладёт
    // их в кэш бэкенда, без него плеер полез бы за audio.getById.
    val uiTracks: List<Track> = remember(state.tracks) {
        MusicBackend.adoptTracks(state.tracks).map { it.toTrack() }
    }
    val playable = remember(uiTracks) { uiTracks.filter(Track::isAvailable) }

    val showTopTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
    }

    // Догрузка следующей страницы, когда до конца списка осталось меньше экрана.
    LaunchedEffect(state.ownerId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 5) {
                VkProfileRepository.loadMoreOwnerAudio()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.settingsBackground)
            // Оверлей поверх профиля: без своего обработчика касания уходили бы
            // в список профиля под ним.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.iconMuted)
            }

            state.isClosed -> OwnerAudioMessage(
                icon = Icons.Rounded.Lock,
                title = "${state.title} closed their music",
                message = "VK doesn't allow access to this profile's audio.",
            )

            state.error != null && state.tracks.isEmpty() -> OwnerAudioMessage(
                icon = Icons.Rounded.Lock,
                title = "Couldn't load audio",
                message = state.error,
            )

            state.tracks.isEmpty() -> OwnerAudioMessage(
                icon = Icons.Rounded.Person,
                title = "No audio",
                message = "${state.title} has no tracks in VK.",
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item {
                    OwnerAudioHeader(
                        title = state.title,
                        subtitle = state.subtitle,
                        avatarUrl = state.avatarUrl,
                        facts = buildList {
                            add("${state.total ?: state.tracks.size} songs")
                            val totalMs = playable.sumOf(Track::durationMs)
                            if (totalMs > 0) add(formatTotalDuration(totalMs))
                            if (state.playlists.isNotEmpty()) add("${state.playlists.size} playlists")
                        },
                        canPlay = playable.isNotEmpty(),
                        onPlay = { PlayerController.play(context, playable, 0) },
                        onShuffle = { PlayerController.play(context, playable.shuffled(), 0) },
                    )
                }

                itemsIndexed(uiTracks, key = { _, track -> track.id }) { index, track ->
                    DetailTrackRow(
                        position = index + 1,
                        title = track.title,
                        subtitle = track.artist,
                        durationMs = track.durationMs,
                        coverUrl = track.coverUrl,
                        isDark = isDark,
                        showDivider = index < uiTracks.lastIndex,
                        enabled = track.isAvailable,
                        onClick = {
                            val target = playable.indexOfFirst { it.id == track.id }
                            if (target >= 0) PlayerController.play(context, playable, target)
                        },
                    )
                }

                if (state.isLoadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colors.iconMuted,
                            )
                        }
                    }
                }

                state.error?.takeIf { state.tracks.isNotEmpty() }?.let { message ->
                    item {
                        Text(
                            text = message,
                            fontFamily = AppFontFamily,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
        }

        DetailTopBar(
            title = state.title,
            showTitle = showTopTitle,
            isDark = isDark,
            onBack = onBack,
        )
    }
}

@Composable
private fun OwnerAudioHeader(
    title: String,
    subtitle: String,
    avatarUrl: String,
    facts: List<String>,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(colors.textTertiary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    null,
                    tint = colors.iconMuted,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontFamily = AppFontFamily,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontFamily = AppFontFamily,
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = facts.joinToString(" • "),
                fontFamily = AppFontFamily,
                color = colors.textTertiary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnerAudioButton("Play", Icons.Rounded.PlayArrow, canPlay, Modifier.weight(1f), onPlay)
            OwnerAudioButton("Shuffle", Icons.Rounded.Shuffle, canPlay, Modifier.weight(1f), onShuffle)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OwnerAudioButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val accent = Color(0xFFFC3C44)
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(colors.textTertiary.copy(alpha = if (enabled) 0.14f else 0.06f))
            .liquidClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) accent else colors.textTertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            fontFamily = AppFontFamily,
            color = if (enabled) accent else colors.textTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OwnerAudioMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = colors.iconMuted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontFamily = AppFontFamily,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontFamily = AppFontFamily,
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}
