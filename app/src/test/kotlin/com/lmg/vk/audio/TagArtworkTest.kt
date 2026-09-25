package com.lmg.vk.audio

import org.junit.Assert.*
import org.junit.Test

class TagArtworkTest {
    @Test fun jpegBoundsDoNotDecodeAnAndroidBitmap() {
        val bytes = javaClass.getResourceAsStream("/audio/cover-fixture.jpg")!!.readBytes()
        assertEquals(64 to 48, imageDimensions(bytes))
        val artwork = TagArtwork().apply { binaryData = bytes }
        assertTrue(artwork.setImageFromData())
        assertEquals(64, artwork.width)
        assertEquals(48, artwork.height)
        assertArrayEquals(bytes, artwork.binaryData)
    }
    @Test fun truncatedJpegAndInvalidLengthsFailWithoutReadingOutsideInput() {
        val bytes = javaClass.getResourceAsStream("/audio/cover-fixture.jpg")!!.readBytes()
        assertNull(imageDimensions(bytes.copyOf(16)))
        assertNull(imageDimensions(ByteArray(20).apply { this[0]=255.toByte();this[1]=216.toByte();this[2]=255.toByte();this[3]=224.toByte() }))
        assertNull(imageDimensions(ByteArray(0)))
    }
}
