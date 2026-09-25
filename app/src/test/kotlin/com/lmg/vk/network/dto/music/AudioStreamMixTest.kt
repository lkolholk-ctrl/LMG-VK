package com.lmg.vk.network.dto.music

import com.lmg.vk.network.VkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioStreamMixTest {
    @Test
    fun catalogKeepsSectionsAndMixesWhenSecondMixHasNoTitleOrDescription() {
        val catalog = requireNotNull(VkJson.moshi.adapter(VkCatalogResponse::class.java).fromJson(
            """
            {
              "catalog": {
                "sections": [{"id":"general","title":"Главное","style":{"type":"primary"}}]
              },
              "audio_stream_mixes": [
                {"id":"first","title":"First mix","description":"Personal mix"},
                {"id":"second","stream_mix":{"id":"common","title":"Nested title"},
                 "titles":{"common_state":"My mix"}}
              ]
            }
            """.trimIndent()
        ))

        assertEquals("general", catalog.catalog?.sections?.single()?.id)
        val mixes = requireNotNull(catalog.audio_stream_mixes)
        assertEquals(2, mixes.size)
        assertEquals("First mix", mixes[0].displayTitle)
        assertEquals("Personal mix", mixes[0].description)
        assertEquals("", mixes[1].description)
        assertEquals("My mix", mixes[1].displayTitle)
        assertEquals("common", mixes[1].playbackMixId)
    }

    @Test
    fun untitledMixUsesNestedTitleWhenAvailable() {
        val mix = requireNotNull(VkJson.moshi.adapter(AudioStreamMix::class.java).fromJson(
            """{"id":"item","stream_mix":{"id":"common","title":"Nested title"}}"""
        ))

        assertEquals("Nested title", mix.displayTitle)
    }

    @Test
    fun mixWithNoTitlesCanStillBeParsedAndPlayed() {
        val mix = requireNotNull(VkJson.moshi.adapter(AudioStreamMix::class.java).fromJson(
            """{"id":"common"}"""
        ))

        assertNull(mix.displayTitle)
        assertEquals("common", mix.playbackMixId)
    }

    @Test
    fun playbackUsesNestedStreamMixId() {
        val mix = AudioStreamMix(
            id = "catalog_item_42",
            title = "Aura",
            description = "Personal mix",
            stream_mix = AudioStreamMixLink(id = "common"),
        )

        assertEquals("common", mix.playbackMixId)
    }

    @Test
    fun playbackFallsBackToCatalogIdForLegacyResponses() {
        val mix = AudioStreamMix(
            id = "common",
            title = "Aura",
            description = "Personal mix",
        )

        assertEquals("common", mix.playbackMixId)
    }
}
