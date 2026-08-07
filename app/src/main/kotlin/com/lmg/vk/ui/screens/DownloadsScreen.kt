package com.lmg.vk.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.audio.TrackDownloadManager
import com.lmg.vk.audio.TrackDownloadState
import com.lmg.vk.data.local.PublicDownloads
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Строка экрана «Загрузки». Своя модель, а не Room-entity, специально: UI не
 * должен знать, из какой таблицы/реестра пришли данные — иначе при смене
 * реестра пришлось бы править всю разметку, а не одно место.
 */
internal data class DownloadedItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val albumName: String,
    /** content:// (публичные Загрузки) либо легаси-путь файла. */
    val fileUri: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val coverUrl: String?
)

/**
 * ЕДИНСТВЕННАЯ точка связи UI загрузок с хранилищем скачанного.
 *
 * Читает таблицу `downloaded_tracks` ([FavoriteTrackDatabase]) — тот самый
 * реестр, который заполняет [com.lmg.vk.engine.AudioDownloadManager]. Отдельного
 * второго реестра сознательно нет: скачивание в проекте одно, и два списка
 * скачанного неизбежно разошлись бы между собой.
 */
internal object DownloadsRegistryBridge {

    /** Реактивный список скачанного. Размер файла считается на IO: это stat по
     *  content://-дескриптору, на main-потоке такое дало бы фризы на сотнях строк. */
    fun observe(context: Context): Flow<List<DownloadedItem>> = flow {
        val db = FavoriteTrackDatabase.getInstance(context)
        emitAll(
            db.downloadsFlow.map { rows ->
                rows.map { row ->
                    DownloadedItem(
                        trackId = row.trackId,
                        title = row.title,
                        artist = row.artistName.orEmpty(),
                        albumName = row.albumTitle.orEmpty(),
                        fileUri = row.localPath,
                        sizeBytes = PublicDownloads.sizeBytes(context, row.localPath),
                        durationMs = row.durationMs,
                        coverUrl = row.localCoverPath ?: row.imageUrl
                    )
                }
            }
        )
    }.flowOn(Dispatchers.IO)

    /** Скачан ли трек — для пункта меню. Поток, чтобы меню перерисовалось само,
     *  когда загрузка завершится, пока лист открыт. */
    fun isDownloadedFlow(context: Context, trackId: String): Flow<Boolean> = flow {
        emitAll(FavoriteTrackDatabase.getInstance(context).isDownloadedFlow(trackId))
    }.flowOn(Dispatchers.IO)

    /** Удалить трек из скачанных ВМЕСТЕ с файлом: запись без файла — мусор,
     *  который потом выглядит как «скачано», но не играет. */
    suspend fun remove(context: Context, trackId: String) = withContext(Dispatchers.IO) {
        com.lmg.vk.engine.AudioDownloadManager.deleteDownloadedTrack(context, trackId)
    }

    /** Снести всё скачанное. Отдельно от [remove], потому что удаление тысячи
     *  файлов по одному через UI-колбэки — гарантированный ANR. */
    suspend fun removeAll(context: Context) {
        com.lmg.vk.engine.AudioDownloadManager.clearAllDownloads(context)
    }
}

/**
 * Экран «Загрузки»: что реально лежит на устройстве. Показывает название,
 * артиста, размер файла, суммарный объём и позволяет удалить трек.
 *
 * Пустой список — честная надпись, а не выдуманные строки: пользователь должен
 * видеть, что скачанного нет, а не думать, что экран сломан.
 */
