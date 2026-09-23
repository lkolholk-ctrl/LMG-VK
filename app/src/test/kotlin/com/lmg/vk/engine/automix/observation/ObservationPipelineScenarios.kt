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

/** Same executable scenarios are used by JUnit and the dependency-light host runner. */
object ObservationPipelineScenarios {
    private fun pair(a: String = "a", b: String = "b") = ObservationPair(
        ObservationTrack("window-$a", 0, ObservationSource(a, "https://private.invalid/$a?secret=redacted"), 180000),
        ObservationTrack("window-$b", 1, ObservationSource(b, "https://private.invalid/$b?secret=redacted"), 200000),
        repeatMode = 0, shuffle = false,
    )
    private fun pipeline(scope: CoroutineScope,
                         decode: (ByteArray, String) -> String = { bytes, _ -> bytes.decodeToString() },
                         calculate: (String, String, ObservationPair) -> String = { a, b, _ -> a + b },
                         timeout: Long = 5000L) = ObservationPipeline(scope, decode, calculate, timeoutMs = timeout)
    private suspend fun ObservationPipeline<String, String>.observed(): ObservationState<String> =
        withTimeout(5000) { state.first { it.phase == ObservationPhase.OBSERVED } }
    private suspend fun entered(latch: CountDownLatch) = withContext(Dispatchers.IO) {
        check(latch.await(5, TimeUnit.SECONDS)) { "Worker did not enter" }
    }
    private fun ObservationPipeline<String, String>.both(a: String = "a", b: String = "b") {
        val requests = state.value.requests
        check(requests.size == 2)
        check(submitOwned(requests[0], "1", a.encodeToByteArray()) == ObservationSubmission.ACCEPTED)
        check(submitOwned(requests[1], "2", b.encodeToByteArray()) == ObservationSubmission.ACCEPTED)
    }

