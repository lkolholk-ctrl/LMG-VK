package com.lmg.vk.engine.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser

internal val preferredLyricsSources = listOf(
    LyricsSource.APPLE_TTML, LyricsSource.BINI_LYRICS, LyricsSource.LYRICS_PLUS, LyricsSource.LRCLIB,
)

internal suspend fun firstPreferredLyrics(
    enabled: Set<LyricsSource>,
    fetch: suspend (LyricsSource) -> LyricsContent?,
): LyricsContent? {
    for (source in preferredLyricsSources) {
        if (source in enabled) fetch(source)?.let { return it }
    }
    return null
}

internal fun LyricsContent.hasWordTiming(): Boolean = when (this) {
    is LyricsContent.Legacy -> lyrics.isWordLevel
    is LyricsContent.Rich -> document.timing == com.lmg.vk.engine.lyrics.apple.AppleTimingType.WORD
    is LyricsContent.RawTtml -> TTMLParser().parse(value).lines.any { it is KaraokeLine }
}
