package com.lmg.vk.engine.lyrics

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object LocalTtmlStore {
    private fun cacheDir(context: Context): File = File(context.filesDir, "lyrics/ttml")

    fun read(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val file = cacheFile(context, title, artist, durationMs)
        if (file.isFile) {
            return runCatching { file.readText().takeIf(String::isNotBlank) }.getOrNull()
        }

        // Migrate the former case-sensitive/whole-second key without losing TTML
        // already downloaded by released builds.
        val legacy = legacyCacheFile(context, title, artist, durationMs)
        val raw = runCatching { legacy.takeIf(File::isFile)?.readText() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        write(context, title, artist, durationMs, raw)
        runCatching { legacy.delete() }
        return raw
    }

    fun delete(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
    ) {
        runCatching { cacheFile(context, title, artist, durationMs).delete() }
        runCatching { legacyCacheFile(context, title, artist, durationMs).delete() }
    }

    fun write(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        ttml: String,
    ) {
        if (ttml.isBlank()) return
        runCatching {
            val target = cacheFile(context, title, artist, durationMs)
            target.parentFile?.mkdirs()
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
    ): File {
        val identity = "${normalize(title)}\u0000${normalize(artist)}\u0000${durationMs.coerceAtLeast(0L)}"
        return File(cacheDir(context), "${sha256(identity)}.ttml")
    }

    private fun legacyCacheFile(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
    ): File {
        val identity = "$title\u0000$artist\u0000${durationMs / 1000L}"
        return File(cacheDir(context), "${sha256(identity)}.ttml")
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
