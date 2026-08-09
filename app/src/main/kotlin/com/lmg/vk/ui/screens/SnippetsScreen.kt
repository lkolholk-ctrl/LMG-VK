package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.network.dto.music.SnippetPageUi
import com.lmg.vk.network.dto.music.SnippetTrackUi
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.viewmodel.SnippetsViewModel
import kotlinx.coroutines.flow.drop

/**
 * Полноэкранная лента сниппетов VK (`audio.getSnippets`).
 *
 * ── Устройство по VK X (`C1718e`) ────────────────────────────────────
 * Это не виджет в списке, а отдельный фрагмент с ДВУМЯ пейджерами:
 *  - внешний вертикальный — по подборкам (`AudioSnippetEntry`);
 *  - внутренний горизонтальный — по трекам подборки (`entry.audios`).
 * Во `C1718e` под это заведены два независимых индекса-состояния (`C16330e`),
 * а `C13721e` на смене ЛЮБОГО из них пересобирает источник и стартует плеер.
 * Та же схема здесь: один [VerticalPager], внутри — [HorizontalPager].
 *
 * Ориентацию внешнего пейджера по декомпилированному коду подтвердить не
 * удалось (composable-обёртки VK X обфусцированы до `AbstractC0865e.ad`,
 * констант ориентации там не видно). Вертикальный внешний + горизонтальный
 * внутренний взяты по постановке задачи и по TikTok-подобной логике самой
 * ленты; это ЕДИНСТВЕННОЕ место, где я не смог опереться на реверс.
 *
 * ── Про обрезанное воспроизведение ───────────────────────────────────
 * Фрагмент режет СЕРВЕР: `audio.url` в этой выдаче уже короткий, а его длина
 * лежит в `stream_duration`. Полей `clip_from`/`clip_to` у сниппета нет (они от
 * другого метода), и VK X ничего не обрезает на клиенте — `ClippingConfiguration`
 * в нём не встречается вообще. Разбор — в `dto/music/SnippetsFeed.kt`.
 */
@Composable
fun SnippetsScreen(
    onBack: () -> Unit,
    viewModel: SnippetsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.load() }

    val lc = LiquidTheme.colors
    val win = rememberWindowInfo()
    val compact = win.useSideBySide

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            // Первая загрузка: ленты ещё нет — показываем спиннер, а не пустоту.
            state.isLoading && state.pages.isEmpty() -> SnippetsLoading()

            state.error != null && state.pages.isEmpty() -> SnippetsError(
                message = state.error.orEmpty(),
                compact = compact,
                onRetry = { viewModel.load(force = true) },
            )

            // Пустой ответ — это не ошибка: говорим честно и даём повторить.
            state.isEmpty -> SnippetsEmpty(
                compact = compact,
                onRetry = { viewModel.load(force = true) },
            )

            else -> SnippetsPager(
                pages = state.pages,
                compact = compact,
                onPlay = { track ->
                    PlayerController.playSnippet(
                        context = context,
                        trackId = track.fullId,
                        streamUrl = track.directUrl,
                        title = track.title,
                        artist = track.artist,
                        coverUrl = track.coverUrl,
                        durationMs = track.effectiveDurationMs,
                    )
                },
                onToggle = { PlayerController.togglePlayPause(context) },
            )
        }

        // Кнопка «назад» поверх контента: фид занимает весь экран, системного
        // топ-бара тут нет.
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, top = 8.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Тонкая полоса при обновлении уже показанной ленты — экран остаётся
        // полезным, как в NewScreen.
        if (state.isLoading && state.pages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .fillMaxWidth(0.34f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(lc.accent),
            )
        }
    }
}

/**
 * Внешний вертикальный пейджер по подборкам + внутренний по трекам.
 *
 * Автостарта воспроизведения на листании НЕТ, в отличие от VK X (там `C13721e`
 * стартует плеер сам на каждой смене пары индексов). Отступление осознанное:
 * у нас лента открывается из вкладки New поверх уже играющей музыки, и
 * самовольный перехват плеера на каждый свайп ломал бы текущую очередь
 * пользователя. Поэтому фрагмент запускается по явному тапу; при уходе со
 * страницы играющий сниппет останавливается (ниже).
 */
