package com.lmg.vk.engine.automix.observation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Lifecycle tests use the production state machine, not a substitute scheduler. */
object ObservationSourceContextScenarios {
    private fun context() = PlannerSourceTransportScenarios.context()
    private fun pair() = ObservationPair(
        ObservationTrack("a", 0, ObservationSource("same", "memory:a"), 120000),
        ObservationTrack("b", 1, ObservationSource("same", "memory:b"), 120000), 0, false)
    private fun ObservationPipeline<String, ObservationPair>.replies(tickets: List<ObservationTicket>) {
        check(submitOwned(tickets[0], "a", byteArrayOf(1)) == ObservationSubmission.ACCEPTED)
        check(submitOwned(tickets[1], "b", byteArrayOf(2)) == ObservationSubmission.ACCEPTED)
    }
    private suspend fun ObservationPipeline<String, ObservationPair>.observed() = withTimeout(5000) {
        val r = state.first { it.phase in setOf(ObservationPhase.OBSERVED, ObservationPhase.REJECTED, ObservationPhase.CLOSED) }
        check(r.phase == ObservationPhase.OBSERVED); requireNotNull(r.result)
    }
    suspend fun sourceBeforeReplies() = coroutineScope {
        val p = ObservationPipeline(this, { b: ByteArray, _: String -> b.decodeToString() }, { _: String, _: String, pair: ObservationPair -> pair })
        try { p.update(pair()); val t = p.state.value.requests
            check(p.bindResolvedScope(t[0], context()) == ObservationScopeSubmission.ACCEPTED)
            p.replies(t); val r = p.observed()
            check(r.selectionScope == context() && r.selectionRevision == 1L && r.selectionGeneration == t[0].generation)
        } finally { p.close() }
    }
    suspend fun sourceAfterRepliesReusesAnalysis() = coroutineScope {
        val decodes = AtomicInteger()
        val p = ObservationPipeline(this, { b: ByteArray, _: String -> decodes.incrementAndGet(); b.decodeToString() }, { _: String, _: String, pair: ObservationPair -> pair })
        try { p.update(pair()); val t = p.state.value.requests; p.replies(t); check(p.observed().selectionScope == null)
            check(p.bindResolvedScope(t[1], context()) == ObservationScopeSubmission.ACCEPTED)
            check(p.observed().selectionScope == context() && decodes.get() == 2 && p.state.value.requests.isEmpty())
        } finally { p.close() }
    }
    suspend fun explicitAndSourceScopesConflict() = coroutineScope {
        for (sourceFirst in listOf(true, false)) {
            val p = ObservationPipeline(this, { b: ByteArray, _: String -> b.decodeToString() }, { _: String, _: String, pair: ObservationPair -> pair })
            try { p.update(pair()); val t = p.state.value.requests
                val first: PlannerObservationScope = if (sourceFirst) context() else ObservationBindingScenarios.scope()
                val second: PlannerObservationScope = if (sourceFirst) ObservationBindingScenarios.scope() else context()
                check(p.bindResolvedScope(t[0], first) == ObservationScopeSubmission.ACCEPTED)
                check(p.bindResolvedScope(t[1], second) == ObservationScopeSubmission.CONFLICT)
                p.replies(t); check(p.observed().selectionScope == first)
            } finally { p.close() }
        }
    }
    suspend fun duplicateAndConflictingSourceContexts() = coroutineScope {
        val p = ObservationPipeline(this, { b: ByteArray, _: String -> b.decodeToString() }, { _: String, _: String, pair: ObservationPair -> pair })
        try { p.update(pair()); val t = p.state.value.requests
            check(p.bindResolvedScope(t[0], context()) == ObservationScopeSubmission.ACCEPTED)
            check(p.bindResolvedScope(t[1], context()) == ObservationScopeSubmission.DUPLICATE)
            check(p.bindResolvedScope(t[1], context().copy(outgoingSpatial = MusicKitSourceKnowledge.UNKNOWN)) == ObservationScopeSubmission.CONFLICT)
            p.replies(t); check(p.observed().selectionRevision == 1L)
        } finally { p.close() }
    }
    suspend fun seekRevokesSourceFacts() = coroutineScope {
        val p = ObservationPipeline(this, { b: ByteArray, _: String -> b.decodeToString() }, { _: String, _: String, pair: ObservationPair -> pair })
        try { p.update(pair()); val old = p.state.value.requests; p.bindResolvedScope(old[0], context())
            p.update(pair(), force = true); check(p.bindResolvedScope(old[0], context()) == ObservationScopeSubmission.STALE)
            p.replies(p.state.value.requests); val r = p.observed(); check(r.selectionScope == null && r.selectionRevision == 0L)
        } finally { p.close() }
    }
    suspend fun lateFailureCannotRevokeSourceBinding() = coroutineScope {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val p = ObservationPipeline(this, { b: ByteArray, _: String -> b.decodeToString() }, { _: String, _: String, pair: ObservationPair ->
            if (pair.selectionRevision == 0L) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); throw IllegalStateException("discarded old result") }
            pair
        })
        try { p.update(pair()); val t = p.state.value.requests; p.replies(t)
            withContext(Dispatchers.IO) { check(entered.await(5, TimeUnit.SECONDS)) }
            check(p.bindResolvedScope(t[1], context()) == ObservationScopeSubmission.ACCEPTED)
            release.countDown(); val r = p.observed(); check(r.selectionRevision == 1L && r.selectionScope == context())
        } finally { release.countDown(); p.close() }
    }
    suspend fun suspendAndCloseRevokeSourceContext() = coroutineScope {
        val p = ObservationPipeline(this, { b: ByteArray, _: String -> b.decodeToString() }, { _: String, _: String, pair: ObservationPair -> pair })
        try { p.update(pair()); val old = p.state.value.requests[0]; p.bindResolvedScope(old, context())
            p.update(null, ObservationReason.PAUSED); check(p.bindResolvedScope(old, context()) == ObservationScopeSubmission.STALE)
            p.update(pair()); val fresh = p.state.value.requests[0]; p.bindResolvedScope(fresh, context()); p.close()
            check(p.bindResolvedScope(fresh, context()) == ObservationScopeSubmission.CLOSED)
        } finally { p.close() }
    }
    fun sourceContextCannotAcquireImplicitFacts() {
        val unknown = MusicKitSourceContext.create(MusicKitOutgoingCriteria.LateInSong, MusicKitIncomingCriteria.InSong, 3)
        check(unknown.upperEligibility == ResolvedPlannerEligibility.UNRESOLVED && unknown.outgoingSpatial == MusicKitSourceKnowledge.UNKNOWN)
        var count = 0
        for (operation in listOf<() -> Unit>(
            { context().copy(workBudget = -1) }, { context().copy(maximumComplexity = 4) },
            { context().copy(outgoingCriteria = MusicKitOutgoingCriteria.LateAfter(Double.NaN)) },
            { context().copy(incomingCriteria = MusicKitIncomingCriteria.Within(4.0, 2.0)) },
        )) try { operation() } catch (_: IllegalArgumentException) { count++ }
        check(count == 4)
        val q = PlannerSourceContextWire.request(1, 1, context(), 120000, 120000); q[11] = 2
        check(context().outgoingSpatial == MusicKitSourceKnowledge.ABSENT)
    }
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        sourceBeforeReplies(); sourceAfterRepliesReusesAnalysis(); explicitAndSourceScopesConflict()
        duplicateAndConflictingSourceContexts(); seekRevokesSourceFacts(); lateFailureCannotRevokeSourceBinding()
        suspendAndCloseRevokeSourceContext(); sourceContextCannotAcquireImplicitFacts()
        println("Source-context lifecycle: 8/8 scenarios PASSED")
    }
}
