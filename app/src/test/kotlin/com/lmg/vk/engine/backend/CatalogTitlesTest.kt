package com.lmg.vk.engine.backend

import com.lmg.vk.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTitlesTest {
    @Test
    fun technicalBannerTitleFromCacheGetsReadableName() {
        assertEquals(R.string.catalog_featured, catalogTitleResource("catalog_banners", "catalog_banners"))
        assertEquals(R.string.catalog_featured, catalogTitleResource("catalog_banners", ""))
    }

    @Test
    fun otherWireTypesAlsoGetReadableNames() {
        assertEquals(R.string.playlists_title, catalogTitleResource("music_playlists", "music_playlists"))
        assertEquals(R.string.catalog_mixes, catalogTitleResource("audio_stream_mixes", "audio_stream_mixes"))
    }

    @Test
    fun editorialTitlesArePreservedIncludingUnderscores() {
        assertNull(catalogTitleResource("Для вас", "catalog_banners"))
        assertNull(catalogTitleResource("my_favourite_music", "music_playlists"))
    }

    @Test
    fun unknownWireTypeDoesNotLeakAsAHeading() {
        assertEquals(R.string.catalog_featured, catalogTitleResource("future_server_type", "future_server_type"))
    }
}
