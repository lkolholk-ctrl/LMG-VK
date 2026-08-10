package com.lmg.vk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VkAudioIdentityTest {
    @Test
    fun acceptsBareAndAccessKeyIds() {
        assertTrue(VkAudioIdentity.isFullId("476293214_456240066"))
        assertTrue(VkAudioIdentity.isFullId("-123_456"))
        assertTrue(
            VkAudioIdentity.isFullId(
                "476293214_456240059_f766d39128622f5110",
            ),
        )
        assertTrue(VkAudioIdentity.isFullId("vk_-123_456_aB09"))
    }

    @Test
    fun rejectsMalformedOrForeignIds() {
        assertFalse(VkAudioIdentity.isFullId("476293214"))
        assertFalse(VkAudioIdentity.isFullId("apple_476293214_456240066"))
        assertFalse(VkAudioIdentity.isFullId("476293214_-456240066"))
        assertFalse(VkAudioIdentity.isFullId("476293214_456240066_bad_key"))
        assertFalse(VkAudioIdentity.isFullId("476293214_456240066_bad-key"))
        assertFalse(VkAudioIdentity.isFullId(""))
    }

    @Test
    fun stripsAccessKeyFromStableIdAndShareUrl() {
        val keyedId = "476293214_456240059_f766d39128622f5110"

        assertEquals("476293214_456240059", VkAudioIdentity.bareFullId(keyedId))
        assertEquals(
            "https://vk.ru/audio476293214_456240059",
            VkAudioIdentity.shareUrl(keyedId),
        )
        assertNull(VkAudioIdentity.bareFullId("not_vk"))
        assertNull(VkAudioIdentity.shareUrl("not_vk"))
    }
}