    suspend fun completePair() = coroutineScope {
        val p = pipeline(this)
        try { p.update(pair()); p.both(); check(p.observed().result == "ab") } finally { p.close() }
    }
    suspend fun incomingMayCompleteFirst() = coroutineScope {
        val p = pipeline(this)
        try {
            p.update(pair()); val t = p.state.value.requests
            check(p.submitOwned(t[1], "2", "b".encodeToByteArray()) == ObservationSubmission.ACCEPTED)
            check(p.submitOwned(t[0], "1", "a".encodeToByteArray()) == ObservationSubmission.ACCEPTED)
            check(p.observed().result == "ab")
        } finally { p.close() }
    }
    suspend fun duplicatesAreSingleUse() = coroutineScope {
        val count = AtomicInteger()
        val p = pipeline(this, decode = { bytes, _ -> count.incrementAndGet(); bytes.decodeToString() })
        try {
            p.update(pair()); val t = p.state.value.requests
            check(p.submitOwned(t[0], "1", byteArrayOf(97)) == ObservationSubmission.ACCEPTED)
            check(p.submitOwned(t[0], "1", byteArrayOf(120)) == ObservationSubmission.DUPLICATE)
            check(p.submitOwned(t[1], "2", byteArrayOf(98)) == ObservationSubmission.ACCEPTED)
            check(p.observed().result == "ab" && count.get() == 2)
        } finally { p.close() }
    }
    suspend fun forgedTicketIsNotAccepted() = coroutineScope {
        val p = pipeline(this)
        try {
            p.update(pair()); val original = p.state.value.requests[0]
            val copy = ObservationTicket(original.generation, original.side, original.source, original.windowIndex)
            check(p.submitOwned(copy, "1", byteArrayOf(97)) == ObservationSubmission.STALE)
            p.both(); check(p.observed().result == "ab")
        } finally { p.close() }
    }
    suspend fun samePairIsDeduplicated() = coroutineScope {
        val p = pipeline(this)
        try {
            val pair = pair(); p.update(pair); val before = p.state.value
            p.update(pair.copy())
            check(p.state.value === before)
        } finally { p.close() }
    }
    suspend fun repeatAndSeekGetNewGenerations() = coroutineScope {
        val p = pipeline(this)
        try {
            val pair = pair("same", "same"); p.update(pair); val old = p.state.value.requests
            p.update(pair, force = true)
            check(p.state.value.generation > old[0].generation)
            check(p.submitOwned(old[0], "1", byteArrayOf(97)) == ObservationSubmission.STALE)
            p.both(); check(p.observed().result == "ab")
        } finally { p.close() }
    }
    suspend fun duplicateMediaIdsKeepSeparateOccurrences() = coroutineScope {
        val p = pipeline(this)
        try {
            val first = pair("same", "same")
            p.update(first.copy(incoming = first.incoming.copy(windowUid = "other-occurrence")))
            val t = p.state.value.requests
            check(t[0] !== t[1] && t[0].source == t[1].source && t[0].windowIndex != t[1].windowIndex)
            p.both("out", "in"); check(p.observed().result == "outin")
        } finally { p.close() }
    }
    suspend fun lateDecodeIsDiscarded() = coroutineScope {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val p = pipeline(this, decode = { bytes, _ ->
            val value = bytes.decodeToString()
            if (value == "old") { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            value
        })
        try {
            p.update(pair()); val old = p.state.value.requests
            p.submitOwned(old[0], "1", "old".encodeToByteArray()); entered(started)
            p.update(pair("c", "d")); p.both("c", "d")
            check(p.submitOwned(old[1], "2", byteArrayOf(98)) == ObservationSubmission.STALE)
            release.countDown()
            check(p.observed().result == "cd")
        } finally { release.countDown(); p.close() }
    }
    suspend fun lateNativeResultIsDiscardedAndWorkIsSerialized() = coroutineScope {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val active = AtomicInteger(); val maximum = AtomicInteger()
        val p = pipeline(this, calculate = { a, b, _ ->
            val n = active.incrementAndGet(); maximum.updateAndGet { maxOf(it, n) }
            try {
                if (a == "old") { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                a + b
            } finally { active.decrementAndGet() }
        })
        try {
            p.update(pair()); p.both("old", "b"); entered(started)
            p.update(pair("c", "d")); p.both("c", "d"); release.countDown()
            check(p.observed().result == "cd" && maximum.get() == 1)
        } finally { release.countDown(); p.close() }
    }
    suspend fun pauseRevokesThenResumeReissues() = coroutineScope {
        val p = pipeline(this)
        try {
            val pair = pair(); p.update(pair); val old = p.state.value.requests
            p.update(null, ObservationReason.PAUSED)
            check(p.state.value.phase == ObservationPhase.SUSPENDED && p.state.value.requests.isEmpty())
            check(p.submitOwned(old[0], "1", byteArrayOf(97)) == ObservationSubmission.STALE)
            p.update(pair); p.both(); check(p.observed().result == "ab")
        } finally { p.close() }
    }
    suspend fun closeRevokesEverything() = coroutineScope {
        val p = pipeline(this); p.update(pair()); val old = p.state.value.requests
        p.close(); p.close()
        check(p.state.value.phase == ObservationPhase.CLOSED && p.state.value.requests.isEmpty())
        check(p.submitOwned(old[0], "1", byteArrayOf(97)) == ObservationSubmission.CLOSED)
        p.update(pair("c", "d")); check(p.state.value.phase == ObservationPhase.CLOSED)
    }
    suspend fun closeDuringNativeWorkStaysClosed() = coroutineScope {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val p = pipeline(this, calculate = { a, b, _ ->
            started.countDown(); check(release.await(5, TimeUnit.SECONDS)); a + b
        })
        try {
            p.update(pair()); p.both(); entered(started); p.close(); release.countDown()
            check(p.state.value.phase == ObservationPhase.CLOSED && p.state.value.result == null)
        } finally { release.countDown(); p.close() }
    }
    suspend fun oversizedResponseNeverReachesDecoder() = coroutineScope {
        val calls = AtomicInteger()
        val p = pipeline(this, decode = { _, _ -> calls.incrementAndGet(); "bad" })
        try {
            p.update(pair()); val ticket = p.state.value.requests[0]
            check(p.submitOwned(ticket, "1", ByteArray(ObservationPipeline.MAX_RESPONSE_BYTES + 1)) ==
                ObservationSubmission.INPUT_TOO_LARGE)
            check(calls.get() == 0 && p.state.value.requests.size == 2)
        } finally { p.close() }
    }
    suspend fun invalidSongIdIsNotAccepted() = coroutineScope {
        val p = pipeline(this)
        try {
            p.update(pair()); val t = p.state.value.requests[0]
            check(p.submitOwned(t, " ", byteArrayOf(97)) == ObservationSubmission.INVALID_SONG_ID)
            check(p.state.value.requests.size == 2)
        } finally { p.close() }
    }
    suspend fun parserFailureRevokesBothTickets() = coroutineScope {
        val p = pipeline(this, decode = { _, _ -> throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "INVALID_INPUT") })
        try {
            p.update(pair()); val old = p.state.value.requests
            p.submitOwned(old[0], "1", byteArrayOf(97))
            val failed = withTimeout(5000) { p.state.first { it.phase == ObservationPhase.REJECTED } }
            check(failed.reason == ObservationReason.ANALYSIS_REJECTED && failed.detail == "INVALID_INPUT")
            check(p.submitOwned(old[1], "2", byteArrayOf(98)) == ObservationSubmission.STALE)
        } finally { p.close() }
    }
    suspend fun nativeFailureDoesNotEscapeToOwner() = coroutineScope {
        val p = pipeline(this, calculate = { _, _, _ -> throw UnsatisfiedLinkError("secret path must not be logged") })
        try {
            p.update(pair()); p.both()
            val failed = withTimeout(5000) { p.state.first { it.phase == ObservationPhase.REJECTED } }
            check(failed.reason == ObservationReason.NATIVE_UNAVAILABLE && failed.detail == null && failed.result == null)
        } finally { p.close() }
    }
    suspend fun deadlinesDiscardBlockingWork() = coroutineScope {
        val p = pipeline(this, decode = { _, _ -> Thread.sleep(120); "too late" }, timeout = 20)
        try {
            p.update(pair()); p.submitOwned(p.state.value.requests[0], "1", byteArrayOf(97))
            val failed = withTimeout(5000) { p.state.first { it.phase == ObservationPhase.REJECTED } }
            check(failed.reason == ObservationReason.TIMEOUT && failed.result == null)
        } finally { p.close() }
    }
    suspend fun sourceRevisionChangeInvalidatesIdentity() = coroutineScope {
        val p = pipeline(this)
        try {
            val pair = pair(); p.update(pair); val before = p.state.value.requests[0]
            p.update(pair.copy(outgoing = pair.outgoing.copy(source = pair.outgoing.source.copy(recordingRevision = "new"))))
            check(!p.accepts(before) && p.state.value.generation > before.generation)
        } finally { p.close() }
    }
    suspend fun durationUpdateTriggersFreshCalculation() = coroutineScope {
        val p = pipeline(this)
        try {
            val pair = pair().let { it.copy(incoming = it.incoming.copy(durationMs = null)) }
            p.update(pair); val before = p.state.value.requests[0]
            p.update(pair.copy(incoming = pair.incoming.copy(durationMs = 200000)))
            check(!p.accepts(before))
        } finally { p.close() }
    }
    suspend fun diagnosticObjectsRedactSources() = coroutineScope {
        val p = pipeline(this)
        try {
            p.update(pair()); val state = p.state.value
            check(!state.toString().contains("private.invalid") && !state.toString().contains("secret="))
            check(!state.requests[0].source.toString().contains("private.invalid"))
            check(ObservationFailure(ObservationReason.INTERNAL_FAILURE, "https://private.invalid").detailCode == null)
        } finally { p.close() }
    }

    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val cases = listOf<Pair<String, suspend () -> Unit>>(
            "completePair" to ::completePair,
            "incomingMayCompleteFirst" to ::incomingMayCompleteFirst,
            "duplicatesAreSingleUse" to ::duplicatesAreSingleUse,
            "forgedTicketIsNotAccepted" to ::forgedTicketIsNotAccepted,
            "samePairIsDeduplicated" to ::samePairIsDeduplicated,
            "repeatAndSeekGetNewGenerations" to ::repeatAndSeekGetNewGenerations,
            "duplicateMediaIdsKeepSeparateOccurrences" to ::duplicateMediaIdsKeepSeparateOccurrences,
            "lateDecodeIsDiscarded" to ::lateDecodeIsDiscarded,
            "lateNativeResultIsDiscardedAndWorkIsSerialized" to ::lateNativeResultIsDiscardedAndWorkIsSerialized,
            "pauseRevokesThenResumeReissues" to ::pauseRevokesThenResumeReissues,
            "closeRevokesEverything" to ::closeRevokesEverything,
            "closeDuringNativeWorkStaysClosed" to ::closeDuringNativeWorkStaysClosed,
            "oversizedResponseNeverReachesDecoder" to ::oversizedResponseNeverReachesDecoder,
            "invalidSongIdIsNotAccepted" to ::invalidSongIdIsNotAccepted,
            "parserFailureRevokesBothTickets" to ::parserFailureRevokesBothTickets,
            "nativeFailureDoesNotEscapeToOwner" to ::nativeFailureDoesNotEscapeToOwner,
            "deadlinesDiscardBlockingWork" to ::deadlinesDiscardBlockingWork,
            "sourceRevisionChangeInvalidatesIdentity" to ::sourceRevisionChangeInvalidatesIdentity,
            "durationUpdateTriggersFreshCalculation" to ::durationUpdateTriggersFreshCalculation,
            "diagnosticObjectsRedactSources" to ::diagnosticObjectsRedactSources,
        )
        for ((name, test) in cases) { test(); println("PASS $name") }
        println("ObservationPipeline: ${cases.size}/${cases.size} scenarios passed")
    }
}
