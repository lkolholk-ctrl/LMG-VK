package com.lmg.vk.engine.lyrics

import android.content.Context
import com.lmg.vk.engine.lyrics.apple.DefaultAppleTtmlClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Independent TTML provider; Apple Music and the public LyricsPlus service remain separate. */
internal object LmgLyricsPlusProvider {
    const val BASE_URL = "https://qq.gsgit.org"
    private val source = LyricsSource.LMG_LYRICS_PLUS
    private val client by lazy {
        DefaultAppleTtmlClient(proxyBaseUrl = BASE_URL, logTag = source.id)
    }

    suspend fun load(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        language: String? = null,
        forceRefresh: Boolean = false,
    ): LyricsContent.RawTtml? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        if (forceRefresh) LocalTtmlStore.delete(context, title, artist, durationMs, source)
        else LocalTtmlStore.read(context, title, artist, durationMs, source)?.let {
            return@withContext content(it)
        }
        val raw = client.fetch(title, artist, durationMs, language).getOrNull()
            ?: return@withContext null
        LocalTtmlStore.write(context, title, artist, durationMs, raw, source)
        content(raw)
    }

    internal fun content(raw: String) = LyricsContent.RawTtml(raw, source.id, source.title)
}
