package com.lmg.vk.artwork

internal class ArtworkCandidateCache(private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val candidates: List<ItunesArtworkCandidate>, val expires: Long)
    private val entries = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 128
    }

    @Synchronized
    fun get(term: String): List<ItunesArtworkCandidate>? =
        entries[term]?.takeIf { it.expires > clock() }?.candidates

    @Synchronized
    fun put(term: String, candidates: List<ItunesArtworkCandidate>) {
        entries[term] = Entry(candidates, clock() +
            if (candidates.isEmpty()) ArtworkCachePolicy.MISS_TTL_MS else 12 * 60 * 60 * 1_000L)
    }

    @Synchronized
    fun match(query: ArtworkQuery): ItunesArtworkCandidate? =
        ItunesArtworkMatcher.match(query, entries.values.asSequence()
            .filter { it.expires > clock() }.flatMap { it.candidates.asSequence() }.toList())
}
