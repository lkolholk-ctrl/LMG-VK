package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.viewmodel.PlaylistCreationState
import com.lmg.vk.ui.viewmodel.YearRecapBlock
import com.lmg.vk.ui.viewmodel.YearRecapLine
import com.lmg.vk.ui.viewmodel.YearRecapViewModel

/**
 * Экран «Итоги года» — порт фичи VK X (`musicStatResults.getMetrics` и
 * `studio.getArtistYearRecapData`, спека `docs/vkx-port/04-periphery.md` §4-§6).
 *
 * Всё, что видно на экране, приходит из ответа VK: заголовки, значения метрик,
 * подписи, обложки и имя плейлиста. Локальных подсчётов и заготовленных чисел
 * здесь нет вовсе — если VK не дал данных, показывается честный текст, а не
 * нули. Локальная статистика прослушивания живёт отдельно, в [StatsScreen].
 *
 * ОТСТУПЛЕНИЕ ОТ VK X. Оригинал показывает итоги как полноэкранную карусель
 * сторис с фоновым видео и шарингом. Здесь — вертикальный список карточек в
 * стиле Liquid: ровно те же блоки в том же порядке (`order`, `is_visible`), но
 * читаемые без свайпов. Фоновая картинка блока используется как обложка
 * карточки; видео и шаринг не переносились.
 *
 * [artistId] — итоги года для конкретного артиста (метод «Студии» ВКонтакте).
 * Без него грузятся музыкальные метрики самого пользователя.
 */
@Composable
fun YearRecapScreen(
    onBack: () -> Unit = {},
    artistId: String? = null,
) {
    val lc = LiquidTheme.colors
    val compact = rememberWindowInfo().useSideBySide
    val sidePad = if (compact) 24.dp else 20.dp

    val viewModel: YearRecapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(artistId) { viewModel.load(artistId) }

    Box(modifier = Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(12.dp))

            // ── Заголовок ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                        .liquidClickable(pressedScale = LiquidMotion.PressIcon) { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                        contentDescription = null,
                        tint = lc.iconDefault,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))
                Icon(
                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CalendarOutline28,
                    contentDescription = null,
                    tint = lc.iconDefault,
                    modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                )
                Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
                Text(
                    text = "Итоги года",
                    color = lc.textPrimary,
                    fontSize = if (compact) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = VkSansDisplay,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                // Первая загрузка: скелетон вместо пустоты.
                state.isLoading && state.blocks.isEmpty() -> RecapSkeleton(compact, sidePad)

                state.error != null && state.blocks.isEmpty() -> RecapLoadError(
                    message = state.error.orEmpty(),
                    compact = compact,
                    onRetry = viewModel::retry,
                )

                // Ответ пришёл, но VK не дал ни одного блока — так и говорим.
                state.isEmpty -> RecapEmpty(
                    artistMode = !artistId.isNullOrBlank(),
                    compact = compact,
                    onRetry = viewModel::retry,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = sidePad,
                        end = sidePad,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Подсказка VK про аудио — только если она реально пришла.
                    state.audioTooltip.takeIf { it.isNotBlank() }?.let { tooltip ->
                        item(key = "tooltip") {
                            Text(
                                text = tooltip,
                                color = lc.textSecondary,
                                fontSize = if (compact) 12.sp else 13.sp,
                                fontFamily = AppFontFamily,
                            )
                        }
                    }

                    items(state.blocks, key = { it.key }) { block ->
                        RecapBlockCard(block = block, compact = compact)
                    }

                    // Действие пользователя из VK X: собрать плейлист по метрикам.
                    if (state.canCreatePlaylist || state.creation is PlaylistCreationState.Created) {
                        item(key = "create-playlist") {
                            CreatePlaylistCard(
                                title = state.playlistTitle,
                                creation = state.creation,
                                compact = compact,
                                onCreate = viewModel::createPlaylist,
                                onDismissError = viewModel::dismissCreationError,
                            )
                        }
                    }

                    // Заголовки действий VK. Показываем как подписи, а НЕ как
                    // кнопки: перечень значений `type` в исходниках VK X не
                    // найден, и вешать на неизвестную строку действие — значит
                    // угадывать. Единственное подтверждённое действие (создание
                    // плейлиста) реализовано выше отдельно.
                    if (state.actionTitles.isNotEmpty()) {
                        item(key = "actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.actionTitles.forEach { title ->
                                    Text(
                                        text = title,
                                        color = lc.textTertiary,
                                        fontSize = if (compact) 11.sp else 12.sp,
                                        fontFamily = AppFontFamily,
                                    )
                                }
                            }
                        }
                    }

                    // Ошибка обновления, когда данные на экране уже есть.
                    state.error?.let { message ->
                        item(key = "inline-error") {
                            RecapInlineError(message, compact, viewModel::retry)
                        }
                    }
                }
            }
        }
    }
}

