package com.lmg.vk.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lmg.vk.ui.theme.LiquidTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.VkSansDisplay

/**
 * Общие части экранов-подборок: альбом, плейлист, избранное, история.
 *
 * Живут отдельно намеренно. Пока каждый экран рисовал свою шапку и свою строку
 * трека, они неизбежно расходились в мелочах — то отступ на два пункта другой,
 * то разделитель начинается не оттуда. Здесь одна реализация на всех, и правка
 * применяется сразу везде.
 */

/** Обложки каталога приходят огромными; для экрана это лишний трафик и память. */
fun String?.toDetailThumb(): String? = this
    ?.replace("1000x1000", "600x600")
    ?.replace("1500x1500", "600x600")
    ?.replace("300x300", "600x600")

/**
 * Вид релиза словами для шапки: `single`/`ep`/`album`/`collection` от VK.
 *
 * `null` при неизвестном значении — и это важнее, чем кажется. Раньше в шапке
 * стояло сырое `type` плейлиста, поэтому сингл подписывался «playlist». Врать
 * «Album» по умолчанию тоже нельзя: у сборника и у участия это неверно, лучше
 * не показать строку вовсе.
 */
@Composable
fun releaseTypeLabel(type: String?): String? {
    val value = type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        value == "ep" || value.contains("extended_play") -> "EP"
        value.contains("single") -> stringResource(R.string.release_type_single)
        value.contains("collection") || value.contains("compilation") -> stringResource(R.string.release_type_compilation)
        value.contains("album") -> stringResource(R.string.release_type_album)
        else -> null
    }
}

/**
 * `main_color` VK → [Color]. VK присылает hex БЕЗ решётки (`"1c2a3b"`), иногда с ней.
 *
 * Служит только подложкой на время загрузки обложки: это один усреднённый тон, и
 * подменять им нашу палитру из bitmap нельзя — она точнее.
 */
fun vkMainColor(hex: String?): Color? {
    val cleaned = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 || it.length == 8 } ?: return null
    if (!cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    // 6 символов — без альфы, добавляем непрозрачность сами.
    return Color(if (cleaned.length == 6) (value or 0xFF000000L).toInt() else value.toInt())
}

/**
 * Шапка подборки: обложка во всю ширину, поверх неё название и кнопки.
 *
 * Занимает примерно половину экрана и считается от его высоты, а не задана
 * числом: на маленьком телефоне фиксированная высота съела бы весь первый экран,
 * на большом — выглядела бы полоской.
 *
 * Снизу к ней примыкает верхушка листа контента. Она нарисована здесь же, внутри
 * общего контейнера: если сдвигать лист отдельным элементом списка, уезжает
 * только он, а следующие остаются на месте — между ними появляется пустая полоса.
 */
@Composable
fun DetailHeader(
    title: String,
    subtitle: String,
    facts: List<String>,
    coverUrl: String?,
    isDark: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    canPlay: Boolean = true,
    /**
     * `main_color` от VK. Лежит ПОД обложкой, пока та грузится: иначе полэкрана
     * секунду-две остаётся серым прямоугольником, что читается как ошибка.
     */
    mainColor: Color? = null,
    centeredArtwork: Boolean = false,
    wideArtwork: Boolean = false,
    onSubtitleClick: (() -> Unit)? = null,
) {
    if (centeredArtwork) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 22.dp, end = 22.dp, top = 76.dp, bottom = 4.dp),
            horizontalAlignment = if (wideArtwork) Alignment.Start else Alignment.CenterHorizontally) {
            AlbumArtImage(uri = null, coverUrl = coverUrl, contentDescription = title,
                modifier = (if (wideArtwork) Modifier.fillMaxWidth().aspectRatio(1.48f) else Modifier.size(232.dp)).clip(RoundedCornerShape(13.dp))
                    .background(mainColor ?: LiquidSurfaces.card(isDark)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(22.dp))
            Text(title, color = LiquidSurfaces.textPrimary(isDark), fontFamily = VkSansDisplay,
                fontWeight = FontWeight.Bold, fontSize = if (wideArtwork) 30.sp else 27.sp, lineHeight = 32.sp,
                textAlign = if (wideArtwork) TextAlign.Start else TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = DetailStyle.accent,
                fontSize = 17.sp, textAlign = if (wideArtwork) TextAlign.Start else TextAlign.Center, modifier = Modifier
                    .liquidClickable(enabled = onSubtitleClick != null, onClick = { onSubtitleClick?.invoke() }).padding(vertical = 10.dp))
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = LiquidSurfaces.textSecondary(isDark),
                fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailActionButton(stringResource(R.string.action_play), com.lmg.vk.ui.icons.LmgGlyphs.Play28,
                    true, isDark, enabled = canPlay, onClick = onPlay)
                DetailActionButton(stringResource(R.string.action_shuffle), com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28,
                    false, isDark, enabled = canPlay, onClick = onShuffle)
            }
        }
        return
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val headerHeight = (screenHeight * 0.52f).coerceIn(360.dp, 560.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                if (mainColor != null) {
                    Box(modifier = Modifier.fillMaxSize().background(mainColor))
                }
                AlbumArtImage(
                    uri = null,
                    coverUrl = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Затемнение снизу: белый текст поверх светлой обложки иначе
                // нечитаем, а обложки бывают любые.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.15f),
                                1f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = LiquidMetrics.ScreenPadding,
                        end = LiquidMetrics.ScreenPadding,
                        // Ровно столько, чтобы кнопки не ушли под край листа.
                        bottom = LiquidMetrics.SheetOverlap + 8.dp
                    )
            ) {
                Text(
                    text = title,
                    color = LiquidSurfaces.onHeaderPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = VkSansDisplay,
                    letterSpacing = (-1.2).sp,
                    lineHeight = 36.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontSize = LiquidMetrics.HeaderCaption,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString(" · "),
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailActionButton(
                        label = stringResource(R.string.action_play),
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.Play28,
                        filled = true,
                        isDark = isDark,
                        onPhoto = true,
                        enabled = canPlay,
                        onClick = onPlay
                    )
                    DetailActionButton(
                        label = stringResource(R.string.action_shuffle),
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28,
                        filled = false,
                        isDark = isDark,
                        onPhoto = true,
                        enabled = canPlay,
                        onClick = onShuffle
                    )
                }
            }
        }

        DetailSheetTop(isDark = isDark, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Верхушка листа контента: наезжает на шапку и скруглена сверху. */
@Composable
fun DetailSheetTop(isDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LiquidMetrics.SheetShape)
            .background(LiquidSurfaces.sheet(isDark))
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(LiquidSurfaces.grabber(isDark))
        )
    }
}

