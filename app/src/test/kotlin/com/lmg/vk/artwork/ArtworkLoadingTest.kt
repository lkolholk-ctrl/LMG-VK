package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class ArtworkLoadingTest {
    private val query = ArtworkQuery("Godzilla", "Eminem feat. Juice WRLD", 210_000)
    private val vk = "https://userapi.com/vk.jpg"
    private val apple = "https://is1-ssl.mzstatic.com/apple.jpg"

    @Test fun coldLookupDoesNotExposeVkWhileAppleIsPending() {
        val firstFrame = ArtworkLoadState.initial(query, vk, null)
        assertFalse(firstFrame.isReady)
        assertNull(firstFrame.coverUrl)
    }

    @Test fun warmLookupHasAppleInItsFirstFrame() {
        val firstFrame = ArtworkLoadState.initial(query, vk, ArtworkLoadState(true, apple))
        assertTrue(firstFrame.isReady)
        assertEquals(apple, firstFrame.coverUrl)
    }

    @Test fun cachedNoMatchIsDifferentFromAnUnresolvedLookup() {
        val noMatch = ArtworkLoadState.initial(query, vk, ArtworkLoadState(true, null))
        assertTrue(noMatch.isReady)
        assertEquals(vk, noMatch.coverUrl)
        assertNotEquals(noMatch, ArtworkLoadState.initial(query, vk, null))
    }

    @Test fun unavailableMetadataPreservesLocalAndVkFallbacks() {
        assertEquals(ArtworkLoadState(true, vk), ArtworkLoadState.initial(query.copy(durationMs = 0), vk, null))
        assertEquals(ArtworkLoadState(true, null), ArtworkLoadState.initial(null, null, null))
    }

    @Test fun currentSongCanSharePendingStateWithoutShowingItsFallback() {
        val selection = ArtworkSelection(query, null, isReady = false)
        assertTrue(selection.matches(query.copy(album = "Side B")))
        assertFalse(selection.isReady)
        assertNull(selection.coverUrl)
    }

    @Test fun oneSearchResponseResolvesVkCopiesWithDifferentDurations() {
        val cache = ArtworkCandidateCache { 1L }
        val candidate = ItunesArtworkCandidate("Godzilla (feat. Juice WRLD)", "Eminem", 210_000,
            "Music To Be Murdered By", apple, "https://music.apple.com/track")
        cache.put("Eminem Godzilla", listOf(candidate))
        assertEquals(candidate, cache.match(ItunesArtworkMatcher.lookupQuery(query)))
        assertEquals(candidate, cache.match(ItunesArtworkMatcher.lookupQuery(query.copy(durationMs = 211_000))))
        assertNull(cache.match(query.copy(durationMs = 240_000)))
        assertNull(cache.match(query.copy(title = "Godzilla (Live)")))
        assertNull(cache.match(query.copy(artist = "Other")))
    }

    @Test fun broadSearchMissDoesNotBecomeAnExactSongMiss() {
        val cache = ArtworkCandidateCache { 1L }
        cache.put("eminem", emptyList())
        assertNotNull(cache.get("eminem"))
        assertNull(cache.get("eminem godzilla"))
        assertNull(cache.match(query))
    }

    @Test fun staleCandidatesAndOldestSearchesAreEvicted() {
        var now = 0L
        val cache = ArtworkCandidateCache { now }
        val candidate = ItunesArtworkCandidate(query.title, query.artist, query.durationMs,
            "", apple, "https://music.apple.com/track")
        cache.put("old", listOf(candidate))
        now = 12 * 60 * 60 * 1_000L
        assertNull(cache.get("old"))
        assertNull(cache.match(query))
        repeat(129) { cache.put("search-$it", emptyList()) }
        assertNull(cache.get("search-0"))
        assertNotNull(cache.get("search-128"))
    }
}
