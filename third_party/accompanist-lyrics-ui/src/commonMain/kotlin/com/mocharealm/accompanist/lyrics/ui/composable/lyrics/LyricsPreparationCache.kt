package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LyricsPreparationCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val maxWeight: Int,
    private val weightOf: (V) -> Int,
    private val isValid: (V) -> Boolean = { true },
) {
    private val entries = LinkedHashMap<K, V>()
    private val preparation = Mutex()

    fun peek(key: K): V? = synchronized(entries) {
        val value = entries.remove(key) ?: return@synchronized null
        if (!isValid(value)) return@synchronized null
        entries[key] = value
        value
    }

    suspend fun getOrPrepare(key: K, prepare: suspend () -> V): V = preparation.withLock {
        currentCoroutineContext().ensureActive()
        peek(key)?.let { return@withLock it }
        val result = prepare()
        currentCoroutineContext().ensureActive()
        synchronized(entries) {
            if (weightOf(result) <= maxWeight && isValid(result)) {
                entries[key] = result
                while (entries.size > maxEntries || entries.values.sumOf(weightOf) > maxWeight) {
                    entries.remove(entries.keys.first())
                }
            }
        }
        result
    }
}
