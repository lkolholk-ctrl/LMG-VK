package com.lmg.vk.engine.lyrics

import android.content.Context
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.apple.AppleLyricsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.resume

object ExternalLyricsRepository {
    private const val BETTER_LYRICS_URL = "https://lyrics-api.boidu.dev/getLyrics"
    private val lyricsPlusMirrors = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val appleClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .build()

    suspend fun findWordTimed(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        enabled: Set<LyricsSource>,
    ): LyricsParser.Lyrics? = coroutineScope {
        if (title.isBlank()) return@coroutineScope null
        DebugLog.add("lookup title=$title artist=$artist durationSec=${durationMs / 1000L} sources=${enabled.joinToString(",") { it.name }}")
        var lineSynced: LyricsParser.Lyrics? = null
        if (LyricsSource.APPLE_TTML in enabled) {
            val cachedTtml = withContext(Dispatchers.IO) {
                LocalTtmlStore.read(context, title, artist, durationMs)
            }
            var cacheUsable = false
            if (cachedTtml != null) {
                DebugLog.add("apple cache hit bytes=${cachedTtml.length} head=${head(cachedTtml)}")
                val cachedParsed = parseAppleTtml(cachedTtml, title, artist)
                logAppleParse("cache", cachedParsed)
                if (cachedParsed == null) {
                    DebugLog.add("apple cache entry invalid, refetching")
                    withContext(Dispatchers.IO) {
                        LocalTtmlStore.delete(context, title, artist, durationMs)
                    }
                } else {
                    cacheUsable = true
                    if (cachedParsed.isWordLevel) return@coroutineScope cachedParsed
                    lineSynced = cachedParsed
                }
            }
            if (!cacheUsable) {
                DebugLog.add("apple cache miss")
                val serverTtml = withTimeoutOrNull(14_000L) {
                    fetchAppleTtml(title, artist, durationMs)
                }
                if (serverTtml == null) {
                    DebugLog.add("apple net timeout/null")
                } else {
                    DebugLog.add("apple body bytes=${serverTtml.length} head=${head(serverTtml)}")
                    val fetchedParsed = parseAppleTtml(serverTtml, title, artist)
                    logAppleParse("net", fetchedParsed)
                    if (fetchedParsed != null) {
                        withContext(Dispatchers.IO) {
                            LocalTtmlStore.write(context, title, artist, durationMs, serverTtml)
                        }
                    }
                    fetchedParsed?.let { fetched ->
                        if (fetched.isWordLevel) return@coroutineScope fetched
                        lineSynced = fetched
                    }
                }
            }
        }
        if (LyricsSource.LMG_LYRICS_PLUS in enabled) {
            val content = LmgLyricsPlusProvider.load(context, title, artist, durationMs)
            val parsed = content?.let { parseAppleTtml(it.value, title, artist) }
                ?.copy(source = LyricsSource.LMG_LYRICS_PLUS.id)
            if (parsed?.isWordLevel == true) return@coroutineScope parsed
            if (lineSynced == null && parsed != null) lineSynced = parsed
        }
        if (LyricsSource.LYRICS_PLUS in enabled) {
            val lyricsPlus = withTimeoutOrNull(8_000L) {
                fetchLyricsPlus(title, artist, durationMs)
            }
            DebugLog.add("lyricsplus " + when {
                lyricsPlus == null -> "none"
                lyricsPlus.isWordLevel -> "found word-level"
                else -> "found line-synced"
            })
            if (lyricsPlus?.isWordLevel == true) return@coroutineScope lyricsPlus
            if (lineSynced == null && lyricsPlus != null) lineSynced = lyricsPlus
        }
        if (LyricsSource.BETTER_LYRICS in enabled) {
            val betterLyrics = withTimeoutOrNull(7_000L) {
                fetchBetterLyrics(title, artist, durationMs)
            }
            DebugLog.add("betterlyrics " + when {
                betterLyrics == null -> "none"
                betterLyrics.isWordLevel -> "found word-level"
                else -> "found line-synced"
            })
            if (betterLyrics?.isWordLevel == true) return@coroutineScope betterLyrics
            if (lineSynced == null && betterLyrics != null) lineSynced = betterLyrics
        }
        lineSynced
    }

