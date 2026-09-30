package com.lmg.vk.engine.automix.analysis

import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Immutable control-thread snapshot. exactSongId may only be provided after the
 * caller has verified this actual recording, not inferred from a numeric mediaId. */
internal class PrefetchRequest<T : Any>(
    val ticket: T,
    val lookup: AnalysisLookup,
    val exactSongId: String?,
) {
    init {
        require(exactSongId == null || (lookup is AnalysisLookup.ById && exactSongId == lookup.songId))
    }
}
internal enum class PrefetchStatus { FETCHING, CANDIDATE_ONLY, SUBMITTED, STALE, UNAVAILABLE, INVALID_INPUT }
internal class PrefetchNotice<T : Any>(val ticket: T, val status: PrefetchStatus, val failure: AnalysisFailure? = null)

/** Two occurrence capabilities are tracked by OBJECT IDENTITY. A generation change
 * cancels work; the reusable validated cache does not contain tickets or contexts.
 * This class neither reads Player nor grants a source-context/PCM execution lease. */
internal class AnalysisPrefetchCoordinator<T : Any>(
    private val owner: CoroutineScope,
    private val store: AnalysisPrefetchStore,
    private val submit: suspend (T, String, ByteArray) -> Boolean,
    private val notify: (PrefetchNotice<T>) -> Unit = {},
    private val checkOwner: () -> Unit = {},
    private val allowDiscoveredMatch: Boolean = false,
    private val fallbackGenerator: ((AnalysisLookup.ByMetadata) -> RawAnalysis?)? = null,
) {
    private var epoch = Long.MIN_VALUE
    private var enabled = false
    private var closed = false
    private val started = IdentityHashMap<T, Job?>()

    fun update(generation: Long, requests: List<PrefetchRequest<T>>, active: Boolean) {
        checkOwner()
        if (closed) return
        require(requests.size <= 2)
        if (epoch != generation || enabled != active) {
            started.values.forEach { it?.cancel() }
            started.clear(); epoch = generation; enabled = active
        }
        if (!active) return
        for (request in requests) {
            if (started.containsKey(request.ticket)) continue
            started[request.ticket] = null
            val job = owner.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    emit(request.ticket, PrefetchStatus.FETCHING)
                    val result = store.get(request.lookup)
                    if (!isCurrent(generation, request.ticket)) return@launch
                    val ready = if (result is AnalysisResult.Ready) {
                        result.analysis
                    } else if (allowDiscoveredMatch && request.lookup is AnalysisLookup.ByMetadata && fallbackGenerator != null) {
                        fallbackGenerator.invoke(request.lookup)
                    } else null

                    if (ready == null) {
                        val failed = result as? AnalysisResult.Failed
                        emit(request.ticket, PrefetchStatus.UNAVAILABLE, failed?.reason); return@launch
                    }
                    val targetSongId = request.exactSongId ?: if (allowDiscoveredMatch) ready.songId else null
                    if (targetSongId == null) {
                        // Search ±6s is only catalog discovery, not a proof of recording identity.
                        emit(request.ticket, PrefetchStatus.CANDIDATE_ONLY); return@launch
                    }
                    if (request.exactSongId != null && ready.songId != request.exactSongId) {
                        emit(request.ticket, PrefetchStatus.INVALID_INPUT, AnalysisFailure.INVALID_RESPONSE); return@launch
                    }
                    val bytes = withContext(Dispatchers.Default) { ready.copyBytes() }
                    if (!isCurrent(generation, request.ticket)) return@launch
                    val accepted = submit(request.ticket, targetSongId, bytes)
                    if (isCurrent(generation, request.ticket))
                        emit(request.ticket, if (accepted) PrefetchStatus.SUBMITTED else PrefetchStatus.STALE)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    if (isCurrent(generation, request.ticket))
                        emit(request.ticket, PrefetchStatus.UNAVAILABLE, AnalysisFailure.NETWORK_ERROR)
                }
            }
            started[request.ticket] = job
            job.start()
        }
    }
    private fun isCurrent(generation: Long, ticket: T) = !closed && enabled && epoch == generation && started.containsKey(ticket)
    private fun emit(ticket: T, status: PrefetchStatus, failure: AnalysisFailure? = null) {
        try { notify(PrefetchNotice(ticket, status, failure)) } catch (_: Exception) { }
    }
    fun close() {
        checkOwner()
        closed = true
        started.values.forEach { it?.cancel() }; started.clear()
    }
}
