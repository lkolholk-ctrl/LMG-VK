package com.lmg.vk.engine.background

/** Main-thread control policy. It neither creates players nor calls Android APIs.
 * Progress means bytes actually accepted by the sink, or its own advancing position;
 * signal amplitude, notification position extrapolation and wall clock are NOT evidence.
 */
internal data class AudioOutputProgress(
    val sinkId: Int,
    val epoch: Long,
    val playing: Boolean,
    val gain: Float,
    val acceptedBytes: Long,
    val lastAcceptedMs: Long,
    val positionUs: Long,
    val inFlight: Boolean,
    val valid: Boolean,
    // -1 supports older callers without a timestamp; then observation time is conservative.
    val lastPositionAdvanceMs: Long = -1L,
)

internal data class PlaybackHealthInput(
    val nowMs: Long,
    val epoch: Long,
    val itemIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val eligible: Boolean,
    val output: AudioOutputProgress?,
)

/** Opaque to the audio path; created only after a sustained observed stall. */
internal class PlaybackRecoveryTicket internal constructor(
    val epoch: Long,
    val itemIndex: Int,
    val sinkId: Int,
    val sinkEpoch: Long,
    val acceptedBytes: Long,
    val positionUs: Long,
    val gain: Float,
    val durationMs: Long,
    val resumePositionMs: Long,
    val createdMs: Long,
)

internal class PlaybackProtectionPolicy {
    companion object {
        const val STALL_MS = 6_000L
        const val SETTLE_MS = 8_000L
        const val MIN_BUFFER_MS = 5_000L
        const val CONFIRM_VALID_MS = 2_000L
        const val RETRY_COOLDOWN_MS = 60_000L
        const val BUDGET_WINDOW_MS = 600_000L
        const val MAX_RECOVERIES = 2
        const val FOREGROUND_RETRY_MS = 5_000L
        const val MAX_FOREGROUND_RETRIES = 3

        fun needsCpu(playWhenReady: Boolean, readyOrBuffering: Boolean,
            suppressed: Boolean, closing: Boolean): Boolean =
            !closing && playWhenReady && readyOrBuffering && !suppressed
    }

    private var closed = false
    private var previous: PlaybackHealthInput? = null
    private var stableSinceMs = -1L
    private var progressAtMs = -1L
    private var lastRealPositionMs = 0L
    private var resumeFractionUs = 0L
    private var pending: PlaybackRecoveryTicket? = null
    private var lastAttemptMs = -1L
    private val recoveries = LongArray(MAX_RECOVERIES) { -1L }
    private var recoverySlot = 0
    private var foregroundAttemptMs = -1L
    private var foregroundAttempts = 0

    fun close() { closed = true; invalidate() }

    /** Discontinuities reset evidence, NOT the rolling recovery budget. */
    fun invalidate() {
        pending = null; previous = null; stableSinceMs = -1L; progressAtMs = -1L
    }

    fun foregroundRepairDue(nowMs: Long, playbackIntended: Boolean, foreground: Boolean): Boolean {
        if (closed || nowMs < 0) return false
        if (!playbackIntended || foreground) {
            foregroundAttempts = 0; foregroundAttemptMs = -1L
            return false
        }
        if (foregroundAttempts >= MAX_FOREGROUND_RETRIES ||
            (foregroundAttemptMs >= 0 && nowMs - foregroundAttemptMs < FOREGROUND_RETRY_MS)) return false
        foregroundAttemptMs = nowMs; foregroundAttempts++
        return true
    }

    private fun usable(s: PlaybackHealthInput): Boolean {
        val p = s.output ?: return false
        return !closed && s.nowMs >= 0 && s.epoch > 0 && s.itemIndex >= 0 && s.eligible &&
            s.positionMs >= 0 && s.durationMs > s.positionMs && s.bufferedMs >= MIN_BUFFER_MS &&
            p.valid && p.playing && p.gain.isFinite() && p.gain > 0.00001f &&
            p.acceptedBytes > 0 && p.lastAcceptedMs >= 0 &&
            p.lastAcceptedMs <= s.nowMs && p.positionUs >= 0
    }

    private fun sameSource(a: PlaybackHealthInput, b: PlaybackHealthInput): Boolean {
        val x = a.output ?: return false
        val y = b.output ?: return false
        return a.epoch == b.epoch && a.itemIndex == b.itemIndex &&
            x.sinkId == y.sinkId && x.epoch == y.epoch && x.gain == y.gain &&
            a.durationMs == b.durationMs
    }

