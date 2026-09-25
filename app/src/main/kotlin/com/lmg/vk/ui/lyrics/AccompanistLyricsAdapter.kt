package com.lmg.vk.ui.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.engine.lyrics.apple.AppleTimingType
import com.lmg.vk.engine.lyrics.apple.AppleTtmlParser
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import java.util.Locale

internal data class AccompanistLyrics(
    val synced: SyncedLyrics,
    val isSynced: Boolean,
    val source: String,
    val songwriters: List<String> = emptyList(),
)

/** Converts provider data once, off the UI thread; Accompanist owns layout and animation. */
internal object AccompanistLyricsAdapter {
    fun convert(content: LyricsContent, durationMs: Long = 0L): AccompanistLyrics = when (content) {
        is LyricsContent.RawTtml -> {
            val parsed = TTMLParser().parse(content.value)
            if (parsed.lines.isNotEmpty()) {
                AccompanistLyrics(parsed, true, content.sourceId, AppleTtmlParser.readSongwriters(content.value))
            } else {
                // Untimed TTML has no begin/end attributes for Accompanist's synced parser.
                val document = AppleTtmlParser.parse(content.value)
                if (document != null) convert(LyricsContent.Rich(document, content.sourceId, content.sourceLabel), durationMs)
                else AccompanistLyrics(parsed, false, content.sourceId)
            }
        }
        is LyricsContent.Legacy -> fromLegacy(content.lyrics, durationMs)
        is LyricsContent.Rich -> {
            val doc = content.document
            val lines = doc.allLines.map { line ->
                val main = layer(line.main)
                LyricsParser.LyricLine(
                    timeMs = line.beginMs,
                    endMs = line.endMs,
                    text = main.text,
                    words = if (doc.timing == AppleTimingType.WORD) main.words else emptyList(),
                    agentId = line.agentId,
                    backgroundLayers = if (line.background.isEmpty()) emptyList() else listOf(
                        layer(line.background).copy(
                            translations = layers(line.translationBackground),
                            pronunciations = layers(line.pronunciationBackground),
                        )
                    ),
                    translations = layers(line.translation),
                    pronunciations = layers(line.pronunciation),
                )
            }
            fromLegacy(
                LyricsParser.Lyrics(lines, doc.timing != AppleTimingType.NONE, null, null, content.sourceId,
                    songwriters = doc.songwriters.map { it.name }),
                durationMs.takeIf { it > 0 } ?: doc.durationMs,
            )
        }
    }

