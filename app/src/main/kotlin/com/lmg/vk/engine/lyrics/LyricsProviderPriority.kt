package com.lmg.vk.engine.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser

internal val preferredLyricsSources = listOf(
    LyricsSource.APPLE_TTML, LyricsSource.LMG_LYRICS_PLUS, LyricsSource.BINI_LYRICS, LyricsSource.LYRICS_PLUS, LyricsSource.LRCLIB,
)

internal suspend fun firstPreferredLyrics(
    enabled: Set<LyricsSource>,
    fetch: suspend (LyricsSource) -> LyricsContent?,
): LyricsContent? {
    for (source in preferredLyricsSources) {
        if (source !in enabled) continue
        val content = fetch(source) ?: continue
        // An Apple line-synced/untimed result must not hide QQ's word timings.
        // Preserve it as the fallback if LMG is unavailable or has no richer result.
        if (source == LyricsSource.APPLE_TTML &&
            LyricsSource.LMG_LYRICS_PLUS in enabled && !content.hasWordTiming()) {
            val lmg = fetch(LyricsSource.LMG_LYRICS_PLUS)
            return lmg?.takeIf { it.hasWordTiming() } ?: content
        }
        return content
    }
    return null
}

internal fun LyricsContent.hasWordTiming(): Boolean = when (this) {
    is LyricsContent.Legacy -> lyrics.isWordLevel
    is LyricsContent.Rich -> document.timing == com.lmg.vk.engine.lyrics.apple.AppleTimingType.WORD
    is LyricsContent.RawTtml -> TTMLParser().parse(value).lines.any { it is KaraokeLine }
}