    suspend fun warmUp() {
        get("${AppleLyricsConfig.PROXY_BASE_URL}/ping", appleClient)
    }

    suspend fun fetchBiniLyricsContent(title: String, artist: String, durationMs: Long): LyricsContent.RawTtml? =
        withTimeoutOrNull(8_000L) {
            BiniLyricsProvider.fetch(title, artist, durationMs) {
                get(it, accept = "application/json, application/ttml+xml, application/xml, text/xml")
            }
        }

    private suspend fun fetchLyricsPlus(
        title: String,
        artist: String,
        durationMs: Long,
    ): LyricsParser.Lyrics? = when (val result = fetchLyricsPlusContent(title, artist, durationMs)) {
        is LyricsContent.Legacy -> result.lyrics
        is LyricsContent.RawTtml -> com.lmg.vk.engine.lyrics.apple.AppleTtmlParser.parse(result.value)?.let {
            LyricsRepository.toLegacyProjection(it, title, artist, LyricsSource.LYRICS_PLUS.id)
        }
        else -> null
    }

    suspend fun fetchLyricsPlusContent(title: String, artist: String, durationMs: Long): LyricsContent? {
        var fallback: LyricsContent? = null
        return withTimeoutOrNull(8_000L) {
            coroutineScope {
                val requests = lyricsPlusMirrors.map { mirror ->
                    mirror to async(Dispatchers.IO) {
                        suspend fun fetch(path: String): LyricsContent? {
                            val url = "$mirror$path".toHttpUrl().newBuilder()
                                .addQueryParameter("title", title)
                                .addQueryParameter("artist", artist)
                                .apply {
                                    if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000.0).toString())
                                }
                                .build()
                            return get(url.toString(), accept = "application/json, application/ttml+xml, application/xml")
                                ?.let { LyricsPlusParser.parse(it, title, artist, durationMs) }
                        }
                        // v2 retains translations/background flags; older mirrors can serve TTML instead.
                        fetch("/v2/lyrics/get") ?: fetch("/v1/ttml/get")
                    }
                }.toMutableList()
                try {
                    while (requests.isNotEmpty()) {
                        val completed = select<Pair<String, LyricsContent?>> {
                            requests.forEach { (mirror, task) ->
                                task.onAwait { mirror to it }
                            }
                        }
                        requests.removeAll { it.first == completed.first }
                        completed.second?.let {
                            if (it.hasWordTiming()) return@coroutineScope it
                            if (fallback == null) fallback = it
                        }
                    }
                    fallback
                } finally {
                    requests.forEach { it.second.cancel() }
                }
            }
        } ?: fallback
    }

    private suspend fun fetchBetterLyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): LyricsParser.Lyrics? = withContext(Dispatchers.IO) {
        val url = BETTER_LYRICS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                if (durationMs > 0) addQueryParameter("d", (durationMs / 1000).toString())
            }
            .build()
        val ttml = get(url.toString())?.let { body ->
            runCatching { JSONObject(body).optString("ttml").takeIf(String::isNotBlank) }.getOrNull()
        } ?: return@withContext null
        parseTtml(ttml)?.takeIf { it.lines.isNotEmpty() }?.let { parsed ->
            LyricsParser.Lyrics(
                lines = parsed.lines,
                isSynced = true,
                title = title,
                artist = artist,
                source = LyricsSource.BETTER_LYRICS.id,
                language = parsed.language,
                timing = parsed.timing,
                songwriters = parsed.songwriters,
            )
        }
    }

    private suspend fun fetchAppleTtml(
        title: String,
        artist: String,
        durationMs: Long,
    ): String? = withContext(Dispatchers.IO) {
        val url = "${AppleLyricsConfig.PROXY_BASE_URL}/v2/lyrics/ttml".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .addQueryParameter("lang", "all")
            .apply {
                if (durationMs > 0L) {
                    addQueryParameter("duration", (durationMs / 1000L).toString())
                }
            }
            .build()
        DebugLog.add("apple request url=$url")
        get(url.toString(), appleClient, "text/plain")
    }

    private fun head(raw: String): String =
        raw.replace("\n", " ").replace("\r", " ").take(80)

    private fun logAppleParse(stage: String, result: LyricsParser.Lyrics?) {
        if (result == null) {
            DebugLog.add("apple parse stage=$stage lines=0 reason=no lines")
        } else {
            val wordLines = result.lines.count { it.words.isNotEmpty() }
            val reason = if (wordLines == 0) " reason=no word timings" else ""
            DebugLog.add("apple parse stage=$stage lines=${result.lines.size} wordLines=$wordLines$reason")
        }
    }

    private fun parseAppleTtml(
        ttml: String,
        title: String,
        artist: String,
    ): LyricsParser.Lyrics? {
        val parsed = parseTtml(ttml)?.takeIf { it.lines.isNotEmpty() } ?: return null
        return LyricsParser.Lyrics(
            lines = parsed.lines,
            isSynced = true,
            title = title,
            artist = artist,
            source = LyricsSource.APPLE_TTML.id,
            language = parsed.language,
            timing = parsed.timing,
            songwriters = parsed.songwriters,
        )
    }

    private suspend fun get(
        url: String,
        httpClient: OkHttpClient = client,
        accept: String = "application/json",
    ): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", "LMG-VK/1.1")
            .apply {
                if (url.startsWith(AppleLyricsConfig.PROXY_BASE_URL)) {
                    header("X-API-Key", AppleLyricsConfig.API_KEY)
                }
            }
            .build()
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                DebugLog.add("get fail cls=${error.javaClass.simpleName} msg=${error.message}")
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val body = runCatching {
                    response.use { if (it.isSuccessful) it.body?.string() else null }
                }.getOrNull()
                DebugLog.add("get status=$code bytes=${body?.length ?: -1}")
                if (continuation.isActive) continuation.resume(body)
            }
        })
    }

    private fun parseTtml(ttml: String): ParsedTtml? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val agents = buildMap {
            val nodes = document.getElementsByTagName("ttm:agent")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val id = element.getAttribute("xml:id").takeIf(String::isNotBlank) ?: continue
                val names = element.getElementsByTagName("ttm:name")
                val name = (names.item(0) as? Element)?.textContent?.trim()?.takeIf(String::isNotBlank)
                put(id, Agent(element.getAttribute("type").ifBlank { "none" }, name))
            }
        }
        run {
            val attrs = document.documentElement.attributes
            val rootAttrs = buildString {
                for (index in 0 until attrs.length) {
                    val item = attrs.item(index)
                    append(item.nodeName).append('=').append(item.nodeValue).append(' ')
                }
            }.trim()
            DebugLog.add(
                "apple xml body=${document.getElementsByTagName("body").length} " +
                    "div=${document.getElementsByTagName("div").length} " +
                    "p=${document.getElementsByTagName("p").length} " +
                    "span=${document.getElementsByTagName("span").length} " +
                    "rootAttrs=$rootAttrs"
            )
        }
        val lines = buildList {
            val divisions = document.getElementsByTagName("div")
            for (divisionIndex in 0 until divisions.length) {
                val division = divisions.item(divisionIndex) as? Element ?: continue
                val paragraphs = division.getElementsByTagName("p")
                for (lineIndex in 0 until paragraphs.length) {
                    val paragraph = paragraphs.item(lineIndex) as? Element ?: continue
                    parseParagraph(paragraph, division, agents)?.let(::add)
                }
            }
            if (isEmpty()) {
                val paragraphs = document.getElementsByTagName("p")
                for (lineIndex in 0 until paragraphs.length) {
                    val paragraph = paragraphs.item(lineIndex) as? Element ?: continue
                    parseParagraph(paragraph, null, agents)?.let(::add)
                }
            }
        }.distinctBy { it.lineKey ?: "${it.timeMs}:${it.text}" }.sortedBy { it.timeMs }
        val writers = buildList {
            val nodes = document.getElementsByTagName("songwriter")
            for (index in 0 until nodes.length) {
                nodes.item(index)?.textContent?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val root = document.documentElement
        ParsedTtml(
            lines = lines,
            language = root.getAttribute("xml:lang").takeIf(String::isNotBlank),
            timing = root.getAttribute("itunes:timing").takeIf(String::isNotBlank),
            songwriters = writers,
        )
    }.getOrNull()

    private fun parseParagraph(
        paragraph: Element,
        division: Element?,
        agents: Map<String, Agent>,
    ): LyricsParser.LyricLine? {
        val main = collectTimedText(paragraph, skippedRoles)
        val paragraphStart = parseClock(paragraph.getAttribute("begin"))
        val start = paragraphStart
            ?: main.words.firstOrNull()?.timeMs
            ?: division?.let { parseClock(it.getAttribute("begin")) }
        if (start == null) {
            DebugLog.add(
                "apple p drop reason=no-begin-no-words " +
                    "begin=${paragraph.getAttribute("begin")} text=${head(main.text)}"
            )
            return null
        }
        if (main.text.isBlank()) {
            DebugLog.add(
                "apple p drop reason=blank-text begin=${paragraph.getAttribute("begin")}"
            )
            return null
        }
        val backgroundLayers = roleElements(paragraph, "x-bg", "x-bg").mapNotNull { element ->
            val layer = collectTimedText(element, localizedRoles)
            layer.takeIf { it.text.isNotBlank() }?.let {
                LyricsParser.LyricLayer(
                    text = it.text,
                    words = it.words,
                    language = element.getAttribute("xml:lang").takeIf(String::isNotBlank),
                    translations = localizedTextLayers(element, "x-translation"),
                    pronunciations = localizedTextLayers(element, "x-roman"),
                )
            }
        }
        val translations = localizedTextLayers(paragraph, "x-translation", "x-bg")
        val pronunciations = localizedTextLayers(paragraph, "x-roman", "x-bg")
        val agentId = paragraph.getAttribute("ttm:agent").takeIf(String::isNotBlank)
            ?: division?.getAttribute("ttm:agent")?.takeIf(String::isNotBlank)
        val agent = agentId?.let(agents::get)
        val end = parseClock(paragraph.getAttribute("end"))
            ?: main.words.lastOrNull()?.endMs
            ?: backgroundLayers.flatMap { it.words }.maxOfOrNull { it.endMs }
            ?: 0L
        return LyricsParser.LyricLine(
            timeMs = start,
            text = main.text,
            words = main.words,
            endMs = end,
            backgroundLayers = backgroundLayers,
            translations = translations,
            pronunciations = pronunciations,
            agentId = agentId,
            agentType = agent?.type,
            agentName = agent?.name,
            songPart = division?.getAttribute("itunes:songPart")?.takeIf(String::isNotBlank),
            lineKey = paragraph.getAttribute("itunes:key").takeIf(String::isNotBlank),
        )
    }

    private fun collectTimedText(node: Node, excludedRoles: Set<String>): TimedText {
        val output = StringBuilder()
        val words = mutableListOf<LyricsParser.LyricWord>()
        fun append(current: Node) {
            val children = current.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element) {
                    if (child.getAttribute("ttm:role") in excludedRoles) continue
                    val start = parseClock(child.getAttribute("begin"))
                    val end = parseClock(child.getAttribute("end"))
                    if (start != null && end != null && !hasTimedChild(child)) {
                        val raw = child.textContent.orEmpty()
                        val leading = raw.indexOfFirst { !it.isWhitespace() }
                            .let { if (it < 0) raw.length else it }
                        val trailing = raw.indexOfLast { !it.isWhitespace() }
                            .let { if (it < 0) leading else it + 1 }
                        val charStart = output.length + leading
                        output.append(raw)
                        if (trailing > leading) {
                            words += LyricsParser.LyricWord(
                                timeMs = start,
                                text = raw.substring(leading, trailing),
                                endMs = end,
                                charStart = charStart,
                                charEnd = charStart + trailing - leading,
                            )
                        }
                    } else {
                        append(child)
                    }
                } else if (child.nodeType == Node.TEXT_NODE) {
                    output.append(child.textContent.orEmpty())
                }
            }
        }
        append(node)
        return trimTimedText(output.toString(), words)
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.getAttribute("begin").isNotBlank() || hasTimedChild(child)) return true
        }
        return false
    }

    private fun roleElements(
        container: Element,
        role: String,
        excludedAncestorRole: String? = null,
    ): List<Element> = buildList {
        val spans = container.getElementsByTagName("span")
        for (index in 0 until spans.length) {
            val element = spans.item(index) as? Element ?: continue
            if (element.getAttribute("ttm:role") != role) continue
            if (excludedAncestorRole != null && hasRoleAncestor(element, container, excludedAncestorRole)) continue
            add(element)
        }
    }

    private fun hasRoleAncestor(element: Element, boundary: Element, role: String): Boolean {
        var parent = element.parentNode
        while (parent != null && parent !== boundary) {
            if (parent is Element && parent.getAttribute("ttm:role") == role) return true
            parent = parent.parentNode
        }
        return false
    }

    private fun localizedTextLayers(
        container: Element,
        role: String,
        excludedAncestorRole: String? = null,
    ): Map<String, LyricsParser.LyricLayer> =
        roleElements(container, role, excludedAncestorRole).mapNotNull { element ->
            val timedText = collectTimedText(element, emptySet())
            val text = timedText.text.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val language = element.getAttribute("xml:lang").ifBlank { "und" }
            language to LyricsParser.LyricLayer(
                text = text,
                words = timedText.words,
                language = language,
            )
        }.toMap()

    private fun parseClock(value: String?): Long? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val parts = raw.removeSuffix("s").split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { minutes ->
                parts[1].toDoubleOrNull()?.let { minutes * 60 + it }
            }
            3 -> parts[0].toDoubleOrNull()?.let { hours ->
                parts[1].toDoubleOrNull()?.let { minutes ->
                    parts[2].toDoubleOrNull()?.let { hours * 3600 + minutes * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private fun trimTimedText(
        raw: String,
        words: List<LyricsParser.LyricWord>,
    ): TimedText {
        val first = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length else it }
        val last = raw.indexOfLast { !it.isWhitespace() }.let { if (it < 0) first else it + 1 }
        if (last <= first) return TimedText("", emptyList())
        val text = raw.substring(first, last)
        val adjusted = words.mapNotNull { word ->
            val start = (word.charStart - first).coerceAtLeast(0)
            val end = (word.charEnd - first).coerceAtMost(text.length)
            if (end <= start) null else word.copy(
                text = text.substring(start, end),
                charStart = start,
                charEnd = end,
            )
        }
        return TimedText(text, adjusted)
    }

    private val localizedRoles = setOf("x-translation", "x-roman")
    private val skippedRoles = localizedRoles + "x-bg"

    private data class TimedText(
        val text: String,
        val words: List<LyricsParser.LyricWord>,
    )

    private data class ParsedTtml(
        val lines: List<LyricsParser.LyricLine>,
        val language: String?,
        val timing: String?,
        val songwriters: List<String>,
    )

    private data class Agent(val type: String, val name: String?)
}
