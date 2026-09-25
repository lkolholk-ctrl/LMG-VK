package com.lmg.vk.ui.lyrics

import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsPreparationCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsPreparationCacheTest {
    @Test fun simultaneousOpeningsShareCompletedPreparation() = runBlocking {
        val cache = LyricsPreparationCache<String, String>(2, 100, String::length)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = async {
            cache.getOrPrepare("song") {
                calls++
                entered.complete(Unit)
                release.await()
                "measured"
            }
        }
        entered.await()
        assertNull(cache.peek("song"))
        val second = async { cache.getOrPrepare("song") { calls++; "wrong" } }
        release.complete(Unit)
        assertEquals("measured", first.await())
        assertEquals("measured", second.await())
        assertEquals(1, calls)
        assertEquals("measured", cache.peek("song"))
    }

    @Test fun cancelledPreparationIsNotPublishedAndCanBeRetried() = runBlocking {
        val cache = LyricsPreparationCache<String, String>(2, 100, String::length)
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            cache.getOrPrepare("song") {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
                "incomplete"
            }
        }
        entered.await()
        job.cancelAndJoin()
        assertNull(cache.peek("song"))
        assertEquals("complete", cache.getOrPrepare("song") { "complete" })
    }

    @Test fun failedPreparationIsNotCached() = runBlocking {
        val cache = LyricsPreparationCache<String, String>(2, 100, String::length)
        try {
            cache.getOrPrepare("song") { error("bad layout") }
            fail("Failure swallowed")
        } catch (expected: IllegalStateException) {
            assertEquals("bad layout", expected.message)
        }
        assertNull(cache.peek("song"))
        assertEquals("retry", cache.getOrPrepare("song") { "retry" })
    }

    @Test fun leastRecentlyUsedEntryIsEvicted() = runBlocking {
        val cache = LyricsPreparationCache<String, String>(2, 100, String::length)
        cache.getOrPrepare("one") { "one" }
        cache.getOrPrepare("two") { "two" }
        cache.peek("one")
        cache.getOrPrepare("three") { "three" }
        assertNull(cache.peek("two"))
        assertEquals("one", cache.peek("one"))
        assertEquals("three", cache.peek("three"))
    }

    @Test fun weightLimitEvictsAndOversizedResultsAreNotRetained() = runBlocking {
        val cache = LyricsPreparationCache<String, String>(3, 5, String::length)
        cache.getOrPrepare("one") { "123" }
        cache.getOrPrepare("two") { "456" }
        assertNull(cache.peek("one"))
        assertEquals("123456", cache.getOrPrepare("large") { "123456" })
        assertNull(cache.peek("large"))
        assertEquals("456", cache.peek("two"))
    }

    @Test fun invalidatedFontResultIsPreparedAgain() = runBlocking {
        data class Result(var valid: Boolean)
        val cache = LyricsPreparationCache<String, Result>(2, 10, { 1 }, { it.valid })
        val old = cache.getOrPrepare("song") { Result(true) }
        old.valid = false
        assertNull(cache.peek("song"))
        val replacement = cache.getOrPrepare("song") { Result(true) }
        assertNotSame(old, replacement)
        assertSame(replacement, cache.peek("song"))
    }
}
