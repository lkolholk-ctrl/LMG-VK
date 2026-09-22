// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.lyrics

/*
 * LyricsModel.kt — lyrics data model (MSVLyrics* + LyricsX view) for iOS 26 Apple Music.
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/TTML_PARSER_CLOSURE.md §1–§7 (inventory, enums, ivar offsets)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.8 (LyricsModel contract)
 *  - deepseek_analysis/10_final_closure/LYRICS_RENDERER_IMPLEMENTATION_SPEC.md §1 (LyricsX types)
 *  - deepseek_analysis/09_appos/APPOS_LYRICSX.md
 *
 * Apple classes (MediaServices, subcache .13): MSVLyricsXMLElement, MSVLyricsElement, MSVLyricsTextElement,
 * MSVLyricsLine, MSVLyricsWord, MSVLyricsTranslationText, MSVLyricsTransliterationText, MSVLyricsSection,
 * MSVLyricsSongInfo, MSVLyricsSongWriter, MSVLyricsAgent, MSVLyricsTranslation, MSVLyricsTransliteration,
 * MSVLyricsAudioAttributes (+ LyricsX.Lyrics/TextLine/Word/Syllable/InstrumentalLine in MEE).
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 * All field names below are re-expressed; no Apple source code copied. Absent instrumentation of the
 * parser is flagged with TODO (see TTMLParseSpec.kt).
 */

// ---------------------------------------------------------------------------------------------
// Enums (all values EXACT — TTML_PARSER_CLOSURE §1)
// ---------------------------------------------------------------------------------------------

/** `MSVLyricsSongInfo.type` (from `<tt itunes:timing>`): 0 NotTimed, 1 TimedLines, 2 TimedWords. */
enum class LyricsInfoType(val rawValue: Int) {
    NOT_TIMED(0),
    TIMED_LINES(1),
    TIMED_WORDS(2),
}

/** `MSVLyricsElement.type`: 0 Section, 1 Line, 2 Word, 3 TranslatedLine, 4 TransliteratedLine. */
enum class LyricsElementType(val rawValue: Int) {
    SECTION(0),
    LINE(1),
    WORD(2),
    TRANSLATED_LINE(3),
    TRANSLITERATED_LINE(4),
}

/**
 * `MSVLyricsSection.songPart` derived from `itunes:songPart` text (case-insensitive):
 * generic=0, verse=1, chorus=2, pre-chorus=3, bridge=4, intro=5, outro=6, refrain=7, instrumental=8.
 */
enum class SongPart(val rawValue: Int) {
    GENERIC(0), VERSE(1), CHORUS(2), PRE_CHORUS(3), BRIDGE(4),
    INTRO(5), OUTRO(6), REFRAIN(7), INSTRUMENTAL(8);

    companion object {
        /** `+[MSVLyricsSection _songPartForText:]` @`0x1abf8a1bc`. [EXACT] */
        fun fromText(text: String?): SongPart = when (text?.lowercase()) {
            "verse" -> VERSE
            "chorus" -> CHORUS
            "pre-chorus" -> PRE_CHORUS
            "bridge" -> BRIDGE
            "intro" -> INTRO
            "outro" -> OUTRO
            "refrain" -> REFRAIN
            "instrumental" -> INSTRUMENTAL
            else -> GENERIC
        }
    }
}

/** `MSVLyricsTranslation.type` (`_translationTypeForText:` @`0x1abf8a884`): nil->0, subtitle->1, replacement->2. */
enum class TranslationType(val rawValue: Int) {
    NONE(0), SUBTITLE(1), REPLACEMENT(2);

