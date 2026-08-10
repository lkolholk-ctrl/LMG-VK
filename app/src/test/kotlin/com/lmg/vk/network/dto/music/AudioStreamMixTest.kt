package com.lmg.vk.network.dto.music

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioStreamMixTest {
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
