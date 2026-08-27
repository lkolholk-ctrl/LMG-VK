package com.lmg.vk.engine.lyrics

import android.content.Context
import android.net.Uri
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.ApplePieceRole
import com.lmg.vk.engine.lyrics.apple.AppleTtmlCache
import com.lmg.vk.engine.lyrics.apple.AppleTtmlClient
import com.lmg.vk.engine.lyrics.apple.AppleTtmlParser
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
        forceRefresh: Boolean = false
    ): LyricsContent = withContext(Dispatchers.IO) {
        if (title.isNotBlank()) {
            val enabledSources = LyricsSourceStore.enabled(context)
            val appleEnabled = LyricsSource.APPLE_TTML in enabledSources

            if (appleEnabled) {
                // 1. Check persistent local Apple TTML cache
                if (!forceRefresh) {
                    val cachedTtml = AppleTtmlCache.read(context, title, artist, durationMs, language)
                    if (!cachedTtml.isNullOrBlank()) {
                        val parsed = AppleTtmlParser.parse(cachedTtml)
                        if (parsed != null) {
                            DebugLog.add("LyricsRepository: Apple TTML cache hit lines=${parsed.allLines.size}")
                            return@withContext LyricsContent.Apple(document = parsed, sourceLabel = "Apple TTML")
                        } else {
                            DebugLog.add("LyricsRepository: cached Apple TTML invalid, deleting cache entry")
                            AppleTtmlCache.delete(context, title, artist, durationMs, language)
                        }
                    }
                }

                // 2. Network fetch Apple TTML
                val fetchResult = ttmlClient.fetch(title, artist, durationMs, language)
                fetchResult.getOrNull()?.let { rawTtml ->
                    val parsed = AppleTtmlParser.parse(rawTtml)
                    if (parsed != null && parsed.allLines.isNotEmpty()) {
                        DebugLog.add("LyricsRepository: Apple TTML network success lines=${parsed.allLines.size}")
                        AppleTtmlCache.write(context, title, artist, durationMs, language, rawTtml)
                        return@withContext LyricsContent.Apple(document = parsed, sourceLabel = "Apple TTML")
                    } else {
                        DebugLog.add("LyricsRepository: Apple TTML network parse empty or failed")
                    }
                }
            }
        }

        // 3. Fallback to existing Legacy LyricsParser
        DebugLog.add("LyricsRepository: falling back to Legacy LyricsParser")
        val legacy = LyricsParser.loadLyrics(
            context = context,
            uri = uri,
            title = title,
            artist = artist,
            durationMs = durationMs,
            trackId = trackId
        )

        LyricsContent.Legacy(lyrics = legacy)
    }

    /**
     * Converts an [AppleLyricsDocument] into a legacy [LyricsParser.Lyrics] projection
     * for components like WaveHomeScreen that only require a simple line projection.
     */
    fun toLegacyProjection(doc: AppleLyricsDocument, title: String? = null, artist: String? = null): LyricsParser.Lyrics {
        val legacyLines = doc.allLines.map { line ->
            val mainPieces = line.main.filter { !it.isWhitespace }
            val words = mainPieces.map { piece ->
                LyricsParser.LyricWord(
                    timeMs = piece.beginMs,
                    text = piece.text,
                    endMs = piece.endMs
                )
            }
            val lineText = line.main.joinToString("") { it.text }.trim()
            val bgText = line.background.joinToString("") { it.text }.trim()
            val fullText = if (bgText.isNotEmpty()) "$lineText ($bgText)" else lineText

            LyricsParser.LyricLine(
                timeMs = line.beginMs,
                text = fullText,
                words = words,
                endMs = line.endMs
            )
        }

        return LyricsParser.Lyrics(
            lines = legacyLines,
            isSynced = true,
            title = title,
            artist = artist,
            source = LyricsSource.APPLE_TTML.id
        )
    }
}