    companion object {
        /** [EXACT] */
        fun fromText(text: String?): TranslationType = when (text?.lowercase()) {
            "subtitle" -> SUBTITLE
            "replacement" -> REPLACEMENT
            else -> NONE
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Element payloads
// ---------------------------------------------------------------------------------------------

/**
 * `NSRange` in the parent text (`MSVLyricsWord.characterRange`, ivar +0x88).
 * Layout/meaning [EXACT] (TTML_PARSER_CLOSURE §5).
 */
data class CharacterRange(val location: Int, val length: Int)

/** Common `MSVLyricsXMLElement`/`MSVLyricsElement` fields (ivar offsets from §1). [EXACT] fields. */
data class ElementFields(
    val elementName: String?,
    val identifier: String?,
    val mutableText: String?,
    /** `_isBackgroundVocal` +0x20 (set when `ttm:role == "x-bg"`). */
    val isBackgroundVocal: Boolean,
    val type: LyricsElementType,
    /** Absolute seconds (from `begin`); parser may leave endTime 0. */
    val startTime: Double?,
    val endTime: Double?,
    val agent: Agent?,
    val role: String?,
)

/** `MSVLyricsTextElement` extras: `_keepParentheses` +0x50, `_lyricsText` +0x58. */
data class TextElementFields(
    val keepParentheses: Boolean,
    val lyricsText: String?,
)

/** `MSVLyricsSongWriter { _name +0x20, _artistID +0x28 }`. [EXACT] */
data class SongWriter(val name: String?, val artistID: String?)

/** `MSVLyricsAgent { _type +0x20, _name +0x28, _artistID +0x30 }`. [EXACT] */
data class Agent(val type: String?, val name: String?, val artistID: String?)

/**
 * `MSVLyricsAudioAttributes { _spatialRole +0x08, _lyricsOffset +0x10, _role +0x18 }`.
 * `spatialRole` is true when `role == "spatial"` (parser rule, [EXACT]).
 */
data class AudioAttributes(
    val spatialRole: Boolean,
    val lyricsOffsetSeconds: Double?,
    val role: String?,
)

// ---------------------------------------------------------------------------------------------
// Translations / transliterations
// ---------------------------------------------------------------------------------------------

/**
 * `MSVLyricsTranslationText` (`MSVLyricsLine` subclass): `_lyricsLineKey` +0xa8.
 * Used inside a `translation.linesMap[for]` or `transliteration.linesMap[for]`.
 */
data class TranslationText(
    val lyricsLineKey: String?,
    val element: ElementFields,
    val text: TextElementFields,
)

/** `MSVLyricsTranslation { _automaticallyCreated, _type, _language, _typeText, _linesMap }`. [EXACT] */
data class Translation(
    val automaticallyCreated: Boolean,
    val type: TranslationType,
    val language: String?,
    val typeText: String?,
    val linesMap: Map<String, TranslationText>,
)

/** `MSVLyricsTransliteration { _automaticallyCreated, _language, _linesMap }`. [EXACT] */
data class Transliteration(
    val automaticallyCreated: Boolean,
    val language: String?,
    val linesMap: Map<String, TranslationText>,
)

// ---------------------------------------------------------------------------------------------
// Line / word / syllable
// ---------------------------------------------------------------------------------------------

/**
 * `MSVLyricsWord` (size 152): `_parentLine` +0x60, `_nextWord` +0x68, `_parentWord` +0x70,
 * `_subwords` +0x78, `_wordIndex` +0x80, `_characterRange` +0x88. All [EXACT] names/offsets.
 * A nested `<span>` produces `subwords` (syllables).
 */
data class Word(
    val element: ElementFields,
    val text: TextElementFields,
    val wordIndex: Int?,
    val characterRange: CharacterRange?,
    val subwords: List<Word>,
) {
    /** Karaoke syllable view; LyricsX maps `subwords` to Syllable. [STRONG_INFERENCE]. */
    fun syllables(): List<Syllable> =
        subwords.map { Syllable(it.element.startTime ?: 0.0, it.element.endTime ?: 0.0, it.text.lyricsText ?: "") }
}

/**
 * `MSVLyricsLine` (size 168): `_instrumentalBreak` +0x60, `_lineIndex` +0x68, `_originalLineIndex` +0x70,
 * `_parentSection` +0x78, `_nextLine` +0x80, `_words` +0x88, `_translationKey` +0x90,
 * `_backgroundVocals` +0x98, `_hasBackgroundVocal` +0x61, `_primaryVocalText` +0xa0. [EXACT] fields.
 *
 * NOTE (verified negative): `_instrumentalBreak` is never set by the parser and never read by
 * LyricsX/Music.app; there is no TTML element/attribute for it (TTML_PARSER_CLOSURE §9, [EXACT]).
 * The interlude threshold used by other platforms was NOT FOUND in iOS.
 */
data class Line(
    val element: ElementFields,
    val text: TextElementFields,
    val instrumentalBreak: Boolean,
    val lineIndex: Int?,
    val originalLineIndex: Int?,
    val translationKey: String?,
    val words: List<Word>,
    val hasBackgroundVocal: Boolean,
    val backgroundVocals: BackgroundVocals?,
    val primaryVocalText: String?,
) {
    val startTime: Double? get() = element.startTime
    val endTime: Double? get() = element.endTime
}

/**
 * `LyricsX.TextLine.BackgroundVocals` — outer background-vocal word plus child spans.
 * Built by `TextLine.BackgroundVocals.init(backgroundVocals:language:)` @`0x1004186d4`
 * (`subwords`, `startTime`, `endTime`, `lyricsText`). [EXACT] types.
 */
data class BackgroundVocals(
    val outer: Word,
    val children: List<Word>,
)

/**
 * `LyricsX.Syllable` karaoke unit `{ startTime, endTime, progress }`.
 * Built from MSV `subwords` or from `Lyrics.words(for:language:)` @`0x100418330`. Types [EXACT];
 * exact mapping [STRONG_INFERENCE].
 */
data class Syllable(
    val startTime: Double,
    val endTime: Double,
    val text: String,
    /** `(t - begin) / (end - begin)` clamped to 0..1 (renderer karaoke contract). [EXACT] formula. */
    val progress: Double = 0.0,
)

// ---------------------------------------------------------------------------------------------
// Section / song info
// ---------------------------------------------------------------------------------------------

/** `MSVLyricsSection { _songPart +0x50, _songPartText +0x58, _lines +0x60 }`. [EXACT] */
data class Section(
    val element: ElementFields,
    val songPart: SongPart,
    val songPartText: String?,
    val lines: List<Line>,
)

/**
 * `MSVLyricsSongInfo` (size 120) — the parser return value.
 * ivars: type +0x08, songDuration +0x10, leadingSilence +0x18, songwriters +0x20, lyricGenId +0x28,
 * language +0x30, availableTranslations +0x38, translations +0x40, transliterations +0x48,
 * lyricsLines +0x50, agents +0x58, audioAttributes +0x60, lyricsSections +0x68, translationsMap +0x70.
 * All [EXACT] names/offsets (TTML_PARSER_CLOSURE §1).
 */
data class SongInfo(
    val type: LyricsInfoType,
    val songDurationSeconds: Double?,
    val leadingSilenceSeconds: Double?,
    val songwriters: List<SongWriter>,
    val lyricGenId: String?,
    val language: String?,
    /**
     * `availableTranslations`: synthesized getter @`0x1abf88838`; no setter in the parser — who fills
     * it is NOT FOUND (TTML_PARSER_CLOSURE §12.3).
     */
    val availableTranslations: List<String>,
    val translations: List<Translation>,
    val transliterations: List<Transliteration>,
    val lyricsLines: List<Line>,
    val agents: List<Agent>,
    val audioAttributes: AudioAttributes?,
    val lyricsSections: List<Section>,
    /**
     * Built by `setTranslations:` @`0x1abf88fe0` for `translation.type == NONE` only
     * (subtitle/replacement translations do not enter the map). [EXACT]
     */
    val translationsMap: Map<String, Map<String, TranslationText>>,
) {
    /**
     * `translatedTextForLyricsLine:language:` @`0x1abf88b54`:
     * `translationsMap[language][line.translationKey].lyricsText` (nil-safe). [EXACT]
     */
    fun translatedText(line: Line, language: String): String? =
        translationsMap[language]?.get(line.translationKey)?.text?.lyricsText
}

/**
 * `LyricsX.Lyrics.InstrumentalLine { lineIndex, startTime, endTime }` — exists in MEE and is a
 * UI marker, but it is NOT created from `MSVLyricsLine` (instrumental selectors are not read in the
 * LyricsX conversion). Types [EXACT]; producer outside the corpus.
 */
data class InstrumentalLine(val lineIndex: Int?, val startTime: Double, val endTime: Double)