    fun fromLegacy(lyrics: LyricsParser.Lyrics, durationMs: Long = 0L): AccompanistLyrics {
        val timed = lyrics.isSynced && lyrics.lines.any { it.timeMs >= 0 }
        val lines = lyrics.lines.filter { it.text.isNotBlank() || it.backgroundLayers.isNotEmpty() }
            .let { if (timed) it.filter { line -> line.timeMs >= 0 }.sortedBy { line -> line.timeMs } else it }
        val primaryAgent = lines.firstNotNullOfOrNull { it.agentId }
        return AccompanistLyrics(
            synced = SyncedLyrics(lines.mapIndexed { index, line ->
                val start = if (timed) line.timeMs.lyricMillis() else 0
                val nextStart = lines.drop(index + 1).firstOrNull { it.timeMs > line.timeMs }?.timeMs
                val end = when {
                    !timed -> 1
                    line.endMs > line.timeMs -> line.endMs
                    line.words.lastOrNull()?.endMs?.let { it > line.timeMs } == true -> line.words.last().endMs
                    nextStart != null -> nextStart
                    durationMs > line.timeMs -> durationMs
                    else -> line.timeMs + 5000L
                }.lyricMillis().coerceAtLeast(start + 1)
                val translation = line.translations.preferred()?.text
                val phonetic = line.pronunciations.preferred()
                val alignment = if (line.agentId != null && line.agentId != primaryAgent) {
                    KaraokeAlignment.End
                } else KaraokeAlignment.Start
                if (line.words.isEmpty() && line.backgroundLayers.isEmpty() &&
                    alignment == KaraokeAlignment.Start && phonetic == null
                ) {
                    SyncedLine(line.text, translation, start, end)
                } else {
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables(line.text, line.words, start, end, phonetic),
                        translation = translation,
                        alignment = alignment,
                        start = start,
                        end = end,
                        phonetic = phonetic?.text,
                        accompanimentLines = line.backgroundLayers.map { bg ->
                            val bgStart = bg.words.firstOrNull()?.timeMs?.lyricMillis() ?: start
                            val bgEnd = (bg.words.lastOrNull()?.endMs?.takeIf { it > bgStart }
                                ?.lyricMillis() ?: end).coerceAtLeast(bgStart + 1)
                            val bgPhonetic = bg.pronunciations.preferred()
                            KaraokeLine.AccompanimentKaraokeLine(
                                syllables(bg.text, bg.words, bgStart, bgEnd, bgPhonetic),
                                bg.translations.preferred()?.text,
                                alignment, bgStart, bgEnd, bgPhonetic?.text,
                            )
                        }.takeIf { it.isNotEmpty() },
                    )
                }
            }, title = lyrics.title.orEmpty()),
            isSynced = timed,
            source = lyrics.source,
            songwriters = lyrics.songwriters.map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }.distinct(),
        )
    }

    private fun syllables(
        text: String,
        words: List<LyricsParser.LyricWord>,
        start: Int,
        end: Int,
        phonetic: LyricsParser.LyricLayer?,
    ): List<KaraokeSyllable> {
        if (words.isEmpty()) return listOf(KaraokeSyllable(text, start, end))
        var cursor = 0
        val result = mutableListOf<KaraokeSyllable>()
        words.forEachIndexed { index, word ->
            val wordStart = word.timeMs.lyricMillis()
            val wordEnd = (word.endMs.takeIf { it > word.timeMs }
                ?: words.getOrNull(index + 1)?.timeMs?.takeIf { it > word.timeMs }
                ?: end.toLong()).lyricMillis().coerceAtLeast(wordStart + 1)
            val from = if (word.charStart >= cursor && word.charEnd > word.charStart && word.charEnd <= text.length) {
                word.charStart
            } else text.indexOf(word.text, cursor).takeIf { it >= 0 } ?: cursor
            val to = if (word.charStart == from && word.charEnd in (from + 1)..text.length) {
                word.charEnd
            } else (from + word.text.length).coerceAtMost(text.length)
            // Keep punctuation and spaces from the full line; joining provider words loses them.
            if (from > cursor) result += KaraokeSyllable(text.substring(cursor, from), wordStart, wordStart + 1)
            if (to > from) result += KaraokeSyllable(
                text.substring(from, to), wordStart, wordEnd,
                phonetic?.words?.getOrNull(index)?.text,
            )
            cursor = to
        }
        if (cursor < text.length) result += KaraokeSyllable(text.substring(cursor), end - 1, end)
        return result.ifEmpty { listOf(KaraokeSyllable(text, start, end)) }
    }

    private fun layer(pieces: List<AppleLyricPiece>): LyricsParser.LyricLayer {
        var cursor = 0
        return LyricsParser.LyricLayer(
            text = pieces.joinToString("") { it.text },
            words = pieces.map { piece ->
                val from = cursor
                cursor += piece.text.length
                LyricsParser.LyricWord(piece.beginMs, piece.text, piece.endMs, from, cursor)
            },
        )
    }

    private fun layers(pieces: List<AppleLyricPiece>): Map<String, LyricsParser.LyricLayer> =
        pieces.groupBy { it.language.orEmpty() }.mapValues { layer(it.value) }

    private fun Map<String, LyricsParser.LyricLayer>.preferred(): LyricsParser.LyricLayer? {
        val locale = Locale.getDefault()
        return entries.firstOrNull { it.key.equals(locale.toLanguageTag(), true) }?.value
            ?: entries.firstOrNull { it.key.substringBefore('-').equals(locale.language, true) }?.value
            ?: values.firstOrNull()
    }
}

internal fun Long.lyricMillis(): Int = coerceIn(0L, Int.MAX_VALUE.toLong() - 1L).toInt()

internal fun ISyncedLine.lyricText(): String = when (this) {
    is KaraokeLine -> syllables.joinToString("") { it.content }
    is SyncedLine -> content
    else -> ""
}
