package com.lmg.vk.audio

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

class DownloadedAudioTagsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(ext: String): File = temporary.newFile("track.$ext").apply {
        DownloadedAudioTagsTest::class.java.getResourceAsStream("/audio/tag-fixture.$ext")!!.use { input ->
            outputStream().use { input.copyTo(it) }
        }
    }

    private fun cover(seed: Long, size: Int = 32): ByteArray {
        val random = java.util.Random(seed)
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { compressed ->
            val row = ByteArray(size * 3)
            repeat(size) {
                random.nextBytes(row)
                compressed.write(0)
                compressed.write(row)
            }
        }
        val header = ByteArrayOutputStream()
        DataOutputStream(header).use {
            it.writeInt(size)
            it.writeInt(size)
            it.write(byteArrayOf(8, 2, 0, 0, 0))
        }
        val png = ByteArrayOutputStream()
        DataOutputStream(png).use { output ->
            output.write(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
            fun chunk(type: String, data: ByteArray) {
                val name = type.toByteArray(Charsets.US_ASCII)
                val crc = CRC32().apply { update(name); update(data) }
                output.writeInt(data.size)
                output.write(name)
                output.write(data)
                output.writeInt(crc.value.toInt())
            }
            chunk("IHDR", header.toByteArray())
            chunk("IDAT", raw.toByteArray())
            chunk("IEND", byteArrayOf())
        }
        return png.toByteArray()
    }

    private fun meta(cover: ByteArray?) = Mp3TagWriter.Meta(
        title = "Песня", artist = "Исполнитель", album = "Альбом", year = null,
        trackNumber = null, genre = null, lyrics = null, comment = null, coverBytes = cover,
    )

    private fun mp3Audio(file: File): ByteArray {
        val start = Id3Utils.id3v2Length(file).toInt()
        val end = (file.length() - Id3Utils.id3v1Length(file)).toInt()
        return file.readBytes().copyOfRange(start, end)
    }

    private fun m4aAudio(file: File): ByteArray {
        val bytes = file.readBytes()
        fun int(offset: Int) = ByteBuffer.wrap(bytes, offset, 4).int
        fun find(type: String, start: Int = 0, end: Int = bytes.size): Int? {
            var offset = start
            while (offset + 8 <= end) {
                val length = int(offset)
                require(length >= 8 && offset + length <= end)
                val name = String(bytes, offset + 4, 4, Charsets.US_ASCII)
                if (name == type) return offset + 8
                if (name in setOf("moov", "trak", "mdia", "minf", "stbl")) {
                    find(type, offset + 8, offset + length)?.let { return it }
                }
                offset += length
            }
            return null
        }
        val stco = find("stco")
        val chunks = stco ?: requireNotNull(find("co64"))
        assertEquals(1, int(chunks + 4))
        val offset = if (stco != null) int(chunks + 8)
            else ByteBuffer.wrap(bytes, chunks + 8, 8).long.toInt()
        val stsz = requireNotNull(find("stsz"))
        val fixedSize = int(stsz + 4)
        val count = int(stsz + 8)
        val length = if (fixedSize > 0) fixedSize * count
            else (0 until count).sumOf { int(stsz + 12 + it * 4) }
        return bytes.copyOfRange(offset, offset + length)
    }

    @Test fun mp3EmbedsOriginalImageLargerThanOldLimitAndPreservesAudio() {
        val file = fixture("mp3")
        val payload = mp3Audio(file)
        val image = cover(7, 1024)
        assertTrue(image.size > 2 * 1024 * 1024)
        assertTrue(DownloadedAudioTags.write(file, meta(image)))
        val tag = AudioFileIO.read(file).tag
        assertArrayEquals(image, tag.firstArtwork.binaryData)
        assertEquals("Песня", tag.getFirst(FieldKey.TITLE))
        assertArrayEquals(payload, mp3Audio(file))
    }

    @Test fun m4aEmbedsOriginalImageWithoutChangingEncodedAudio() {
        val file = fixture("m4a")
        val payload = m4aAudio(file)
        val image = cover(3)
        assertTrue(DownloadedAudioTags.write(file, meta(image)))
        val tag = AudioFileIO.read(file).tag
        assertArrayEquals(image, tag.firstArtwork.binaryData)
        assertEquals("Исполнитель", tag.getFirst(FieldKey.ARTIST))
        assertArrayEquals(payload, m4aAudio(file))
    }

    @Test fun replacingCoverLeavesOnlyNewArtworkInBothFormats() {
        for (ext in listOf("mp3", "m4a")) {
            val file = fixture(ext)
            assertTrue(DownloadedAudioTags.write(file, meta(cover(1))))
            val selected = cover(2)
            assertTrue(DownloadedAudioTags.write(file, meta(selected)))
            val tag = AudioFileIO.read(file).tag
            assertEquals(1, tag.artworkList.size)
            assertArrayEquals(selected, tag.firstArtwork.binaryData)
        }
    }

    @Test fun failedContainerWritePreservesOriginalFile() {
        val file = temporary.newFile("broken.m4a")
        val original = "not an audio container".toByteArray()
        file.writeBytes(original)
        assertFalse(DownloadedAudioTags.write(file, meta(cover(1))))
        assertArrayEquals(original, file.readBytes())
        assertEquals(listOf(file.name), temporary.root.list()!!.toList())
    }

    @Test fun missingNewCoverDoesNotEraseExistingArtworkOnRetry() {
        for (ext in listOf("mp3", "m4a")) {
            val file = fixture(ext)
            val image = cover(9)
            assertTrue(DownloadedAudioTags.write(file, meta(image)))
            assertTrue(DownloadedAudioTags.write(file, meta(null)))
            assertArrayEquals(image, AudioFileIO.read(file).tag.firstArtwork.binaryData)
        }
    }

    @Test fun absentCoverStillWritesTextTags() {
        for (ext in listOf("mp3", "m4a")) {
            val file = fixture(ext)
            assertTrue(DownloadedAudioTags.write(file, meta(null)))
            assertEquals("Песня", AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE))
        }
    }

    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/metadata" itunes:timing="Word" xml:lang="pt">
      <body dur="00:03.000"><div><p begin="00:01.000" end="00:02.500" ttm:agent="v1"><span begin="00:01.000" end="00:01.450">SÃO</span> <span begin="00:01.500" end="00:02.100">PAULO</span><span ttm:role="x-bg"><span begin="00:02.100" end="00:02.500">Эй</span></span></p></div></body></tt>"""

    @Test fun ttmlRoundTripKeepsXmlArtworkAndEncodedAudio() {
        for (ext in listOf("mp3", "m4a", "flac", "ogg")) {
            val file = fixture(ext)
            val audio = when (ext) { "mp3" -> mp3Audio(file); "m4a" -> m4aAudio(file); else -> null }
            val image = cover(23)
            assertNotNull(EmbeddedLyrics.validTtml(ttml))
            assertTrue("write TTML to $ext", DownloadedAudioTags.write(file, meta(image).copy(lyrics = ttml)))
            assertEquals(ttml, EmbeddedLyrics.read(file))
            assertArrayEquals(image, AudioFileIO.read(file).tag.firstArtwork.binaryData)
            if (audio != null) assertArrayEquals(audio, if (ext == "mp3") mp3Audio(file) else m4aAudio(file))
            // A cover repair without a new lyric response must keep the existing XML.
            assertTrue(DownloadedAudioTags.write(file, meta(null)))
            assertEquals(ttml, EmbeddedLyrics.read(file))
            val opaque = File(temporary.root, "opaque-$ext.audio")
            file.copyTo(opaque)
            assertEquals(ttml, EmbeddedLyrics.read(opaque))
            val projected = com.lmg.vk.engine.LyricsParser.parseLyrics(EmbeddedLyrics.read(opaque)!!)
            assertEquals(1000L, projected.lines.first().timeMs)
            assertEquals("SÃO", projected.lines.first().words.first().text)
            assertEquals(1450L, projected.lines.first().words.first().endMs)
        }
    }

    @Test fun invalidMarkupAndExternalEntitiesAreNotLyrics() {
        assertNull(EmbeddedLyrics.validTtml("<html>Access denied</html>"))
        assertNull(EmbeddedLyrics.validTtml("<tt><body>broken"))
        assertNull(EmbeddedLyrics.validTtml("<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///missing'>]>" + ttml))
        assertNull(EmbeddedLyrics.validTtml("x".repeat(EmbeddedLyrics.MAX_TEXT_LENGTH + 1)))
        assertTrue(com.lmg.vk.engine.LyricsParser.parseLyrics("<html>Denied</html>").lines.isEmpty())
        assertEquals("Обычный текст", com.lmg.vk.engine.LyricsParser.parseLyrics("[00:01.00]Обычный текст").lines.first().text)
    }
}