@Composable
fun DownloadsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lc = LiquidTheme.colors

    // Адаптив как в остальных экранах: в широком окне строки компактнее, иначе
    // портретные размеры выглядят непропорционально крупными.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val compact = win.useSideBySide

    // remember по context: пересоздавать поток на каждую рекомпозицию — значит
    // заново открывать БД и пересчитывать размеры файлов.
    val downloadsFlow = remember(context) { DownloadsRegistryBridge.observe(context) }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())

    // Активные загрузки берём из менеджера — единственного, кто знает прогресс.
    val downloadStates by TrackDownloadManager.states.collectAsState()
    val running = downloadStates.values.filterIsInstance<TrackDownloadState.Running>()

    var itemToDelete by remember { mutableStateOf<DownloadedItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val dialogShown = itemToDelete != null || confirmClearAll

    val totalBytes = downloads.sumOf { it.sizeBytes }

    Box(modifier = Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Диалог поверх размытого контента — тот же приём, что в Библиотеке.
                .then(if (dialogShown) Modifier.blur(16.dp) else Modifier)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(12.dp))

            // ── Шапка ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                        .liquidClickable(pressedScale = LiquidMotion.PressIcon) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = lc.iconDefault,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                    )
                }
                Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Downloads",
                        color = lc.textPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = if (compact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Подзаголовок печатаем только когда есть что печатать: строка
                    // «0 треков · 0 МБ» — это шум, а не информация.
                    if (downloads.isNotEmpty()) {
                        Text(
                            text = buildString {
                                append(downloadsCountLabel(downloads.size))
                                formatDownloadSize(totalBytes)?.let { append(" · ").append(it) }
                            },
                            color = lc.textSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = if (compact) 12.sp else 13.sp
                        )
                    }
                }
                // «Clear all» — только когда есть что чистить; кнопка над пустым
                // списком лишь путала бы.
                if (downloads.isNotEmpty()) {
                    Text(
                        text = "Clear all",
                        color = lc.accentRed,
                        fontFamily = AppFontFamily,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .liquidClickable(pressedScale = LiquidMotion.PressButton) { confirmClearAll = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Прогресс одноразового переноса скачанного в публичные Загрузки.
            // Пока он идёт, список может быть неполным — без этой строки экран
            // выглядел бы просто «потерявшим» треки.
            val migration by com.lmg.vk.data.local.DownloadsMigrator.progress.collectAsState()
            migration?.let { (done, total) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(lc.cardSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Moving downloads to Downloads… $done/$total",
                        color = lc.textSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        color = lc.accent,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Идущие загрузки ──
            // Названий у менеджера нет (в states только id), поэтому показываем
            // честную сводку, а не подставляем чужие заголовки.
            if (running.isNotEmpty()) {
                val avg = running.sumOf { it.percent } / running.size
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(lc.cardSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Downloading ${running.size} · $avg%",
                        color = lc.textPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { avg / 100f },
                        color = lc.accent,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = lc.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Nothing downloaded yet",
                            color = lc.textSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Download a track from its menu",
                            color = lc.textTertiary,
                            fontFamily = AppFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Снизу оставляем место под мини-плеер и бар, как на остальных
                    // экранах, иначе последняя строка уезжает под них.
                    contentPadding = PaddingValues(
                        start = if (compact) 24.dp else 20.dp,
                        end = if (compact) 24.dp else 20.dp,
                        bottom = 178.dp
                    )
                ) {
                    items(downloads, key = { it.trackId }) { item ->
                        DownloadedRow(
                            item = item,
                            compact = compact,
                            onClick = { playFromDownloads(context, downloads, item) },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }

        // ── Подтверждение удаления (вне размытого контента) ──
        itemToDelete?.let { target ->
            GlassDialog(
                visible = true,
                onDismiss = { itemToDelete = null },
                title = "Remove download?",
                message = "\"${target.title}\" will be deleted from this device together with its file.",
                icon = Icons.Rounded.Delete,
                iconTint = lc.accentRed,
                primaryButton = GlassDialogButton(
                    text = "Delete",
                    onClick = {
                        itemToDelete = null
                        scope.launch { DownloadsRegistryBridge.remove(context, target.trackId) }
                    },
                    backgroundColor = lc.accentRed,
                    textColor = Color.White
                ),
                secondaryButton = GlassDialogButton(
                    text = "Cancel",
                    onClick = { itemToDelete = null },
                    backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                    textColor = lc.textSecondary
                )
            )
        }

        if (confirmClearAll) {
            GlassDialog(
                visible = true,
                onDismiss = { confirmClearAll = false },
                title = "Clear all downloads?",
                message = buildString {
                    append(downloadsCountLabel(downloads.size))
                    formatDownloadSize(totalBytes)?.let { append(" (").append(it).append(')') }
                    append(" will be deleted from this device. This cannot be undone.")
                },
                icon = Icons.Rounded.Delete,
                iconTint = lc.accentRed,
                primaryButton = GlassDialogButton(
                    text = "Clear all",
                    onClick = {
                        confirmClearAll = false
                        scope.launch { DownloadsRegistryBridge.removeAll(context) }
                    },
                    backgroundColor = lc.accentRed,
                    textColor = Color.White
                ),
                secondaryButton = GlassDialogButton(
                    text = "Cancel",
                    onClick = { confirmClearAll = false },
                    backgroundColor = lc.textPrimary.copy(alpha = 0.08f),
                    textColor = lc.textSecondary
                )
            )
        }
    }
}

/**
 * Играем скачанное как ЛОКАЛЬНУЮ очередь (playLocalOnJuce + Downloads-контекст):
 * иначе движок счёл бы треки онлайновыми и полез разрешать стрим-URL — то есть
 * в сеть за тем, что уже лежит на диске.
 */
private fun playFromDownloads(
    context: Context,
    all: List<DownloadedItem>,
    target: DownloadedItem
) {
    val tracks = all.map { item ->
        Track(
            id = item.trackId,
            title = item.title,
            artist = item.artist,
            albumName = item.albumName,
            uri = PublicDownloads.toPlayableUri(item.fileUri),
            durationMs = item.durationMs,
            albumId = item.albumName.takeIf { it.isNotBlank() }?.hashCode()?.toLong() ?: -1L,
            coverUrl = item.coverUrl
        )
    }
    val index = tracks.indexOfFirst { it.id == target.trackId }
    if (index >= 0) {
        PlayerController.playLocalOnJuce(
            context = context,
            tracks = tracks,
            startIndex = index,
            playbackContext = PlaybackContext.Downloads
        )
    }
}

@Composable
private fun DownloadedRow(
    item: DownloadedItem,
    compact: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(vertical = if (compact) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtImage(
            uri = null,
            coverUrl = item.coverUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            // placeholderKey — чтобы у трека без обложки цвет заглушки был
            // стабильным между перерисовками; разделитель полей — как в TrackActionsSheet.
            placeholderKey = "${item.trackId}\u0000${item.title}\u0000${item.artist}",
            modifier = Modifier
                .size(if (compact) 40.dp else 52.dp)
                .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
        )
        Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = lc.textPrimary,
                fontFamily = AppFontFamily,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(item.artist)
                    formatDownloadSize(item.sizeBytes)?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                },
                color = lc.textSecondary,
                fontFamily = AppFontFamily,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 36.dp)
                .clip(CircleShape)
                .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete download",
                tint = lc.textTertiary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }
    }
}

/**
 * Размер по-человечески: МБ с одним знаком, гигабайты — отдельно, иначе
 * «1536,0 МБ» читается хуже, чем «1,5 ГБ». null — размер неизвестен (файла нет
 * или дескриптор не открылся): лучше не показать ничего, чем показать «0 МБ».
 */
private fun formatDownloadSize(bytes: Long): String? = when {
    bytes <= 0L -> null
    bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f GB".format(bytes / 1_073_741_824.0)
}

/** Единственное/множественное число — иначе в шапке висело бы «1 tracks». */
private fun downloadsCountLabel(count: Int): String =
    if (count == 1) "1 track" else "$count tracks"
