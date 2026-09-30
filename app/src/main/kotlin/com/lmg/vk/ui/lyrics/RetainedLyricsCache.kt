package com.lmg.vk.ui.lyrics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/** Data/preparation belongs to the player session, not to a sheet's composition. */
internal class RetainedLyricsCache<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val maxEntries: Int,
    private val maxWeight: Int,
    private val weightOf: (V) -> Int,
    private val isValid: (V) -> Boolean = { true },
    private val expiresAfterMs: (V) -> Long = { Long.MAX_VALUE },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private class Entry<V>(val value: V, val storedAt: Long)
    private class Work<V>(val token: Any, val result: Deferred<V>)
    private val lock = Any()
    private val entries = LinkedHashMap<K, Entry<V>>()
    private val pending = LinkedHashMap<K, Work<V>>()
    private val permits = Semaphore(2)

    fun peek(key: K): V? = synchronized(lock) {
        val entry = entries.remove(key) ?: return@synchronized null
        if (!isValid(entry.value) || nowMs() - entry.storedAt >= expiresAfterMs(entry.value)) return@synchronized null
        entries[key] = entry
        entry.value
    }

    suspend fun getOrPrepare(key: K, prepare: suspend () -> V): V {
        val work = synchronized(lock) {
            peek(key)?.let { return it }
            pending[key] ?: run {
                val token = Any()
                val result = scope.async(start = CoroutineStart.LAZY) {
                    val value = try {
                        withTimeout(60_000L) { permits.withPermit { prepare() } }
                    } catch (timeout: TimeoutCancellationException) {
                        throw IllegalStateException("Lyrics preparation timed out", timeout)
                    }
                    currentCoroutineContext().ensureActive()
                    synchronized(lock) {
                        if (pending[key]?.token === token && isValid(value) && weightOf(value) <= maxWeight) {
                            entries[key] = Entry(value, nowMs())
                            while (entries.size > maxEntries || entries.values.sumOf { weightOf(it.value) } > maxWeight) {
                                entries.remove(entries.keys.first())
                            }
                        }
                    }
                    value
                }
                Work(token, result).also { work ->
                    pending[key] = work
                    result.invokeOnCompletion {
                        synchronized(lock) {
                            if (pending[key]?.token === token) pending.remove(key)
                        }
                    }
                    // Rapid skips must not build an unbounded queue of network/layout jobs.
                    while (pending.size > maxEntries) {
                        pending.remove(pending.keys.first())?.result?.cancel()
                    }
                }
            }
        }
        return work.result.await()
    }

    fun invalidate(key: K) = synchronized(lock) {
        entries.remove(key)
        pending.remove(key)?.result?.cancel()
        Unit
    }

    fun cancelPending(key: K) = synchronized(lock) {
        pending.remove(key)?.result?.cancel()
        Unit
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        val work = pending.values.toList()
        pending.clear()
        work.forEach { it.result.cancel() }
    }
}
