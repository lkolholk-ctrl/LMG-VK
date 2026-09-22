// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.lyrics

/*
 * TTMLParseSpec.kt — parser contract for `MSVLyricsTTMLParser` (MediaServices.framework, iOS 26).
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/TTML_PARSER_CLOSURE.md (canonical; class_ro 0x1f23a81d0, 39 methods)
 *  - deepseek_analysis/10_final_closure/FINAL_RED_TEAM_AUDIT.md §9 (independent verification + corrections)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.8
 *
 * IMPORTANT (RETRACTION recorded in the source reports): the parser lives in
 * MediaServices.framework (text subcache .13), NOT MediaPlayer.framework.
 * MEE imports it with dylib ordinal 49; MusicApplication with ordinal 0x1d (0x1d correction:
 * Music.app/Music uses ordinal 85 — red-team correction §9).
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

// ---------------------------------------------------------------------------------------------
// Entry points / lifecycle (EXACT addresses; behavior per TTML_PARSER_CLOSURE §2)
// ---------------------------------------------------------------------------------------------

/** `MSVLyricsTTMLParser` entry points and delegate methods. All addresses [EXACT]. */
enum class ParserMethod(val address: String, val behavior: String) {
    INIT_WITH_DATA("0x1abf8e2bc", "alloc NSInputStream with data; return initWithTTMLStream:"),
    INIT_WITH_STREAM("0x1abf8e1b8", "super init; serial queue 'com.apple.MediaServices.MSVLyricsTTMLParser'; arrays: elementStack cap 10, lyricLines cap 100, agents cap 3"),
    PARSE_WITH_ERROR("0x1abf8e004", "reset flags; new NSXMLParser(stream); shouldProcessNamespaces=YES; parse; return _lyricsInfo"),
    PARSE_WITH_COMPLETION("0x1abf8deac", "dispatch_async on parseQueue; completion(info, error)"),
    DID_START_ELEMENT("0x1abf8cb14", "element -> model construction; shared tail sets start/end times, agent, role, parenthesis; push to stack"),
    DID_END_ELEMENT("0x1abf8bd8c", "stack check/pop; line/section/translation finalization; sorting at </body>"),
    FOUND_CHARACTERS("0x1abf8bc28", "append to currentTextElement.mutableText; Word also appends to parentLine/parentWord"),
    PARSE_ERROR_OCCURRED("0x1abf8bb58", "log via os_log error; set parserError (parse returns partial model)"),
}

/**
 * Parser contract facade. Pure helpers below are re-expressions of confirmed behavior;
 * the orchestration is a port implementation point.
 */
object TtmlParserSpec {
    /** Serial queue label. [EXACT] (initWithTTMLStream:, §2). */
    const val PARSE_QUEUE_LABEL = "com.apple.MediaServices.MSVLyricsTTMLParser"

    /** os_log subsystem/category. [EXACT] (CF 0x1abfd22de/0x1abfd236f). */
    const val LOG_SUBSYSTEM = "com.apple.amp.MediaServices"
    const val LOG_CATEGORY = "LyricsTTMLParser"

    /** Top-level error message. [EXACT] (§3, didStartElement LAB 0x1abf8d1f0). */
    const val TOP_LEVEL_ERROR = "PARSE ERROR: Top-level element must be <tt> for TTML documents"

    /**
     * `MSVLyricsTTMLParserErrorDomain` (`0x1f23a03b8`) is declared but never used by any
     * decompiled method [NOT FOUND usage] (§8).
     */
    const val ERROR_DOMAIN = "MSVLyricsTTMLParserErrorDomain"

    /**
     * Orchestrate parse. TODO (PORT DESIGN): implement segment parsing over the model
     * defined in LyricsModel.kt; behavior chart is in this file.
     */
    fun parse(ttmlText: String): SongInfo =
        TODO("PORT DESIGN: orchestrate XML->model using the rules in TtmlParserSpec")

    /**
     * STATUS: UNKNOWN — needs runtime/device: the MEE call-site of `initWithTTMLData:` /
     * `parseWithCompletion:` is NOT localized statically (TTML_PARSER_CLOSURE §12.1).
     */
    fun hasLocalizedMeeCallSite(): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: MEE call-site of the parser not localized")
}

// ---------------------------------------------------------------------------------------------
// Time format (`msvl_timeValue` @0x1abf8e344)
// ---------------------------------------------------------------------------------------------

/**
 * Time parsing contract (EXACT, TTML_PARSER_CLOSURE §4):
 * ```
 * components = raw.split(":")
 * seconds = components.last().toDouble()
 * if (components.size >= 2) seconds += components[components.size - 2].toInt() * 60.0
 * if (components.size >= 3) warn "Warning: time format should specify [minutes:]seconds only; ..."
 *                          // hours are IGNORED, the value is NOT changed
 * return seconds
 * ```
 * Formats observed: `SS.mmm`, `MM:SS.mmm`; `HH:MM:SS.mmm` parses but ignores hours with a warning.
 */
