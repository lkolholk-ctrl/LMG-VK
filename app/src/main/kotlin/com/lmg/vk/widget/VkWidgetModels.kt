package com.lmg.vk.widget

import com.lmg.vk.network.dto.gen.music.AudioWidgetItem
import kotlinx.serialization.Serializable

/**
 * Модели виджета домашнего экрана — порт `audio.getWidgetElements` из VK X.
 *
 * Оригинал (`C16375e` в деобфусцированном дампе VK X) держит ровно две оси:
 * размер (`small`/`medium`/`large`) и тип подборки (`recomms`/`mymusic`), обе
 * уезжают в метод как строковые параметры. Значения ниже — дословно те же
 * строки, менять их нельзя: сервер VK отдаёт по ним разное количество и разный
 * состав элементов.
 */

/**
 * Размер виджета -> параметр `size` метода.
 *
 * Почему enum, а не строка в вызове: строку легко опечатать, а VK на неизвестный
 * `size` отвечает не ошибкой, а пустым списком — то есть опечатка выглядела бы
 * как «у пользователя нет подборок», и мы бы её не заметили.
 */
enum class VkWidgetSize(val apiValue: String, val maxElements: Int) {
    /** `small` — узкий виджет, помещается пара строк без обложек. */
    SMALL("small", maxElements = 2),

    /** `medium` — размер по умолчанию, обложка + две строки текста. */
    MEDIUM("medium", maxElements = 4),

    /** `large` — во всю ширину экрана. */
    LARGE("large", maxElements = 6),
}

/** Тип подборки -> параметр `type` метода (VK X: `recomms` / `mymusic`). */
enum class VkWidgetContentType(val apiValue: String, val titleRes: Int) {
    /** Рекомендации VK — состояние виджета по умолчанию, как в VK X. */
    RECOMMS("recomms", com.lmg.vk.R.string.vk_widget_type_recomms),

    /** «Моя музыка» — плейлисты и альбомы самого пользователя. */
    MYMUSIC("mymusic", com.lmg.vk.R.string.vk_widget_type_mymusic);

    /** Кнопка в заголовке переключает типы по кругу. */
    fun next(): VkWidgetContentType = if (this == RECOMMS) MYMUSIC else RECOMMS

    companion object {
        fun fromApiValue(value: String?): VkWidgetContentType =
            entries.firstOrNull { it.apiValue == value } ?: RECOMMS
    }
}

/**
 * Один элемент подборки в том виде, в котором его рисует виджет.
 *
 * Зачем отдельная модель вместо сетевого [AudioWidgetItem]: этот объект уходит
 * в кэш на диск и должен пережить перезапуск процесса, поэтому он
 * `@Serializable` и содержит только плоские строки. Сетевой DTO для этого не
 * годится — он на Moshi и тянет за собой `AlbumThumb` со всеми размерами фото,
 * из которых виджету нужен ровно один.
 */
@Serializable
data class VkWidgetElement(
    val title: String,
    val subtitle: String,
    /** `type` из ответа VK (playlist/album/...). Показываем только как подпись. */
    val type: String,
    /** Ссылка VK на подборку. Пустая строка = тап откроет просто приложение. */
    val url: String,
    /** URL обложки; пустая строка — обложки в ответе не было. */
    val coverUrl: String,
)

/**
 * Что виджет показывает прямо сейчас.
 *
 * Все ветки, кроме [Content], — honest-состояния: пользователь видит причину,
 * а не пустой прямоугольник. Виджет живёт на домашнем экране постоянно, поэтому
 * молчаливая пустота здесь хуже честной надписи об ошибке.
 */
sealed interface VkWidgetState {

    /** Нет VK-сессии: рисуем приглашение войти, а не выдуманные карточки. */
    data object NotLoggedIn : VkWidgetState

    /** VK ответил успешно, но список пуст — так бывает у новых аккаунтов. */
    data object Empty : VkWidgetState

    /**
     * Сеть/VK не ответили, и в кэше нет ничего, что можно показать.
     * [reason] уже пригоден для показа пользователю.
     */
    data class Error(val reason: String) : VkWidgetState

    /**
     * Есть элементы. [stale] = true, когда кэш просрочен, а обновить его не
     * удалось: элементы настоящие, но старые, и мы это не скрываем.
     */
    data class Content(
        val elements: List<VkWidgetElement>,
        val stale: Boolean,
    ) : VkWidgetState
}

/**
 * Снимок для отрисовки: подборка + строка «сейчас играет».
 *
 * Now-playing здесь не ради красоты: именно из-за него имеет смысл
 * `PlayerController.refreshHomeWidget()` на смене трека. Без него виджет нечего
 * было бы перерисовывать чаще раза в час.
 */
data class VkWidgetSnapshot(
    val size: VkWidgetSize,
    val contentType: VkWidgetContentType,
    val state: VkWidgetState,
    /** `null` — плеер молчит, строку now-playing не рисуем вообще. */
    val nowPlaying: String?,
    val isPlaying: Boolean,
)

/**
 * Сетевой DTO -> модель виджета. `null` для элементов без заголовка: карточка
 * без названия бесполезна, а придумывать заголовок нельзя.
 */
internal fun AudioWidgetItem.toWidgetElement(): VkWidgetElement? {
    val safeTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return VkWidgetElement(
        title = safeTitle,
        subtitle = subtitle?.trim().orEmpty(),
        type = type?.trim().orEmpty(),
        url = url?.trim().orEmpty(),
        coverUrl = photo?.widgetThumbUrl().orEmpty(),
    )
}

/**
 * Обложка под виджет.
 *
 * ОТСТУПЛЕНИЕ от общего `AlbumThumb.bestUrl`: тот отдаёт photo_1200, а обложка
 * в виджете занимает ~48dp. Через RemoteViews картинка едет в лончер как
 * Bitmap в биндере, и у транзакции жёсткий лимит — большое фото это лишние
 * мегабайты в парселе на каждый элемент. Поэтому берём самый маленький
 * пригодный размер и только потом деградируем вверх.
 */
private fun com.lmg.vk.network.dto.music.AlbumThumb.widgetThumbUrl(): String? =
    listOf(photo_270, photo_300, photo_135, photo_600, src, photo_68, photo_34)
        .firstOrNull { !it.isNullOrBlank() }
