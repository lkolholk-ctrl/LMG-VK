package com.lmg.vk.engine.automix.observation

import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Exact source identity asserted by the response provider, not a title/artist match.
 * URI and cache keys can contain credentials. Never put this object into logs.
 */
data class ObservationSource(
    val mediaId: String,
    val uri: String,
    val customCacheKey: String? = null,
    val recordingRevision: String? = null,
) {
    override fun toString(): String = "ObservationSource(redacted)"
}

/** A playlist occurrence, NOT a renderer/PCM identity or an Apple catalog ID. */
data class ObservationTrack(
    val windowUid: Any,
    val windowIndex: Int,
    val source: ObservationSource,
    val durationMs: Long?,
)

data class ObservationPair(
    val outgoing: ObservationTrack,
    val incoming: ObservationTrack,
    val repeatMode: Int,
    val shuffle: Boolean,
    /** Worker-only captured binding, not part of the owner's playlist identity. */
    internal val selectionScope: PlannerObservationScope? = null,
    internal val selectionGeneration: Long = 0,
    internal val selectionRevision: Long = 0,
)

enum class ObservationSide { OUTGOING, INCOMING }

/** Non-copyable, single-use capability for one occurrence in one service epoch. */
class ObservationTicket internal constructor(
    val generation: Long,
    val side: ObservationSide,
    val source: ObservationSource,
    val windowIndex: Int,
) {
    override fun toString(): String = "ObservationTicket(generation=$generation, side=$side)"
}

enum class ObservationPhase {
    SUSPENDED, WAITING_FOR_ANALYSIS, DECODING, CALCULATING, OBSERVED, REJECTED, CLOSED,
}
enum class ObservationReason {
    NONE, NO_PAIR, PAUSED, STOPPED, WRONG_BACKEND, AD_UNSUPPORTED,
    UNSUPPORTED_SOURCE, UNSUPPORTED_TIMELINE, PLAYER_ERROR, CATALOG_REJECTED,
    ANALYSIS_REJECTED, NATIVE_UNAVAILABLE, NATIVE_FAILURE, BRIDGE_CONTRACT_MISMATCH,
    TIMEOUT, INTERNAL_FAILURE, CLOSED,
}
enum class ObservationSubmission {
    ACCEPTED, STALE, DUPLICATE, INPUT_TOO_LARGE, INVALID_SONG_ID, CLOSED, SERVICE_UNAVAILABLE,
}

data class ObservationState<out R>(
    val generation: Long,
    val phase: ObservationPhase,
    val reason: ObservationReason = ObservationReason.NONE,
    val detail: String? = null,
    /** Only tickets still waiting for a response; never includes an in-flight decode. */
    val requests: List<ObservationTicket> = emptyList(),
    val result: R? = null,
)

/** Only trusted, low-cardinality codes may be exposed to diagnostics. */
internal class ObservationFailure(
    val reason: ObservationReason,
    detail: String? = null,
) : RuntimeException(reason.name) {
    val detailCode: String? = detail?.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) }
}

/**
 * Owner-thread state machine. The owner is the player's application looper in production.
 * Workers receive immutable pair snapshots and privately owned response bytes, never Player.
 * One mutex serializes all decode/native work. Cancellation + generation checks prevent an
 * uninterruptible JNI call from publishing after seek, repeat, queue change, pause or close.
 * A timeout is a discard deadline, not a promise to interrupt arbitrary native code.
 */
