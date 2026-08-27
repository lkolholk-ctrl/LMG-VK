package com.lmg.vk.engine.lyrics.apple

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.LyricsSource

/** Lossless rich document stays canonical; this is only a simple-text consumer view. */
object AppleLyricsProjector {
    fun toLegacy(
        document: AppleLyricsDocument,
        title: String? = null,
        artist: String? = null,
    ): LyricsParser.Lyrics {
        val lines = document.allLines.map { line ->
            val words = line.main.filterNot { it.isWhitespace }.map { piece ->
                LyricsParser.LyricWord(
                    timeMs = piece.beginMs,
                    text = piece.text,
                    endMs = piece.endMs,
                )
            }
            val mainText = line.main.joinToString("") { it.text }.trim()
            val backgroundText = line.background.joinToString("") { it.text }.trim()
            LyricsParser.LyricLine(
                timeMs = line.beginMs,
                text = if (backgroundText.isEmpty()) mainText else "$mainText ($backgroundText)",
                words = words,
                endMs = line.endMs,
                agentId = line.agentId,
                songPart = document.sections.firstOrNull { line in it.lines }?.songPart,
                lineKey = line.key,
            )
        }
        return LyricsParser.Lyrics(
            lines = lines,
            isSynced = true,
            title = title,
            artist = artist,
            source = LyricsSource.APPLE_TTML.id,
            language = document.language,
            timing = document.timing.name,
            songwriters = document.songwriters.map { it.name },
        )
    }
}
