package com.lmg.vk.engine.lyrics

import android.content.Context
import java.io.File
import java.security.MessageDigest

object LocalTtmlStore {
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    private fun cacheDir(context: Context): File = File(context.filesDir, "lyrics/ttml")

    private fun prune(context: Context) {
        val folder = cacheDir(context)
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

    fun read(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val file = cacheFile(context, title, artist, durationMs)
        if (!file.isFile) return null
        return runCatching { file.readText().takeIf(String::isNotBlank) }.getOrNull()
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
            prune(context)
        }
    }

    private fun cacheFile(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
    ): File {
        val identity = "$title\u0000$artist\u0000${durationMs / 1000L}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir(context), "$digest.ttml")
    }
}
