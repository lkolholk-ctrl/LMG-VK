package com.lmg.vk.engine.automix.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Service-lifetime bounded memory cache. No disk persistence and no background polling.
 * A positive entry is stored only AFTER the existing native decoder validates it.
 * Search entries remain candidates: caching must never upgrade their recording trust. */
internal class AnalysisPrefetchStore(
    parent: CoroutineScope,
    private val makeCall: (AnalysisLookup) -> AnalysisHttpCall,
    private val validate: suspend (RawAnalysis) -> AnalysisFailure?,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val positiveTtlMs: Long = 600_000,
    private val negativeTtlMs: Long = 30_000,
    private val maximumBytes: Long = 16L * 1024 * 1024,
    private val maximumEntries: Int = 32,
    private val timeoutMs: Long = 30_000,
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val lock = Mutex()
    private val slots = Semaphore(2)
    private class Entry(val value: AnalysisResult, val stored: Long, val cost: Long)
    private class Flight {
        lateinit var task: kotlinx.coroutines.Deferred<AnalysisResult>
        var readers = 0
    }
    private val cache = LinkedHashMap<AnalysisLookup, Entry>(16, .75f, true)
    private val flights = HashMap<AnalysisLookup, Flight>()
    private var byteCount = 0L
    private var closed = false
    init { require(positiveTtlMs > 0 && negativeTtlMs > 0 && maximumBytes > 0 && maximumEntries > 0 && timeoutMs > 0) }

    suspend fun get(lookup: AnalysisLookup): AnalysisResult {
        var immediate: AnalysisResult? = null
        val flight = lock.withLock {
            check(!closed) { "Analysis store closed" }
            cache[lookup]?.let { e ->
                val age = clockMs() - e.stored
                val ttl = if (e.value is AnalysisResult.Ready) positiveTtlMs else negativeTtlMs
                if (age in 0 until ttl) immediate = e.value
                else { byteCount -= e.cost; cache.remove(lookup) }
            }
            if (immediate != null) return@withLock null
            val existing = flights[lookup]
            if (existing != null) { existing.readers++; return@withLock existing }
            if (flights.size >= 4) { immediate = AnalysisResult.Failed(AnalysisFailure.BUSY); return@withLock null }
            val created = Flight()
            created.readers = 1
            created.task = scope.async(start = CoroutineStart.LAZY) {
                val result = try {
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        val fetched = slots.withPermit { execute(makeCall(lookup)) }
                        currentCoroutineContext().ensureActive()
                        if (fetched is AnalysisResult.Ready) {
                            validate(fetched.analysis)?.let { AnalysisResult.Failed(it) } ?: fetched
                        } else fetched
                    }
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    AnalysisResult.Failed(AnalysisFailure.TIMEOUT)
                }
                currentCoroutineContext().ensureActive()
                lock.withLock {
                    if (!closed && flights[lookup] === created && created.readers > 0 &&
                        (result is AnalysisResult.Ready ||
                            (result is AnalysisResult.Failed && result.reason == AnalysisFailure.NOT_FOUND))) {
                        val cost = if (result is AnalysisResult.Ready) result.analysis.byteCount.toLong() else 0L
                        if (cost <= maximumBytes) {
                            cache.remove(lookup)?.let { byteCount -= it.cost }
                            while (cache.isNotEmpty() && (cache.size >= maximumEntries || byteCount + cost > maximumBytes)) {
                                val iterator = cache.entries.iterator()
                                byteCount -= iterator.next().value.cost; iterator.remove()
                            }
                            cache[lookup] = Entry(result, clockMs(), cost); byteCount += cost
                        }
                    }
                }
                result
            }
            flights[lookup] = created
            created
        }
        immediate?.let { return it }
        val shared = checkNotNull(flight)
        try {
            shared.task.start()
            return shared.task.await()
        } finally {
            // Cleanup must run even when the occurrence coroutine was cancelled.
            withContext(kotlinx.coroutines.NonCancellable) {
                lock.withLock {
                    shared.readers--
                    if (shared.readers == 0 && flights[lookup] === shared) {
                        flights.remove(lookup)
                        shared.task.cancel()
                    }
                }
            }
        }
    }

    private suspend fun execute(call: AnalysisHttpCall): AnalysisResult = kotlinx.coroutines.coroutineScope {
        suspendCancellableCoroutine { continuation ->
            // The child and its semaphore permit live until the blocking HTTP call really exits.
            val job = launch(io, start = CoroutineStart.LAZY) {
                try { continuation.resume(call.execute()) }
                catch (cancel: CancellationException) { continuation.cancel(cancel) }
                catch (error: Exception) { continuation.resumeWithException(error) }
            }
            continuation.invokeOnCancellation {
                job.cancel()
                // disconnect() is best-effort and can block. Never execute it on Main/audio.
                try { io.dispatch(EmptyCoroutineContext, Runnable { call.cancel() }) } catch (_: Exception) { }
            }
            job.start()
        }
    }

    suspend fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            cache.clear(); byteCount = 0
            flights.values.forEach { it.task.cancel() }; flights.clear()
        }
        scope.cancel()
    }
}
