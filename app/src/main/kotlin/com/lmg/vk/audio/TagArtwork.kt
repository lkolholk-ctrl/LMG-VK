package com.lmg.vk.audio

import org.jaudiotagger.tag.images.AndroidArtwork

/** Tag containers need image dimensions, not a decoded multi-megapixel bitmap. */
internal class TagArtwork : AndroidArtwork() {
    override fun setImageFromData(): Boolean {
        val dimensions = imageDimensions(binaryData ?: return false) ?: return false
        width = dimensions.first
        height = dimensions.second
        return true
    }
}

internal fun imageDimensions(bytes: ByteArray): Pair<Int, Int>? {
    fun u8(i: Int) = bytes[i].toInt() and 255
    fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
    fun u32(i: Int) = (u16(i) shl 16) or u16(i + 2)
    fun valid(w: Int, h: Int) = if (w > 0 && h > 0) w to h else null
    when (Id3Utils.sniffImageMime(bytes)) {
        "image/png" -> {
            if (bytes.size < 33 || String(bytes, 12, 4, Charsets.US_ASCII) != "IHDR" || u32(8) != 13) return null
            return valid(u32(16), u32(20))
        }
        "image/jpeg" -> {
            var offset = 2
            while (offset + 1 < bytes.size) {
                if (u8(offset++) != 255) return null
                while (offset < bytes.size && u8(offset) == 255) offset++
                if (offset >= bytes.size) return null
                val marker = u8(offset++)
                if (marker == 0xDA || marker == 0xD9) return null
                if (marker == 0x01 || marker in 0xD0..0xD8) continue
                if (offset + 2 > bytes.size) return null
                val length = u16(offset)
                if (length < 2 || length > bytes.size - offset) return null
                if (marker in 0xC0..0xCF && marker !in setOf(0xC4, 0xC8, 0xCC)) {
                    if (length < 8) return null
                    return valid(u16(offset + 5), u16(offset + 3))
                }
                offset += length
            }
        }
    }
    return null
}
