package com.lmg.vk.engine.lyrics

import com.lmg.vk.engine.LyricsParser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
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

    suspend fun findWordTimed(
        title: String,
        artist: String,
        durationMs: Long,
        enabled: Set<LyricsSource>,
    ): LyricsParser.Lyrics? = coroutineScope {
        if (title.isBlank() || artist.isBlank()) return@coroutineScope null
        val pending = mutableListOf<Pair<LyricsSource, Deferred<LyricsParser.Lyrics?>>>()
        if (LyricsSource.LYRICS_PLUS in enabled) {
            pending += LyricsSource.LYRICS_PLUS to async(Dispatchers.IO) {
                fetchLyricsPlus(title, artist, durationMs)
            }
        }
        if (LyricsSource.BETTER_LYRICS in enabled) {
            pending += LyricsSource.BETTER_LYRICS to async(Dispatchers.IO) {
                fetchBetterLyrics(title, artist, durationMs)
            }
        }
        var lineSynced: LyricsParser.Lyrics? = null
        try {
            while (pending.isNotEmpty()) {
                val completed = select<Pair<LyricsSource, LyricsParser.Lyrics?>> {
                    pending.forEach { (source, task) ->
                        task.onAwait { source to it }
                    }
                }
                pending.removeAll { it.first == completed.first }
                val lyrics = completed.second ?: continue
                if (lyrics.isWordLevel) return@coroutineScope lyrics
                if (lineSynced == null) lineSynced = lyrics
            }
            lineSynced
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetchLyricsPlus(
        title: String,
        artist: String,
        durationMs: Long,
    ): LyricsParser.Lyrics? = coroutineScope {
        val requests = lyricsPlusMirrors.map { mirror ->
            mirror to async(Dispatchers.IO) {
                val url = "$mirror/v2/lyrics/get".toHttpUrl().newBuilder()
                    .addQueryParameter("title", title)
                    .addQueryParameter("artist", artist)
                    .apply {
                        if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString())
                    }
                    .build()
                get(url.toString())?.let(::parseLyricsPlus)
            }
        }.toMutableList()
        try {
            while (requests.isNotEmpty()) {
                val completed = select<Pair<String, LyricsParser.Lyrics?>> {
                    requests.forEach { (mirror, task) ->
                        task.onAwait { mirror to it }
                    }
                }
                requests.removeAll { it.first == completed.first }
                completed.second?.let { return@coroutineScope it }
            }
            null
        } finally {
            requests.forEach { it.second.cancel() }
        }
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
        parseTtml(ttml).takeIf { it.isNotEmpty() }?.let { lines ->
            LyricsParser.Lyrics(
                lines = lines,
                isSynced = true,
                title = title,
                artist = artist,
                source = LyricsSource.BETTER_LYRICS.id,
            )
        }
    }

    private suspend fun get(url: String): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "LMG-VK/1.1")
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = runCatching {
                    response.use { if (it.isSuccessful) it.body?.string() else null }
                }.getOrNull()
                if (continuation.isActive) continuation.resume(body)
            }
        })
    }

    private fun parseLyricsPlus(body: String): LyricsParser.Lyrics? = runCatching {
        val root = JSONObject(body)
        val rows = root.optJSONArray("lyrics") ?: JSONArray()
        val lines = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val start = row.optLongOrNull("time") ?: continue
                val syllables = row.optJSONArray("syllabus")
                val words = syllables?.let(::mergeSyllables).orEmpty()
                val text = if (words.isNotEmpty()) words.joinToString(" ") { it.text }
                else row.optString("text").trim()
                if (text.isBlank()) continue
                val duration = row.optLongOrNull("duration") ?: 0L
                add(
                    LyricsParser.LyricLine(
                        timeMs = minOf(start, words.firstOrNull()?.timeMs ?: start),
                        text = text,
                        words = words,
                        endMs = if (duration > 0) start + duration else words.lastOrNull()?.endMs ?: 0L,
                    )
                )
            }
        }.sortedBy { it.timeMs }
        if (lines.isEmpty()) return@runCatching null
        LyricsParser.Lyrics(
            lines = lines,
            isSynced = true,
            title = null,
            artist = null,
            source = LyricsSource.LYRICS_PLUS.id,
        )
    }.getOrNull()

    private fun mergeSyllables(syllables: JSONArray): List<LyricsParser.LyricWord> {
        val result = mutableListOf<LyricsParser.LyricWord>()
        val text = StringBuilder()
        var start = 0L
        var end = 0L
        for (index in 0 until syllables.length()) {
            val syllable = syllables.optJSONObject(index) ?: continue
            val raw = syllable.optString("text")
            val time = syllable.optLongOrNull("time") ?: continue
            if (raw.isBlank()) continue
            if (text.isEmpty()) start = time
            text.append(raw.trim())
            end = time + (syllable.optLongOrNull("duration") ?: 0L)
            if (raw.lastOrNull()?.isWhitespace() == true) {
                result += LyricsParser.LyricWord(start, text.toString(), end)
                text.clear()
            }
        }
        if (text.isNotEmpty()) result += LyricsParser.LyricWord(start, text.toString(), end)
        return result
    }

    private fun parseTtml(ttml: String): List<LyricsParser.LyricLine> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val paragraphs = document.getElementsByTagName("p")
        buildList {
            for (index in 0 until paragraphs.length) {
                val paragraph = paragraphs.item(index) as? Element ?: continue
                parseParagraph(paragraph)?.let(::add)
            }
        }.sortedBy { it.timeMs }
    }.getOrDefault(emptyList())

    private fun parseParagraph(paragraph: Element): LyricsParser.LyricLine? {
        if (paragraph.getAttribute("ttm:role") in skippedRoles) return null
        val pieces = mutableListOf<Piece>()
        collectTimedPieces(paragraph, pieces)
        val words = mergeTimedPieces(pieces)
        val paragraphStart = parseClock(paragraph.getAttribute("begin"))
        if (words.isNotEmpty()) {
            val start = paragraphStart ?: words.first().timeMs
            return LyricsParser.LyricLine(
                timeMs = minOf(start, words.first().timeMs),
                text = words.joinToString(" ") { it.text },
                words = words,
                endMs = words.last().endMs,
            )
        }
        val text = paragraph.textContent?.trim().orEmpty()
        val start = paragraphStart ?: return null
        if (text.isBlank()) return null
        return LyricsParser.LyricLine(
            timeMs = start,
            text = text,
            endMs = parseClock(paragraph.getAttribute("end")) ?: 0L,
        )
    }

    private fun collectTimedPieces(node: Node, output: MutableList<Piece>) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                if (child.getAttribute("ttm:role") in skippedRoles) continue
                val start = parseClock(child.getAttribute("begin"))
                val end = parseClock(child.getAttribute("end"))
                if (start != null && end != null && !hasTimedChild(child)) {
                    output += Piece.Timed(child.textContent.orEmpty(), start, end)
                } else {
                    collectTimedPieces(child, output)
                }
            } else if (child.nodeType == Node.TEXT_NODE && child.textContent.isNotEmpty()) {
                output += Piece.Text(child.textContent)
            }
        }
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.getAttribute("begin").isNotBlank() || hasTimedChild(child)) return true
        }
        return false
    }

    private fun mergeTimedPieces(pieces: List<Piece>): List<LyricsParser.LyricWord> {
        val result = mutableListOf<LyricsParser.LyricWord>()
        val text = StringBuilder()
        var start = 0L
        var end = 0L
        var timed = false
        fun flush() {
            val word = text.toString().trim()
            if (word.isNotEmpty() && timed) result += LyricsParser.LyricWord(start, word, end)
            text.clear()
            timed = false
        }
        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> if (piece.value.isBlank()) flush() else if (timed) text.append(piece.value)
                is Piece.Timed -> {
                    if (piece.value.isBlank()) return@forEach
                    if (piece.value.first().isWhitespace()) flush()
                    if (text.isEmpty()) start = piece.startMs
                    text.append(piece.value.trim())
                    end = piece.endMs
                    timed = true
                    if (piece.value.last().isWhitespace()) flush()
                }
            }
        }
        if (text.isNotEmpty()) flush()
        return result
    }

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

    private fun JSONObject.optLongOrNull(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return optLong(name)
    }

    private val skippedRoles = setOf("x-translation", "x-roman", "x-bg")

    private sealed interface Piece {
        data class Text(val value: String) : Piece
        data class Timed(val value: String, val startMs: Long, val endMs: Long) : Piece
    }
}
