package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.dto.music.VkCatalogBlock
import com.lmg.vk.network.dto.music.VkCatalogProfile
import com.lmg.vk.network.dto.music.VkCatalogResponse
import com.lmg.vk.network.getOrNull
import com.lmg.vk.network.methods.VkCatalogApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Одно сообщество со страницы артиста.
 *
 * [id] — положительный id сообщества, как его отдаёт VK в `groups[].id`.
 */
data class ArtistCommunity(
    val id: Long,
    val name: String,
    val cover: String?,
    val isFollowed: Boolean,
) {
    /**
     * Отрицательный owner_id — ровно то, что ждут `GroupScreen`/`GroupViewModel`
     * и `NavRoutes.group()`. Та же конвенция, что у `VkGroup.audioOwnerId`:
     * в ссылках и в audio.* сообщество всегда адресуется как `-id`.
     */
    val ownerId: Long get() = -id
}

/**
 * Сообщества артиста: собственное и похожие.
 *
 * ── Что именно отдаёт VK ──
 * Страница артиста (`catalog.getAudioArtist`) — обычный CatalogKit-ответ. Блок с
 * сообществами приходит с `data_type = "groups"`
 * (`Catalog2BlockJsonAdapter.java:25`: `CuratorGroupBlock.class → "groups"`), и
 * единственный список сущностей у него — `group_ids`
 * (`Catalog2Block_CuratorGroupBlockJsonAdapter.java:20`: `"id", "layout",
 * "actions", "next_from", "listen_events", "group_ids"`). Сами сообщества лежат
 * в корне ответа в `groups` (`Catalog2ResponseJsonAdapter.java:61`), элемент —
 * `VKProfile` с полями `id`, `first_name`, `last_name`, `photo_base`, `name`,
 * `is_followed`, `can_follow` (`VKProfileJsonAdapter.java:15`). Никаких других
 * полей у сообщества в каталоге нет — ни счётчика участников, ни описания.
 *
 * ── Чем «своё» отличается от «похожих» ──
 * Отличие ровно одно и оно в layout'е, а не в самих сообществах. Блок с
 * официальной страницей артиста VK помечает layout'ом `owner_cell`, у которого
 * единственное поле — `owner_id`
 * (`Catalog2Layout_OwnerCellJsonAdapter.java:13`, `Catalog2LayoutJsonAdapter.java:86`).
 * У блоков с похожими сообществами layout обычный и `owner_id` отсутствует.
 * Поэтому «своё» ищем по `owner_id` из `owner_cell`, а остальное считаем
 * похожим. Иного признака в ответе нет — оба блока состоят из одних `group_ids`.
 *
 * Заголовок блока мы не выдумываем: в CatalogKit это отдельный `header`-блок,
 * который применяется к СЛЕДУЮЩЕМУ блоку с контентом (так же устроен разбор
 * главной в `MusicBackend`). Берём `layout.title` этого header'а.
 */
class ArtistCommunitiesViewModel : ViewModel() {

    data class State(
        /** Официальная страница самого артиста; `null`, если VK её не дал. */
        val own: ArtistCommunity? = null,
        /** Похожие сообщества; пустой список означает «VK не отдал». */
        val similar: List<ArtistCommunity> = emptyList(),
        /** Заголовок блока похожих ровно тот, что прислал VK. */
        val similarTitle: String? = null,
        val isLoading: Boolean = false,
    ) {
        /** Рисовать блок только когда есть что показать — пустоты не рисуем. */
        val hasContent: Boolean get() = own != null || similar.isNotEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadedArtistId: String? = null

    /**
     * Грузит сообщества для артиста.
     *
     * Повторный вызов для того же артиста пропускаем, только если предыдущий
     * УДАЧНО что-то нашёл: экран пересоздаёт эффекты часто, а каталог — тяжёлый
     * запрос. После неудачи пробуем снова, иначе разовый сетевой сбой навсегда
     * прятал бы блок.
     */
    fun load(artistId: String) {
        val normalizedId = artistId.removePrefix("vk_")
        if (normalizedId.isBlank()) return
        if (loadJob?.isActive == true && loadedArtistId == normalizedId) return
        if (loadedArtistId == normalizedId && _state.value.hasContent) return
        loadedArtistId = normalizedId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = State(isLoading = true)
            val loaded = try {
                fetch(normalizedId)
            } catch (cancellation: CancellationException) {
                // Отмена — это смена артиста, а не ошибка: состояние не трогаем,
                // его выставит следующий запуск.
                throw cancellation
            } catch (_: Exception) {
                null
            }
            _state.value = loaded ?: State(isLoading = false)
        }
    }

