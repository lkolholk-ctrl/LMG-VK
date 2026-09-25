package com.lmg.vk.debug

internal class StartupStallGate {
    private var samples = 0
    private var lastSampleMs = Long.MIN_VALUE

    fun shouldSample(nowMs: Long, pendingSinceMs: Long, loopGapMs: Long, foreground: Boolean): Boolean {
        if (!foreground || pendingSinceMs <= 0 || nowMs - pendingSinceMs < 120 || loopGapMs > 400) return false
        if (samples >= 12 || (lastSampleMs != Long.MIN_VALUE && nowMs - lastSampleMs < 5000)) return false
        samples++
        lastSampleMs = nowMs
        return true
    }
}