fun parseTtmlTime(raw: String, onWarning: (String) -> Unit = {}): Double {
    val components = raw.split(":")
    var seconds = components.last().toDouble()
    if (components.size >= 2) {
        seconds += components[components.size - 2].toInt() * 60.0
    }
    if (components.size >= 3) {
        onWarning("Warning: time format should specify [minutes:]seconds only; other components are ignored: $raw")
    }
    return seconds
}

// ---------------------------------------------------------------------------------------------
// Element rules chart (TTML_PARSER_CLOSURE §3)
// ---------------------------------------------------------------------------------------------

/**
 * One XML element rule. `behavior` is a paraphrase; per-element status is in `status`.
 * `warningOnly = true` means misplaced known elements only os_log a warning and parsing continues.
 */
data class TtmlElementRule(
    val element: String,
    val behavior: String,
    val warningOnly: Boolean,
    val status: String,
)

/** Complete element chart (all [EXACT] per §3). Unknown elements are ignored. */
val TTML_ELEMENT_RULES: List<TtmlElementRule> = listOf(
    TtmlElementRule("tt", "create MSVLyricsSongInfo; lyricGenId; xml:lang; type from itunes:timing (absent->0, 'line'->1, 'word'->2, other->0, case-insensitive); NSAssert songwriters/lyricsSections non-nil", false, "EXACT"),
    TtmlElementRule("body", "songDuration = msvl_timeValue(dur); warning if dur absent", true, "EXACT"),
    TtmlElementRule("div", "MSVLyricsSection; songPartText = itunes:songPart (setter derives songPart enum)", false, "EXACT"),
    TtmlElementRule("p", "MSVLyricsLine; translationKey = itunes:key", false, "EXACT"),
    TtmlElementRule("span", "MSVLyricsWord; parent line if parent type 1/3/4; parentWord+parentLine if type 2; else warning", true, "EXACT"),
    TtmlElementRule("metadata", "container only", false, "EXACT"),
    TtmlElementRule("iTunesMetadata", "leadingSilence = msvl_timeValue(leadingSilence)", false, "EXACT"),
    TtmlElementRule("songwriters", "warning if parent is not iTunesMetadata", true, "EXACT"),
    TtmlElementRule("songwriter", "MSVLyricsSongWriter; artistID = artistId; name from char data at end", false, "EXACT"),
    TtmlElementRule("audio", "MSVLyricsAudioAttributes; lyricsOffset; role; spatialRole = (role == 'spatial')", true, "EXACT"),
    TtmlElementRule("translations", "start translations array; warning if parent is not iTunesMetadata", true, "EXACT"),
    TtmlElementRule("translation", "requires xml:lang (warning otherwise); MSVLyricsTranslation; automaticallyCreated = (attr == 'true'); typeText from type; linesMap", true, "EXACT"),
    TtmlElementRule("transliterations", "start transliterations array; warning about parents", true, "EXACT"),
    TtmlElementRule("transliteration", "like translation without type/typeText", true, "EXACT"),
    TtmlElementRule("text", "MSVLyricsTranslationText / MSVLyricsTransliterationText; lyricsLineKey = for", true, "EXACT"),
    TtmlElementRule("agent", "MSVLyricsAgent; type; itunes:artistId; appended to _agents", true, "EXACT"),
    TtmlElementRule("name (ttm:name)", "MSVLyricsXMLElement with mutableText; NSAssert parent is last agent", false, "EXACT"),
    TtmlElementRule("<unknown>", "ignored (no handling branch)", false, "EXACT"),
)

/** Full attribute list handled by the parser. [EXACT] (§3). */
val TTML_ATTRIBUTES: List<String> = listOf(
    "begin", "end", "dur", "itunes:songPart", "itunes:key", "itunes:timing", "itunes:lyricGenId",
    "itunes:artistId", "itunes:parenthesis", "xml:id", "xml:lang", "ttm:agent", "ttm:role",
    "type", "artistId", "leadingSilence", "lyricOffset", "role", "automaticallyCreated", "for",
)

// ---------------------------------------------------------------------------------------------
// Background vocals (ttm:role="x-bg") — TTML_PARSER_CLOSURE §6
// ---------------------------------------------------------------------------------------------

object BackgroundVocalsSpec {
    /** `isBackgroundVocal = [attrs["ttm:role"] isEqualToString:@"x-bg"]`. [EXACT] */
    fun isBackgroundVocalRole(role: String?): Boolean = role == "x-bg"

    /** `keepParentheses = [attrs["itunes:parenthesis"] isEqualToString:@"keep"]`. [EXACT] */
    fun keepParentheses(attributeValue: String?): Boolean = attributeValue == "keep"

