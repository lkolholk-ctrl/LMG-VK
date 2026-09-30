package com.lmg.vk.ui.lyrics

import com.lmg.vk.engine.lyrics.LyricsSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RetainedLyricsCacheTest {
    @Test fun completedEmptyLookupIsReusedButExpiresForRetry() = cacheTest { owner ->
        var now = 0L
        val cache = RetainedLyricsCache<String, String>(owner, 4, 100, String::length,
            expiresAfterMs = { if (it.isEmpty()) 30_000 else Long.MAX_VALUE }, nowMs = { now })
        assertEquals("", cache.getOrPrepare("missing") { "" })
        assertEquals("", cache.getOrPrepare("missing") { error("Repeated background search") })
        now = 30_000
        assertNull(cache.peek("missing"))
        assertEquals("recovered", cache.getOrPrepare("missing") { "recovered" })
    }

    @Test fun trackChangeCancelsOldRequestWithoutDiscardingReadyLyrics() = cacheTest { owner ->
        val cache = cache(owner)
        cache.getOrPrepare("ready") { "saved" }
        val entered = CompletableDeferred<Unit>()
        val old = launch { cache.getOrPrepare("old") { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        cache.cancelPending("old")
        old.join()
        assertTrue(old.isCancelled)
        assertEquals("saved", cache.peek("ready"))
        assertEquals("current", cache.getOrPrepare("new") { "current" })
    }

    @Test fun clearingRemovesMemoryAndCancelsPendingLoads() = cacheTest { owner ->
        val cache = cache(owner)
        cache.getOrPrepare("ready") { "saved" }
        val entered = CompletableDeferred<Unit>()
        val pending = launch {
            cache.getOrPrepare("pending") { entered.complete(Unit); awaitCancellation() }
        }
        entered.await()
        cache.clear()
        pending.join()
        assertTrue(pending.isCancelled)
        assertNull(cache.peek("ready"))
        assertNull(cache.peek("pending"))
        assertEquals("fresh", cache.getOrPrepare("pending") { "fresh" })
    }
    private fun cacheTest(block: suspend CoroutineScope.(CoroutineScope) -> Unit) = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { block(owner) } finally { owner.cancel() }
    }

    private fun cache(owner: CoroutineScope, entries: Int = 4, weight: Int = 100) =
        RetainedLyricsCache<String, String>(owner, entries, weight, String::length, String::isNotEmpty)

    @Test fun closingSheetDoesNotCancelLoadAndReopeningJoinsSameWork() = cacheTest { owner ->
        val cache = cache(owner)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val firstScreen = launch {
            cache.getOrPrepare("song") { calls++; entered.complete(Unit); release.await(); "lyrics" }
        }
        entered.await()
        firstScreen.cancelAndJoin()
        val nextScreen = async { cache.getOrPrepare("song") { calls++; "wrong" } }
        yield()
        release.complete(Unit)
        assertEquals("lyrics", nextScreen.await())
        assertEquals(1, calls)
        assertEquals("lyrics", cache.peek("song"))
        assertEquals("lyrics", cache.getOrPrepare("song") { error("Repeated provider request") })
    }

    @Test fun loadCompletesWithoutAnySheetAndIsAvailableSynchronously() = cacheTest { owner ->
        val cache = cache(owner)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val screen = launch {
            cache.getOrPrepare("song") { entered.complete(Unit); release.await(); "ready" }
        }
        entered.await()
        screen.cancelAndJoin()
        release.complete(Unit)
        assertEquals("ready", cache.peek("song"))
    }

    @Test fun latePreviousTrackCannotReplaceCurrentTrack() = cacheTest { owner ->
        val cache = cache(owner)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async {
            cache.getOrPrepare("old") { entered.complete(Unit); release.await(); "old lyrics" }
        }
        entered.await()
        assertEquals("new lyrics", cache.getOrPrepare("new") { "new lyrics" })
        release.complete(Unit)
        old.await()
        assertEquals("new lyrics", cache.peek("new"))
        assertEquals("old lyrics", cache.peek("old"))
    }

    @Test fun errorsAndEmptyResponsesDoNotPoisonCache() = cacheTest { owner ->
        val cache = cache(owner)
        val error = runCatching { cache.getOrPrepare("song") { error("network") } }.exceptionOrNull()
        assertEquals("network", error?.message)
        assertNull(cache.peek("song"))
        assertEquals("", cache.getOrPrepare("song") { "" })
        assertNull(cache.peek("song"))
        assertEquals("recovered", cache.getOrPrepare("song") { "recovered" })
    }

    @Test fun invalidationRejectsLateUncooperativeResult() = cacheTest { owner ->
        val cache = cache(owner)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async {
            runCatching {
                cache.getOrPrepare("song") {
                    withContext(NonCancellable) { entered.complete(Unit); release.await(); "stale" }
                }
            }
        }
        entered.await()
        cache.invalidate("song")
        assertEquals("fresh", cache.getOrPrepare("song") { "fresh" })
        release.complete(Unit)
        old.await()
        assertEquals("fresh", cache.peek("song"))
    }

    @Test fun leastRecentlyUsedEntriesAndWeightAreBounded() = cacheTest { owner ->
        val cache = cache(owner, entries = 2, weight = 6)
        cache.getOrPrepare("a") { "aaa" }
        cache.getOrPrepare("b") { "bbb" }
        cache.peek("a")
        cache.getOrPrepare("c") { "ccc" }
        assertNull(cache.peek("b"))
        assertEquals("aaa", cache.peek("a"))
        assertEquals("toolarge", cache.getOrPrepare("large") { "toolarge" })
        assertNull(cache.peek("large"))
        cache.getOrPrepare("d") { "dddd" }
        assertNull(cache.peek("a"))
        assertNull(cache.peek("c"))
        assertEquals("dddd", cache.peek("d"))
    }

    @Test fun changedFontValidityForcesNewPreparation() = cacheTest { owner ->
        data class Layout(var valid: Boolean)
        val cache = RetainedLyricsCache<String, Layout>(owner, 2, 10, { 1 }, { it.valid })
        val first = cache.getOrPrepare("song") { Layout(true) }
        first.valid = false
        assertNull(cache.peek("song"))
        assertNotSame(first, cache.getOrPrepare("song") { Layout(true) })
    }

    @Test fun cacheIdentityIncludesVersionSourcesEmbeddedTextAndLanguage() {
        val key = LoadedLyricsKey("id", "title", "artist", 300_000, null,
            setOf(LyricsSource.APPLE_TTML), "ru")
        assertNotEquals(key, key.copy(trackId = "other"))
        assertNotEquals(key, key.copy(durationMs = 90_000))
        assertNotEquals(key, key.copy(sources = setOf(LyricsSource.BINI_LYRICS)))
        assertNotEquals(key, key.copy(embedded = "new lyrics"))
        assertNotEquals(key, key.copy(locale = "en"))
        assertEquals(key, key.copy())
    }
}
