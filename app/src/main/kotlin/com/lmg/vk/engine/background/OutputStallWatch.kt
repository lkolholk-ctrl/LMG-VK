package com.lmg.vk.engine.background

/** Read-only observations on an independent thread. A report never authorizes a seek or restart. */
internal class OutputStallWatch {
    private var source: PlaybackHealthInput? = null
    private var output: AudioOutputProgress? = null
    private var stableAt = -1L
    private var lastReport = -1L
    private val wakeups = LongArray(2) { -1L }
    private var wakeupSlot = 0

    fun observe(input: PlaybackHealthInput?, current: AudioOutputProgress?, now: Long): Boolean {
        if (!usable(input, current, now)) {
            source = null; output = null; stableAt = -1L
            return false
        }
        val s = checkNotNull(input)
        val p = checkNotNull(current)
        val old = source
        val previous = output
        source = s; output = p
        if (old == null || previous == null || old.epoch != s.epoch || old.itemIndex != s.itemIndex ||
            previous.sinkId != p.sinkId || previous.epoch != p.epoch || previous.gain != p.gain ||
            p.acceptedBytes < previous.acceptedBytes || p.positionUs < previous.positionUs || now < stableAt) {
            stableAt = now
            return false
        }
        if (now - stableAt < 8_000L || !stale(p, now)) return false
        if (lastReport >= 0 && now - lastReport < 30_000L) return false
        lastReport = now
        return true
    }

    /** The only allowed wakeup is a no-op PlayerMessage: Media3 schedules its normal work afterwards. */
    fun claimWakeup(now: Long): Boolean {
        if (wakeups.any { it >= 0 && now - it < 60_000L } ||
            wakeups.count { it >= 0 && now - it < 600_000L } >= wakeups.size) return false
        wakeups[wakeupSlot] = now
        wakeupSlot = (wakeupSlot + 1) % wakeups.size
        return true
    }

    companion object {
        fun stale(p: AudioOutputProgress, now: Long): Boolean =
            p.lastAcceptedMs in 0..now && p.lastPositionAdvanceMs in 0..now &&
                now - maxOf(p.lastAcceptedMs, p.lastPositionAdvanceMs) >= 2_000L

        fun usable(s: PlaybackHealthInput?, p: AudioOutputProgress?, now: Long): Boolean =
            s != null && p != null && s.eligible && s.epoch > 0 && s.itemIndex >= 0 &&
                now >= s.nowMs && now - s.nowMs <= s.bufferedMs - PlaybackProtectionPolicy.MIN_BUFFER_MS &&
                p.valid && p.playing && p.gain.isFinite() && p.gain > 0.00001f &&
                p.acceptedBytes > 0 && p.positionUs >= 0 &&
                p.lastAcceptedMs in 0..now && p.lastPositionAdvanceMs in 0..now
    }
}
