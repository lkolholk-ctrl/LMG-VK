package com.lmg.vk.engine.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument

sealed interface LyricsContent {
    /**
     * Untouched Apple Music TTML, parsed by Accompanist at the display boundary.
     * Keep the original document in the cache to preserve all timing and vocal layers.
     */
    data class RawTtml(
        val value: String,
        val sourceId: String,
        val sourceLabel: String,
    ) : LyricsContent

    data class Rich(
        val document: AppleLyricsDocument,
        val sourceId: String,
        val sourceLabel: String,
    ) : LyricsContent

    data class Legacy(
        val lyrics: LyricsParser.Lyrics
    ) : LyricsContent
}
