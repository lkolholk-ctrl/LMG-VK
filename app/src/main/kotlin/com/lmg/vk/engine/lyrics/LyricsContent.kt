package com.lmg.vk.engine.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument

sealed interface LyricsContent {
    /**
     * Untouched Apple Music TTML. It is deliberately parsed only by AMLL in
     * the WebView so the native compatibility projection cannot reject syntax
     * that AMLL supports.
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