@Composable
private fun SnippetsPager(
    pages: List<SnippetPageUi>,
    compact: Boolean,
    onPlay: (SnippetTrackUi) -> Unit,
    onToggle: () -> Unit,
) {
    val outerState = rememberPagerState(pageCount = { pages.size })
    val context = LocalContext.current

    // Все id ленты — чтобы глушить ТОЛЬКО свой сниппет.
    val feedTrackIds = remember(pages) {
        pages.flatMapTo(HashSet()) { page -> page.tracks.map { it.fullId } }
    }

    // Ушли с подборки — глушим играющий сниппет: он короткий и «догоняет» уже
    // другую страницу. Два обязательных условия:
    //  - `drop(1)`: snapshotFlow отдаёт текущую страницу сразу при входе, и без
    //    этого открытие ленты ставило бы на паузу постороннюю музыку;
    //  - проверка по [feedTrackIds]: чужую очередь пользователя (волна, альбом)
    //    лента трогать не имеет права — глушим только то, что started сама.
    LaunchedEffect(outerState, feedTrackIds) {
        snapshotFlow { outerState.currentPage }
            .drop(1)
            .collect {
                if (PlayerController.currentTrack.value?.id in feedTrackIds) {
                    PlayerController.pause(context)
                }
            }
    }

    VerticalPager(
        state = outerState,
        modifier = Modifier.fillMaxSize(),
    ) { pageIndex ->
        val page = pages[pageIndex]
        val innerState = rememberPagerState(pageCount = { page.tracks.size })

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = innerState,
                modifier = Modifier.fillMaxSize(),
            ) { trackIndex ->
                SnippetCard(
                    page = page,
                    track = page.tracks[trackIndex],
                    trackNumber = trackIndex + 1,
                    trackCount = page.tracks.size,
                    compact = compact,
                    onPlay = onPlay,
                    onToggle = onToggle,
                )
            }
        }
    }
}

/** Одна карточка фида: обложка на весь экран, поверх — подводка VK и трек. */
@Composable
private fun SnippetCard(
    page: SnippetPageUi,
    track: SnippetTrackUi,
    trackNumber: Int,
    trackCount: Int,
    compact: Boolean,
    onPlay: (SnippetTrackUi) -> Unit,
    onToggle: () -> Unit,
) {
    val currentTrack by PlayerController.currentTrack.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val isBuffering by PlayerController.isBuffering.collectAsState()
    val positionMs by PlayerController.currentPositionMs.collectAsState()

    // «Этот сниппет активен» — сравнение по id трека, а не по факту плейбека:
    // иначе на паузе карточка теряла бы прогресс.
    val isActive = currentTrack?.id == track.fullId
    val isActivePlaying = isActive && isPlaying

    Box(modifier = Modifier.fillMaxSize()) {
        // Фон — обложка трека; постер подборки (`image`) идёт в резерв, если у
        // трека обложки нет.
        AlbumArtImage(
            uri = null,
            contentDescription = track.title,
            coverUrl = track.coverUrl ?: page.imageUrl,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Затемнение снизу — без него белый текст на светлой обложке не читается.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            page.title.takeIf(String::isNotBlank)?.let { title ->
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            page.text.takeIf(String::isNotBlank)?.let { text ->
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 46.dp else 54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable {
                            // Первый тап по чужому/новому сниппету — старт,
                            // повторный по активному — пауза/продолжение.
                            if (isActive) onToggle() else onPlay(track)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isActive && isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (isActivePlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isActivePlaying) "Пауза" else "Слушать фрагмент",
                            tint = Color.White,
                            modifier = Modifier.size(if (compact) 22.dp else 26.dp),
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = if (compact) 17.sp else 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (track.isExplicit) {
                            Text(
                                text = "18+",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontFamily = AppFontFamily,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    track.artist.takeIf(String::isNotBlank)?.let { artist ->
                        Text(
                            text = artist,
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = if (compact) 13.sp else 15.sp,
                            fontFamily = AppFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            // Прогресс считаем по ДЛИНЕ ФРАГМЕНТА (`stream_duration`), а не по
            // полной длительности трека: иначе полоса почти не двигалась бы.
            val total = track.effectiveDurationMs
            val progress = if (isActive && total > 0L) {
                (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White),
                )
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // Подпись честная: «фрагмент» пишем ТОЛЬКО когда VK реально
                    // урезал поток, иначе это был бы обман.
                    text = if (track.isClipped) {
                        "Фрагмент · ${formatSnippetTime(track.effectiveDurationMs)}"
                    } else {
                        formatSnippetTime(track.effectiveDurationMs)
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = AppFontFamily,
                )
                Spacer(Modifier.weight(1f))
                if (trackCount > 1) {
                    Text(
                        text = "$trackNumber / $trackCount",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = AppFontFamily,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SnippetsLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SnippetsError(message: String, compact: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Не удалось загрузить сниппеты",
            color = Color.White,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.padding(top = 6.dp),
        )
        SnippetsRetryButton(onRetry)
    }
}

@Composable
private fun SnippetsEmpty(compact: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "VK пока не прислал сниппеты",
            color = Color.White,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
        Text(
            text = "Метод audio.getSnippets ответил пустым списком",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.padding(top = 6.dp),
        )
        SnippetsRetryButton(onRetry)
    }
}

@Composable
private fun SnippetsRetryButton(onRetry: () -> Unit) {
    Text(
        text = "Повторить",
        color = LiquidTheme.colors.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = AppFontFamily,
        modifier = Modifier
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onRetry)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** мм:сс для длительности фрагмента. */
private fun formatSnippetTime(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
