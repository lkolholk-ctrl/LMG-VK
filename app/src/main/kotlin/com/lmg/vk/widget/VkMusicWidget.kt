package com.lmg.vk.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lmg.vk.R
import com.lmg.vk.engine.PlayerController

/**
 * Виджет домашнего экрана с подборками VK — порт виджета VK X
 * (`ua.itaysonlab.vkxreborn.playback.widget_glance` в дампе:
 * `SmallPlaylistsGlanceWidgetReceiver`, загрузка — `C16375e`, потребитель — `C16115e`).
 *
 * Реализация на Glance, а не на «сырых» RemoteViews — по двум причинам.
 * Во-первых, так сделано в оригинале. Во-вторых, зависимость
 * `androidx.glance:glance-appwidget:1.1.1` в проекте УЖЕ прописана, и
 * `PlayerController` уже импортирует из неё `updateAll` — то есть Glance здесь
 * не новый риск для сборки: этот файл лишь даёт тому импорту настоящего
 * потребителя. Новых зависимостей порт не потребовал.
 *
 * `SizeMode.Exact` — потому что от размера зависит не только вёрстка, но и сам
 * запрос: VK принимает `size` (`small`/`medium`/`large`) и отдаёт под него
 * разный состав элементов.
 */
class VkMusicWidget : GlanceAppWidget() {

    /**
     * Exact, а не Responsive: у Responsive нужно заранее перечислить набор
     * размеров, а нам нужен фактический — чтобы выбрать `size` для API.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Грузим данные ДО provideContent: сеть внутри композиции Glance
        // приводит к лишним перекомпозициям, а на одну отрисовку нужен ровно
        // один снимок состояния.
        val snapshot = buildSnapshot(context, id)
        val covers = when (val state = snapshot.state) {
            is VkWidgetState.Content -> VkWidgetCovers.load(context, state.elements)
            else -> emptyMap()
        }

        provideContent {
            // GlanceTheme даёт системные динамические цвета на Android 12+ и сам
            // отрабатывает светлую/тёмную тему лончера. Хардкодить цвета нельзя:
            // виджет лежит на чужих обоях.
            GlanceTheme {
                WidgetBody(snapshot = snapshot, covers = covers)
            }
        }
    }

    private suspend fun buildSnapshot(context: Context, id: GlanceId): VkWidgetSnapshot {
        val size = resolveSize(context, id)
        val contentType = VkWidgetPreferences.contentType(context)
        val forceRefresh = VkWidgetPreferences.consumeForceRefresh(context)

        val state = VkWidgetRepository.load(
            context = context,
            size = size,
            contentType = contentType,
            forceRefresh = forceRefresh,
        )

        // Now-playing читаем из живого состояния плеера. Если процесс поднялся
        // только ради виджета, флоу пустые — это честное «ничего не играет», а не
        // повод что-то придумывать.
        val track = runCatching { PlayerController.currentTrack.value }.getOrNull()
        val playing = runCatching { PlayerController.isPlaying.value }.getOrDefault(false)

        return VkWidgetSnapshot(
            size = size,
            contentType = contentType,
            state = state,
            nowPlaying = track?.let { t ->
                listOf(t.title, t.artist)
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")
                    .takeIf { it.isNotBlank() }
            },
            isPlaying = playing,
        )
    }

    /**
     * Фактический размер -> `size` для API.
     *
     * Берём габариты у [AppWidgetManager], а не из `LocalSize`: `LocalSize`
     * доступен только внутри композиции, а размер нужен раньше — он участвует в
     * сетевом запросе и в ключе кэша.
     *
     * Пороги в dp подобраны по сетке лончеров: ячейка ~70dp, то есть 2 ячейки —
     * это узкий виджет, 4 — средний. При любой неожиданности отдаём MEDIUM:
     * это середина, на ней вёрстка выглядит адекватно в обе стороны.
     */
    private fun resolveSize(context: Context, id: GlanceId): VkWidgetSize = runCatching {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        when {
            minWidthDp <= 0 -> VkWidgetSize.MEDIUM
            minWidthDp < 180 -> VkWidgetSize.SMALL
            minWidthDp < 300 -> VkWidgetSize.MEDIUM
            else -> VkWidgetSize.LARGE
        }
    }.getOrDefault(VkWidgetSize.MEDIUM)

    companion object {
        /**
         * Перерисовать все экземпляры виджета — единая точка и для
         * `PlayerController.refreshHomeWidget()`, и для кнопки обновления.
         *
         * runCatching обязателен: `updateAll` падает, если виджет ещё не
         * добавлен на экран, а звать его будут из плеера при каждой смене трека.
         */
        suspend fun refreshAll(context: Context) {
            runCatching { VkMusicWidget().updateAll(context) }
        }
    }
}

