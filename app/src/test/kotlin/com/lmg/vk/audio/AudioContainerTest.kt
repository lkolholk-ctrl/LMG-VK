package com.lmg.vk.audio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioContainerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun validatesAndNamesActualContainerWithoutChangingAudioBytes() {
        for ((fixture, extension) in listOf("mp3" to "mp3", "m4a" to "m4a", "flac" to "flac", "alac.m4a" to "m4a")) {
            val name = if (fixture == "alac.m4a") "tag-fixture-alac.m4a" else "tag-fixture.$fixture"
            val original = javaClass.getResourceAsStream("/audio/$name")!!.use { it.readBytes() }
            val staging = temp.newFile("${fixture}.temp").apply { writeBytes(original) }
            val audio = AudioContainer.normalize(staging)
            assertEquals(extension, audio.extension)
            assertArrayEquals(original, audio.readBytes())
            assertFalse(staging.exists())
        }
    }
    @Test fun fakeContainerIsRejectedAndOriginalIsRetained() {
        val file = temp.newFile("fake.temp").apply { writeBytes("0000ftyp0000000000000000000000000000000000000".toByteArray()) }
        assertThrows(Exception::class.java) { AudioContainer.normalize(file) }
        assertTrue(file.exists())
    }
}