    /** Poll on the existing application looper. No hardware getters are used here. */
    fun observe(s: PlaybackHealthInput): PlaybackRecoveryTicket? {
        if (!usable(s)) { invalidate(); return null }
        val old = previous
        val p = checkNotNull(s.output)
        previous = s
        if (old == null || !sameSource(old, s) || s.nowMs < old.nowMs ||
            p.acceptedBytes < checkNotNull(old.output).acceptedBytes ||
            p.positionUs < old.output.positionUs) {
            pending = null
            stableSinceMs = s.nowMs; progressAtMs = s.nowMs
            lastRealPositionMs = s.positionMs; resumeFractionUs = 0L
            return null
        }
        val oldOutput = checkNotNull(old.output)
        if (p.acceptedBytes > oldOutput.acceptedBytes || p.positionUs > oldOutput.positionUs) {
            // Derive the resume point from the sink delta relative to a known real
            // player position, not from a notification's extrapolated stopwatch.
            if (p.positionUs > oldOutput.positionUs) {
                val deltaUs = p.positionUs - oldOutput.positionUs
                val fraction = deltaUs % 1_000L + resumeFractionUs
                val deltaMs = deltaUs / 1_000L + fraction / 1_000L
                val availableMs = (minOf(s.positionMs, s.durationMs - 1L) - lastRealPositionMs).coerceAtLeast(0L)
                lastRealPositionMs += minOf(deltaMs, availableMs)
                resumeFractionUs = if (deltaMs > availableMs) 0L else fraction % 1_000L
            }
            pending = null
            // A delayed Main poll may discover progress that happened seconds ago.
            // Do not relabel that old audio as newly delivered at observation time.
            progressAtMs = if (p.lastPositionAdvanceMs in 0..s.nowMs)
                maxOf(p.lastAcceptedMs, p.lastPositionAdvanceMs) else s.nowMs
        }
        // A normal short write is not a source/lifecycle change. Preserve evidence,
        // but never request or confirm a destructive recovery while it is in flight.
        if (p.inFlight) return null
        if (s.nowMs - stableSinceMs < SETTLE_MS || s.nowMs - progressAtMs < STALL_MS ||
            s.nowMs - p.lastAcceptedMs < STALL_MS) return null
        val existing = pending
        if (existing != null) {
            if (s.nowMs - existing.createdMs > CONFIRM_VALID_MS) pending = null
            return null
        }
        if (lastAttemptMs >= 0 && s.nowMs - lastAttemptMs < RETRY_COOLDOWN_MS) return null
        if (recoveries.count { it >= 0 && s.nowMs - it < BUDGET_WINDOW_MS } >= MAX_RECOVERIES) return null
        lastAttemptMs = s.nowMs
        return PlaybackRecoveryTicket(s.epoch, s.itemIndex, p.sinkId, p.epoch,
            p.acceptedBytes, p.positionUs, p.gain, s.durationMs, lastRealPositionMs, s.nowMs).also { pending = it }
    }

    /** Called again on Main AFTER an acknowledgement from the existing playback looper. */
    fun confirm(ticket: PlaybackRecoveryTicket, s: PlaybackHealthInput): Boolean {
        if (pending !== ticket || !usable(s) || s.nowMs - ticket.createdMs !in 0..CONFIRM_VALID_MS ||
            s.epoch != ticket.epoch || s.itemIndex != ticket.itemIndex || s.durationMs != ticket.durationMs ||
            !unchanged(ticket, s.output) || s.nowMs - s.output!!.lastAcceptedMs < STALL_MS) {
            if (pending === ticket) pending = null
            return false
        }
        pending = null
        recoveries[recoverySlot] = s.nowMs
        recoverySlot = (recoverySlot + 1) % MAX_RECOVERIES
        previous = null; stableSinceMs = -1L; progressAtMs = -1L
        return true
    }

    /** Pure immutable comparison: safe to call on the existing playback thread. */
    fun unchanged(ticket: PlaybackRecoveryTicket, p: AudioOutputProgress?): Boolean =
        p != null && p.valid && p.playing && !p.inFlight && p.gain.isFinite() && p.gain > 0.00001f &&
            p.sinkId == ticket.sinkId && p.epoch == ticket.sinkEpoch && p.gain == ticket.gain &&
            p.acceptedBytes == ticket.acceptedBytes && p.positionUs == ticket.positionUs
}
