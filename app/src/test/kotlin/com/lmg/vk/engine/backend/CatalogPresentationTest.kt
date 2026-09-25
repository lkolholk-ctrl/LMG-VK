package com.lmg.vk.engine.backend

import com.lmg.vk.network.dto.music.AudioRecommendedPlaylistDto
import com.lmg.vk.network.dto.music.VkCatalogProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogPresentationTest {
    @Test
    fun differentGenreCardsUsingTheSameMixAreNotDiscarded() {
        val items = listOf(
            HomeItem("genre_pop", "Поп", isCustom = true, streamMixId = "common"),
            HomeItem("genre_rock", "Рок", isCustom = true, streamMixId = "common"),
        )
        assertEquals(2, (items + items.first()).distinctBy { it.catalogIdentity() }.size)
    }

    @Test
    fun recommendationKeepsItsPlaylistOwnerAndOrderedTrackPreview() {
        val source = AudioRecommendedPlaylistDto(
            id = 7, owner_id = 42, percentage = 96f,
            percentage_title = "совпадение с вашим вкусом",
            cover = "https://example.com/header.jpg", audios = listOf("1_2", "1_1"),
        )
        val playlist = HomeItem("42_7", "Рябина", collectionId = "42_7", isPlaylist = true)
        val item = requireNotNull(source.toCatalogRecommendation(
            playlist, VkCatalogProfile(id = 42, first_name = "Alex", last_name = "Owner"),
            mapOf("1_1" to HomeItem("1_1", "First"), "1_2" to HomeItem("1_2", "Second")),
        ))
        assertEquals("Рябина", item.title)
        assertEquals("42_7", item.collectionId)
        assertEquals(96f, item.recommendation?.percentage)
        assertEquals("Alex Owner", item.recommendation?.ownerName)
        assertEquals(listOf("1_2", "1_1"), item.recommendation?.tracks?.map { it.id })
    }
}
