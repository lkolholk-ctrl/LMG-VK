package com.lmg.vk.ui.lyrics.apple

import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece

data class AppleRenderGroup(
    val id: String,
    val pieces: List<AppleLyricPiece>,
    val nonBreaking: Boolean,
) {
    val timedPieces: List<AppleLyricPiece> = pieces.filterNot { it.isWhitespace }
    val beginMs: Long = timedPieces.minOfOrNull { it.beginMs } ?: 0L
    val endMs: Long = timedPieces.maxOfOrNull { it.endMs } ?: beginMs + 1L
    val durationMs: Long = (endMs - beginMs).coerceAtLeast(1L)
    val text: String = timedPieces.joinToString("") { it.text }
}

/** Keep TTML syllables between explicit whitespace boundaries in one visual group. */
fun buildAppleRenderGroups(pieces: List<AppleLyricPiece>): List<AppleRenderGroup> {
    if (pieces.isEmpty()) return emptyList()
    val groups = mutableListOf<AppleRenderGroup>()
    val pending = mutableListOf<AppleLyricPiece>()

    fun flush() {
        if (pending.isEmpty()) return
        groups += AppleRenderGroup(
            id = pending.first().id,
            pieces = pending.toList(),
            nonBreaking = pending.count { !it.isWhitespace } > 1,
        )
        pending.clear()
    }

    pieces.forEach { piece ->
        pending += piece
        if (piece.isWhitespace) flush()
    }
    flush()
    return groups
}
