package com.lmg.vk.engine.lyrics

import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleTtmlParser
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import kotlinx.serialization.json.*
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Provider-only normalization; the user's original TTML never passes through this adapter. */
internal object LyricsPlusParser {
    fun parse(body: String, title: String, artist: String, durationMs: Long): LyricsContent? = runCatching {
        if (body.trimStart().startsWith("<")) return@runCatching ttml(body, durationMs)
        val root = Json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
        root.string("ttml")?.let { return@runCatching ttml(it, durationMs) }
        val rows = root["lyrics"] as? JsonArray ?: return@runCatching null
        val metadata = root["metadata"] as? JsonObject
        val timed = !root.string("type").equals("None", true)
        val lines = rows.mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val syllables = (row["syllabus"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val main = layer(syllables.filterNot { it.background() }, row.string("text").orEmpty()
                .takeIf { syllables.none { it.background() } }.orEmpty())
            val background = layer(syllables.filter { it.background() }, "")
            if (main.text.isBlank() && background.text.isBlank()) return@mapNotNull null
            val start = if (timed) row.time("time") ?: main.words.firstOrNull()?.timeMs
                ?: background.words.firstOrNull()?.timeMs ?: return@mapNotNull null else -1L
            val explicitEnd = row.time("duration")?.takeIf { it > 0 }?.let { start + it }
            val end = maxOf(explicitEnd ?: 0L, (main.words + background.words).maxOfOrNull { it.endMs } ?: 0L)
            val element = row["element"] as? JsonObject
            LyricsParser.LyricLine(start, main.text, if (timed) main.words else emptyList(), end,
                backgroundLayers = listOf(background).filter { it.text.isNotBlank() },
                translations = localized(row["translation"]),
                pronunciations = localized(row["transliteration"]),
                agentId = element?.string("singer"),
                songPart = element?.string("songPart"), lineKey = element?.string("key"))
        }.let { if (timed) it.sortedBy { line -> line.timeMs } else it }.let { parsed ->
            parsed.mapIndexed { index, line ->
                val lastStart = line.words.maxOfOrNull { it.timeMs } ?: line.timeMs
                if (!timed || line.endMs > lastStart) line else line.copy(endMs =
                    parsed.drop(index + 1).firstOrNull { it.timeMs > lastStart }?.timeMs
                        ?: durationMs.takeIf { it > lastStart } ?: (lastStart + 5000))
            }
        }
        if (lines.isEmpty()) return@runCatching null
        LyricsContent.Legacy(LyricsParser.Lyrics(lines, timed, title, artist, LyricsSource.LYRICS_PLUS.id,
            language = metadata?.string("language"), timing = root.string("type")))
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.time(key: String): Long? =
        (get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 && it < Int.MAX_VALUE }
            ?.toLong()

    private fun JsonObject.background(): Boolean =
        (get("isBackground") as? JsonPrimitive)?.booleanOrNull == true

    private fun layer(parts: List<JsonObject>, fullText: String): LyricsParser.LyricLayer {
        val raw = parts.joinToString("") { (it["text"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
        // A full line can retain spaces/punctuation stripped from individual syllables.
        val text = fullText.takeIf { it.isNotBlank() } ?: raw
        val words = mutableListOf<LyricsParser.LyricWord>()
        var cursor = 0
        for (part in parts) {
            val token = (part["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (token.isEmpty()) continue
            val exact = text.indexOf(token, cursor)
            val trimmed = token.trim()
            val from = if (exact >= 0) exact else text.indexOf(trimmed, cursor)
            if (from < 0) continue
            val matched = if (exact >= 0) token else trimmed
            val leading = matched.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) matched.length else it }
            val visible = matched.trim()
            val start = part.time("time")
            if (start != null && visible.isNotEmpty()) {
                words += LyricsParser.LyricWord(start, visible, start + (part.time("duration") ?: 0L),
                    from + leading, from + leading + visible.length)
            }
            cursor = from + matched.length
        }
        val leading = text.length - text.trimStart().length
        val trimmed = text.trim()
        return LyricsParser.LyricLayer(trimmed, words.map { it.copy(
            charStart = it.charStart - leading, charEnd = it.charEnd - leading) })
    }

    private fun localized(value: JsonElement?): Map<String, LyricsParser.LyricLayer> {
        val layers = when (value) {
            is JsonArray -> value.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(value)
            else -> emptyList()
        }
        return layers.mapNotNull { item ->
            val lang = item.string("lang") ?: "und"
            val parsed = layer((item["syllabus"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject },
                item.string("text").orEmpty())
            parsed.takeIf { it.text.isNotBlank() }?.let { lang to it.copy(language = lang) }
        }.toMap()
    }

    private fun ttml(raw: String, durationMs: Long): LyricsContent.RawTtml? {
        // Android's DOM factory does not support the desktop parser's security features.
        // Reject DTDs before parsing on every platform, and refuse external resolution too.
        if (raw.contains("<!DOCTYPE", ignoreCase = true)) return null
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are not supported") }
        }
        val document = builder.parse(InputSource(StringReader(raw)))
        val root = document.documentElement ?: return null
        if (root.tagName != "tt") return null
        root.setAttribute("xmlns", "http://www.w3.org/ns/ttml")
        root.setAttribute("xmlns:ttm", "http://www.w3.org/ns/ttml#metadata")
        root.setAttribute("xmlns:itunes", "http://music.apple.com/lyric-ttml-internal")
        fun Element.time(name: String): Long? = AppleTtmlParser.parseAppleTime(getAttribute(name))
        fun Element.elements(tag: String): List<Element> {
            val nodes = getElementsByTagName(tag)
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }
        val paragraphs = root.elements("p")
        for (p in paragraphs) {
            if (p.time("begin") == null) {
                p.elements("span").mapNotNull { it.time("begin") }.minOrNull()?.let {
                    p.setAttribute("begin", "${it}ms")
                }
            }
        }
        var hasWordTiming = false
        for ((index, p) in paragraphs.withIndex()) {
            val spans = p.elements("span").filter { it.time("begin") != null }
            val start = p.time("begin") ?: spans.mapNotNull { it.time("begin") }.minOrNull() ?: continue
            val lastStart = spans.mapNotNull { it.time("begin") }.maxOrNull() ?: start
            val timedEnd = spans.mapNotNull { span -> span.time("end")
                ?: span.time("dur")?.let { span.time("begin")!! + it } }.maxOrNull()
            val end = p.time("end") ?: p.time("dur")?.let { start + it }
                ?: timedEnd?.takeIf { it > lastStart }
                ?: paragraphs.drop(index + 1).firstNotNullOfOrNull { it.time("begin")?.takeIf { it > lastStart } }
                ?: durationMs.takeIf { it > lastStart } ?: (lastStart + 5000)
            p.setAttribute("begin", "${start}ms")
            p.setAttribute("end", "${maxOf(end, start + 1)}ms")
            // Resolve children first, so a background wrapper encloses all of its words.
            for (span in spans.asReversed()) {
                val begin = span.time("begin")!!
                val siblingStarts = (span.parentNode as? Element)?.elements("span").orEmpty()
                    .filter { it.parentNode == span.parentNode }
                    .filter { it.getAttribute("ttm:role") == span.getAttribute("ttm:role") }
                    .mapNotNull { it.time("begin") }.filter { it > begin }
                val children = span.elements("span")
                val childEnd = children.mapNotNull { it.time("end") }.maxOrNull()
                val finish = span.time("end") ?: span.time("dur")?.let { begin + it }
                    ?: childEnd ?: siblingStarts.minOrNull() ?: end
                span.setAttribute("end", "${maxOf(finish, begin + 1)}ms")
            }
            hasWordTiming = hasWordTiming || spans.isNotEmpty()
        }
        if (!root.hasAttribute("itunes:timing")) root.setAttribute("itunes:timing", if (hasWordTiming) "Word" else "Line")
        val output = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
        val normalized = output.toString()
        if (TTMLParser().parse(normalized).lines.isEmpty()) return null
        return LyricsContent.RawTtml(normalized, LyricsSource.LYRICS_PLUS.id, LyricsSource.LYRICS_PLUS.title)
    }
}
