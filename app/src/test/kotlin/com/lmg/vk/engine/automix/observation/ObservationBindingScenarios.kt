package com.lmg.vk.engine.automix.observation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Also executed verbatim from JUnit. No mock Player or native math substitution. */
object ObservationBindingScenarios {
    internal fun scope(eligibility: ResolvedPlannerEligibility = ResolvedPlannerEligibility.ALLOWED) =
        ResolvedPlannerScope.create(listOf(8, 9, 12).map { it.toLong() },
            listOf(ResolvedPlannerStyle(8, 8), ResolvedPlannerStyle(9, 8), ResolvedPlannerStyle(12, 16)),
            ResolvedPlannerBounds(104.0, 32.0, 60.0, 104.0, 32.0, 60.0, 120.0, 0.0, 32.0), eligibility)
    private fun pair() = ObservationPair(
        ObservationTrack("a-window", 0, ObservationSource("same", "memory:a"), 120000),
        ObservationTrack("b-window", 1, ObservationSource("same", "memory:b"), 120000), 0, false)
    private fun pipeline(s: CoroutineScope, decoded: AtomicInteger = AtomicInteger(),
                         calculate: (String, String, ObservationPair) -> ObservationPair = { _, _, p -> p }) =
        ObservationPipeline(s, { b, _ -> decoded.incrementAndGet(); b.decodeToString() }, calculate)
    private fun ObservationPipeline<String, ObservationPair>.both(t: List<ObservationTicket>) {
        check(submitOwned(t[0], "a", byteArrayOf(1)) == ObservationSubmission.ACCEPTED)
        check(submitOwned(t[1], "b", byteArrayOf(2)) == ObservationSubmission.ACCEPTED)
    }
    private suspend fun ObservationPipeline<String, ObservationPair>.observed() = withTimeout(5000) {
        val end = state.first { it.phase in setOf(ObservationPhase.OBSERVED, ObservationPhase.REJECTED, ObservationPhase.CLOSED) }
        check(end.phase == ObservationPhase.OBSERVED)
        requireNotNull(end.result)
    }
    private suspend fun entered(l: CountDownLatch) = withContext(Dispatchers.IO) { check(l.await(5, TimeUnit.SECONDS)) }