    /**
     * `_stripParenthesesFromBackgroundVocalWord:backgroundVocalText:` @`0x1abf8af0c` (EXACT rule):
     * take a trailing-whitespace-trimmed copy of the line text; if it ends with ")" remove first and
     * last characters; `characterRange.location` grows by the removed prefix length; the first
     * subword loses a leading "(" and the last subword loses a trailing ")".
     *
     * @return stripped text, or null when the text does not end with ")".
     */
    fun stripParentheses(lineText: String): String? {
        val trimmed = lineText.trimEnd()
        if (!trimmed.endsWith(")")) return null
        return trimmed.drop(1).dropLast(1)
    }

    /**
     * `primaryVocalText` computation at `</span>` (EXACT rule):
     * copy of line text; drop the background-vocal character range; remove all "()" occurrences;
     * trim whitespace.
     */
    fun primaryVocalText(lineText: String, backgroundRange: CharacterRange?): String {
        val withoutBackground = if (backgroundRange == null) {
            lineText
        } else {
            val end = (backgroundRange.location + backgroundRange.length).coerceAtMost(lineText.length)
            if (backgroundRange.location >= lineText.length || end < backgroundRange.location) {
                lineText
            } else {
                lineText.removeRange(backgroundRange.location, end)
            }
        }
        return withoutBackground.replace("()", "").trim()
    }
}

// ---------------------------------------------------------------------------------------------
// Translations / transliterations — TTML_PARSER_CLOSURE §7
// ---------------------------------------------------------------------------------------------

object TranslationSpec {
    /** `setTranslations:` builds `translationsMap = {language: linesMap}` for `type == NONE` only. [EXACT] */
    fun buildTranslationsMap(translations: List<Translation>): Map<String, Map<String, TranslationText>> =
        translations
            .filter { it.type == TranslationType.NONE }
            .mapNotNull { translation ->
                val language = translation.language ?: return@mapNotNull null
                language to translation.linesMap
            }
            .toMap()

    /**
     * `_translatedLyrics:forLanguage:` @`0x1abf8b60c`: works ONLY for language prefixes
     * `zh-Hant` / `zh-Hans` (`hasPrefix:`); picks the `type == NONE` translation with the same prefix;
     * copies startTime/endTime/agent/translationKey into translated lines; lines without translation
     * stay as-is. Other languages return null and the original lines are kept. [EXACT]
     */
    fun selectPreferredTranslation(
        translations: List<Translation>,
        preferredLanguage: String?,
    ): Translation? {
        val language = preferredLanguage ?: return null
        if (!language.startsWith("zh-Hant") && !language.startsWith("zh-Hans")) return null
        val prefix = if (language.startsWith("zh-Hant")) "zh-Hant" else "zh-Hans"
        return translations.firstOrNull { it.type == TranslationType.NONE && it.language?.startsWith(prefix) == true }
    }

    /** Caller at `</body>` uses `[[NSLocale preferredLanguages] firstObject]`. [EXACT] */
    fun preferredLanguageOfDevice(preferredLanguages: List<String>): String? = preferredLanguages.firstOrNull()
}

// ---------------------------------------------------------------------------------------------
// Ordering / indexing — TTML_PARSER_CLOSURE §4
// ---------------------------------------------------------------------------------------------

object LineOrdering {
    /**
     * `_linesAreSortedByStartTime` update rule (EXACT):
     * `if (endTime != 0.0 && startTime < currentStartTime) linesAreSortedByStartTime = false`
     * then `currentStartTime = startTime`.
     */
    fun updateSortedFlag(startTime: Double, endTime: Double?, currentStartTime: Double): Pair<Boolean, Double> {
        val end = endTime ?: 0.0
        val stillSorted = !(end != 0.0 && startTime < currentStartTime)
        return stillSorted to startTime
    }

    /**
     * `setLyricsLinesSortedByStartTime:` @`0x1abf88a0c` -> `_sortLyricsLinesByStartTime:`:
     * `sortedArrayUsingComparator:` by startTime ascending; then `lineIndex = i` and
     * `nextLine = lines[i+1]` (nil for the last). [EXACT].
     * If order was violated the lines are stored as-is (no sorting) with an os_log.
     */
    fun sortAndReindex(lines: List<Line>): List<Line> =
        lines.sortedBy { it.startTime ?: 0.0 }

    /** Log emitted when order was violated: "Lyrics lines are out of order: ...". [EXACT] string. */
    const val OUT_OF_ORDER_LOG = "Lyrics lines are out of order: they should be ordered by start time"
}

// ---------------------------------------------------------------------------------------------
// Offsets not present in iOS (do not invent)
// ---------------------------------------------------------------------------------------------

/**
 * Instrumental markers: the parser creates NO instrumental lines and never sets `instrumentalBreak`
 * (TTML_PARSER_CLOSURE §9, [EXACT] negative). Anything threshold-based is absent in iOS.
 * STATUS: UNKNOWN — needs runtime/device: instrumental marker producer for
 * `LyricsX.InstrumentalLine` is outside the parser (server payload / other binary).
 */
fun parseInstrumentalMarkerFromTtml(): Nothing =
    TODO("STATUS: UNKNOWN — needs runtime/device: no TTML instrumental element; producer outside corpus")
