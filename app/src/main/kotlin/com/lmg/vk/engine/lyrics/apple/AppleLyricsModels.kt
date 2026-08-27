package com.lmg.vk.engine.lyrics.apple

enum class AppleTimingType {
    NONE,
    LINE,
    WORD
}

enum class AppleAgentType {
    NONE,
    PERSON,
    CHARACTER,
    GROUP,
    ORGANIZATION,
    OTHER
}

enum class ApplePieceRole {
    MAIN,
    BACKGROUND,
    TRANSLATION,
    TRANSLATION_BACKGROUND,
    PRONUNCIATION,
    PRONUNCIATION_BACKGROUND
}

data class AppleSongwriter(
    val name: String
)

data class AppleLyricsAgent(
    val id: String,
    val type: AppleAgentType,
    val name: String? = null
)

data class AppleLyricsDocument(
    val durationMs: Long,
    val language: String?,
    val timing: AppleTimingType,
    val sections: List<AppleLyricsSection>,
    val agents: Map<String, AppleLyricsAgent>,
    val script: String? = null,
    val translation: String? = null,
    val pronunciation: String? = null,
    val songwriters: List<AppleSongwriter> = emptyList(),
    val rawTtml: String? = null
) {
    val allLines: List<AppleLyricsLine> by lazy {
        sections.flatMap { it.lines }
    }
}

data class AppleLyricsSection(
    val beginMs: Long,
    val endMs: Long,
    val songPart: String?,
    val lines: List<AppleLyricsLine>
)

data class AppleLyricsLine(
    val id: String,
    val key: String?,
    val beginMs: Long,
    val endMs: Long,
    val agentId: String?,
    val main: List<AppleLyricPiece>,
    val background: List<AppleLyricPiece> = emptyList(),
    val translation: List<AppleLyricPiece> = emptyList(),
    val translationBackground: List<AppleLyricPiece> = emptyList(),
    val pronunciation: List<AppleLyricPiece> = emptyList(),
    val pronunciationBackground: List<AppleLyricPiece> = emptyList(),
    val keepParenthesis: Boolean = false
) {
    val durationMs: Long get() = (endMs - beginMs).coerceAtLeast(1L)
}

data class AppleLyricPiece(
    val id: String,
    val text: String,
    val beginMs: Long,
    val endMs: Long,
    val agentId: String?,
    val role: ApplePieceRole,
    val isWhitespace: Boolean = false,
    val sourceOrder: Int
) {
    val durationMs: Long get() = (endMs - beginMs).coerceAtLeast(1L)
}
