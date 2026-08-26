package com.lmg.vk.engine.lyrics

import android.content.Context
import java.io.File
import java.security.MessageDigest

object LocalTtmlStore {
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
        return File(context.filesDir, "lyrics/ttml/$digest.ttml")
    }
}
