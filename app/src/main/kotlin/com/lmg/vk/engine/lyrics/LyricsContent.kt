package com.lmg.vk.engine.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument

sealed interface LyricsContent {
    data class Rich(
        val document: AppleLyricsDocument,
        val sourceId: String,
        val sourceLabel: String,
    ) : LyricsContent

    data class Legacy(
        val lyrics: LyricsParser.Lyrics
    ) : LyricsContent
}
