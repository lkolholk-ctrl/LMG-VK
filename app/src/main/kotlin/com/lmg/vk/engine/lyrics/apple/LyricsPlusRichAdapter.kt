package com.lmg.vk.engine.lyrics.apple

import com.lmg.vk.engine.LyricsParser

/** Converts LyricsPlus timed syllables to the shared rich renderer model. */
object LyricsPlusRichAdapter {
    fun convert(lyrics: LyricsParser.Lyrics, durationMs: Long = 0L): AppleLyricsDocument? {
        if (!lyrics.isWordLevel || lyrics.lines.isEmpty()) return null

        val lines = lyrics.lines.mapIndexedNotNull { index, line ->
            val text = line.text
            if (text.isBlank()) return@mapIndexedNotNull null
            val nextBegin = lyrics.lines.getOrNull(index + 1)?.timeMs
            val lineBegin = line.timeMs.coerceAtLeast(0L)
            val lineEnd = line.endMs.takeIf { it > lineBegin }
                ?: line.words.maxOfOrNull { it.endMs }?.takeIf { it > lineBegin }
                ?: nextBegin?.takeIf { it > lineBegin }
                ?: (lineBegin + 2_000L)
            val pieces = buildPieces(
                lineId = "lyrics_plus_line_$index",
                text = text,
                words = line.words,
                lineBeginMs = lineBegin,
                lineEndMs = lineEnd,
            )
            if (pieces.none { !it.isWhitespace }) return@mapIndexedNotNull null

            AppleLyricsLine(
                id = "lyrics_plus_line_$index",
                key = line.lineKey,
                beginMs = lineBegin,
                endMs = lineEnd.coerceAtLeast(lineBegin + 1L),
                agentId = null,
                main = pieces,
            )
        }
        if (lines.isEmpty()) return null

        val documentEnd = maxOf(
            durationMs.coerceAtLeast(0L),
            lines.maxOf { it.endMs },
        )
        return AppleLyricsDocument(
            durationMs = documentEnd,
            language = lyrics.language,
            timing = AppleTimingType.WORD,
            sections = listOf(
                AppleLyricsSection(
                    beginMs = lines.first().beginMs,
                    endMs = lines.maxOf { it.endMs },
                    songPart = null,
                    lines = lines,
                )
            ),
            agents = emptyMap(),
            songwriters = lyrics.songwriters.map(::AppleSongwriter),
        )
    }

    private fun buildPieces(
        lineId: String,
        text: String,
        words: List<LyricsParser.LyricWord>,
        lineBeginMs: Long,
        lineEndMs: Long,
    ): List<AppleLyricPiece> {
        if (words.isEmpty()) {
            return listOf(piece(lineId, 0, text, lineBeginMs, lineEndMs))
        }

        val resolved = resolveRanges(text, words)
        if (resolved.isEmpty()) {
            return listOf(piece(lineId, 0, text, lineBeginMs, lineEndMs))
        }

        val result = mutableListOf<AppleLyricPiece>()
        var cursor = 0
        var order = 0
        var previousEndMs = lineBeginMs
        resolved.forEachIndexed { index, word ->
            if (word.start > cursor) {
                val gap = text.substring(cursor, word.start)
                result += piece(
                    lineId = lineId,
                    order = order++,
                    text = gap,
                    beginMs = previousEndMs,
                    endMs = word.timeMs.coerceAtLeast(previousEndMs + 1L),
                )
            }
            val nextStartMs = resolved.getOrNull(index + 1)?.timeMs
            val wordEndMs = word.endMs.takeIf { it > word.timeMs }
                ?: nextStartMs?.takeIf { it > word.timeMs }
                ?: lineEndMs
            result += piece(
                lineId = lineId,
                order = order++,
                text = text.substring(word.start, word.end),
                beginMs = word.timeMs,
                endMs = wordEndMs,
            )
            cursor = word.end
            previousEndMs = wordEndMs.coerceAtLeast(word.timeMs + 1L)
        }
        if (cursor < text.length) {
            result += piece(
                lineId = lineId,
                order = order,
                text = text.substring(cursor),
                beginMs = previousEndMs,
                endMs = lineEndMs.coerceAtLeast(previousEndMs + 1L),
            )
        }
        return result
    }

    private fun piece(
        lineId: String,
        order: Int,
        text: String,
        beginMs: Long,
        endMs: Long,
    ) = AppleLyricPiece(
        id = "${lineId}_piece_$order",
        text = text,
        beginMs = beginMs.coerceAtLeast(0L),
        endMs = endMs.coerceAtLeast(beginMs + 1L),
        agentId = null,
        role = ApplePieceRole.MAIN,
        isWhitespace = text.all { it.isWhitespace() },
        sourceOrder = order,
    )

    private fun resolveRanges(
        text: String,
        words: List<LyricsParser.LyricWord>,
    ): List<ResolvedWord> {
        var searchFrom = 0
        return buildList {
            words.forEach { word ->
                val explicitStart = word.charStart.takeIf { it in 0 until text.length }
                val start = explicitStart
                    ?: text.indexOf(word.text, searchFrom).takeIf { it >= 0 }
                    ?: return@forEach
                val explicitEnd = word.charEnd.takeIf { it in (start + 1)..text.length }
                val end = explicitEnd ?: (start + word.text.length).coerceAtMost(text.length)
                if (end <= start || start < searchFrom) return@forEach
                add(ResolvedWord(start, end, word.timeMs.coerceAtLeast(0L), word.endMs))
                searchFrom = end
            }
        }
    }

    private data class ResolvedWord(
        val start: Int,
        val end: Int,
        val timeMs: Long,
        val endMs: Long,
    )
}
