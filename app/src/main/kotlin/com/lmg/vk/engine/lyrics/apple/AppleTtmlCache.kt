package com.lmg.vk.engine.lyrics.apple

import android.content.Context
import java.io.File
import java.security.MessageDigest

object AppleTtmlCache {
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024 // 50 MB

    private fun storageDir(context: Context): File = File(context.filesDir, "lyrics/apple_ttml")

    fun read(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null
    ): String? {
        if (title.isBlank()) return null
        val file = fileFor(context, title, artist, durationMs, language)
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            runCatching { file.delete() }
            return null
        }
        return runCatching { file.readText().takeIf { it.isNotBlank() } }.getOrNull()
    }

    fun write(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null,
        rawTtml: String
    ) {
        if (title.isBlank() || rawTtml.isBlank()) return
        runCatching {
            val target = fileFor(context, title, artist, durationMs, language)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(rawTtml)
            if (!temp.renameTo(target)) {
                target.writeText(rawTtml)
                temp.delete()
            }
            prune(context)
        }
    }

    fun delete(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null
    ) {
        runCatching { fileFor(context, title, artist, durationMs, language).delete() }
    }

    private fun prune(context: Context) {
        val folder = storageDir(context)
        val files = folder.listFiles()?.filter(File::isFile) ?: return
        val now = System.currentTimeMillis()
        files.filter { now - it.lastModified() > MAX_AGE_MS }.forEach { runCatching { it.delete() } }
        val live = files.filter { it.exists() }
        var total = live.sumOf { it.length() }
        if (total <= MAX_TOTAL_BYTES) return
        val oldestFirst = live.sortedBy { it.lastModified() }
        var index = 0
        while (total > MAX_TOTAL_BYTES && index < oldestFirst.size) {
            total -= oldestFirst[index].length()
            runCatching { oldestFirst[index].delete() }
            index += 1
        }
    }

    private fun fileFor(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String?
    ): File {
        val identity = "${title.trim().lowercase()}|${artist.trim().lowercase()}|${durationMs / 1000L}|${language.orEmpty().trim().lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(storageDir(context), "$digest.ttml")
    }
}
