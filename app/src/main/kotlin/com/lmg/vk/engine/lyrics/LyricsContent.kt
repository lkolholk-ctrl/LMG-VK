package com.lmg.vk.engine.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument

sealed interface LyricsContent {
    data class Apple(
        val document: AppleLyricsDocument,
        val sourceLabel: String = "Apple TTML"
    ) : LyricsContent

    data class Legacy(
        val lyrics: LyricsParser.Lyrics
    ) : LyricsContent
}
