package com.lmg.vk.network.methods

import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.music.VkAccountToggle
import com.lmg.vk.network.dto.music.VkCatalogResponse
import com.lmg.vk.network.dto.music.VkCatalogSearchRecent
import org.json.JSONArray
import org.json.JSONObject

class VkCatalogApi(
    private val client: VkApiClient,
) {
    suspend fun getAudio(
        ref: String? = null,
        needBlocks: Boolean? = true,
        url: String? = null,
        ownerId: Long? = null,
        appliedToggles: List<VkAccountToggle> = emptyList(),
    ): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudio").apply {
            ref?.let { param("ref", it) }
            needBlocks?.let { param("need_blocks", it) }
            param("url", url)
            ownerId?.let { param("owner_id", it) }
            appliedToggles.takeIf { it.isNotEmpty() }
                ?.let { param("applied_toggles", encodeToggles(it)) }
        }
        return client.execute(method)
    }

    suspend fun getAudioArtist(artistId: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioArtist").apply {
            param("need_blocks", 1)
            param("artist_id", artistId)
        }
        return client.execute(method)
    }

    suspend fun getAudioPlaylist(
        ownerId: Long,
        playlistId: Int,
        accessKey: String? = null,
        ref: String? = null,
        needBlocks: Boolean = true,
        appliedToggles: List<VkAccountToggle> = emptyList(),
    ): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioPlaylist").apply {
            param("owner_id", ownerId)
            param("id", playlistId)
            param("ref", ref)
            param("need_blocks", needBlocks)
            param("access_key", accessKey)
            appliedToggles.takeIf { it.isNotEmpty() }
                ?.let { param("applied_toggles", encodeToggles(it)) }
        }
        return client.execute(method)
    }

    suspend fun searchAudio(
        query: String,
        requestedSectionId: String? = null,
        needBlocks: Boolean = true,
        showSuggests: Boolean = true,
        screenRef: String? = "search_music",
        suggestTrackCode: String? = null,
        searchRecents: List<VkCatalogSearchRecent> = emptyList(),
        appliedToggles: List<VkAccountToggle> = emptyList(),
        ref: String? = null,
    ): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioSearch").apply {
            param("need_blocks", needBlocks)
            param("show_suggests", showSuggests)
            param("q", query)
            param("screen_ref", screenRef)
            param("suggest_trackcode", suggestTrackCode)
            param("requested_section_id", requestedSectionId)
            searchRecents.takeIf { it.isNotEmpty() }
                ?.let { param("search_recents", encodeSearchRecents(it)) }
            appliedToggles.takeIf { it.isNotEmpty() }
                ?.let { param("applied_toggles", encodeToggles(it)) }
            param("ref", ref)
        }
        return client.execute(method)
    }

    suspend fun getSection(
        sectionId: String,
        startFrom: String? = null,
        count: Int? = null,
        forceRefresh: Boolean? = null,
        appliedToggles: List<VkAccountToggle> = emptyList(),
        ref: String? = null,
    ): VkResult<VkCatalogResponse> {
        require(sectionId.isNotBlank())
        require(count == null || count > 0)
        val method = method("catalog.getSection").apply {
            param("section_id", sectionId)
            startFrom?.let { param("start_from", it) }
            count?.let { param("count", it) }
            forceRefresh?.let { param("force_refresh", it) }
            appliedToggles.takeIf { it.isNotEmpty() }
                ?.let { param("applied_toggles", encodeToggles(it)) }
            param("ref", ref)
        }
        return client.execute(method)
    }

    suspend fun getBlockItems(
        blockId: String,
        startFrom: String? = null,
        count: Int? = null,
        merchant: String? = null,
        purchaseFor: Long? = null,
        entryPoint: String? = null,
        appliedToggles: List<VkAccountToggle> = emptyList(),
        ref: String? = null,
    ): VkResult<VkCatalogResponse> {
        require(blockId.isNotBlank())
        require(count == null || count in 1..100)
        require(merchant == null || merchant in setOf("apple", "google"))
        require(
            entryPoint == null ||
                entryPoint in setOf("clips_player", "owner_page", "tvchannels_player"),
        )
        val method = method("catalog.getBlockItems").apply {
            param("block_id", blockId)
            startFrom?.let { param("start_from", it) }
            count?.let { param("count", it) }
            param("merchant", merchant)
            purchaseFor?.let { param("purchase_for", it) }
            param("entry_point", entryPoint)
            appliedToggles.takeIf { it.isNotEmpty() }
                ?.let { param("applied_toggles", encodeToggles(it)) }
            ref?.let { param("ref", it) }
        }
        return client.execute(method)
    }

    suspend fun hideBlock(blockId: String): VkResult<Int> {
        require(blockId.isNotBlank())
        val method = VkMethod(
            "catalog.hideBlock",
            MoshiEnvelopeParser<Int>(Int::class.javaObjectType),
        ).apply {
            param("block_id", blockId)
        }
        return client.execute(method)
    }

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

    suspend fun searchAudioByContext(context: String, query: String): VkResult<VkCatalogResponse> {
        val method = method("catalog.getAudioSearch").apply {
            param("context", context)
            param("q", query)
            param("need_blocks", true)
        }
        return client.execute(method)
    }

    private fun encodeToggles(items: List<VkAccountToggle>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("name", item.name)
                put("value", item.value)
                put("enabled", item.enabled)
                item.ab_group_id?.let { put("ab_group_id", it) }
                item.experiment_id?.let { put("experiment_id", it) }
            })
        }
    }.toString()

    private fun encodeSearchRecents(items: List<VkCatalogSearchRecent>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("entity_type", item.entity_type)
                item.id?.let { put("id", it) }
                item.owner_id?.let { put("owner_id", it) }
            })
        }
    }.toString()

    private fun method(name: String): VkMethod<VkCatalogResponse> = VkMethod(
        name,
        MoshiEnvelopeParser<VkCatalogResponse>(VkCatalogResponse::class.java),
    )
}
