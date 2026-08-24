package com.lmg.vk.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.audio.TrackDownloadManager
import com.lmg.vk.audio.TrackDownloadState
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.screens.DownloadsRegistryBridge
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

/**
 * Контекст-меню трека (долгий тап по строке): играть следующим, в конец
 * очереди, поделиться. Стиль — карточки как в настройках (cardSurface).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onCache: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LiquidTheme.colors.isDark
    val sheetBg = if (isDark) Color(0xFF141416) else Color.White
    val rowBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

    // ── Состояние скачивания этого трека ──
    // Прогресс — из менеджера (он один знает байты), факт «уже скачан» — из
    // реестра. Два разных источника специально: загрузка может идти, а записи в
    // реестре ещё нет, и наоборот — файл лежит, а менеджер про него забыл после
    // перезапуска процесса.
    val downloadStates by TrackDownloadManager.states.collectAsState()
    val downloadState = downloadStates[track.id]
    val isDownloadedFlow = remember(context, track.id) {
        DownloadsRegistryBridge.isDownloadedFlow(context, track.id)
    }
    val isDownloaded by isDownloadedFlow.collectAsState(initial = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        dragHandle = null
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            // ── Шапка: обложка + название + артист ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArtImage(
                    uri = track.albumArtUri,
                    contentDescription = null,
                    coverUrl = track.coverUrl,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(rowBg)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = LiquidTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = LiquidTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ActionRow(rowBg, com.lmg.vk.ui.icons.LmgGlyphs.PlayNextOutline24, stringResource(R.string.action_play_next)) {
                PlayerController.insertNext(track)
                onDismiss()
            }
            Spacer(Modifier.height(8.dp))
            ActionRow(rowBg, lmgVector(LmgDrawables.ListInsertLastOutline28), stringResource(R.string.action_add_to_queue)) {
                PlayerController.addToQueue(track)
                onDismiss()
            }
            Spacer(Modifier.height(8.dp))
            if (onAddToPlaylist != null) {
                ActionRow(rowBg, lmgVector(LmgDrawables.ListPlusOutline20), stringResource(R.string.add_to_playlist)) {
                    onAddToPlaylist()
                    onDismiss()
                }
                Spacer(Modifier.height(8.dp))
            }
            if (onMoveUp != null) {
                ActionRow(rowBg, lmgVector(LmgDrawables.ArrowUpOutline24), stringResource(R.string.action_move_up)) {
                    onMoveUp()
                    onDismiss()
                }
                Spacer(Modifier.height(8.dp))
            }
            if (onMoveDown != null) {
                ActionRow(rowBg, lmgVector(LmgDrawables.ChevronDown24), stringResource(R.string.action_move_down)) {
                    onMoveDown()
                    onDismiss()
                }
                Spacer(Modifier.height(8.dp))
            }
            if (onRemoveFromPlaylist != null) {
                ActionRow(rowBg, lmgVector(LmgDrawables.ListDeleteOutline20), stringResource(R.string.action_remove_from_playlist)) {
                    onRemoveFromPlaylist()
                    onDismiss()
                }
                Spacer(Modifier.height(8.dp))
            }
            if (isFavorite != null && onToggleFavorite != null) {
                ActionRow(
                    rowBg,
                    if (isFavorite) com.lmg.vk.ui.icons.LmgGlyphs.Favorite28
                    else lmgVector(LmgDrawables.FavoriteAddOutline28),
                    stringResource(if (isFavorite) R.string.action_remove_from_my_tracks else R.string.action_add_to_my_tracks),
                ) {
                    onToggleFavorite()
                    onDismiss()
                }
                Spacer(Modifier.height(8.dp))
            }
            if (onCache != null) {
                ActionRow(rowBg, com.lmg.vk.ui.icons.LmgGlyphs.BookmarkOutline28, stringResource(R.string.action_cache_track)) {
                    onCache()
                    onDismiss()
                }
                Spacer(Modifier.height(8.dp))
            }
            // ── Скачивание файла на устройство ──
            // Лист НЕ закрываем: пользователь должен видеть, что процесс пошёл, и
            // иметь возможность отменить его тут же, не открывая меню заново.
            when (val st = downloadState) {
                is TrackDownloadState.Running -> {
                    DownloadProgressRow(
                        bg = rowBg,
                        percent = st.percent,
                        bytes = st.bytes,
                        totalBytes = st.totalBytes,
                        onCancel = { TrackDownloadManager.cancel(track.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                is TrackDownloadState.Failed -> {
                    // Причину показываем в самой строке: «не удалось» без причины
                    // заставляет тыкать наугад.
                    ActionRow(rowBg, lmgVector(LmgDrawables.DownloadCrossBadgeOutline24), stringResource(R.string.action_download_failed_retry), subtitle = st.message) {
                        TrackDownloadManager.enqueue(context, track)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                else -> {
                    // Источник истины про «лежит на устройстве» — реестр, а НЕ
                    // TrackDownloadState.Done: Done остаётся в states и после того,
                    // как файл удалили, и строка «Downloaded» висела бы враньём.
                    if (isDownloaded) {
                        ActionRow(rowBg, com.lmg.vk.ui.icons.LmgGlyphs.DownloadCheckOutline28, stringResource(R.string.action_downloaded_remove)) {
                            scope.launch { DownloadsRegistryBridge.remove(context, track.id) }
                        }
                    } else {
                        ActionRow(rowBg, com.lmg.vk.ui.icons.LmgGlyphs.DownloadOutline28, stringResource(R.string.action_download)) {
                            TrackDownloadManager.enqueue(context, track)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            ActionRow(rowBg, com.lmg.vk.ui.icons.LmgGlyphs.ShareOutline28, stringResource(R.string.action_share)) {
                val vkUrl = com.lmg.vk.engine.VkAudioIdentity.shareUrl(track.id)
                val text = buildString {
                    append("${track.title} — ${track.artist}")
                    if (vkUrl != null) append("\n").append(vkUrl)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, track.title))
                onDismiss()
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionRow(
    bg: Color,
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))   // строки-пилюли, как в настройках
            .background(bg)
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LiquidTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = LiquidTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            // Вторая строка нужна только для причины ошибки, поэтому она
            // опциональна — иначе у всех пилюль поехала бы вертикальная метрика.
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = LiquidTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Строка идущей загрузки: процент, мегабайты и крестик отмены. Отдельный
 * composable, а не ActionRow с флагом: тут два независимых клика (по строке
 * ничего не происходит, отмена — только по крестику), и полоса прогресса, из-за
 * которой высота отличается от пилюли.
 */
@Composable
private fun DownloadProgressRow(
    bg: Color,
    percent: Int,
    bytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.DownloadCancelOutline28,
            contentDescription = null,
            tint = lc.accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(stringResource(R.string.download_progress_percent, percent.coerceIn(0, 100)))
                    // Байты показываем только если сервер отдал Content-Length:
                    // «0.0 MB of 0.0 MB» выглядит как поломка.
                    if (totalBytes > 0L) {
                        append(
                            stringResource(
                                R.string.download_progress_bytes,
                                "%.1f".format(bytes / 1_048_576.0),
                                "%.1f".format(totalBytes / 1_048_576.0)
                            )
                        )
                    }
                },
                color = lc.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { percent.coerceIn(0, 100) / 100f },
                color = lc.accent,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                contentDescription = stringResource(R.string.action_cancel_download),
                tint = lc.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
