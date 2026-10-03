package com.lmg.vk.debug

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import com.lmg.vk.engine.background.OutputStallWatch
import com.lmg.vk.engine.background.PlaybackHealthInput
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Independent of Activity lifecycle and the Main polling job. No Player/AudioTrack getters on
 * the monitor thread, no extra player, no pause/play/seek and no concurrent sink calls. */
@UnstableApi
internal class PlaybackStallMonitor(
    private val player: ExoPlayer,
    private val diagnostics: PlaybackAudioDiagnostics,
) {
    private class Wakeup(val message: PlayerMessage) { val used = AtomicBoolean(false) }
    private data class Published(val input: PlaybackHealthInput, val wakeup: Wakeup)
    private val applicationThread = player.applicationLooper.thread
    private val playbackThread = player.playbackLooper.thread
    private val published = AtomicReference<Published?>()
    private val watch = OutputStallWatch()
    @Volatile private var closed = false
    @Volatile private var acknowledgedAt = -1L
    private val worker = Thread({ runMonitor() }, "AudioProgressMonitor").apply {
        isDaemon = true
        start()
    }

    /** Called on the application looper. Construct the one-shot message here, before a stall.
     * Sending it later does not require an application-thread round trip. */
    fun publish(input: PlaybackHealthInput) {
        check(Looper.myLooper() == player.applicationLooper)
        if (closed || !input.eligible) { invalidate(); return }
        val old = published.get()
        val wakeup = if (old != null && old.input.epoch == input.epoch &&
            old.input.itemIndex == input.itemIndex && !old.wakeup.used.get()) old.wakeup else {
            Wakeup(player.createMessage(PlayerMessage.Target { _, _ ->
                acknowledgedAt = SystemClock.elapsedRealtime()
            }))
        }
        published.set(Published(input, wakeup))
    }

    fun invalidate() { published.set(null) }
    fun close() { closed = true; invalidate(); worker.interrupt() }

    private fun runMonitor() {
        var previousElapsed = SystemClock.elapsedRealtime()
        var previousUptime = SystemClock.uptimeMillis()
        var lastDelayedReport = -1L
        while (!closed) {
            try { Thread.sleep(500L) } catch (_: InterruptedException) { return }
            val now = SystemClock.elapsedRealtime()
            val uptime = SystemClock.uptimeMillis()
            val elapsedGap = now - previousElapsed
            val awakeGap = uptime - previousUptime
            previousElapsed = now; previousUptime = uptime
            val state = published.get()
            val output = diagnostics.recoveryOutput()
            val staleOutput = watch.observe(state?.input, output, now)
            // If this thread was delayed too, fresh output may already have resumed.
            // Preserve that evidence, but do not label it proof of an OS freeze.
            val delayedMonitor = OutputStallWatch.usable(state?.input, output, now) &&
                awakeGap >= 2_500L && (lastDelayedReport < 0 || now - lastDelayedReport >= 30_000L)
            if (!staleOutput && !delayedMonitor) continue
            if (state == null || output == null || closed) continue
            if (delayedMonitor) lastDelayedReport = now
            val playbackStack = threadSnapshot("playback", playbackThread)
            val applicationStack = threadSnapshot("application", applicationThread)
            diagnostics.event("stall-monitor trigger=${if (staleOutput) "STALE_OUTPUT" else "LATE_MONITOR"}" +
                " item=${state.input.itemIndex} sink=${output.sinkId}" +
                " lastBytesAt=${output.lastAcceptedMs} lastPositionAt=${output.lastPositionAdvanceMs}" +
                " inFlight=${output.inFlight} mainSampleAgeMs=${now - state.input.nowMs}" +
                " monitorGapElapsedMs=$elapsedGap monitorGapAwakeMs=$awakeGap" +
                " lastWakeAckMs=$acknowledgedAt")
            // Snapshot before any wake request: the stopped call is the useful evidence.
            diagnostics.event(playbackStack)
            diagnostics.event(applicationStack)
            if (!staleOutput) continue
            val latest = published.get()
            val fresh = diagnostics.recoveryOutput()
            val checkedAt = SystemClock.elapsedRealtime()
            if (closed || latest == null || latest.input.epoch != state.input.epoch ||
                latest.input.itemIndex != state.input.itemIndex || fresh == null ||
                fresh.sinkId != output.sinkId || fresh.epoch != output.epoch ||
                !OutputStallWatch.usable(latest.input, fresh, checkedAt) ||
                !OutputStallWatch.stale(fresh, checkedAt) || fresh.inFlight ||
                latest.wakeup.used.get() || !watch.claimWakeup(checkedAt) ||
                !latest.wakeup.used.compareAndSet(false, true)) continue
            try {
                // Public Media3 message transport, prepared on Main, sent once. Its target
                // changes no playback state; sendMessageToTarget schedules ordinary work.
                latest.wakeup.message.send()
                diagnostics.event("stall-work-request epoch=${latest.input.epoch} sink=${fresh.sinkId}")
            } catch (e: RuntimeException) {
                diagnostics.event("stall-work-unavailable ${e.javaClass.simpleName}")
            }
        }
    }

    private fun threadSnapshot(role: String, thread: Thread): String {
        val start = SystemClock.elapsedRealtime()
        val stack = try {
            thread.stackTrace.take(18).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        } catch (e: RuntimeException) { "unavailable:${e.javaClass.simpleName}" }
        return "stall-thread role=$role state=${thread.state}" +
            " captureMs=${SystemClock.elapsedRealtime() - start} stack=$stack"
    }
}
