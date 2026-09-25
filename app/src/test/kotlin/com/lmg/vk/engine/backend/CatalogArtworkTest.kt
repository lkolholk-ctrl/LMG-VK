package com.lmg.vk.engine.backend

import com.lmg.vk.network.dto.music.VkArtistPhoto
import com.lmg.vk.network.dto.music.VkAudioContentCard
import com.lmg.vk.network.dto.music.VkCatalogLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogArtworkTest {
    @Test
    fun podcastArtworkHasPriorityOverLargerGradient() {
        val card = VkAudioContentCard(
            editor_background_image = listOf(VkArtistPhoto(300, "https://vk.test/podcast.jpg", 300)),
            editor_gradient_image = listOf(VkArtistPhoto(1000, "https://vk.test/gradient.jpg", 1000)),
        )
        assertEquals("https://vk.test/podcast.jpg", card.coverUrl())
    }

    @Test
    fun nestedPodcastSizesChooseFullResolution() {
        val images = listOf(mapOf("sizes" to listOf(
            mapOf("src" to "https://vk.test/small.jpg", "width" to 30.0, "height" to 30.0),
            mapOf("src" to "https://vk.test/large.jpg", "width" to 600.0, "height" to 600.0),
        )))
        assertEquals("https://vk.test/large.jpg", bestCatalogImageUrl(images))
    }

    @Test
    fun shortcutsSupportNestedImages() {
        val link = VkCatalogLink(images = listOf(listOf(VkArtistPhoto(200, "https://vk.test/cover.jpg", 200))))
        assertEquals("https://vk.test/cover.jpg", link.coverUrl())
    }

    @Test
    fun fractionMatchesBecomePercentages() {
        assertEquals(96, catalogMatchPercent(.96f))
        assertEquals(92, catalogMatchPercent(.92f))
        assertEquals(100, catalogMatchPercent(1f))
        assertEquals(0, catalogMatchPercent(0f))
        assertEquals(96, catalogMatchPercent(96f))
        assertNull(catalogMatchPercent(null))
        assertNull(catalogMatchPercent(Float.NaN))
    }
}