internal class ObservationPipeline<A : Any, R : Any>(
    ownerScope: CoroutineScope,
    private val decode: (ByteArray, String) -> A,
    private val calculate: (A, A, ObservationPair) -> R,
    private val checkOwner: () -> Unit = {},
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val timeoutMs: Long = 10_000L,
    private val onStatePublished: (ObservationState<R>, ObservationPair?, Long) -> Unit = { _, _, _ -> },
) {
    private val scope = CoroutineScope(ownerScope.coroutineContext + SupervisorJob(ownerScope.coroutineContext[Job]))
    private val workerMutex = Mutex()
    private var generation = 0L
    private var pair: ObservationPair? = null
    private var suspendedReason = ObservationReason.NO_PAIR
    private var closed = false
    private var tickets = emptyList<ObservationTicket>()
    private val accepted = BooleanArray(2)
    private val values = mutableListOf<A?>(null, null)
    private val decodeJobs = arrayOfNulls<Job>(2)
    private var calculationJob: Job? = null
    private var resolvedScope: PlannerObservationScope? = null
    private var bindingRevision = 0L
    private val mutableState = MutableStateFlow<ObservationState<R>>(
        ObservationState(0L, ObservationPhase.SUSPENDED, ObservationReason.NO_PAIR),
    )
    val state: StateFlow<ObservationState<R>> = mutableState.asStateFlow()

    init { require(timeoutMs > 0) }

    private fun publish(next: ObservationState<R>) {
        // Revoke/publish the metadata gate synchronously BEFORE observable state changes.
        // The callback must never control Player or perform blocking/native work.
        try { onStatePublished(next, pair, bindingRevision) } catch (_: Exception) { }
        mutableState.value = next
    }

    fun update(newPair: ObservationPair?, reason: ObservationReason = ObservationReason.NO_PAIR, force: Boolean = false) {
        checkOwner()
        if (closed) return
        // Owner snapshots contain playlist identity only; a bound worker snapshot
        // cannot bypass the original-ticket binding path when replayed as input.
        require(newPair == null || (newPair.selectionScope == null &&
            newPair.selectionGeneration == 0L && newPair.selectionRevision == 0L))
        if (!force && newPair == pair && (newPair != null || reason == suspendedReason)) return
        generation = Math.addExact(generation, 1L)
        cancelWork()
        pair = newPair
        suspendedReason = reason
        if (newPair == null) {
            publish(ObservationState(generation, ObservationPhase.SUSPENDED, reason))
            return
        }
        tickets = listOf(
            ObservationTicket(generation, ObservationSide.OUTGOING, newPair.outgoing.source, newPair.outgoing.windowIndex),
            ObservationTicket(generation, ObservationSide.INCOMING, newPair.incoming.source, newPair.incoming.windowIndex),
        )
        publishWaiting()
    }

    fun accepts(ticket: ObservationTicket): Boolean {
        checkOwner()
        return !closed && ticket.generation == generation && tickets.any { it === ticket }
    }

    /** One immutable scope per epoch, authenticated by an original occurrence ticket.
     * Accepted response bytes/decoded values stay owned by this pipeline. A binding
     * does not consume a response ticket and never schedules another HTTP request.
     */
    fun bindResolvedScope(ticket: ObservationTicket, scope: PlannerObservationScope): ObservationScopeSubmission {
        checkOwner()
        if (closed) return ObservationScopeSubmission.CLOSED
        if (!accepts(ticket)) return ObservationScopeSubmission.STALE
        resolvedScope?.let {
            return if (it == scope) ObservationScopeSubmission.DUPLICATE else ObservationScopeSubmission.CONFLICT
        }
        resolvedScope = scope
        bindingRevision = Math.addExact(bindingRevision, 1L)
        calculationJob?.cancel()
        calculationJob = null
        val a = values[0]
        val b = values[1]
        val snapshot = pair
        if (a != null && b != null && snapshot != null) startCalculation(generation, a, b, snapshot)
        else publishWaiting()
        return ObservationScopeSubmission.ACCEPTED
    }

    /** Read-only preflight before the public adapter copies a response off Main.
     * Checked again on submission because the epoch may change during that copy.
     */
    fun submissionRejection(ticket: ObservationTicket, songId: String, byteCount: Int): ObservationSubmission? {
        checkOwner()
        if (closed) return ObservationSubmission.CLOSED
        if (!accepts(ticket)) return ObservationSubmission.STALE
        if (accepted[ticket.side.ordinal]) return ObservationSubmission.DUPLICATE
        if (byteCount > MAX_RESPONSE_BYTES) return ObservationSubmission.INPUT_TOO_LARGE
        if (songId.isBlank() || songId.length > 4096) return ObservationSubmission.INVALID_SONG_ID
        return null
    }

    /** Internal ownership transfer: caller must not read/write input after an ACCEPTED result.
     * The public Android adapter makes this private copy OFF the application looper.
     */
    fun submitOwned(ticket: ObservationTicket, requestedSongId: String, input: ByteArray): ObservationSubmission {
        submissionRejection(ticket, requestedSongId, input.size)?.let { return it }
        val index = ticket.side.ordinal
        accepted[index] = true
        publishWaiting()
        val epoch = generation
        decodeJobs[index] = scope.launch {
            try {
                val value = work { decode(input, requestedSongId) }
                currentCoroutineContext().ensureActive()
                if (closed || generation != epoch) return@launch
                values[index] = value
                val outgoing = values[0]
                val incoming = values[1]
                val snapshot = pair
                if (outgoing != null && incoming != null && snapshot != null) {
                    startCalculation(epoch, outgoing, incoming, snapshot)
                } else {
                    publishWaiting()
                }
            } catch (_: TimeoutCancellationException) {
                reject(epoch, ObservationReason.TIMEOUT)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: ObservationFailure) {
                reject(epoch, failure.reason, failure.detailCode)
            } catch (_: LinkageError) {
                reject(epoch, ObservationReason.NATIVE_UNAVAILABLE)
            } catch (_: Exception) {
                reject(epoch, ObservationReason.INTERNAL_FAILURE)
            }
        }
        return ObservationSubmission.ACCEPTED
    }

    private fun startCalculation(epoch: Long, outgoing: A, incoming: A, snapshot: ObservationPair) {
        if (calculationJob != null) return
        val revision = bindingRevision
        val boundSnapshot = snapshot.copy(selectionScope = resolvedScope,
            selectionGeneration = epoch, selectionRevision = revision)
        publish(ObservationState(generation, ObservationPhase.CALCULATING))
        calculationJob = scope.launch {
            try {
                val result = work { calculate(outgoing, incoming, boundSnapshot) }
                currentCoroutineContext().ensureActive()
                if (!closed && generation == epoch && bindingRevision == revision) {
                    publish(ObservationState(epoch, ObservationPhase.OBSERVED, result = result))
                }
            } catch (_: TimeoutCancellationException) {
                rejectCalculation(epoch, revision, ObservationReason.TIMEOUT)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: ObservationFailure) {
                rejectCalculation(epoch, revision, failure.reason, failure.detailCode)
            } catch (_: LinkageError) {
                rejectCalculation(epoch, revision, ObservationReason.NATIVE_UNAVAILABLE)
            } catch (_: Exception) {
                rejectCalculation(epoch, revision, ObservationReason.INTERNAL_FAILURE)
            }
        }
    }

    // Cancellation does not guarantee that an uninterruptible JNI call throws
    // CancellationException: a late native failure can surface instead. Guard
    // error publication with the SAME revision test as successful publication.
    private fun rejectCalculation(epoch: Long, revision: Long, reason: ObservationReason, detail: String? = null) {
        if (bindingRevision == revision) reject(epoch, reason, detail)
    }

    private suspend fun <T> work(block: () -> T): T = withTimeout(timeoutMs) {
        withContext(worker) {
            workerMutex.withLock {
                currentCoroutineContext().ensureActive()
                block()
            }
        }
    }

    private fun publishWaiting() {
        val outstanding = tickets.filterIndexed { index, _ -> !accepted[index] }
        val phase = if (accepted.indices.any { accepted[it] && values[it] == null }) {
            ObservationPhase.DECODING
        } else {
            ObservationPhase.WAITING_FOR_ANALYSIS
        }
        publish(ObservationState(generation, phase,
            requests = Collections.unmodifiableList(outstanding)))
    }

    private fun reject(epoch: Long, reason: ObservationReason, detail: String? = null) {
        if (closed || generation != epoch) return
        generation = Math.addExact(generation, 1L) // Revoke both response capabilities.
        cancelWork()
        publish(ObservationState(generation, ObservationPhase.REJECTED, reason, detail))
    }

    private fun cancelWork() {
        decodeJobs.forEach { it?.cancel() }
        decodeJobs.fill(null)
        calculationJob?.cancel()
        calculationJob = null
        resolvedScope = null
        bindingRevision = 0L
        values[0] = null
        values[1] = null
        accepted.fill(false)
        tickets = emptyList()
    }

    fun close() {
        checkOwner()
        if (closed) return
        closed = true
        generation = Math.addExact(generation, 1L)
        cancelWork()
        pair = null
        scope.cancel()
        publish(ObservationState(generation, ObservationPhase.CLOSED, ObservationReason.CLOSED))
    }

    companion object { const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024 }
}
