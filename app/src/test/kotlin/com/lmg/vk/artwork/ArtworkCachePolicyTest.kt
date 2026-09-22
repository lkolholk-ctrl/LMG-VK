package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class ArtworkCachePolicyTest {
    @Test fun oldNegativeResultsAreIgnoredEvenBeforeTheirTwelveHourExpiry() {
        assertFalse(ArtworkCachePolicy.canReuse(false, 43_200_000, 0, 1_000))
        assertTrue(ArtworkCachePolicy.canReuse(true, 43_200_000, 0, 1_000))
    }

    @Test fun freshNegativeResultsExpireWithoutARequestOnEveryScreenOpening() {
        val expires = 1_000 + ArtworkCachePolicy.MISS_TTL_MS
        assertTrue(ArtworkCachePolicy.canReuse(false, expires, ArtworkCachePolicy.LOOKUP_VERSION, 1_001))
        assertFalse(ArtworkCachePolicy.canReuse(false, expires, ArtworkCachePolicy.LOOKUP_VERSION, expires))
        assertFalse(ArtworkCachePolicy.canReuse(true, expires, 0, expires))
    }

    @Test fun emptyItunesSearchCannotKeepSuppressingRetriesForTwelveHours() {
        var now = 0L
        val cache = ArtworkCandidateCache { now }
        cache.put("ordinary loss health", emptyList())
        assertNotNull(cache.get("ordinary loss health"))
        now = ArtworkCachePolicy.MISS_TTL_MS
        assertNull(cache.get("ordinary loss health"))
    }
}