@Composable
fun RowScope.DetailActionButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    isDark: Boolean,
    onPhoto: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = if (filled) DetailStyle.accent else DetailStyle.surface(isDark)
    val contentColor = if (filled) Color.White else LiquidSurfaces.textPrimary(isDark)
    val outline = if (filled) Color.White.copy(alpha = .28f) else if (isDark) Color(0xFF4B4B4F) else Color(0xFFD0D0D5)

    Row(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, outline, CircleShape)
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 15.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Строка трека.
 *
 * @param coverUrl если задана — вместо номера показывается обложка. У альбома
 *   обложка одна на всех, там нужнее номер; в плейлисте и истории треки разные,
 *   и обложка помогает узнать вещь быстрее номера.
 */
@Composable
fun DetailTrackRow(
    position: Int,
    title: String,
    subtitle: String?,
    durationMs: Long,
    coverUrl: String?,
    isDark: Boolean,
    showDivider: Boolean,
    showArtwork: Boolean = true,
    enabled: Boolean = true,
    onMore: (() -> Unit)? = null,
    onMorePosition: ((Rect) -> Unit)? = null,
    onClick: () -> Unit
) {
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    Column(modifier = Modifier.padding(horizontal = DetailStyle.padding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.42f)
                .clip(RoundedCornerShape(6.dp))
                .liquidClickable(enabled = enabled, pressedScale = LiquidMotion.PressButton, onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showArtwork) {
                AlbumArtImage(
                    uri = null,
                    coverUrl = coverUrl,
                    artworkQuery = com.lmg.vk.artwork.ArtworkQuery(title, subtitle.orEmpty(), durationMs),
                    contentDescription = title,
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(
                            elevation = LiquidMetrics.CoverElevation,
                            shape = LiquidMetrics.CoverShapeSmall,
                            ambientColor = LiquidSurfaces.shadowTint(isDark),
                            spotColor = LiquidSurfaces.shadowTint(isDark)
                        )
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(11.dp))
            } else {
                Text(
                    text = "$position",
                    color = LiquidSurfaces.textTertiary(isDark),
                    fontSize = LiquidMetrics.LinkLabel,
                    modifier = Modifier.width(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) title else stringResource(R.string.track_unavailable_suffix, title),
                    color = LiquidSurfaces.textPrimary(isDark),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = LiquidSurfaces.textSecondary(isDark),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (durationMs > 0) {
                Text(
                    text = formatTrackDuration(durationMs),
                    color = LiquidSurfaces.textTertiary(isDark),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (onMore != null) {
                IconButton(
                    enabled = enabled,
                    onClick = { onMorePosition?.invoke(moreBounds); onMore() },
                    modifier = Modifier.size(44.dp).onGloballyPositioned { moreBounds = it.boundsInWindow() },
                ) {
                    Icon(
                        com.lmg.vk.ui.icons.LmgGlyphs.MoreHorizontal28,
                        contentDescription = stringResource(R.string.track_actions),
                        tint = LiquidSurfaces.textTertiary(isDark),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (showDivider) {
            // Разделитель начинается под текстом, а не под номером или обложкой:
            // так список читается колонкой, а не решёткой.
            Box(
                modifier = Modifier
                    .padding(start = if (showArtwork) 55.dp else 22.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LiquidSurfaces.divider(isDark))
            )
        }
    }
}

/**
 * Верхняя панель: кнопка возврата всегда, название — только после прокрутки.
 * Пока шапка видна целиком, дублировать её название в панели незачем.
 */
@Composable
fun DetailTopBar(
    title: String,
    showTitle: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    idleTitle: String = "",
    titleContent: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()
        .background(if (showTitle) DetailStyle.background(isDark) else Color.Transparent)
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 44.dp)) {
        DetailCircleButton(com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
            stringResource(R.string.action_back), modifier = Modifier.align(Alignment.CenterStart), onClick = onBack)
        Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 56.dp),
            contentAlignment = Alignment.Center) {
            if (showTitle && titleContent != null) {
                titleContent()
            } else {
                Text(if (showTitle) title else idleTitle, color = DetailStyle.text(isDark),
                    fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

fun formatTrackDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun formatTotalDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes)
    else stringResource(R.string.duration_minutes, minutes)
}
