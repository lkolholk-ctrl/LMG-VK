package com.lmg.vk.engine.lyrics

import android.content.Context
import android.net.Uri
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.AppleLyricsProjector
import com.lmg.vk.engine.lyrics.apple.AppleTtmlCache
import com.lmg.vk.engine.lyrics.apple.AppleTtmlClient
import com.lmg.vk.engine.lyrics.apple.DefaultAppleTtmlClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LyricsRepository {

    private val ttmlClient: AppleTtmlClient = DefaultAppleTtmlClient()

    suspend fun load(
        context: Context,
        uri: Uri?,
        title: String,
        artist: String,
        durationMs: Long = 0L,
        trackId: String? = null,
        language: String? = null,
        forceRefresh: Boolean = false,
        enabledSources: Set<LyricsSource>? = null,
    ): LyricsContent = withContext(Dispatchers.IO) {
        // Saved/downloaded audio owns its lyrics even when all network providers are disabled.
        val localUri = uri?.takeIf { it.scheme == "content" || it.scheme == "file" }
            ?: trackId?.let { com.lmg.vk.data.local.db.FavoriteTrackDatabase.getInstance(context).getDownloadedTrack(it) }
                ?.localPath?.let(com.lmg.vk.data.local.PublicDownloads::toPlayableUri)
        localUri?.let { local ->
            com.lmg.vk.audio.EmbeddedLyrics.read(context, local)?.let { raw ->
                com.lmg.vk.audio.EmbeddedLyrics.validTtml(raw)?.let {
                    return@withContext LyricsContent.RawTtml(it, "embedded", "Встроенный TTML")
                }
                LyricsParser.parseLyrics(raw).takeIf { it.lines.isNotEmpty() }?.let {
                    return@withContext LyricsContent.Legacy(it.copy(source = "embedded"))
                }
            }
        }
        val enabled = enabledSources ?: LyricsSourceStore.enabled(context)
        if (title.isNotBlank()) {
            firstPreferredLyrics(enabled) { source ->
                when (source) {
                    LyricsSource.APPLE_TTML -> loadPrimary(context, title, artist, durationMs, language, forceRefresh)
                    LyricsSource.BINI_LYRICS -> ExternalLyricsRepository.fetchBiniLyricsContent(title, artist, durationMs)
                    LyricsSource.LYRICS_PLUS -> ExternalLyricsRepository.fetchLyricsPlusContent(title, artist, durationMs)
                    LyricsSource.LRCLIB -> LyricsParser.fetchLrcLib(context, uri, title, artist, durationMs, trackId)
                        .takeIf { it.lines.isNotEmpty() }?.let { LyricsContent.Legacy(it) }
                    else -> null
                }
            }?.let { return@withContext it }
        }

        // Optional providers and embedded/VK fallback run only after the preferred sources.
        DebugLog.add("LyricsRepository: falling back to Legacy LyricsParser")
        val legacy = LyricsParser.loadLyrics(
            context = context,
            uri = uri,
            title = title,
            artist = artist,
            durationMs = durationMs,
            trackId = trackId,
            excludedSources = preferredLyricsSources.toSet(),
            preferExternalBeforeOfficial = true,
            sourceSnapshot = enabled,
        )

        LyricsContent.Legacy(lyrics = legacy)
    }

    private suspend fun loadPrimary(
        context: Context, title: String, artist: String, durationMs: Long,
        language: String?, forceRefresh: Boolean,
    ): LyricsContent.RawTtml? {
        if (forceRefresh) AppleTtmlCache.delete(context, title, artist, durationMs, language)
        if (!forceRefresh) {
            AppleTtmlCache.read(context, title, artist, durationMs, language)?.takeIf { it.isNotBlank() }?.let {
                return LyricsContent.RawTtml(it, LyricsSource.APPLE_TTML.id, LyricsSource.APPLE_TTML.title)
            }
        }
        val raw = ttmlClient.fetch(title, artist, durationMs, language).getOrNull() ?: return null
        AppleTtmlCache.write(context, title, artist, durationMs, language, raw)
        return LyricsContent.RawTtml(raw, LyricsSource.APPLE_TTML.id, LyricsSource.APPLE_TTML.title)
    }

    /**
     * Converts an [AppleLyricsDocument] into a legacy [LyricsParser.Lyrics] projection
     * for components like WaveHomeScreen that only require a simple line projection.
     */
    fun toLegacyProjection(
        doc: AppleLyricsDocument,
        title: String? = null,
        artist: String? = null,
        source: String = LyricsSource.APPLE_TTML.id,
    ): LyricsParser.Lyrics {
        return AppleLyricsProjector.toLegacy(doc, title, artist, source)
    }
}