    private suspend fun fetch(artistId: String): State {
        val client = runCatching { VkApiLocator.apiClient() }.getOrNull()
            ?: return State(isLoading = false)
        val catalogApi = VkCatalogApi(client)

        val root = catalogApi.getAudioArtist(artistId).getOrNull()
            ?: return State(isLoading = false)

        // Сначала пробуем обойтись одним запросом: у большинства артистов блок с
        // сообществами лежит в секции по умолчанию, которая приходит сразу.
        parse(listOf(root)).takeIf { it.hasContent }?.let { return it }

        // Если нет — досматриваем остальные секции. Догружаем только те, чьи
        // блоки VK в корне не прислал, и не больше нескольких: это добор
        // недостающего, а не полная пагинация каталога.
        val pendingSectionIds = root.catalog?.sections.orEmpty()
            .filter { it.blocks.isNullOrEmpty() }
            .map { it.id }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_EXTRA_SECTIONS)
        if (pendingSectionIds.isEmpty()) return State(isLoading = false)

        val extraPages = coroutineScope {
            pendingSectionIds
                .map { sectionId -> async { catalogApi.getSection(sectionId).getOrNull() } }
                .mapNotNull { it.await() }
        }
        return parse(listOf(root) + extraPages)
    }

    private fun parse(pages: List<VkCatalogResponse>): State {
        val blocks = pages.flatMap { it.allBlocks() }.distinctBy { it.id }

        // Сообщество может прийти в `groups`, а у части ответов — среди
        // `music_owners` (тот же VKProfile, отрицательный id у сообществ).
        // Ключ — модуль id: в `group_ids` VK пишет то положительный, то
        // отрицательный id, и сравнение «как есть» теряло половину карточек.
        val profilesById = HashMap<Long, VkCatalogProfile>()
        pages.forEach { page ->
            (page.groups.orEmpty() + page.music_owners.orEmpty()).forEach { profile ->
                if (profile.id != 0L && profile.displayName.isNotBlank()) {
                    profilesById.putIfAbsent(abs(profile.id), profile)
                }
            }
        }
        if (profilesById.isEmpty()) return State(isLoading = false)

        // `owner_cell` помечает официальную страницу артиста. Берём именно его
        // `owner_id`, а не первый попавшийся id из блока.
        val ownId = blocks
            .firstNotNullOfOrNull { block ->
                block.layout?.owner_id?.takeIf { block.layout?.name == OWNER_CELL_LAYOUT }
            }
            ?.let { abs(it) }

        var pendingHeaderTitle: String? = null
        var similarTitle: String? = null
        val similarIds = LinkedHashSet<Long>()
        blocks.forEach { block ->
            val layoutName = block.layout?.name.orEmpty()
            if (layoutName in HEADER_LAYOUTS) {
                // Заголовок — отдельный блок без сущностей, он относится к
                // следующему блоку с контентом.
                pendingHeaderTitle = block.layout?.title?.takeIf(String::isNotBlank)
                    ?: pendingHeaderTitle
                return@forEach
            }
            // Блок собственной страницы артиста в «похожие» не идёт и не должен
            // забирать себе заголовок следующего блока.
            if (layoutName == OWNER_CELL_LAYOUT) {
                pendingHeaderTitle = null
                return@forEach
            }
            val ids = block.group_ids.orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .map { abs(it) }
            if (ids.isEmpty()) return@forEach
            if (similarTitle == null) similarTitle = pendingHeaderTitle
            pendingHeaderTitle = null
            similarIds += ids
        }
        // Своё сообщество не дублируем в «похожих».
        if (ownId != null) similarIds -= ownId

        val own = ownId?.let { profilesById[it] }?.toCommunity()
        val similar = similarIds.mapNotNull { profilesById[it]?.toCommunity() }
        return State(
            own = own,
            similar = similar,
            similarTitle = similarTitle?.takeIf { similar.isNotEmpty() },
            isLoading = false,
        )
    }

    private fun VkCatalogProfile.toCommunity(): ArtistCommunity = ArtistCommunity(
        id = abs(id),
        name = displayName,
        cover = photo_base?.takeIf(String::isNotBlank),
        isFollowed = is_followed == true,
    )

    /** Блоки лежат в трёх разных местах ответа в зависимости от вызова. */
    private fun VkCatalogResponse.allBlocks(): List<VkCatalogBlock> = buildList {
        block?.let(::add)
        section?.blocks.orEmpty().let(::addAll)
        catalog?.sections.orEmpty().flatMap { it.blocks.orEmpty() }.let(::addAll)
    }

    private companion object {
        const val OWNER_CELL_LAYOUT = "owner_cell"
        val HEADER_LAYOUTS = setOf(
            "header",
            "header_compact",
            "header_large",
            "header_extended",
        )
        const val MAX_EXTRA_SECTIONS = 4
    }
}
