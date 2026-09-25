package com.lmg.vk.audio

import android.content.Context
import android.net.Uri
import com.lmg.vk.engine.lyrics.apple.AppleTtmlParser
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Preserve the original XML, including word timing, backing vocals and translations. */
internal object EmbeddedLyrics {
    const val MAX_TEXT_LENGTH = 2 * 1024 * 1024

    fun validTtml(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > MAX_TEXT_LENGTH) return null
        val text = raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!text.startsWith("<") || !Regex("<(?:[\\w.-]+:)?tt(?:\\s|>)").containsMatchIn(text)) return null
        return raw.takeIf { AppleTtmlParser.parse(text) != null }
    }

    fun read(file: File): String? = runCatching {
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        val magic = String(header, 0, 4, Charsets.US_ASCII)
        val extension = when {
            String(header, 4, 4, Charsets.US_ASCII) == "ftyp" -> "m4a"
            magic == "fLaC" -> "flac"
            magic == "OggS" -> "ogg"
            magic.startsWith("ID3") || (header[0].toInt() and 255 == 255 && header[1].toInt() and 224 == 224) -> "mp3"
            else -> file.extension
        }
        AudioFileIO.readAs(file, extension).tag?.getFirst(FieldKey.LYRICS)
            ?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_LENGTH }
    }.getOrNull()

    /** MediaStore URIs often have no extension; detect their actual container by bytes. */
    fun read(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.let(::read)
        if (uri.scheme != "content") return null
        val temp = File.createTempFile("embedded-lyrics-", ".audio", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            read(temp)
        } catch (_: Exception) { null } finally { temp.delete() }
    }
}
