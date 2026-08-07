package com.lmg.vk.engine.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели и правила навигации CatalogKit, восстановленные из VK X 8.12.1.
 *
 * Здесь живёт то, что в реверсе оказалось НЕ таким, как мы предполагали в
 * `docs/vkx-port/06-new-section.md`, поэтому вынесено в отдельный файл с
 * ссылками на исходники — иначе следующий, кто это прочитает, снова решит,
 * что табы ходят по `section_id`.
 */

/**
 * Один таб блока `subsection_tabs`.
 *
 * ПОЧЕМУ не `section_id`. В спеке было записано предположение, что каждому табу
 * отвечает своя секция и грузится она через `catalog.getSection(section_id)`.
 * Реверс это опровергает: `Catalog2Layout.SubsectionTabs` несёт ровно одно поле
 * `style: String` (`src-deobf/ua_itaysonlab_catalogkit_objects_seals_Catalog2Layout$SubsectionTabs.java`,
 * адаптер `…_SubsectionTabsJsonAdapter.java:14-18` знает только ключ `style`) —
 * ни заголовков табов, ни идентификаторов в layout нет.
 *
 * Сами табы VK X берёт из блока: `Catalog2Block.actions` → первый элемент
 * (`AbstractC13480e.this(list)` = `firstOrNull`) → `Catalog2Button.options`,
 * список `Catalog2ReplacementOption` (`C2077e.java:645-672`, ветка `case 8`,
 * которую диспетчер `C3198e.java:103-108` навешивает именно на
 * `Catalog2Layout.SubsectionTabs`). Отрисовку списка делает
 * `AbstractC1574e.ad(options, onClick, …)`, и начальный таб он ищет как первый
 * `option.selected == 1` (`AbstractC1574e.java:71-82`).
 *
 * Ключи опции подтверждены адаптером
 * `ua_itaysonlab_catalogkit_objects_Catalog2ReplacementOptionJsonAdapter.java:14-25`:
 * `replacement_id`, `text`, `icon`, `selected`. **`selected` — Integer, не
 * Boolean** (`Integer.class` в конструкторе адаптера), на проводе 0/1.
 */
@Serializable
data class HomeSubsectionTab(
    /** `replacement_id` — то, что уходит в запрос при выборе таба. */
    @SerialName("replacementId") val replacementId: String,
    /** `text` опции. Пустой не показываем: таб без подписи нажать невозможно. */
    val title: String,
    val icon: String? = null,
    /** `selected == 1` — таб, активный по мнению сервера. */
    val selected: Boolean = false,
)

/**
 * Как VK X превращает `replacement_id` таба в сетевой запрос.
 *
 * Развилка — `C8661e.loadAd()` ветка `case 1` (`src-deobf/C8661e.java:205-262`),
 * куда приводит нажатие таба (`AbstractC15876e.finally(String)`, `:722-726`):
 *
 *  - `replacement_id.startsWith("#")` → `AbstractC15876e.switch` (`:463-528`):
 *    строка режется по разделителю `"/#"`, берётся элемент с ИНДЕКСОМ 1
 *    (`AbstractC13480e.native(1, list)` = `getOrNull(1)`), и это уже section_id
 *    для `catalog.getSection` (`C4600e.java:388-396`, case 12). Если элемента
 *    нет — VK X возвращает `Unit` и не делает запрос вовсе.
 *  - иначе → `catalog.replaceBlocks` с единственным `replacement_ids`
 *    (`C8661e` case 0 → `C4600e(String[])`, `C4600e.java:432-440`).
 *
 * Обе ветки получают обычный `Catalog2Response` и мержат его в текущую выдачу.
 */
sealed interface CatalogTabRequest {
    /** Ветка `"#"`: догрузка целой секции по её id. */
    data class Section(val sectionId: String) : CatalogTabRequest

    /** Обычная ветка: подмена блоков по `replacement_id`. */
    data class Replacement(val replacementId: String) : CatalogTabRequest
}

/**
 * Разбор `replacement_id` по правилу VK X (см. [CatalogTabRequest]).
 *
 * `null` — ровно тот случай, когда VK X молча ничего не запрашивает: id начат с
 * `#`, но второй сегмент отсутствует. Возвращаем `null`, а не «попробуем как
 * replacement», чтобы не отправлять на сервер заведомо мусорный параметр.
 */
fun parseCatalogTabRequest(replacementId: String): CatalogTabRequest? {
    if (!replacementId.startsWith("#")) {
        return replacementId.takeIf { it.isNotBlank() }?.let(CatalogTabRequest::Replacement)
    }
    val sectionId = replacementId.split("/#").getOrNull(1)?.takeIf(String::isNotBlank)
        ?: return null
    return CatalogTabRequest.Section(sectionId)
}

/** Порция элементов блока, полученная по `next_from`. */
data class HomeBlockPage(
    val items: List<HomeItem>,
    /** Следующий курсор; `null` — сервер сказал, что больше нечего отдавать. */
    val nextFrom: String?,
)

/** Состояние выбранного таба `subsection_tabs` для одного блока. */
sealed interface CatalogTabState {
    data object Loading : CatalogTabState

    data class Ready(val blocks: List<HomeBlock>) : CatalogTabState

    /** Честный текст вместо пустого места: запрос был и не удался. */
    data class Failed(val message: String) : CatalogTabState
}

/**
 * Состояние догрузки элементов одного блока (шторка «показать все»).
 *
 * `exhausted` отдельно от `nextFrom == null`, потому что VK умеет присылать
 * непустой `next_from`, по которому приходит ноль новых элементов (курсор
 * блока успел устареть). Без явного флага список бы вечно просил ещё.
 */
data class BlockPagingState(
    val extraItems: List<HomeItem> = emptyList(),
    val nextFrom: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val exhausted: Boolean = false,
) {
    val canLoadMore: Boolean
        get() = !isLoading && !exhausted && error == null && !nextFrom.isNullOrBlank()
}
