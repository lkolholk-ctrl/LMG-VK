package com.lmg.vk.engine.lyrics.apple

import android.content.Context
import com.lmg.vk.engine.lyrics.LocalTtmlStore
import java.io.File
import java.security.MessageDigest

object AppleTtmlCache {
    /**
     * Apple rich lyrics and the legacy projection intentionally share one raw TTML store.
     * The language argument remains in the rich API for a future catalog-id cache key, but
     * the current proxy returns all localizations in one TTML document.
     */
    fun read(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null
    ): String? {
        LocalTtmlStore.read(context, title, artist, durationMs)?.let { return it }

        // One-time migration from the short-lived rich cache path used by the first
        // implementation. It is never written again after migration.
        val previous = previousRichFile(context, title, artist, durationMs, language)
        val raw = runCatching { previous.takeIf(File::isFile)?.readText() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        LocalTtmlStore.write(context, title, artist, durationMs, raw)
        runCatching { previous.delete() }
        return raw
    }

    fun write(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null,
        rawTtml: String
    ) = LocalTtmlStore.write(context, title, artist, durationMs, rawTtml)

    fun delete(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null
    ) {
        LocalTtmlStore.delete(context, title, artist, durationMs)
        runCatching { previousRichFile(context, title, artist, durationMs, language).delete() }
    }

    private fun previousRichFile(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String?,
    ): File {
        val identity = "${title.trim().lowercase()}|${artist.trim().lowercase()}|" +
            "${durationMs / 1000L}|${language.orEmpty().trim().lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "lyrics/apple_ttml/$digest.ttml")
    }
}
