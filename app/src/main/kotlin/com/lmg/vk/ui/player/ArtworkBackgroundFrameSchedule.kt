package com.lmg.vk.ui.player

/** Finish the renderer's one-second artwork blend before freezing a hidden surface. */
internal class ArtworkBackgroundFrameSchedule(private val settleMs: Long = 1_000L) {
    private var initialized = false
    private var artwork: Any? = null
    private var settleAt = 0L
    private var pending = false

    // elapsedMs is renderer time, excluding Activity STOPPED time.
    fun needsFrame(source: Any?, visible: Boolean, elapsedMs: Long): Boolean {
        if (!initialized || artwork !== source) {
            initialized = true
            artwork = source
            settleAt = elapsedMs + settleMs
            pending = true
        }
        return visible || pending
    }

    fun rendered(elapsedMs: Long) {
        if (elapsedMs >= settleAt) pending = false
    }
}
