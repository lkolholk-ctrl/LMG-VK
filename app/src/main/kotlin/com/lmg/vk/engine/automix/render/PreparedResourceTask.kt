package com.lmg.vk.engine.automix.render

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Control-plane, single-use resource handoff. The factory MUST NOT bind the
 * created native objects to its worker (no stats/push/poll before adoption).
 * Cancellation owns disposal only before take(); after take the playback owner
 * alone closes the object. No waits and no work is performed by poll/take.
 */
internal class PreparedResourceTask<T : AutoCloseable>(
    private val executor: Executor,
    factory: () -> T,
    current: () -> Boolean,
) {
    private class Ready<T>(val value: T)
    private class Failed(val cause: Throwable)
    private val state = AtomicReference<Any>(PENDING)
    init {
        try {
            executor.execute {
                if (state.get() !== PENDING) return@execute
                try {
                    val resource = factory()
                    if (!current() || !state.compareAndSet(PENDING, Ready(resource))) {
                        resource.close()
                        state.compareAndSet(PENDING, CANCELLED)
                    }
                } catch (failure: Throwable) { state.compareAndSet(PENDING, Failed(failure)) }
            }
        } catch (failure: Throwable) { state.compareAndSet(PENDING, Failed(failure)) }
    }
    val ready: Boolean get() = state.get() is Ready<*>
    val failed: Boolean get() = state.get() is Failed || state.get() === CANCELLED
    @Suppress("UNCHECKED_CAST")
    fun take(): T? {
        val value = state.get()
        if (value !is Ready<*> || !state.compareAndSet(value, TAKEN)) return null
        return value.value as T
    }
    /** Safe from Main or playback. Never disposes an adopted native object. */
    fun cancel() {
        while (true) {
            val value = state.get()
            if (value === CANCELLED || value === TAKEN) return
            if (!state.compareAndSet(value, CANCELLED)) continue
            if (value is Ready<*>) {
                val close = Runnable { (value.value as AutoCloseable).close() }
                try { executor.execute(close) }
                catch (_: java.util.concurrent.RejectedExecutionException) {
                    // Executor shutdown is a cold failure path, never a PCM loop.
                    close.run()
                }
            }
            return
        }
    }
    private companion object {
        val PENDING = Any()
        val CANCELLED = Any()
        val TAKEN = Any()
    }
}
