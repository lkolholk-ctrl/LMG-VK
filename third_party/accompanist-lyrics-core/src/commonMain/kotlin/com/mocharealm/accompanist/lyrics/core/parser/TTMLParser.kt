package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.karaoke.PhoneticLevel
import com.mocharealm.accompanist.lyrics.core.model.karaoke.copy
import com.mocharealm.accompanist.lyrics.core.model.karaoke.mapper.contentToString
import com.mocharealm.accompanist.lyrics.core.model.karaoke.mapper.stripEnclosingParentheses
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.utils.PhoneticProvider
import com.mocharealm.accompanist.lyrics.core.utils.SimpleXmlParser
import com.mocharealm.accompanist.lyrics.core.utils.XmlElement
import com.mocharealm.accompanist.lyrics.core.utils.parseAsTime

/**
 * A parser for lyrics in the TTML(Apple Syllable) format.
 *
 * More information about TTML(Apple Syllable) format can be found [here](https://help.apple.com/itc/videoaudioassetguide/#/itc0f14fecdd).
 *
 * @property fallbackPhoneticProvider
 */
class TTMLParser(
    private val fallbackPhoneticProvider: PhoneticProvider? = null,
) : ILyricsParser {
    override fun canParse(content: String): Boolean =
        content.contains("http://www.w3.org/ns/ttml")

    // Workaround for AMLL and other tools not strictly following the spec
    private fun preformattingTTML(content: String): String =
        content
            .replace(" </span><span", "</span> <span")
            .replace(",</span><span", ",</span> <span")

    private fun normalizeXmlTextContent(text: String): String =
        text.replace(Regex("\\s+"), " ")
            .trim()

    // Single-name fast path: avoids allocating a vararg array on every lookup
    // (begin/end/ttm:agent/itunes:key are read for every line and syllable).
    private fun XmlElement.attr(name: String) =
        attributes.firstOrNull { it.name == name }?.value

    private fun XmlElement.attr(vararg names: String) =
        attributes.firstOrNull { it.name in names }?.value

    private fun XmlElement.hasRole(role: String) =
        attributes.any { it.name.endsWith(":role") && it.value == role }

    override fun parse(content: String): SyncedLyrics {
        val root = SimpleXmlParser().parse(preformattingTTML(content))

        // Agents are declared in the head <metadata>; scope that lookup to it.
        // Translations/transliterations, however, can be inline within the body in
        // some Apple/AMLL variants, so those keep searching the whole tree.
        val metadata = findMetadata(root)
        val agentTypes = metadata?.let(::parseAgentTypes) ?: emptyMap()
        val translations = parseITunesTranslations(root)
        val transliterations = parseITunesTransliterations(root)

        // Parse each line's begin time once (as the sort key) rather than letting
        // sortedBy re-evaluate parseAsTime O(n log n) times.
        val sortedPElements = findAllPElements(root)
            .mapNotNull(::prepareLine)
            .sortedBy { it.timing.start }
        val lineAlignments = computeLineAlignments(sortedPElements.map { it.element }, agentTypes)

        val parsedLines = sortedPElements.mapIndexedNotNull { index, pElement ->
            parseSingleLine(pElement, lineAlignments[index], translations, transliterations)
        }

        val syncedLyrics = SyncedLyrics(lines = parsedLines.sortedBy { it.start })
        return applyFallbackPhonetics(syncedLyrics)
    }

    private fun extractAllText(element: XmlElement): String {
        if (element.name == "#text") return element.text
        val sb = StringBuilder()
        for (child in element.children) {
            // we don't extract from translation or ruby spans if they are just metadata, but for safe fallback let's just extract all text that isn't translation
            if (child.name == "span" && (child.hasRole("x-translation") || child.hasRole("x-bg") || child.hasRole("x-roman"))) continue
            sb.append(extractAllText(child))
        }
        return sb.toString()
    }

    private fun parseSingleLine(
        prepared: PreparedLine,
        alignment: KaraokeAlignment,
        translations: Map<String, TTMLTranslation>,
        transliterations: Map<String, List<String>>
    ): ISyncedLine? {
        val p = prepared.element
        val start = prepared.timing.start
        val end = prepared.timing.end
        val itunesKey = p.attr("itunes:key", "key")

        // 1. 解析主音轨音节
        var syllables = prepared.syllables
        transliterations[itunesKey]?.let { phonetics ->
            if (phonetics.size == syllables.size) {
                syllables = syllables.mapIndexed { i, s -> s.copy(phonetic = phonetics[i]) }
            }
        }

        // 2. 解析主音轨注音 (Line Level)
        val linePhonetic =
            p.children.firstOrNull { it.name == "span" && it.hasRole("x-roman") }
                ?.let(::extractTextContent)?.trim()

        // 3. 解析主音轨翻译
        val inlineTranslation = p.children.firstOrNull {
            it.name == "span" && it.hasRole("x-translation") && !it.hasRole("x-bg")
        }?.let(::extractTextContent)?.trim()
        val itunesTranslation = translations[itunesKey]

        // 4. 解析和声轨 (Background Vocals)
        val accompanimentLines = p.children
            .filter { it.name == "span" && it.hasRole("x-bg") }
            .mapNotNull { bgSpan ->
                parseAccompaniment(
                    bgSpan,
                    itunesKey,
                    alignment,
                    translations,
                    prepared.timing
                )
            }

        if (syllables.isEmpty() && accompanimentLines.isEmpty()) {
            val content = normalizeXmlTextContent(extractAllText(p))
            if (content.isEmpty()) return null
            return SyncedLine(
                content = content,
                translation = inlineTranslation ?: itunesTranslation?.main,
                start = start,
                end = end
            )
        }

        if (syllables.isEmpty()) {
            val content = normalizeXmlTextContent(extractAllText(p))
            if (content.isNotEmpty()) syllables = listOf(KaraokeSyllable(content, start, end))
        }

        return KaraokeLine.MainKaraokeLine(
            syllables = syllables,
            translation = inlineTranslation ?: itunesTranslation?.main,
            alignment = alignment,
            start = start,
            end = end,
            accompanimentLines = accompanimentLines.ifEmpty { null },
            phonetic = linePhonetic
        )
    }

    private fun parseAccompaniment(
        bgSpan: XmlElement,
        parentKey: String?,
        alignment: KaraokeAlignment?,
        translations: Map<String, TTMLTranslation>,
        parentTiming: VocalTiming
    ): KaraokeLine.AccompanimentKaraokeLine? {
        var syllables = parseSyllablesFromChildren(bgSpan.children)
        val timing = resolveTiming(bgSpan, syllables, parentTiming = parentTiming) ?: return null
        if (syllables.isEmpty()) {
            val text = normalizeXmlTextContent(extractAllText(bgSpan))
            if (text.isEmpty()) return null
            syllables = listOf(KaraokeSyllable(text, timing.start, timing.end))
        }
        syllables = syllables.stripEnclosingParentheses()
        if (syllables.all { it.content.isBlank() }) return null

        val bgKey = bgSpan.attr("itunes:key", "key") ?: parentKey
        val bgTranslation =
            bgSpan.children
                .firstOrNull { it.hasRole("x-translation") }
                ?.let(::extractTextContent)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: translations[bgKey]?.background

        return KaraokeLine.AccompanimentKaraokeLine(
            syllables = syllables,
            translation = bgTranslation?.stripEnclosingParentheses(),
            alignment = alignment ?: KaraokeAlignment.Start,
            start = timing.start,
            end = timing.end
        )
    }

    private data class VocalTiming(val start: Int, val end: Int)

    private data class PreparedLine(
        val element: XmlElement,
        val syllables: List<KaraokeSyllable>,
        val timing: VocalTiming
    )

    private fun prepareLine(p: XmlElement): PreparedLine? {
        val syllables = parseSyllablesFromChildren(p.children)
        val backgroundTimings = if (p.attr("begin").isNullOrBlank() || p.attr("end").isNullOrBlank()) {
            p.children.filter { it.name == "span" && it.hasRole("x-bg") }
                .filter { extractAllText(it).isNotBlank() }
                .mapNotNull { resolveTiming(it, parseSyllablesFromChildren(it.children)) }
        } else emptyList()
        val timing = resolveTiming(p, syllables, backgroundTimings) ?: return null
        return PreparedLine(p, syllables, timing)
    }

    private fun resolveTiming(
        element: XmlElement,
        syllables: List<KaraokeSyllable>,
        backgroundTimings: List<VocalTiming> = emptyList(),
        parentTiming: VocalTiming? = null
    ): VocalTiming? {
        val starts = syllables.map { it.start } + backgroundTimings.map { it.start }
        val ends = syllables.map { it.end } + backgroundTimings.map { it.end }
        val start = element.attr("begin")?.takeIf { it.isNotBlank() }?.parseAsTime()
            ?: starts.minOrNull() ?: parentTiming?.start ?: return null
        val end = element.attr("end")?.takeIf { it.isNotBlank() }?.parseAsTime()
            ?: ends.maxOrNull() ?: parentTiming?.end ?: return null
        return VocalTiming(start, end).takeIf { end >= start }
    }

    /** Translation metadata is structurally split into the main and background-vocal tracks. */
    private data class TTMLTranslation(
        val main: String?,
        val background: String?
    )

    /** Extracts an element's own text plus all descendants, preserving actual lyric content. */
    private fun extractTextContent(element: XmlElement): String =
        if (element.name == "#text") element.text
        else element.children.joinToString("") { extractTextContent(it) }

    /**
     * Parses Apple/iTunes translation metadata without flattening nested x-bg spans
     * into the main-line translation. A metadata <text> may look like:
     *
     * <text for="L3">主唱翻译<span ttm:role="x-bg">伴唱翻译</span></text>
     */
    private fun parseITunesTranslations(element: XmlElement): Map<String, TTMLTranslation> {
        val translations = mutableMapOf<String, TTMLTranslation>()

        fun findTranslations(elem: XmlElement) {
            if (elem.name == "translation" || elem.name.endsWith(":translation")) {
                elem.children.forEach { textElem ->
                    if (textElem.name != "text") return@forEach

                    val key = textElem.attr("for") ?: return@forEach
                    val main = extractAllText(textElem)
                        .trim()
                        .takeIf { it.isNotEmpty() }

                    val background = textElem.children
                        .asSequence()
                        .filter { it.name == "span" && it.hasRole("x-bg") }
                        .map(::extractTextContent)
                        .map(String::trim)
                        .filter { it.isNotEmpty() }
                        .joinToString("")
                        .takeIf { it.isNotEmpty() }

                    if (main != null || background != null) {
                        translations[key] = TTMLTranslation(
                            main = main,
                            background = background
                        )
                    }
                }
            }

            elem.children.forEach(::findTranslations)
        }

        findTranslations(element)
        return translations
    }

    private fun parseITunesTransliterations(element: XmlElement): Map<String, List<String>> {
        val transliterations = mutableMapOf<String, List<String>>()

        // 递归寻找 <transliterations> 节点
        fun findTransliterations(elem: XmlElement) {
            if (elem.name == "transliterations" || elem.name.endsWith(":transliterations")) {
                elem.children.forEach { transElem ->
                    if (transElem.name == "transliteration" || transElem.name.endsWith(":transliteration")) {
                        transElem.children.forEach { textElem ->
                            if (textElem.name == "text") {
                                val key = textElem.attributes.find { it.name == "for" }?.value
                                // 提取所有内部 span 的文本作为音标列表
                                val phoneticSpans = textElem.children
                                    .filter { it.name == "span" }
                                    .map { extractTextContent(it).trim() }

                                if (key != null && phoneticSpans.isNotEmpty()) {
                                    transliterations[key] = phoneticSpans
                                }
                            }
                        }
                    }
                }
            }
            elem.children.forEach { findTransliterations(it) }
        }

        findTransliterations(element)
        return transliterations
    }

    private fun applyFallbackPhonetics(syncedLyrics: SyncedLyrics): SyncedLyrics {
        val provider = fallbackPhoneticProvider ?: return syncedLyrics
        val processedLines = syncedLyrics.lines.map { line ->
            if (line !is KaraokeLine) return@map line

            // 如果当前行已有任何形式的发音（行级或音节级），则不进行 fallback
            val hasExistingPhonetic = !line.phonetic.isNullOrBlank() ||
                    line.syllables.any { !it.phonetic.isNullOrBlank() }

            if (hasExistingPhonetic) return@map line

            return@map when (provider.phoneticLevel) {
                PhoneticLevel.LINE -> {
                    line.copy(phonetic = provider.getPhonetic(line.syllables.contentToString()))
                }

                PhoneticLevel.SYLLABLE -> {
                    val newSyllables = line.syllables.map { syllable ->
                        syllable.copy(phonetic = provider.getPhonetic(syllable.content))
                    }
                    line.copy(syllables = newSyllables)
                }
            }
        }

        return SyncedLyrics(lines = processedLines)
    }

    /**
     * Parses a list of XmlElement children to extract KaraokeSyllables.
     * This function intelligently handles spacing by checking for `#text` nodes between `<span>` elements.
     */
    private fun parseSyllablesFromChildren(children: List<XmlElement>): List<KaraokeSyllable> {
        val syllables = mutableListOf<KaraokeSyllable>()
        var leadingText = ""
        for (i in children.indices) {
            val child = children[i]

            if (child.name == "#text") {
                if (syllables.isNotEmpty()) {
                    val last = syllables.last()
                    syllables[syllables.lastIndex] = last.copy(content = last.content + child.text)
                } else if (leadingText.isNotEmpty() || child.text.isNotBlank()) {
                    leadingText += child.text
                }
                continue
            }

            // We only care about <span> elements that are not for translation or background roles at this level.
            if (child.name == "span") {
                // Single pass over the (tiny) attribute list: grab begin/end and
                // detect an excluded role at once instead of scanning it 3×.
                var spanBegin: String? = null
                var spanEnd: String? = null
                var excludedRole = false
                for (attr in child.attributes) {
                    when {
                        attr.name == "begin" -> spanBegin = attr.value
                        attr.name == "end" -> spanEnd = attr.value
                        attr.name.endsWith(":role") &&
                            (attr.value == "x-translation" || attr.value == "x-bg" || attr.value == "x-roman") -> excludedRole = true
                    }
                }

                if (!excludedRole && spanBegin != null && spanEnd != null) {
                    val wordText = extractAllText(child)
                    if (wordText.isEmpty()) continue
                    syllables.add(
                        KaraokeSyllable(
                            content = leadingText + wordText,
                            start = spanBegin.parseAsTime(),
                            end = spanEnd.parseAsTime()
                        )
                    )
                    leadingText = ""
                }
            }
        }

        // Trim the trailing space from the very last syllable of the line.
        if (syllables.isNotEmpty()) {
            val last = syllables.last()
            syllables[syllables.lastIndex] =
                last.copy(content = last.content.trimEnd())
        }

        return syllables
    }

    /** The single <metadata> node (in the head), or null. Stops at the first match. */
    private fun findMetadata(elem: XmlElement): XmlElement? {
        if (elem.name == "metadata") return elem
        return elem.children.firstNotNullOfOrNull { findMetadata(it) }
    }

    /** Map of `ttm:agent` id → its declared `type` ("person" / "group" / "other"). */
    private fun parseAgentTypes(metadata: XmlElement): Map<String, String> {
        return metadata.children
            .filter { it.name.endsWith(":agent") || it.name == "agent" }
            .mapNotNull { agent ->
                val id = agent.attributes.find { it.name == "xml:id" || it.name == "id" }?.value
                val type = agent.attributes.find { it.name == "type" }?.value ?: "person"
                if (id != null) id to type else null
            }.toMap()
    }

    /**
     * Decide each line's left/right side, matching how Apple Music assembles lines
     * (`assembleProcessedLines`, reverse-engineered from MusicApplication) — with one
     * deliberate change: the starting side is seeded from the **first line's agent
     * id** (v1/v3/v5… → left, v2/v4/v6… → right) instead of Apple's always-left.
     *
     * Then, over the lines in start-time order:
     *  - a **person** agent keeps the current side while the same person keeps
     *    singing, and **flips** to the other side each time the person changes;
     *  - a **group** agent (合唱/everyone) is always on the left and is transparent
     *    to the flip (it neither flips nor becomes the "current" person);
     *  - an **other** agent is always on the right, also transparent to the flip.
     *
     * An agent with no declared type defaults to "person"; a line with no agent at
     * all keeps the current side.
     */
    private fun computeLineAlignments(
        sortedLines: List<XmlElement>,
        agentTypes: Map<String, String>
    ): List<KaraokeAlignment> {
        val firstOrdinal = sortedLines.firstOrNull()
            ?.attr("ttm:agent")
            ?.takeLastWhile { it.isDigit() }
            ?.toIntOrNull() ?: 1
        var onRight = firstOrdinal % 2 == 0
        var currentPerson: String? = null

        fun side() = if (onRight) KaraokeAlignment.End else KaraokeAlignment.Start

        return sortedLines.map { p ->
            val agentId = p.attr("ttm:agent")
            val type = if (agentId == null) "none" else agentTypes[agentId] ?: "person"
            when (type) {
                "group" -> KaraokeAlignment.Start
                "other",
                "person" -> {
                    if (currentPerson == null) {
                        currentPerson = agentId
                    } else if (agentId != currentPerson) {
                        onRight = !onRight
                        currentPerson = agentId
                    }
                    side()
                }
                else -> side()
            }
        }
    }

    private fun findAllPElements(element: XmlElement): List<XmlElement> {
        val pElements = mutableListOf<XmlElement>()
        if (element.name == "p") {
            pElements.add(element)
        }
        element.children.forEach { child ->
            pElements.addAll(findAllPElements(child))
        }
        return pElements
    }
}
