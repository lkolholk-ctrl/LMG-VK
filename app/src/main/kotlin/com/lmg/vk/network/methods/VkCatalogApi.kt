package com.lmg.vk.network.methods

import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.music.VkCatalogResponse

/**
 * Восстановленные catalog.* вызовы из C4600e.
 * Каталог VK сам содержит треки, плейлисты, артистов, радиостанции и StreamMix.
 */
class VkCatalogApi(
    private val client: VkApiClient,
) {
    /** Главная музыкальная страница: `catalog.getAudioAuto`, C4600e id=11. */
    suspend fun getAudioAuto(): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioAuto").apply {
            param("need_blocks", 1)
        }
        return client.execute(method)
    }

    /** Универсальная главная по URL каталога: `catalog.getAudio`, C4600e id=7. */
    suspend fun getAudio(url: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudio").apply {
            param("need_blocks", 1)
            param("url", url)
        }
        return client.execute(method)
    }

    /** Страница исполнителя: `catalog.getAudioArtist`, C4600e id=4. */
    suspend fun getAudioArtist(artistId: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioArtist").apply {
            param("need_blocks", 1)
            param("artist_id", artistId)
        }
        return client.execute(method)
    }

    /** Поиск CatalogKit: `catalog.getAudioSearch`, C4600e id=16. */
    suspend fun searchAudio(
        query: String,
        requestedSectionId: String? = null,
        needBlocks: Boolean = true,
        showSuggests: Boolean = true,
    ): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioSearch").apply {
            param("need_blocks", needBlocks)
            param("show_suggests", showSuggests)
            param("query", query)
            param("requested_section_id", requestedSectionId)
        }
        return client.execute(method)
    }

    /** Дозагрузка секции: `catalog.getSection`, C4600e id=12. */
    suspend fun getSection(
        sectionId: String,
        startFrom: String? = null,
    ): VkResult<VkCatalogResponse> {
        val method = method("catalog.getSection").apply {
            param("need_blocks", 1)
            param("section_id", sectionId)
            startFrom?.let { param("start_from", it) }
        }
        return client.execute(method)
    }

    /**
     * Дозагрузка элементов блока по `next_from`.
     *
     * ВАЖНО: метода `catalog.getBlockItems` у VK **нет** — имя досталось от более
     * раннего порта и было ошибкой (в реверсе оно встречается только в чужом
     * дампе `vkmp3mod`, не в VK X). Настоящая пагинация каталога у VK X одна:
     * повторный `catalog.getSection` с `start_from` (`AbstractC15876e:61-116`).
     * Запрос по несуществующему методу возвращал ошибку, из-за чего догрузка
     * молча заканчивалась на первой странице.
     *
     * Поэтому здесь идёт `getSection`. Параметр [sectionOrBlockId] назван так
     * честно: вызывающая сторона знает про блок, но VK листает секциями, и для
     * блоков верхнего уровня каталога их идентификаторы совпадают. Если секция
     * неизвестна или не совпала, VK ответит ошибкой, и догрузка просто
     * остановится — это лучше прежнего гарантированного отказа.
     */
    suspend fun getBlockItems(
        sectionOrBlockId: String,
        startFrom: String? = null,
    ): VkResult<VkCatalogResponse> = getSection(sectionOrBlockId, startFrom)

    suspend fun replaceBlocks(replacementIds: Collection<String>): VkResult<VkCatalogResponse> {
        val method = method("catalog.replaceBlocks").apply {
            param("replacement_ids", replacementIds.joinToString(","))
        }
        return client.execute(method)
    }

    suspend fun replaceSections(replacementId: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.replaceSections").apply {
            param("replacement_id", replacementId)
        }
        return client.execute(method)
    }

    suspend fun getAudioBooks(genreId: Int? = null): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioBooks").apply {
            param("need_blocks", 1)
            genreId?.let { param("genre_id", it) }
        }
        return client.execute(method)
    }

    suspend fun getPersonAudioBooks(
        personId: Int,
        genreId: Int? = null,
    ): VkResult<VkCatalogResponse> {
        val name = if (genreId != null) {
            "catalog.getPersonAudioBooks"
        } else {
            "catalog.getAudioBooksPerson"
        }
        val method = method(name).apply {
            param("need_blocks", 1)
            param("person_id", personId)
            genreId?.let { param("genre_id", it) }
        }
        return client.execute(method)
    }

    suspend fun getAudioCurator(curatorId: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioCurator").apply {
            param("need_blocks", 1)
            param("curator_id", curatorId)
        }
        return client.execute(method)
    }

    suspend fun getPodcasts(categoryId: String, ownerId: Long): VkResult<VkCatalogResponse> {
        val method = method("catalog.getPodcasts").apply {
            param("need_blocks", 1)
            param("category_id", categoryId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    /** Вторая форма C4600e id=29: context/query без requested_section_id. */
    suspend fun searchAudioByContext(context: String, query: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioSearch").apply {
            param("context", context)
            param("query", query)
        }
        return client.execute(method)
    }

    private fun method(name: String): VkMethod<VkCatalogResponse> = VkMethod(
        name,
        MoshiEnvelopeParser<VkCatalogResponse>(VkCatalogResponse::class.java),
    )
}