// ───────────────────────────────── вёрстка ─────────────────────────────────

@Composable
private fun WidgetBody(
    snapshot: VkWidgetSnapshot,
    covers: Map<String, Bitmap>,
) {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Header(snapshot = snapshot)

        Spacer(modifier = GlanceModifier.height(8.dp))

        when (val state = snapshot.state) {
            // Все «пустые» ветки по смыслу одинаковы: объясняем причину текстом
            // и делаем площадь кликабельной — открыть приложение это
            // единственное осмысленное действие в такой ситуации.
            is VkWidgetState.NotLoggedIn -> Notice(
                text = context.getString(R.string.vk_widget_not_logged_in),
            )

            is VkWidgetState.Empty -> Notice(
                text = context.getString(R.string.vk_widget_empty),
            )

            // Причину показываем как есть: «что-то пошло не так» на домашнем
            // экране не даёт пользователю ни одного следующего шага.
            is VkWidgetState.Error -> Notice(
                text = context.getString(R.string.vk_widget_error, state.reason),
            )

            is VkWidgetState.Content -> ElementList(
                elements = state.elements,
                covers = covers,
                stale = state.stale,
            )
        }

        // Строка «сейчас играет» — снизу, чтобы не разъезжалась вёрстка
        // подборок, и только когда трек действительно есть.
        snapshot.nowPlaying?.let { nowPlaying ->
            Spacer(modifier = GlanceModifier.height(6.dp))
            NowPlayingRow(text = nowPlaying, isPlaying = snapshot.isPlaying)
        }
    }
}

@Composable
private fun Header(snapshot: VkWidgetSnapshot) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Тап по названию раздела переключает recomms <-> mymusic и сразу
        // перерисовывает виджет — замена configuration-activity из VK X.
        Text(
            text = context.getString(snapshot.contentType.titleRes),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickable(actionRunCallback<VkWidgetToggleTypeAction>()),
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        Text(
            text = context.getString(R.string.vk_widget_refresh),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.clickable(actionRunCallback<VkWidgetRefreshAction>()),
        )
    }
}

@Composable
private fun ElementList(
    elements: List<VkWidgetElement>,
    covers: Map<String, Bitmap>,
    stale: Boolean,
) {
    val context = LocalContext.current

    // Обычный Column, а не LazyColumn: элементов максимум 6 (см. VkWidgetSize),
    // они все влезают, а LazyColumn в виджете тянет RemoteViewsService и
    // отдельный адаптер — на шести строках это чистые накладные расходы.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        elements.forEach { element ->
            ElementRow(element = element, cover = covers[element.coverUrl])
            Spacer(modifier = GlanceModifier.height(6.dp))
        }

        // Возраст данных показываем только когда они реально устарели, иначе
        // пользователь не отличит «VK молчит» от «у меня так и есть».
        if (stale) {
            Text(
                text = context.getString(R.string.vk_widget_stale),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ElementRow(element: VkWidgetElement, cover: Bitmap?) {
    val context = LocalContext.current

    // Нет разбираемой ссылки — ведём в приложение. Некликабельная карточка
    // читается как зависший виджет, поэтому «ничего не делать» не вариант.
    val intent = elementIntent(context, element) ?: openAppIntent(context)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = element.title,
                modifier = GlanceModifier.size(40.dp).cornerRadius(8.dp),
            )
        } else {
            // Плейсхолдер вместо картинки: без него строки без обложки съезжают
            // к левому краю и список выглядит сломанным.
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .cornerRadius(8.dp)
                    .background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = element.title.take(1).uppercase(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = element.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            // Вторую строку рисуем только если VK её прислал; `type` — запасной
            // вариант, он хотя бы говорит «плейлист это или альбом».
            val secondLine = element.subtitle.ifBlank { element.type }
            if (secondLine.isNotBlank()) {
                Text(
                    text = secondLine,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingRow(text: String, isPlaying: Boolean) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(openAppIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Символом, а не иконкой: виджет не управляет плеером (кнопки play/pause
        // тут сознательно не делаем, это не now-playing виджет), значок нужен
        // только чтобы отличить «играет» от «на паузе».
        Text(
            text = if (isPlaying) "▶" else "❙❙",
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Notice(text: String) {
    val context = LocalContext.current

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(openAppIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            maxLines = 3,
        )
    }
}
