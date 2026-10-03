package com.lmg.vk.debug

/** Diagnostic counters only. Exactly one writer: the existing playback thread.
 * Readers use a bounded versioned snapshot; they never spin until audio progresses.
 * No allocation, locking, logging, player calls or buffer access in begin/end.
 */
internal class AudioWriteProgress {
    @Volatile private var version = 0L
    @Volatile private var calls = 0L
    @Volatile private var completions = 0L
    @Volatile private var inFlight = false
    @Volatile private var enteredElapsedMs = -1L
    @Volatile private var enteredUptimeMs = -1L
    @Volatile private var returnedElapsedMs = -1L
    @Volatile private var returnedUptimeMs = -1L
    @Volatile private var lastDurationElapsedMs = -1L
    @Volatile private var lastDurationAwakeMs = -1L
    @Volatile private var lastOutcome = 0
    @Volatile private var acceptedOnReturnBytes = 0L
    @Volatile private var lastConsumeReturnedMs = -1L
    @Volatile private var invalidPositionDelta = false

    fun begin(elapsedMs: Long, uptimeMs: Long) {
        version++
        calls++
        enteredElapsedMs = elapsedMs
        enteredUptimeMs = uptimeMs
        inFlight = true
        version++
    }

    /** outcome: 1=true, 2=false, 3=exception. No exception is swallowed here. */
    fun end(elapsedMs: Long, uptimeMs: Long, byteDelta: Int, outcome: Int) {
        version++
        completions++
        returnedElapsedMs = elapsedMs
        returnedUptimeMs = uptimeMs
        lastDurationElapsedMs = elapsedMs - enteredElapsedMs
        lastDurationAwakeMs = uptimeMs - enteredUptimeMs
        lastOutcome = outcome
        if (byteDelta > 0) {
            acceptedOnReturnBytes += byteDelta.toLong()
            lastConsumeReturnedMs = elapsedMs
        } else if (byteDelta < 0) {
            invalidPositionDelta = true
        }
        inFlight = false
        version++
    }

    fun reset() {
        version++
        calls = 0; completions = 0; inFlight = false
        enteredElapsedMs = -1; enteredUptimeMs = -1
        returnedElapsedMs = -1; returnedUptimeMs = -1
        lastDurationElapsedMs = -1; lastDurationAwakeMs = -1
        lastOutcome = 0; acceptedOnReturnBytes = 0
        lastConsumeReturnedMs = -1; invalidPositionDelta = false
        version++
    }

    /** Allocation is confined to the existing control-thread diagnostic dump. */
    fun snapshot(): AudioWriteSnapshot? {
        repeat(3) {
            val before = version
            if ((before and 1L) == 0L) {
                val result = AudioWriteSnapshot(calls, completions, inFlight,
                    enteredElapsedMs, enteredUptimeMs, returnedElapsedMs,
                    returnedUptimeMs, lastDurationElapsedMs, lastDurationAwakeMs,
                    lastOutcome, acceptedOnReturnBytes, lastConsumeReturnedMs,
                    invalidPositionDelta)
                if (before == version) return result
            }
        }
        return null // Explicitly unstable, never substitute all-zero progress.
    }
}

internal data class AudioWriteSnapshot(
    val calls: Long, val completions: Long, val inFlight: Boolean,
    val enteredElapsedMs: Long, val enteredUptimeMs: Long,
    val returnedElapsedMs: Long, val returnedUptimeMs: Long,
    val lastDurationElapsedMs: Long, val lastDurationAwakeMs: Long,
    val lastOutcome: Int, val acceptedOnReturnBytes: Long,
    val lastConsumeReturnedMs: Long, val invalidPositionDelta: Boolean,
)

/** Call only from the existing application-thread snapshot path. A difference
 * of clocks can detect device-suspend time, NOT an OEM process-freeze by itself.
 */
internal class AudioPollProgress {
    private var previousElapsed = -1L
    private var previousUptime = -1L
    fun observe(elapsedBeforeMs: Long, uptimeMs: Long, elapsedAfterMs: Long): AudioPollDelta {
        val span = elapsedAfterMs - elapsedBeforeMs
        val valid = elapsedBeforeMs >= 0 && uptimeMs >= 0 && span in 0..50
        val contiguous = valid && previousElapsed >= 0 &&
            elapsedAfterMs >= previousElapsed && uptimeMs >= previousUptime
        val de = if (contiguous) elapsedAfterMs - previousElapsed else -1L
        val du = if (contiguous) uptimeMs - previousUptime else -1L
        previousElapsed = if (valid) elapsedAfterMs else -1L
        previousUptime = if (valid) uptimeMs else -1L
        return AudioPollDelta(valid, span, de, du,
            if (contiguous) (de - du).coerceAtLeast(0L) else -1L)
    }
}

internal data class AudioPollDelta(
    val sampleValid: Boolean, val sampleSpanMs: Long,
    val elapsedDeltaMs: Long, val awakeDeltaMs: Long, val suspendGapEstimateMs: Long,
)
