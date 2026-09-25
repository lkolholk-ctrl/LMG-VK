package com.lmg.vk.engine.lyrics

import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.math.abs

internal object BiniLyricsProvider {
    private const val BASE_URL = "https://lyrics-api.binimum.org/getLyrics"

    suspend fun fetch(
        title: String,
        artist: String,
        durationMs: Long,
        get: suspend (String) -> String?,
    ): LyricsContent.RawTtml? {
        if (title.isBlank() || artist.isBlank()) return null
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$title $artist".trim()).build()
        val body = get(url.toString()) ?: return null
        for (candidate in candidates(body, title, artist, durationMs).take(3)) {
            val raw = get(candidate) ?: continue
            val valid = runCatching {
                !raw.contains("<!DOCTYPE", ignoreCase = true) &&
                    TTMLParser().canParse(raw) && TTMLParser().parse(raw).lines.isNotEmpty()
            }.getOrDefault(false)
            if (valid) return LyricsContent.RawTtml(raw, LyricsSource.BINI_LYRICS.id, LyricsSource.BINI_LYRICS.title)
        }
        return null
    }

    internal fun candidates(body: String, title: String, artist: String, durationMs: Long): List<String> = runCatching {
        val rows = (Json.parseToJsonElement(body) as? JsonObject)?.get("results") as? JsonArray
            ?: return@runCatching emptyList()
        val wantedTitle = normalize(title)
        val wantedArtist = normalize(artist)
        if (wantedTitle.isEmpty() || wantedArtist.isEmpty()) return@runCatching emptyList()
        rows.mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val name = row.text("track_name") ?: return@mapNotNull null
            val performer = normalize(row.text("artist_name").orEmpty())
            if (performer != wantedArtist) return@mapNotNull null
            val exactTitle = normalize(name) == wantedTitle
            val seconds = (row["duration"] as? JsonPrimitive)?.doubleOrNull
                ?.takeIf { it.isFinite() && it > 0 }
            val difference = seconds?.let { abs(it * 1000 - durationMs) }
            if (durationMs > 0 && difference != null && difference > 8000) return@mapNotNull null
            // A generic title may match a labeled edition only when its duration also matches.
            val baseTitle = normalize(name.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " "))
            if (!exactTitle && !(baseTitle == wantedTitle && durationMs > 0 && difference != null)) {
                return@mapNotNull null
            }
            val lyricUrl = row.text("lyricsUrl")?.toHttpUrlOrNull() ?: return@mapNotNull null
            if (!lyricUrl.isHttps || lyricUrl.host != "lyrics-storage.binimum.org" ||
                lyricUrl.username.isNotEmpty() || lyricUrl.password.isNotEmpty() || lyricUrl.port != 443
            ) return@mapNotNull null
            val score = (if (exactTitle) 100 else 0) +
                (if (row.text("timing_type").equals("word", true)) 20 else 0) -
                (if (durationMs > 0) (difference ?: 8000.0) / 1000 else 0.0)
            lyricUrl.toString() to score
        }.sortedByDescending { it.second }.map { it.first }.distinct()
    }.getOrDefault(emptyList())

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
