package com.lmg.vk.network.dto.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistHeroPhotoTest {
    @Test fun heroSelectsLargestAdvertisedVariantAcrossContainers() {
        val artist=VkArtistDto(photo=listOf(VkArtistPhoto(600,"https://example.test/600.jpg",600)),
            photos=listOf(VkArtistPhotosContainer(photo=listOf(VkArtistPhoto(200,"https://example.test/art?as=200x200,1200x1200&cs=200x200",200)))))
        assertEquals(1200,artist.heroPhoto()?.width)
        assertEquals("https://example.test/art?as=200x200,1200x1200&cs=1200x1200",artist.heroPhoto()?.url)
        assertEquals("https://example.test/600.jpg",artist.coverUrl())
    }
    @Test fun originalUrlWithoutAdvertisedVariantsIsPreserved() {
        val url="https://example.test/1500.jpg?cs=1500x1500"
        assertEquals(url,VkArtistDto(photo=listOf(VkArtistPhoto(1500,url,1500))).heroPhoto()?.url)
    }
}