/** Карточка одного блока итогов. Рисует только те поля, что пришли от VK. */
@Composable
private fun RecapBlockCard(block: YearRecapBlock, compact: Boolean) {
    val lc = LiquidTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
            .padding(if (compact) 14.dp else 18.dp),
    ) {
        // Обложка блока: фон из ответа либо первая из photo_urls.
        val cover = block.backgroundUrl ?: block.photoUrls.firstOrNull()
        if (!cover.isNullOrBlank()) {
            AlbumArtImage(
                uri = null,
                coverUrl = cover,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (compact) 2.4f else 1.9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        block.titles.forEach { line ->
            RecapHeadline(line, compact)
        }

        if (block.subtitles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            block.subtitles.forEach { line ->
                Text(
                    text = listOf(line.title, line.value, line.caption)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = lc.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontFamily = AppFontFamily,
                )
            }
        }

        if (block.metrics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            block.metrics.forEach { line ->
                RecapMetricRow(line, compact)
            }
        }

        // Несколько фото — горизонтальная лента (в VK X это коллаж слайда).
        if (block.photoUrls.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(block.photoUrls.drop(if (cover == block.photoUrls.firstOrNull()) 1 else 0)) { url ->
                    AlbumArtImage(
                        uri = null,
                        coverUrl = url,
                        modifier = Modifier
                            .size(if (compact) 64.dp else 78.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }

        // Плейлист, предложенный самим блоком.
        val playlistTitle = block.playlistTitle
        if (!playlistTitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val playlistCover = block.playlistPhotoUrl
                if (!playlistCover.isNullOrBlank()) {
                    AlbumArtImage(
                        uri = null,
                        coverUrl = playlistCover,
                        modifier = Modifier
                            .size(if (compact) 40.dp else 48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = playlistTitle,
                    color = lc.textPrimary,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecapHeadline(line: YearRecapLine, compact: Boolean) {
    val lc = LiquidTheme.colors
    Column {
        line.title.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = lc.textPrimary,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (compact) AppFontFamily else VkSansDisplay,
            )
        }
        line.value.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = lc.accent,
                fontSize = if (compact) 22.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = VkSansDisplay,
            )
        }
        line.caption.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = lc.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontFamily = AppFontFamily,
            )
        }
    }
}

/** Строка метрики: слева подпись, справа значение. Обложка — если пришла. */
@Composable
private fun RecapMetricRow(line: YearRecapLine, compact: Boolean) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!line.coverUrl.isNullOrBlank()) {
            val cover = line.coverUrl
            AlbumArtImage(
                uri = null,
                coverUrl = cover,
                modifier = Modifier
                    .size(if (compact) 36.dp else 44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            line.title.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    color = lc.textPrimary,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            line.caption.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    color = lc.textSecondary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        line.value.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = lc.accent,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
            )
        }
    }
}

/**
 * Кнопка «Собрать плейлист» — действие `musicStatResults.createPlaylist`.
 * Имя плейлиста берётся из ответа VK (или дефолт `"My 2025"` самого VK X).
 */
@Composable
private fun CreatePlaylistCard(
    title: String?,
    creation: PlaylistCreationState,
    compact: Boolean,
    onCreate: () -> Unit,
    onDismissError: () -> Unit,
) {
    val lc = LiquidTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
            .padding(if (compact) 14.dp else 18.dp),
    ) {
        Text(
            text = when (creation) {
                is PlaylistCreationState.Created -> "Плейлист готов"
                else -> "Плейлист по итогам года"
            },
            color = lc.textPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
        // Имя — только если VK его прислал; иначе строку просто не рисуем.
        title?.takeIf { it.isNotBlank() }?.let { name ->
            Text(
                text = name,
                color = lc.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontFamily = AppFontFamily,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        when (creation) {
            is PlaylistCreationState.Created -> Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = lmgVector(LmgDrawables.CheckDoubleOutline16),
                    contentDescription = null,
                    tint = lc.accentGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сохранён в вашей музыке ВКонтакте",
                    color = lc.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontFamily = AppFontFamily,
                )
            }

            is PlaylistCreationState.InProgress -> Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = lc.accent,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ВКонтакте собирает плейлист…",
                    color = lc.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontFamily = AppFontFamily,
                )
            }

            is PlaylistCreationState.Failed -> Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = creation.message,
                    color = lc.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontFamily = AppFontFamily,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(lc.accent.copy(alpha = 0.16f))
                        .clickable {
                            onDismissError()
                            onCreate()
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                        contentDescription = null,
                        tint = lc.accent,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Попробовать снова",
                        color = lc.accent,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AppFontFamily,
                    )
                }
            }

            PlaylistCreationState.Idle -> Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(lc.accent.copy(alpha = 0.16f))
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = lmgVector(LmgDrawables.ListPlusOutline20),
                    contentDescription = null,
                    tint = lc.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Собрать плейлист",
                    color = lc.accent,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppFontFamily,
                )
            }
        }
    }
}

