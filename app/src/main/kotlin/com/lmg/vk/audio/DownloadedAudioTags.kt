package com.lmg.vk.audio

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object DownloadedAudioTags {
    fun write(file: File, meta: Mp3TagWriter.Meta): Boolean {
        // An unavailable new cover must not erase artwork already embedded in the saved song.
        val existingCover = if (meta.coverBytes == null) runCatching {
            AudioFileIO.read(file).tag?.firstArtwork?.binaryData
        }.getOrNull() else null
        val existingLyrics = if (meta.lyrics.isNullOrBlank()) EmbeddedLyrics.read(file) else null
        val effective = meta.copy(coverBytes = meta.coverBytes ?: existingCover, lyrics = meta.lyrics?.takeIf(String::isNotBlank) ?: existingLyrics)
        if (file.extension.equals("mp3", ignoreCase = true)) return Mp3TagWriter.write(file, effective)
        if (file.extension.lowercase() !in setOf("m4a", "mp4", "flac", "ogg")) return false
        val temporary = File.createTempFile("tags-", ".${file.extension}", file.parentFile)
        return try {
            file.copyTo(temporary, overwrite = true)
            val audio = AudioFileIO.read(temporary)
            val tag = audio.tagOrCreateAndSetDefault
            listOf(FieldKey.TITLE to meta.title, FieldKey.ARTIST to meta.artist,
                FieldKey.ALBUM to meta.album, FieldKey.GENRE to meta.genre).forEach { (key, value) ->
                if (!value.isNullOrBlank()) tag.setField(key, value)
            }
            effective.lyrics?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.LYRICS, it) }
            effective.coverBytes?.let { bytes ->
                val mime = Id3Utils.sniffImageMime(bytes)
                if (mime != null && bytes.size <= Mp3TagWriter.MAX_COVER_BYTES) {
                    tag.deleteArtworkField()
                    tag.setField(TagArtwork().apply {
                        binaryData = bytes
                        mimeType = mime
                        pictureType = 3
                        description = ""
                    })
                }
            }
            audio.commit()
            Files.move(temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }
}
