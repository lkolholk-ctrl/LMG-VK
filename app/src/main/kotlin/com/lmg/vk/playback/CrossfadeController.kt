package com.lmg.vk.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * Двуплеерный кроссфейд (восстановлено по использованию в PlaybackService,
 * аналог C1511e из VK X). playerA = активный, playerB = подменный.
 *
 * Типичный цикл: attach(playerA, playerB) → подменный плеер получает трек и
 * паузу → startCrossfade() плавно сводит громкости → onFinish срабатывает на
 * «активном» плеере → onReset() возвращает оба плеера в исходное состояние.
 */
class CrossfadeController(
    private val onFinish: (() -> Unit)? = null
) {
    private var playerA: Player? = null
    private var playerB: Player? = null
    private var active = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var crossfadeRunnable: Runnable? = null

    fun attach(playerA: Player, playerB: Player) {
        this.playerA = playerA
        this.playerB = playerB
        playerA.volume = 1f
        playerB.volume = 0f
        active = 0
    }

    val activePlayer: Player?
        get() = if (active == 0) playerA else playerB

    val inactivePlayer: Player?
        get() = if (active == 0) playerB else playerA

    fun startCrossfade(durationMs: Long = 1000L, stepMs: Long = 50L) {
        val from = activePlayer ?: return
        val to = inactivePlayer ?: return
        if (from === to) return
        val steps = (durationMs / stepMs).toInt().coerceAtLeast(1)
        var step = 0
        crossfadeRunnable?.let { mainHandler.removeCallbacks(it) }
        crossfadeRunnable = object : Runnable {
            override fun run() {
                step++
                val progress = step.toFloat() / steps.toFloat()
                from.volume = (1f - progress).coerceIn(0f, 1f)
                to.volume = progress.coerceIn(0f, 1f)
                if (step >= steps) {
                    active = if (active == 0) 1 else 0
                    from.volume = 0f
                    to.volume = 1f
                    onFinish?.invoke()
                } else {
                    mainHandler.postDelayed(this, stepMs)
                }
            }
        }
        mainHandler.postDelayed(crossfadeRunnable!!, stepMs)
    }

    fun onReset() {
        crossfadeRunnable?.let { mainHandler.removeCallbacks(it) }
        crossfadeRunnable = null
        playerA?.volume = 1f
        playerB?.volume = 0f
        active = 0
    }
}
