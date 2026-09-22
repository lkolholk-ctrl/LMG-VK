package com.lmg.vk.artwork

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class ArtworkQuery(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String = "",
) {
    val usable: Boolean get() = title.isNotBlank() && artist.isNotBlank() && durationMs > 0
}

data class ItunesArtworkCandidate(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String,
    val artworkUrl: String,
    val trackUrl: String,
)

object ItunesArtworkMatcher {
    private val featuring = Regex("(?i)\\b(?:feat\\.?|ft\\.?|featuring)\\s+|п\\.?\\s*у\\.?\\s+|п\\.у\\.\\s*")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val marks = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")
    private val featuredTitle = Regex("""(?i)\s+(?:\(|\[)?(?:feat\.?|ft\.?|featuring)\s+(.+?)(?:\)|\])?\s*$""")
    private val noiseTag = """[\[(]\s*(?:(?:https?://)?(?:www\.)?(?:vk\.com|vk\.ru|vkontakte\.ru)/[^\s\])]+|\d{2,4}\s*(?:kbps|kb/s|кбит/с))\s*[\])]"""
    private val taggedGenre = Regex("""(?i)$noiseTag\s*[-–—|:]?\s*(?:electronic|electronica|hip[ -]?hop|rap|pop|rock|dance|house|techno|trance|dubstep|metal|r&b)(?:\s*$noiseTag)*\s*$""")
    private val noise = Regex(noiseTag, RegexOption.IGNORE_CASE)

    internal fun cleanMetadata(value: String): String = value.replace(taggedGenre, " ")
        .replace(noise, " ").trim().replace(spaces, " ")

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(marks, "").lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(punctuation, " ").trim().replace(spaces, " ")

    internal fun artists(value: String): Set<String> = featuring.replace(value, " & ")
        .split(Regex("\\s*[&,;]\\s*|\\s+[x×]\\s+", RegexOption.IGNORE_CASE))
        .map(::normalize).filter(String::isNotBlank).toSet()

    internal fun signature(title: String, artist: String): Pair<String, Set<String>> {
        val cleanedTitle = cleanMetadata(title)
        val feature = featuredTitle.find(cleanedTitle)
        return normalize(if (feature == null) cleanedTitle else cleanedTitle.removeRange(feature.range)) to
            (artists(cleanMetadata(artist)) + feature?.groupValues?.get(1)?.let(::artists).orEmpty())
    }

    fun lookupQuery(query: ArtworkQuery): ArtworkQuery {
        val (title, artists) = signature(query.title, query.artist)
        return ArtworkQuery(title, artists.sorted().joinToString(", "), query.durationMs,
            normalize(cleanMetadata(query.album)))
    }

    internal fun matchArtists(expected: Set<String>, candidate: Set<String>): Int? {
        if (expected.isEmpty() || candidate.isEmpty()) return null
        if (expected == candidate) return 0
        if (expected.containsAll(candidate) || candidate.containsAll(expected)) return 1
        return null
    }

    fun match(query: ArtworkQuery, candidates: List<ItunesArtworkCandidate>): ItunesArtworkCandidate? {
        if (!query.usable) return null
        val expected = signature(query.title, query.artist)
        if (expected.first.isBlank() || expected.second.isEmpty()) return null
        val matches = candidates.mapNotNull { cand ->
            val candSig = signature(cand.title, cand.artist)
            if (candSig.first != expected.first || cand.durationMs <= 0) return@mapNotNull null
            val durationDiff = abs(cand.durationMs - query.durationMs)
            if (durationDiff > 3_500) return@mapNotNull null
            if (!cand.artworkUrl.startsWith("https://") || !cand.trackUrl.startsWith("https://")) return@mapNotNull null
            val artistScore = matchArtists(expected.second, candSig.second) ?: return@mapNotNull null
            val albumScore = if (query.album.isNotBlank() && normalize(cleanMetadata(cand.album)) == normalize(cleanMetadata(query.album))) 0 else 1
            cand to Triple(artistScore, albumScore, durationDiff)
        }
        return matches.minWithOrNull(compareBy<Pair<ItunesArtworkCandidate, Triple<Int, Int, Long>>> { it.second.first }
            .thenBy { it.second.second }
            .thenBy { it.second.third }
        )?.first
    }
}
