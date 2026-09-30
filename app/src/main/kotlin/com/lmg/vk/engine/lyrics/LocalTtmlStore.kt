package com.lmg.vk.engine.lyrics

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object LocalTtmlStore {
    internal fun cacheDir(filesDir: File, source: LyricsSource): File = when (source) {
        LyricsSource.APPLE_TTML -> File(filesDir, "lyrics/ttml")
        LyricsSource.LMG_LYRICS_PLUS -> File(filesDir, "lyrics/lmg_lyrics_plus")
        else -> error("Unsupported TTML cache source: $source")
    }

    fun read(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        source: LyricsSource = LyricsSource.APPLE_TTML,
    ): String? {
        val file = cacheFile(context, title, artist, durationMs, source)
        if (file.isFile) {
            com.lmg.vk.engine.CacheCatalog.remember(context, file.absolutePath, title, artist, durationMs.toString())
            return runCatching { file.readText().takeIf(String::isNotBlank) }.getOrNull()
        }

        if (source != LyricsSource.APPLE_TTML) return null

        // Migrate the former case-sensitive/whole-second key without losing TTML
        // already downloaded by released builds.
        val legacy = legacyCacheFile(context, title, artist, durationMs, source)
        val raw = runCatching { legacy.takeIf(File::isFile)?.readText() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        write(context, title, artist, durationMs, raw, source)
        runCatching { legacy.delete() }
        return raw
    }

    fun delete(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        source: LyricsSource = LyricsSource.APPLE_TTML,
    ) {
        runCatching { cacheFile(context, title, artist, durationMs, source).delete() }
        runCatching { legacyCacheFile(context, title, artist, durationMs, source).delete() }
    }

    fun write(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        ttml: String,
        source: LyricsSource = LyricsSource.APPLE_TTML,
    ) {
        if (ttml.isBlank()) return
        runCatching {
            val target = cacheFile(context, title, artist, durationMs, source)
            target.parentFile?.mkdirs()
            com.lmg.vk.engine.CacheCatalog.remember(context, target.absolutePath, title, artist, durationMs.toString())
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(ttml)
            if (!temporary.renameTo(target)) {
                target.writeText(ttml)
                temporary.delete()
            }
        }
    }

    private fun cacheFile(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        source: LyricsSource = LyricsSource.APPLE_TTML,
    ): File {
        val identity = "${normalize(title)}\u0000${normalize(artist)}\u0000${durationMs.coerceAtLeast(0L)}"
        return File(cacheDir(context.filesDir, source), "${sha256(identity)}.ttml")
    }

    private fun legacyCacheFile(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        source: LyricsSource = LyricsSource.APPLE_TTML,
    ): File {
        val identity = "$title\u0000$artist\u0000${durationMs / 1000L}"
        return File(cacheDir(context.filesDir, source), "${sha256(identity)}.ttml")
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
