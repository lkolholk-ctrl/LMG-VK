package com.lmg.vk.artwork

internal class MotionArtworkLifetime<T> {
    private val outputs = mutableSetOf<T>()
    private var retired = false
    var closed = false
        private set

    val canPlay: Boolean get() = !closed && !retired
    val shouldRelease: Boolean get() = !closed && retired && outputs.isEmpty()

    fun attach(output: T): Boolean = !closed && outputs.add(output)

    fun contains(output: T): Boolean = !closed && output in outputs

    fun detach(output: T) {
        outputs.remove(output)
    }

    fun retire() {
        retired = true
    }

    fun close() {
        closed = true
        outputs.clear()
    }
}
