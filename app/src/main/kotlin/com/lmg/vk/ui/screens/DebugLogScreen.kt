package com.lmg.vk.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.lmg.vk.R
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * Экран отладочного лога [DebugLog].
 *
 * ЗАЧЕМ: воспроизведение ломается на телефоне, где нет `adb logcat`, поэтому
 * единственный способ узнать, на каком шаге всё встаёт — показать буфер прямо в
 * приложении и дать его СКОПИРОВАТЬ/ОТПРАВИТЬ. Отсюда приоритеты: читаемость и
 * кнопки экспорта, а не оформление.
 *
 * Порядок строк — хронологический (новые СНИЗУ), как в logcat: причину ищут от
 * начала попытки к месту обрыва, и читать снизу вверх при таком разборе неудобно.
 * Автопрокрутка к свежим строкам работает, но только пока пользователь сам не
 * увёл список вверх — иначе экран выдёргивал бы его из места, которое он читает.
 */
@Composable
fun DebugLogScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lc = LiquidTheme.colors
    val compact = rememberWindowInfo().useSideBySide

    // Опрос буфера вместо подписки: DebugLog зовут из движка и сети десятки раз
    // в секунду, и StateFlow на каждую строку заставлял бы Compose перерисовывать
    // список чаще, чем человек способен читать. Раз в 500 мс достаточно.
    var lines by remember { mutableStateOf(DebugLog.snapshot()) }
    var revision by remember { mutableStateOf(DebugLog.revision()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            val rev = DebugLog.revision()
            // Сначала сравниваем счётчик: snapshot() копирует до 2000 строк, и
            // делать это на каждом тике «просто на случай» — зря греть телефон.
            if (rev != revision) {
                revision = rev
                lines = DebugLog.snapshot()
            }
        }
    }

    var filter by remember { mutableStateOf("") }
    var filterVisible by remember { mutableStateOf(false) }

    // Фильтр — по подстроке, без регистра: искать «error» и «Error» по отдельности
    // при разборе лога бессмысленно.
    val visible = remember(lines, filter) {
        if (filter.isBlank()) lines
        else lines.filter { it.contains(filter, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                        contentDescription = stringResource(R.string.action_back),
                        tint = lc.iconDefault,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                    )
                }
                Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_log_title),
                        color = lc.textPrimary,
                        fontFamily = VkSansDisplay,
                        fontSize = if (compact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Счётчик показывает, что лог ЖИВОЙ и пополняется: без него
                    // непонятно, приложение молчит или экран не обновляется.
                    Text(
                        text = if (filter.isBlank()) {
                            "${lines.size} строк"
                        } else {
                            "${visible.size} из ${lines.size} строк"
                        },
                        color = lc.textSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = if (compact) 12.sp else 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (compact) 30.dp else 36.dp)
                        .clip(CircleShape)
                        .liquidClickable(pressedScale = LiquidMotion.PressIcon) {
                            filterVisible = !filterVisible
                            // Скрыли поле — снимаем и фильтр: иначе список остался
                            // бы урезанным без всякого видимого объяснения.
                            if (!filterVisible) filter = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (filterVisible) com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28 else com.lmg.vk.ui.icons.LmgGlyphs.Filter24,
                        contentDescription = stringResource(R.string.debug_filter_cd),
                        tint = if (filterVisible) lc.accent else lc.textTertiary,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Поле фильтра (по желанию, скрыто по умолчанию) ──
            if (filterVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(lc.searchFieldBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28,
                        contentDescription = null,
                        tint = lc.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        textStyle = TextStyle(
                            color = lc.textPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(lc.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (filter.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.debug_filter_hint),
                                        color = lc.textTertiary,
                                        fontSize = 14.sp,
                                        fontFamily = AppFontFamily
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Кнопки экспорта ──
            // Копируем/шлём ВЕСЬ лог, а не отфильтрованный: фильтр — инструмент
            // чтения на экране, а разбирать причину по обрезанному логу нельзя,
            // нужное событие часто лежит именно в отброшенных строках.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogActionButton(
                    text = stringResource(R.string.debug_copy_all),
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.CopyOutline28,
                    modifier = Modifier.weight(1f),
                    onClick = { copyLogToClipboard(context, buildLogText(lines)) }
                )
                LogActionButton(
                    text = stringResource(R.string.debug_share),
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.ShareOutline28,
                    modifier = Modifier.weight(1f),
                    onClick = { shareLogText(context, buildLogText(lines)) }
                )
                LogActionButton(
                    text = stringResource(R.string.debug_clear),
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.DeleteOutline28,
                    tint = lc.accentRed,
                    showLabel = false,
                    onClick = {
                        DebugLog.clear()
                        // Обновляем список СРАЗУ, не дожидаясь тика опроса: иначе
                        // после нажатия экран полсекунды выглядит «не отреагировавшим».
                        revision = DebugLog.revision()
                        lines = DebugLog.snapshot()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (visible.isEmpty()) {
                // Пусто — честный текст, никаких примеров-заглушек: увидев
                // выдуманные строки, пользователь решил бы, что лог работает.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (lines.isEmpty()) stringResource(R.string.debug_log_empty) else stringResource(R.string.debug_nothing_found),
                            color = lc.textSecondary,
                            fontFamily = AppFontFamily,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (lines.isEmpty()) {
                                stringResource(R.string.debug_empty_hint)
                            } else {
                                stringResource(R.string.debug_filter_hint2)
                            },
                            color = lc.textTertiary,
                            fontFamily = AppFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Автопрокрутка к свежим строкам, пока пользователь не увёл список
                // вверх. Флаг «мы у конца» обновляем ТОЛЬКО во время его собственного
                // жеста: если считать положение после пополнения списка, то пачка
                // новых строк (за 500 мс их прилетает десяток) сама уводила бы нас
                // от конца и автопрокрутка отключалась бы сразу же.
                var followTail by remember { mutableStateOf(true) }
                LaunchedEffect(listState) {
                    snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                        if (scrolling) return@collect
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        followTail = last >= info.totalItemsCount - 2
                    }
                }
                LaunchedEffect(visible.size) {
                    // scrollToItem, а не animateScrollToItem: анимация до конца
                    // 2000 строк при постоянном пополнении не успевала бы завершиться.
                    if (followTail) listState.scrollToItem((visible.size - 1).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        // Место под мини-плеер и таб-бар, как на остальных экранах.
                        bottom = 178.dp
                    )
                ) {
                    // key по индексу+строке: сами строки не уникальны (одно и то
                    // же событие повторяется), а по индексу список переиспользует
                    // ячейки при вытеснении начала буфера.
                    itemsIndexed(visible, key = { i, line -> "$i:${line.hashCode()}" }) { _, line ->
                        Text(
                            text = line,
                            color = lc.textPrimary,
                            // Моноширинный: в логе выравнены отметки времени и
                            // числа, пропорциональный шрифт ломает эти столбцы.
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            // Ни maxLines, ни Ellipsis: длинные строки (URL,
                            // стектрейсы) переносятся ЦЕЛИКОМ. Обрезка съела бы
                            // ровно тот хвост, в котором лежит причина.
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Кнопка-пилюля панели экспорта. У «Очистить» подпись выключена ([showLabel]):
 * три полноразмерные подписи в ряд не влезают на узком экране.
 */
@Composable
private fun LogActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    val lc = LiquidTheme.colors
    val contentColor = tint ?: lc.textPrimary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(lc.cardSurface)
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        // Подпись только у растянутых кнопок: у «Очистить» ширина по контенту,
        // и текст рядом с иконкой распёр бы её за счёт соседей.
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = contentColor,
                fontFamily = AppFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Лог в один текст для буфера/файла. Шапка с версией и моделью — чтобы по
 * присланному логу было видно, на какой сборке и железе воспроизводился баг:
 * без этого приходится переспрашивать.
 */
private fun buildLogText(lines: List<String>): String = buildString {
    append("LMG VK ").append(com.lmg.vk.BuildConfig.VERSION_NAME).append('\n')
    append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
        .append(" · Android ").append(android.os.Build.VERSION.RELEASE).append('\n')
    append(context.getString(R.string.debug_lines_count, lines.size)).append('\n')
    append("----\n")
    if (lines.isEmpty()) {
        // Пустой лог тоже информация: значит, до логирующего кода не дошли.
        append("(лог пуст)\n")
    } else {
        lines.forEach { append(it).append('\n') }
    }
}

/**
 * Копирование с обязательным тостом: на Android 12 и ниже система ничего не
 * показывает, и без подтверждения кнопка выглядит нерабочей (это уже было в
 * полевом отзыве про CrashActivity).
 */
private fun copyLogToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("lmg_vk_debug_log", text))
        Toast.makeText(context, R.string.debug_copied, Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        Toast.makeText(context, context.getString(R.string.debug_copy_failed, t.message), Toast.LENGTH_LONG).show()
    }
}

/**
 * Отправка лога ФАЙЛОМ через FileProvider (cache/logs — путь уже объявлен в
 * res/xml/file_paths.xml). Через EXTRA_TEXT лог на 2000 строк ловит
 * TransactionTooLargeException, а мессенджеры такой объём текста режут — именно
 * так «отправка лога» ломалась в CrashActivity. Если файл создать не удалось,
 * деградируем к тексту: молчащая кнопка хуже урезанного лога.
 */
private fun shareLogText(context: Context, text: String) {
    val intent = try {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "lmg_vk_debug_log.txt").apply { writeText(text) }
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LMG VK debug log")
            putExtra(Intent.EXTRA_STREAM, uri)
            // Дублируем текстом для клиентов, читающих только EXTRA_TEXT.
            putExtra(Intent.EXTRA_TEXT, text.take(90_000))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (_: Throwable) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LMG VK debug log")
            putExtra(Intent.EXTRA_TEXT, text.take(90_000))
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.debug_send_via)))
    } catch (t: Throwable) {
        Toast.makeText(context, context.getString(R.string.debug_no_send_app, t.message), Toast.LENGTH_LONG).show()
    }
}
