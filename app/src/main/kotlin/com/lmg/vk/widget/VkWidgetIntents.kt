package com.lmg.vk.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lmg.vk.MainActivity
import com.lmg.vk.engine.VkLinkResolver

/**
 * Куда ведёт тап по элементу виджета.
 *
 * Своего механизма навигации виджет не заводит: в приложении уже есть полный
 * путь «ссылка VK -> цель -> экран» — [VkLinkResolver] разбирает адрес (в том
 * числе сетевым `utils.resolveScreenName`), а `VkLinkRouter` доносит цель до
 * Compose-графа. Поэтому виджет отправляет в `MainActivity` обычный
 * `ACTION_VIEW` с тем же URL, что пришёл от VK, и дальше работает штатный
 * обработчик `MainActivity.handleVkLink`. Ни резолвер, ни маршруты для этого
 * менять не пришлось.
 */
/**
 * Intent на конкретный элемент подборки.
 *
 * `null` возвращается, когда ссылку нельзя привести к виду, который понимает
 * [VkLinkResolver]. Вызывающий в этом случае обязан подставить
 * [openAppIntent] — тап по карточке, который вообще ничего не делает,
 * пользователь читает как зависший виджет.
 */
internal fun elementIntent(context: Context, element: VkWidgetElement): Intent? {
    val uri = normalizeVkUrl(element.url) ?: return null

    // Явно указываем класс: без него ACTION_VIEW ушёл бы в системный выбор
    // приложения (наш intent-filter на vk.com там не единственный), и тап по
    // виджету иногда открывал бы браузер.
    return Intent(Intent.ACTION_VIEW, uri)
        .setClass(context, MainActivity::class.java)
        // Виджет запускается не из задачи приложения, поэтому NEW_TASK
        // обязателен; MainActivity объявлена singleTask, так что второй копии
        // не появится.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Intent «просто открыть приложение» — для заголовка виджета и для честного
 * поведения в состояниях без данных (не залогинен, ошибка, пусто).
 */
internal fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Привести `url` из ответа VK к абсолютной ссылке VK.
 *
 * Зачем нормализация: `audio.getWidgetElements` отдаёт адреса не в одном
 * формате — встречаются и полные `https://vk.com/music/playlist/...`, и
 * короткие вида `/audio_playlist-2000_1`, и совсем без слэша. А
 * [VkLinkResolver.isVkLink] требует host из своего списка, то есть на
 * относительном пути он честно ответит «это не ссылка VK», и переход молча
 * не сработал бы.
 *
 * Проверяем результат самим резолвером, а не регуляркой: список доменов живёт
 * там, и дублировать его здесь означало бы расхождение при следующем домене VK.
 */
private fun normalizeVkUrl(rawUrl: String): Uri? {
    val url = rawUrl.trim().takeIf { it.isNotEmpty() } ?: return null

    val candidate = runCatching {
        when {
            url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) -> Uri.parse(url)

            // Относительный путь от корня VK — самый частый короткий формат.
            else -> Uri.parse("https://vk.com/" + url.trimStart('/'))
        }
    }.getOrNull() ?: return null

    // Чужой домен (VK иногда кладёт ссылки на внешние промо) резолвер не
    // откроет, и подсовывать его MainActivity бессмысленно.
    return candidate.takeIf { runCatching { VkLinkResolver.isVkLink(it) }.getOrDefault(false) }
}
