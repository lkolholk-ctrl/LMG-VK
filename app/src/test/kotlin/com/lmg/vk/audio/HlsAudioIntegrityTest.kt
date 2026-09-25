package com.lmg.vk.audio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class HlsAudioIntegrityTest {
    @get:Rule val temp = TemporaryFolder()
    private fun bytes(name: String) = javaClass.getResourceAsStream("/audio/$name")!!.use { it.readBytes() }
    private fun fixture(codec: String) = temp.newFile("$codec.ts").apply { writeBytes(bytes("download-$codec.ts")) }

    @Test fun mp3DemuxPreservesEveryEncodedByte() {
        val out = temp.newFile("out.mp3")
        assertEquals(HlsDownloader.RemuxResult.Mp3, HlsDownloader.remuxTsToMp3(fixture("mp3"), out))
        assertArrayEquals(bytes("download-mp3.payload"), out.readBytes())
    }
    @Test fun aacIsDetectedAndPreservedInsteadOfMislabelledAsMp3() {
        val input = fixture("aac")
        val out = temp.newFile("out.aac")
        assertEquals(HlsDownloader.RemuxResult.Aac, HlsDownloader.remuxTsToMp3(input, temp.newFile("wrong.mp3")))
        assertTrue(HlsDownloader.extractAacStream(input, out))
        assertArrayEquals(bytes("download-aac.payload"), out.readBytes())
    }
    @Test fun truncatedTransportIsRejected() {
        val input = fixture("mp3")
        input.writeBytes(input.readBytes().dropLast(7).toByteArray())
        assertThrows(IOException::class.java) { HlsDownloader.remuxTsToMp3(input, temp.newFile("bad.mp3")) }
    }
    @Test fun brokenSyncCannotBecomeSuccessfulPartialSong() {
        val input = fixture("mp3")
        val content = input.readBytes()
        content[content.size - 188] = 0
        input.writeBytes(content)
        assertThrows(IOException::class.java) { HlsDownloader.remuxTsToMp3(input, temp.newFile("bad.mp3")) }
    }
}
