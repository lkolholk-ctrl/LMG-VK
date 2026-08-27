package com.lmg.vk.engine.lyrics.apple

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

object AppleTtmlParser {

    fun parse(rawTtml: String): AppleLyricsDocument? = runCatching {
        if (rawTtml.isBlank()) return null

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(rawTtml)))
        val root = document.documentElement ?: return null

        val timingAttr = root.getAttrOrNull("itunes:timing")
            ?: root.getAttrOrNull("timing")
        val timing = when (timingAttr?.trim()?.lowercase()) {
            "word" -> AppleTimingType.WORD
            "line" -> AppleTimingType.LINE
            "none" -> AppleTimingType.NONE
            else -> AppleTimingType.WORD
        }

        val language = root.getAttrOrNull("xml:lang") ?: root.getAttrOrNull("lang")
        val script = root.getAttrOrNull("itunes:script")
        val translation = root.getAttrOrNull("itunes:translation")
        val pronunciation = root.getAttrOrNull("itunes:pronunciation")

        val agents = parseAgents(document)
        val songwriters = parseSongwriters(document)

        val sections = parseBodySections(document, agents)
        if (sections.isEmpty() || sections.all { it.lines.isEmpty() }) return null

        val bodyDuration = parseAppleTime(document.getElementsByTagName("body").item(0)?.let {
            (it as? Element)?.getAttrOrNull("dur")
        }) ?: sections.maxOfOrNull { it.endMs } ?: 0L

        AppleLyricsDocument(
            durationMs = bodyDuration,
            language = language,
            timing = timing,
            sections = sections,
            agents = agents,
            script = script,
            translation = translation,
            pronunciation = pronunciation,
            songwriters = songwriters,
            rawTtml = rawTtml
        )
    }.getOrNull()

    fun parseAppleTime(raw: String?): Long? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        if (trimmed.endsWith("ms", ignoreCase = true)) {
            return trimmed.dropLast(2).trim().toDoubleOrNull()?.roundToLong()
        }

        val clean = trimmed.removeSuffix("s").removeSuffix("S").trim()
        val parts = clean.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> {
                val min = parts[0].toDoubleOrNull()
                val sec = parts[1].toDoubleOrNull()
                if (min != null && sec != null) min * 60.0 + sec else null
            }
            3 -> {
                val hr = parts[0].toDoubleOrNull()
                val min = parts[1].toDoubleOrNull()
                val sec = parts[2].toDoubleOrNull()
                if (hr != null && min != null && sec != null) hr * 3600.0 + min * 60.0 + sec else null
            }
            else -> null
        } ?: return null

        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    private fun parseAgents(document: Document): Map<String, AppleLyricsAgent> {
        val agents = mutableMapOf<String, AppleLyricsAgent>()
        val agentNodes = document.getElementsByTagName("ttm:agent")
        val agentNodesAlt = if (agentNodes.length == 0) document.getElementsByTagName("agent") else agentNodes

        for (i in 0 until agentNodesAlt.length) {
            val element = agentNodesAlt.item(i) as? Element ?: continue
            val id = element.getAttrOrNull("xml:id") ?: element.getAttrOrNull("id") ?: continue
            val typeStr = element.getAttrOrNull("type")?.trim()?.lowercase().orEmpty()
            val agentType = when (typeStr) {
                "person" -> AppleAgentType.PERSON
                "character" -> AppleAgentType.CHARACTER
                "group" -> AppleAgentType.GROUP
                "organization" -> AppleAgentType.ORGANIZATION
                "other" -> AppleAgentType.OTHER
                else -> AppleAgentType.NONE
            }

            var name: String? = null
            val nameNodes = element.getElementsByTagName("ttm:name")
            val nameNodesAlt = if (nameNodes.length == 0) element.getElementsByTagName("name") else nameNodes
            if (nameNodesAlt.length > 0) {
                name = nameNodesAlt.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }

            agents[id] = AppleLyricsAgent(id = id, type = agentType, name = name)
        }
        return agents
    }

    private fun parseSongwriters(document: Document): List<AppleSongwriter> {
        val writers = mutableListOf<AppleSongwriter>()
        val nodes = document.getElementsByTagName("songwriter")
        for (i in 0 until nodes.length) {
            val text = nodes.item(i)?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            writers += AppleSongwriter(name = text)
        }
        if (writers.isEmpty()) {
            val ttmNodes = document.getElementsByTagName("ttm:songwriter")
            for (i in 0 until ttmNodes.length) {
                val text = ttmNodes.item(i)?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                writers += AppleSongwriter(name = text)
            }
        }
        return writers
    }

    private fun parseBodySections(
        document: Document,
        agents: Map<String, AppleLyricsAgent>
    ): List<AppleLyricsSection> {
        val divisions = document.getElementsByTagName("div")
        if (divisions.length > 0) {
            val sections = mutableListOf<AppleLyricsSection>()
            for (i in 0 until divisions.length) {
                val div = divisions.item(i) as? Element ?: continue
                val section = parseDivision(div, agents, defaultOrderStart = sections.sumOf { it.lines.size } * 1000)
                if (section.lines.isNotEmpty()) {
                    sections += section
                }
            }
            if (sections.isNotEmpty()) return sections
        }

        // Fallback: if no <div> or empty divs, parse all <p> in <body> as a single section
        val bodyElements = document.getElementsByTagName("body")
        val bodyElement = if (bodyElements.length > 0) bodyElements.item(0) as? Element else null
        val paragraphs = (bodyElement ?: document.documentElement).getElementsByTagName("p")
        val lines = mutableListOf<AppleLyricsLine>()
        var orderCounter = 0

        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue
            val line = parseParagraph(p, null, agents, lineIndex = i, orderStart = orderCounter)
            if (line != null) {
                lines += line
                orderCounter += (line.main.size + line.background.size) + 10
            }
        }

        if (lines.isEmpty()) return emptyList()

        val secBegin = lines.first().beginMs
        val secEnd = lines.maxOfOrNull { it.endMs } ?: secBegin
        return listOf(
            AppleLyricsSection(
                beginMs = secBegin,
                endMs = secEnd,
                songPart = null,
                lines = lines.sortedBy { it.beginMs }
            )
        )
    }

    private fun parseDivision(
        division: Element,
        agents: Map<String, AppleLyricsAgent>,
        defaultOrderStart: Int
    ): AppleLyricsSection {
        val songPart = division.getAttrOrNull("itunes:songPart") ?: division.getAttrOrNull("songPart")
        val divAgentId = division.getAttrOrNull("ttm:agent") ?: division.getAttrOrNull("agent")
        val divBegin = parseAppleTime(division.getAttrOrNull("begin")) ?: 0L

        val paragraphs = division.getElementsByTagName("p")
        val lines = mutableListOf<AppleLyricsLine>()
        var orderCounter = defaultOrderStart

        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue
            val line = parseParagraph(p, divAgentId, agents, lineIndex = i, orderStart = orderCounter)
            if (line != null) {
                lines += line
                orderCounter += (line.main.size + line.background.size + line.translation.size + line.pronunciation.size) + 10
            }
        }

        val sortedLines = lines.sortedBy { it.beginMs }
        val begin = parseAppleTime(division.getAttrOrNull("begin"))
            ?: sortedLines.firstOrNull()?.beginMs
            ?: 0L
        val end = parseAppleTime(division.getAttrOrNull("end"))
            ?: sortedLines.maxOfOrNull { it.endMs }
            ?: begin

        return AppleLyricsSection(
            beginMs = begin,
            endMs = end.coerceAtLeast(begin + 1),
            songPart = songPart,
            lines = sortedLines
        )
    }

    private fun parseParagraph(
        p: Element,
        inheritedAgentId: String?,
        agents: Map<String, AppleLyricsAgent>,
        lineIndex: Int,
        orderStart: Int
    ): AppleLyricsLine? {
        val lineId = p.getAttrOrNull("xml:id") ?: p.getAttrOrNull("id") ?: "line_$lineIndex"
        val lineKey = p.getAttrOrNull("itunes:key") ?: p.getAttrOrNull("key")
        val lineAgentId = p.getAttrOrNull("ttm:agent") ?: p.getAttrOrNull("agent") ?: inheritedAgentId

        val parsedMain = mutableListOf<AppleLyricPiece>()
        val parsedBg = mutableListOf<AppleLyricPiece>()
        val parsedTrans = mutableListOf<AppleLyricPiece>()
        val parsedTransBg = mutableListOf<AppleLyricPiece>()
        val parsedPron = mutableListOf<AppleLyricPiece>()
        val parsedPronBg = mutableListOf<AppleLyricPiece>()

        var pieceOrder = orderStart

        fun processNode(
            node: Node,
            currentRole: ApplePieceRole,
            inheritedPieceAgentId: String?,
            defaultBeginMs: Long?,
            defaultEndMs: Long?
        ) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) ?: continue
                if (child is Element) {
                    val roleAttr = child.getAttrOrNull("ttm:role") ?: child.getAttrOrNull("role")
                    val targetRole = when (roleAttr) {
                        "x-bg" -> if (currentRole == ApplePieceRole.TRANSLATION) ApplePieceRole.TRANSLATION_BACKGROUND
                                  else if (currentRole == ApplePieceRole.PRONUNCIATION) ApplePieceRole.PRONUNCIATION_BACKGROUND
                                  else ApplePieceRole.BACKGROUND
                        "x-translation" -> ApplePieceRole.TRANSLATION
                        "x-roman" -> ApplePieceRole.PRONUNCIATION
                        else -> currentRole
                    }

                    val spanAgentId = child.getAttrOrNull("ttm:agent") ?: child.getAttrOrNull("agent") ?: inheritedPieceAgentId
                    val spanBegin = parseAppleTime(child.getAttrOrNull("begin")) ?: defaultBeginMs
                    val spanEnd = parseAppleTime(child.getAttrOrNull("end")) ?: defaultEndMs

                    if (hasTimedDescendant(child)) {
                        processNode(child, targetRole, spanAgentId, spanBegin, spanEnd)
                    } else {
                        val text = child.textContent.orEmpty()
                        if (text.isNotEmpty()) {
                            val bMs = spanBegin ?: 0L
                            val eMs = (spanEnd ?: (bMs + 1)).coerceAtLeast(bMs + 1)
                            val piece = AppleLyricPiece(
                                id = "${lineId}_p_${pieceOrder++}",
                                text = text,
                                beginMs = bMs,
                                endMs = eMs,
                                agentId = spanAgentId,
                                role = targetRole,
                                isWhitespace = text.all { it.isWhitespace() },
                                sourceOrder = pieceOrder
                            )
                            when (targetRole) {
                                ApplePieceRole.MAIN -> parsedMain += piece
                                ApplePieceRole.BACKGROUND -> parsedBg += piece
                                ApplePieceRole.TRANSLATION -> parsedTrans += piece
                                ApplePieceRole.TRANSLATION_BACKGROUND -> parsedTransBg += piece
                                ApplePieceRole.PRONUNCIATION -> parsedPron += piece
                                ApplePieceRole.PRONUNCIATION_BACKGROUND -> parsedPronBg += piece
                            }
                        }
                    }
                } else if (child.nodeType == Node.TEXT_NODE) {
                    val rawText = child.textContent.orEmpty()
                    // If text node is purely newlines/indentation between XML tags, ignore it as formatting
                    val isFormatting = (rawText.contains('\n') || rawText.contains('\r')) && rawText.isBlank()
                    if (rawText.isNotEmpty() && !isFormatting) {
                        val bMs = defaultBeginMs ?: 0L
                        val eMs = (defaultEndMs ?: (bMs + 1)).coerceAtLeast(bMs + 1)
                        val piece = AppleLyricPiece(
                            id = "${lineId}_p_${pieceOrder++}",
                            text = rawText,
                            beginMs = bMs,
                            endMs = eMs,
                            agentId = inheritedPieceAgentId,
                            role = currentRole,
                            isWhitespace = rawText.all { it.isWhitespace() },
                            sourceOrder = pieceOrder
                        )
                        when (currentRole) {
                            ApplePieceRole.MAIN -> parsedMain += piece
                            ApplePieceRole.BACKGROUND -> parsedBg += piece
                            ApplePieceRole.TRANSLATION -> parsedTrans += piece
                            ApplePieceRole.TRANSLATION_BACKGROUND -> parsedTransBg += piece
                            ApplePieceRole.PRONUNCIATION -> parsedPron += piece
                            ApplePieceRole.PRONUNCIATION_BACKGROUND -> parsedPronBg += piece
                        }
                    }
                }
            }
        }

        val pBegin = parseAppleTime(p.getAttrOrNull("begin"))
        val pEnd = parseAppleTime(p.getAttrOrNull("end"))

        processNode(p, ApplePieceRole.MAIN, lineAgentId, pBegin, pEnd)

        val nonWhitespaceMain = parsedMain.filter { !it.isWhitespace }
        val nonWhitespaceBg = parsedBg.filter { !it.isWhitespace }

        if (nonWhitespaceMain.isEmpty() && nonWhitespaceBg.isEmpty()) return null

        val lineBegin = pBegin
            ?: nonWhitespaceMain.firstOrNull()?.beginMs
            ?: nonWhitespaceBg.firstOrNull()?.beginMs
            ?: 0L

        val lineEnd = pEnd
            ?: maxOf(
                nonWhitespaceMain.maxOfOrNull { it.endMs } ?: lineBegin,
                nonWhitespaceBg.maxOfOrNull { it.endMs } ?: lineBegin
            ).coerceAtLeast(lineBegin + 1)

        return AppleLyricsLine(
            id = lineId,
            key = lineKey,
            beginMs = lineBegin,
            endMs = lineEnd.coerceAtLeast(lineBegin + 1),
            agentId = lineAgentId,
            main = parsedMain,
            background = parsedBg,
            translation = parsedTrans,
            translationBackground = parsedTransBg,
            pronunciation = parsedPron,
            pronunciationBackground = parsedPronBg
        )
    }

    private fun hasTimedDescendant(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.getAttrOrNull("begin") != null || hasTimedDescendant(child)) return true
        }
        return false
    }

    private fun Element.getAttrOrNull(name: String): String? {
        val v = getAttribute(name)
        return if (v.isNullOrBlank()) null else v
    }
}