/** Скелетон первой загрузки: те же карточки, но без содержимого. */
@Composable
private fun RecapSkeleton(compact: Boolean, sidePad: androidx.compose.ui.unit.Dp) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePad),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 120.dp else 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)),
                contentAlignment = Alignment.Center,
            ) {
                if (it == 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = lc.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

/** VK ответил, но блоков нет. Честный текст вместо пустого экрана. */
@Composable
private fun RecapEmpty(artistMode: Boolean, compact: Boolean, onRetry: () -> Unit) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (artistMode) {
                "ВКонтакте не показывает итоги года для этого артиста"
            } else {
                "ВКонтакте пока не собрал ваши итоги года"
            },
            color = lc.textPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
        )
        Text(
            text = "Итоги появляются, когда их публикует сам ВКонтакте — обычно в конце года.",
            color = lc.textSecondary,
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(lc.accent.copy(alpha = 0.16f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                contentDescription = null,
                tint = lc.accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Проверить снова",
                color = lc.accent,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
            )
        }
    }
}

@Composable
private fun RecapLoadError(message: String, compact: Boolean, onRetry: () -> Unit) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (lc.isDark) Color(0xFF1D1D1F) else Color(0xFFF2F2F7))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = "Не удалось загрузить итоги года",
            color = lc.textPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
        Text(
            text = message,
            color = lc.textSecondary,
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(lc.accent.copy(alpha = 0.16f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                contentDescription = null,
                tint = lc.accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Повторить загрузку",
                color = lc.accent,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
            )
        }
    }
}

@Composable
private fun RecapInlineError(message: String, compact: Boolean, onRetry: () -> Unit) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (lc.isDark) Color(0xFF221B1C) else Color(0xFFFFF1F1))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = lc.textSecondary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontFamily = AppFontFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Повторить",
            color = lc.accent,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
