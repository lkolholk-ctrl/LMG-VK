package com.lmg.vk.ui.player

internal class ArtworkBackgroundGeneration {
    private var source: Any? = null
    private var enabled = false
    private var generation = 0

    fun update(nextSource: Any?, nextEnabled: Boolean): Int {
        if (source != nextSource && (!enabled || !nextEnabled)) generation++
        source = nextSource
        enabled = nextEnabled
        return generation
    }
}