    suspend fun bindingBeforeReplies() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests
            check(p.bindResolvedScope(t[0], scope()) == ObservationScopeSubmission.ACCEPTED)
            p.both(t); val r = p.observed(); check(r.selectionScope != null && r.selectionRevision == 1L && r.selectionGeneration == t[0].generation)
        } finally { p.close() }
    }
    suspend fun bindingAfterObservedReusesResponses() = coroutineScope {
        val dec = AtomicInteger(); val p = pipeline(this, dec)
        try { p.update(pair()); val t = p.state.value.requests; p.both(t)
            check(p.observed().selectionScope == null)
            check(p.bindResolvedScope(t[1], scope()) == ObservationScopeSubmission.ACCEPTED)
            check(p.observed().selectionRevision == 1L && dec.get() == 2 && p.state.value.requests.isEmpty())
        } finally { p.close() }
    }
    private suspend fun inFlight(failure: (() -> Throwable)?) = coroutineScope {
        val begin = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        val p = pipeline(this, calculate = { _, _, pair ->
            calls.incrementAndGet()
            if (pair.selectionRevision == 0L) { begin.countDown(); check(release.await(5, TimeUnit.SECONDS)); failure?.let { throw it() } }
            pair
        })
        try { p.update(pair()); val t = p.state.value.requests; p.both(t); entered(begin)
            check(p.bindResolvedScope(t[0], scope()) == ObservationScopeSubmission.ACCEPTED)
            release.countDown(); val r = p.observed()
            check(r.selectionGeneration == t[0].generation && r.selectionRevision == 1L && calls.get() == 2)
            check(p.state.value.generation == t[0].generation)
        } finally { release.countDown(); p.close() }
    }
    suspend fun bindingDuringNativeCall() = inFlight(null)
    suspend fun lateFailureCannotRejectReboundScope() {
        for (failure in listOf<() -> Throwable>(
            { ObservationFailure(ObservationReason.NATIVE_FAILURE) },
            { IllegalStateException("late call") },
            { UnsatisfiedLinkError("late linkage") },
        )) inFlight(failure)
    }
    suspend fun duplicateBinding() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests[0]
            check(p.bindResolvedScope(t, scope()) == ObservationScopeSubmission.ACCEPTED)
            val state = p.state.value
            check(p.bindResolvedScope(t, scope()) == ObservationScopeSubmission.DUPLICATE && p.state.value === state)
        } finally { p.close() }
    }
    suspend fun conflictingBinding() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests; p.bindResolvedScope(t[0], scope())
            check(p.bindResolvedScope(t[1], scope(ResolvedPlannerEligibility.DENIED)) == ObservationScopeSubmission.CONFLICT)
            p.both(t); check(p.observed().selectionScope?.eligibility == ResolvedPlannerEligibility.ALLOWED)
        } finally { p.close() }
    }
    suspend fun staleBinding() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests[0]; p.update(pair(), force = true)
            check(p.bindResolvedScope(t, scope()) == ObservationScopeSubmission.STALE)
        } finally { p.close() }
    }
    suspend fun forgedTicket() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests[0]
            val fake = ObservationTicket(t.generation, t.side, t.source, t.windowIndex)
            check(p.bindResolvedScope(fake, scope()) == ObservationScopeSubmission.STALE)
        } finally { p.close() }
    }
    suspend fun bindingAfterClose() = coroutineScope {
        val p = pipeline(this); p.update(pair()); val t = p.state.value.requests[0]; p.close()
        check(p.bindResolvedScope(t, scope()) == ObservationScopeSubmission.CLOSED)
    }
    suspend fun newGenerationClearsScope() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests[0]; p.bindResolvedScope(t, scope())
            p.update(pair(), force = true); val fresh = p.state.value.requests; p.both(fresh)
            val r = p.observed(); check(r.selectionScope == null && r.selectionRevision == 0L && r.selectionGeneration > t.generation)
        } finally { p.close() }
    }
    suspend fun samePairRefreshPreservesScope() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests; p.bindResolvedScope(t[0], scope())
            p.update(pair()); check(p.state.value.generation == t[0].generation); p.both(t)
            check(p.observed().selectionScope == scope())
        } finally { p.close() }
    }
    suspend fun onePendingReplyIsPreserved() = coroutineScope {
        val dec = AtomicInteger(); val p = pipeline(this, dec)
        try { p.update(pair()); val t = p.state.value.requests
            check(p.submitOwned(t[0], "a", byteArrayOf(1)) == ObservationSubmission.ACCEPTED)
            p.bindResolvedScope(t[0], scope())
            check(p.state.value.requests.size == 1 && p.state.value.requests[0] === t[1])
            check(p.submitOwned(t[1], "b", byteArrayOf(2)) == ObservationSubmission.ACCEPTED)
            check(p.observed().selectionRevision == 1L && dec.get() == 2)
        } finally { p.close() }
    }
    fun scopeCopiesCallerLists() {
        val s = scope(); val ids = s.requestedIds.toMutableList(); val records = s.records.toMutableList()
        val owned = ResolvedPlannerScope.create(ids, records, s.bounds, s.eligibility)
        ids.clear(); records.clear(); check(owned == s)
        var rejected = false
        try { (owned.requestedIds as MutableList<Long>).clear() } catch (_: UnsupportedOperationException) { rejected = true }
        check(rejected)
    }
    fun requestCopiesOwnedLists() {
        val s = scope(); val first = PlannerSelectionWire.request(1, 1, s, 120000, 120000); val saved = first.copyOf()
        first.fill(0); check(PlannerSelectionWire.request(1, 1, s, 120000, 120000).contentEquals(saved))
    }
    private suspend fun suspendRevokes(reason: ObservationReason) = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); val t = p.state.value.requests[0]; p.bindResolvedScope(t, scope())
            p.update(null, reason); check(p.bindResolvedScope(t, scope()) == ObservationScopeSubmission.STALE)
            p.update(pair()); val fresh = p.state.value.requests; p.both(fresh)
            check(p.observed().selectionScope == null)
        } finally { p.close() }
    }
    suspend fun pauseRevokesScope() = suspendRevokes(ObservationReason.PAUSED)
    suspend fun backendChangeRevokesScope() = suspendRevokes(ObservationReason.WRONG_BACKEND)
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        bindingBeforeReplies(); bindingAfterObservedReusesResponses(); bindingDuringNativeCall()
        lateFailureCannotRejectReboundScope(); duplicateBinding(); conflictingBinding(); staleBinding()
        forgedTicket(); bindingAfterClose(); newGenerationClearsScope(); samePairRefreshPreservesScope()
        onePendingReplyIsPreserved(); scopeCopiesCallerLists(); requestCopiesOwnedLists()
        pauseRevokesScope(); backendChangeRevokesScope()
        println("Observation scope lifecycle: 16/16 scenarios PASSED (3 late-error variants)")
    }
}
