package com.lmg.vk.artwork

internal class MotionArtworkFrameDelivery(private val requestDraw: () -> Unit) {
    var needsFrame = true
        private set

    fun invalidate() {
        needsFrame = true
    }

    fun submitted() {
        needsFrame = false
        requestDraw()
    }
}
