package com.lmg.vk.engine.background

import java.util.concurrent.atomic.AtomicReference

/** All policy mutations happen on the application looper. The confirmation is posted to
 * the EXISTING ExoPlayer playback looper; it never calls a sink concurrently with write.
 * Neither this controller nor its host may create another player or use forced pause/play.
 */
internal interface PlaybackRecoveryHost {
    fun input(): PlaybackHealthInput
    fun epoch(): Long
    fun elapsedMs(): Long
    fun output(): AudioOutputProgress?
    fun postPlayback(block: () -> Unit)
    fun postApplication(block: () -> Unit)
    fun reprepareSamePlayer(itemIndex: Int, positionMs: Long)
    fun event(message: String)
}

internal class PlaybackRecoveryController(private val host: PlaybackRecoveryHost) {
    private val policy = PlaybackProtectionPolicy()
    private val pending = AtomicReference<PlaybackRecoveryTicket?>()
    @Volatile private var closed = false

    fun invalidate() { pending.set(null); policy.invalidate() }
    fun close() { closed = true; pending.set(null); policy.close() }
    fun foregroundRepairDue(nowMs: Long, intended: Boolean, foreground: Boolean): Boolean =
        policy.foregroundRepairDue(nowMs, intended, foreground)

    fun tick() {
        if (closed) return
        val ticket = policy.observe(host.input()) ?: return
        pending.set(ticket)
        host.event("stall-confirm-request epoch=${ticket.epoch} sink=${ticket.sinkId}")
        try {
            host.postPlayback {
                // Immutable ticket and volatile counters only; no Player getters on this looper.
                if (!closed && pending.get() === ticket && host.epoch() == ticket.epoch &&
                    host.elapsedMs() - ticket.createdMs in 0..PlaybackProtectionPolicy.CONFIRM_VALID_MS &&
                    policy.unchanged(ticket, host.output())) {
                    host.postApplication { confirmed(ticket) }
                } else {
                    pending.compareAndSet(ticket, null)
                }
            }
        } catch (e: RuntimeException) {
            pending.compareAndSet(ticket, null)
            host.event("stall-confirm-unavailable ${e.javaClass.simpleName}")
        }
    }

    private fun confirmed(ticket: PlaybackRecoveryTicket) {
        if (closed || !pending.compareAndSet(ticket, null)) return
        if (!policy.confirm(ticket, host.input())) return
        host.event("reprepare-same-player epoch=${ticket.epoch} positionMs=${ticket.resumePositionMs}")
        try {
            host.reprepareSamePlayer(ticket.itemIndex, ticket.resumePositionMs)
        } catch (e: RuntimeException) {
            // Do not turn a failed reprepare into an unbounded retry or an unsolicited play().
            host.event("reprepare-failed ${e.javaClass.simpleName}")
        }
    }
}
