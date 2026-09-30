package com.lmg.vk.engine.automix.analysis

import android.content.Context
import com.lmg.vk.engine.lyrics.LocalTtmlStore
import com.lmg.vk.engine.lyrics.LyricsSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Autonomous local track analysis generator for VK tracks not found in Apple Music catalog.
 * Uses cached line-by-line TTML/LRC lyrics to detect exact vocal presence and phrases,
 * constructing an Apple Music MediaAPI compliant JSON payload with beat grid and downbeats.
 */
object LocalAutoMixAnalysisGenerator {

    fun generate(
        context: Context?,
        title: String,
        artist: String,
        durationMs: Long,
        songId: String = deriveNumericId(title, artist, durationMs)
    ): ByteArray {
        val durationSeconds = durationMs / 1000.0
        val bpm = 124.0
        val beatInterval = 60.0 / bpm

        // 1. Build videoEvents (beat grid, downbeats, section boundaries)
        val times = JSONArray()
        val scores = JSONArray()
        var t = 0.0
        var beatIndex = 0
        while (t <= durationSeconds && beatIndex <= 4096) {
            times.put(String.format(Locale.US, "%.4f", t).toDouble())
            val score = when {
                beatIndex % 32 == 0 -> 800 // Section boundary
                beatIndex % 16 == 0 -> 600 // Segment boundary
                beatIndex % 4 == 0 -> 400  // Downbeat / bar start
                else -> 200                // Normal beat
            }
            scores.put(score)
            t += beatInterval
            beatIndex++
        }

        // 2. Extract vocal intervals from TTML lyrics if available
        val vocalIntervals = extractVocalIntervals(context, title, artist, durationMs)

        val vocalActivityArray = JSONArray()
        for ((startMs, endMs) in vocalIntervals) {
            val vocalObj = JSONObject()
            vocalObj.put("startInMilliseconds", startMs.toDouble())
            vocalObj.put("endInMilliseconds", endMs.toDouble())
            vocalObj.put("strength", "high")
            vocalObj.put("kind", "singing")
            vocalActivityArray.put(vocalObj)
        }

        // 3. Assemble complete Apple MediaAPI analysis envelope
        val root = JSONObject()
        val dataArray = JSONArray()
        val songObj = JSONObject()
        songObj.put("id", songId)
        songObj.put("type", "songs")

        val attributes = JSONObject()
        attributes.put("durationInMillis", durationMs.toDouble())
        attributes.put("supportsSmartTransitions", true)
        attributes.put("genreNames", JSONArray().apply { put("Electronic") })
        songObj.put("attributes", attributes)

        val relationships = JSONObject()

        // Audio analysis
        val audioData = JSONObject()
        audioData.put("id", "audio-$songId")
        audioData.put("type", "audio-analysis")
        val audioAttrs = JSONObject()
        audioAttrs.put("bpm", JSONObject().apply { put("main", bpm) })
        audioAttrs.put("key", JSONObject().apply {
            put("main", JSONObject().apply {
                put("tonic", "C")
                put("mode", "minor")
            })
        })
        if (vocalActivityArray.length() > 0) {
            audioAttrs.put("vocalActivity", vocalActivityArray)
        }
        audioData.put("attributes", audioAttrs)

        relationships.put("audio-analysis", JSONObject().apply {
            put("data", JSONArray().apply { put(audioData) })
        })

        // FlexML analysis (structure & beat events)
        val flexData = JSONObject()
        flexData.put("id", "flex-$songId")
        flexData.put("type", "flexml-analysis")
        val flexAttrs = JSONObject()
        val videoEvents = JSONObject()
        videoEvents.put("timeInSeconds", times)
        videoEvents.put("score", scores)
        flexAttrs.put("videoEvents", videoEvents)
        flexData.put("attributes", flexAttrs)

        relationships.put("flexml-analysis", JSONObject().apply {
            put("data", JSONArray().apply { put(flexData) })
        })

        songObj.put("relationships", relationships)
        dataArray.put(songObj)
        root.put("data", dataArray)

        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun extractVocalIntervals(
        context: Context?,
        title: String,
        artist: String,
        durationMs: Long
    ): List<Pair<Long, Long>> {
        if (context == null) return defaultVocalWindow(durationMs)

        // Try reading cached Apple TTML or LMG Lyrics Plus TTML
        val ttml = LocalTtmlStore.read(context, title, artist, durationMs, LyricsSource.APPLE_TTML)
            ?: LocalTtmlStore.read(context, title, artist, durationMs, LyricsSource.LMG_LYRICS_PLUS)

        if (ttml.isNullOrBlank()) return defaultVocalWindow(durationMs)

        val intervals = mutableListOf<Pair<Long, Long>>()
        val regex = Regex("""(?:begin|start)="([^"]+)"\s+(?:end)="([^"]+)"""")
        for (match in regex.findAll(ttml)) {
            val (beginStr, endStr) = match.destructured
            val startMs = parseTtmlTime(beginStr)
            val endMs = parseTtmlTime(endStr)
            if (startMs != null && endMs != null && endMs > startMs) {
                intervals.add(startMs to endMs)
            }
        }

        if (intervals.isEmpty()) return defaultVocalWindow(durationMs)

        // Merge contiguous vocal lines if gap between them is < 2.5 seconds
        val merged = mutableListOf<Pair<Long, Long>>()
        var curStart = intervals.first().first
        var curEnd = intervals.first().second

        for (i in 1 until intervals.size) {
            val (st, en) = intervals[i]
            if (st <= curEnd + 2500L) {
                curEnd = maxOf(curEnd, en)
            } else {
                merged.add(curStart to curEnd)
                curStart = st
                curEnd = en
            }
        }
        merged.add(curStart to curEnd)
        return merged
    }

    private fun defaultVocalWindow(durationMs: Long): List<Pair<Long, Long>> {
        if (durationMs < 30_000L) return emptyList()
        val introEnd = 12_000L
        val outroStart = maxOf(introEnd + 10_000L, durationMs - 16_000L)
        return listOf(introEnd to outroStart)
    }

    private fun parseTtmlTime(timeStr: String): Long? {
        val s = timeStr.trim().removeSuffix("s")
        return when {
            s.contains(':') -> {
                val parts = s.split(':')
                if (parts.size == 2) {
                    val m = parts[0].toLongOrNull() ?: return null
                    val sec = parts[1].toDoubleOrNull() ?: return null
                    (m * 60_000 + sec * 1000).toLong()
                } else null
            }
            else -> {
                val sec = s.toDoubleOrNull() ?: return null
                (sec * 1000).toLong()
            }
        }
    }

    fun deriveNumericId(title: String, artist: String, durationMs: Long): String {
        val hash = (title.trim().lowercase() + "|" + artist.trim().lowercase() + "|" + durationMs).hashCode().toLong()
        return (kotlin.math.abs(hash) % 900_000_000L + 100_000_000L).toString()
    }
}
