package com.lmg.vk.debug

internal class OpeningStallGate {
    private var samples = 0
    private var heartbeat = 0L
    private var heartbeatSamples = 0
    private var lastSample = Long.MIN_VALUE

    fun shouldSample(now: Long, pendingSince: Long, loopGap: Long): Boolean {
        if (pendingSince <= 0 || now - pendingSince < 40 || loopGap !in 0..64) return false
        if (samples >= 18 || (lastSample != Long.MIN_VALUE && now - lastSample < 32)) return false
        if (heartbeat != pendingSince) {
            heartbeat = pendingSince
            heartbeatSamples = 0
        }
        if (heartbeatSamples >= 3) return false
        heartbeatSamples++
        samples++
        lastSample = now
        return true
    }
}
