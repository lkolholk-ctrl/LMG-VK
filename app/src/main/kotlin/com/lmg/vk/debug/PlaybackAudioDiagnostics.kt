package com.lmg.vk.debug

import android.media.AudioTrack
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import com.lmg.vk.engine.background.AudioOutputProgress

/** Service-scoped observations only: never pauses, changes gain, flushes or retries playback. */
@UnstableApi
class PlaybackAudioDiagnostics(
    private val environment: (() -> String)? = null,
) {
    private val probes = CopyOnWriteArrayList<Probe>()
    private var lastPollMs = Long.MIN_VALUE
    private val pollProgress = AudioPollProgress()
    @Volatile private var hardwareExecutor: Executor? = null
    @Volatile private var closed = false
    private val hardwareBusy = AtomicBoolean(false)

    /** No extra thread: the service supplies its existing IO executor. */
    fun installHardwareExecutor(executor: Executor) { hardwareExecutor = executor }
    fun close() { closed = true; hardwareExecutor = null }

    /** A pure volatile snapshot, with NO AudioTrack/Binder getters. More than one
     * playing sink is ambiguous (crossfade/prewarm): recovery must not touch it. */
    internal fun recoveryOutput(): AudioOutputProgress? {
        if (closed) return null
        var selected: AudioOutputProgress? = null
        for (probe in probes) {
            val value = probe.progress() ?: return null
            if (!value.playing) continue
            if (selected != null) return null
            selected = value
        }
        return selected
    }

    private fun refreshHardware() {
        val executor = hardwareExecutor ?: return
        if (closed || !hardwareBusy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    if (!closed) for (probe in probes) probe.refreshHardware()
                } finally { hardwareBusy.set(false) }
            }
        } catch (_: RuntimeException) { hardwareBusy.set(false) }
    }

    fun newProbe(): Probe = Probe(probes.size).also { probes.add(it) }

    fun event(message: String) {
        DebugLog.add("AUDIO_DIAG t=${SystemClock.elapsedRealtime()} $message")
    }

    fun playerSnapshot(player: Player, reason: String, force: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastPollMs != Long.MIN_VALUE && now - lastPollMs < 5_000) return
        val uptime = SystemClock.uptimeMillis()
        val sampleEnd = SystemClock.elapsedRealtime()
        val timing = pollProgress.observe(now, uptime, sampleEnd)
        lastPollMs = now
        event("$reason clockSampleAtMs=$sampleEnd uptimeMs=$uptime" +
            " clockSampleValid=${timing.sampleValid} clockReadSpanMs=${timing.sampleSpanMs}" +
            " pollGapElapsedMs=${timing.elapsedDeltaMs} pollGapAwakeMs=${timing.awakeDeltaMs}" +
            " suspendGapEstimateMs=${timing.suspendGapEstimateMs}")
        val environmentStart = SystemClock.elapsedRealtime()
        val environmentText = runCatching { environment?.invoke() }
            .getOrElse { "environmentUnavailable=${it.javaClass.simpleName}" }
        if (environmentText != null) event("$reason $environmentText" +
            " environmentReadMs=${SystemClock.elapsedRealtime() - environmentStart}")
        event("$reason state=${player.playbackState} playWhenReady=${player.playWhenReady}" +
            " playing=${player.isPlaying} suppression=${player.playbackSuppressionReason}" +
            " item=${player.currentMediaItemIndex} posMs=${player.currentPosition}" +
            " bufferedMs=${player.totalBufferedDuration} volume=${player.volume}")
        probes.forEach { it.dump(reason) }
        refreshHardware()
    }

    inner class Probe internal constructor(internal val id: Int) {
        @Volatile private var track: AudioTrack? = null
        @Volatile private var generation = 0
        @Volatile private var playing = false
        @Volatile private var gain = 1f
        @Volatile private var acceptedBytes = 0L
        @Volatile private var lastAcceptedMs = -1L
        @Volatile private var sinkPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
        @Volatile private var lastPositionAdvanceMs = -1L
        @Volatile private var positionVersion = 0L
        @Volatile private var inputPeak = -1f
        @Volatile private var outputPeak = -1f
        @Volatile private var inputSampleAt = Long.MIN_VALUE
        @Volatile private var outputSampleAt = Long.MIN_VALUE
        @Volatile private var lifecycleVersion = 0L
        @Volatile private var hardware = "track=unobserved"
        @Volatile private var hardwareAtMs = -1L
        private var inputWidth = 0
        private val writes = AudioWriteProgress()

        fun attached(value: AudioTrack) {
            lifecycleVersion++
            track = value
            generation++
            lifecycleVersion++
            // No hardware getters or logging on the renderer thread.
        }

        /** Called after user DSP has produced a buffer, before Media3 consumes it. */
        fun processed(buffer: ByteBuffer, width: Int) {
            val now = SystemClock.elapsedRealtime()
            if (outputSampleAt != Long.MIN_VALUE && now - outputSampleAt < 250) return
            outputSampleAt = now
            outputPeak = PcmSignalSample.peak(buffer, width)
        }

        fun dump(reason: String) {
            val sampleElapsed = SystemClock.elapsedRealtime()
            val sampleUptime = SystemClock.uptimeMillis()
            val write = writes.snapshot()
            // Volatile ledger snapshot precedes hardware getters, which may be slow.
            if (write == null) {
                event("sink=$id $reason writeSnapshot=unstable")
            } else {
                val inFlightElapsed = if (write.inFlight) (sampleElapsed - write.enteredElapsedMs).coerceAtLeast(0L) else -1L
                val inFlightAwake = if (write.inFlight) (sampleUptime - write.enteredUptimeMs).coerceAtLeast(0L) else -1L
                event("sink=$id $reason writeSnapshotAtMs=$sampleElapsed" +
                    " calls=${write.calls} completions=${write.completions}" +
                    " inFlight=${write.inFlight} inFlightElapsedMs=$inFlightElapsed" +
                    " inFlightAwakeMs=$inFlightAwake enteredMs=${write.enteredElapsedMs}" +
                    " returnedMs=${write.returnedElapsedMs} lastCallResult=${write.lastOutcome}" +
                    " lastCallElapsedMs=${write.lastDurationElapsedMs}" +
                    " lastCallAwakeMs=${write.lastDurationAwakeMs}" +
                    " acceptedOnReturnBytes=${write.acceptedOnReturnBytes}" +
                    " lastConsumeReturnedMs=${write.lastConsumeReturnedMs}" +
                    " invalidPositionDelta=${write.invalidPositionDelta}")
            }
            event("sink=$id $reason gen=$generation playing=$playing gain=$gain" +
                " consumedBytes=$acceptedBytes lastConsumeMs=$lastAcceptedMs lastPositionAdvanceMs=$lastPositionAdvanceMs" +
                " sinkPosUs=$sinkPositionUs preFxSample=$inputPeak@$inputSampleAt" +
                " postDspSample=$outputPeak@$outputSampleAt hardwareAtMs=$hardwareAtMs $hardware")
        }

        /** Slow vendor getters live on the existing IO worker, never on the renderer
         * or application looper. An old result cannot attach to a replacement track. */
        fun refreshHardware() {
            val audio = track ?: return
            val epoch = lifecycleVersion
            if ((epoch and 1L) != 0L) return
            val start = SystemClock.elapsedRealtime()
            val description = runCatching {
                "trackState=${audio.state} playState=${audio.playState}" +
                    " head=${audio.playbackHeadPosition.toLong() and 0xffffffffL}" +
                    " underruns=${audio.underrunCount} route=${audio.routedDevice?.type}"
            }.getOrElse { "track=unavailable:${it.javaClass.simpleName}" }
            if (!closed && audio === track && epoch == lifecycleVersion) {
                hardware = "$description hardwareReadMs=${SystemClock.elapsedRealtime() - start}"
                hardwareAtMs = SystemClock.elapsedRealtime()
            }
        }

        internal fun progress(): AudioOutputProgress? {
            val epoch = lifecycleVersion
            if ((epoch and 1L) != 0L) return null
            val write = writes.snapshot() ?: return null
            val version = positionVersion
            if ((version and 1L) != 0L) return null
            val position = sinkPositionUs
            val advancedAt = lastPositionAdvanceMs
            val value = AudioOutputProgress(id, epoch, playing, gain,
                write.acceptedOnReturnBytes, write.lastConsumeReturnedMs, position,
                write.inFlight, track != null && inputWidth != 0 &&
                    !write.invalidPositionDelta && write.lastOutcome != 3, advancedAt)
            return if (epoch == lifecycleVersion && version == positionVersion) value else null
        }

        fun wrap(delegate: AudioSink): AudioSink = object : ForwardingAudioSink(delegate) {
            override fun configure(config: AudioSink.AudioSinkConfig) {
                lifecycleVersion++
                try {
                    delegate.configure(config)
                    inputWidth = when (config.format.pcmEncoding) {
                        C.ENCODING_PCM_16BIT -> 2
                        C.ENCODING_PCM_FLOAT -> 4
                        else -> 0
                    }
                } finally { lifecycleVersion++ }
                // Configuration changes invalidate any control-thread recovery ticket.
            }

            override fun handleBuffer(buffer: ByteBuffer, pts: Long, units: Int): Boolean {
                val now = SystemClock.elapsedRealtime()
                if (inputSampleAt == Long.MIN_VALUE || now - inputSampleAt >= 250) {
                    inputSampleAt = now
                    inputPeak = PcmSignalSample.peak(buffer, inputWidth)
                }
                val start = buffer.position()
                writes.begin(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())
                var outcome = 3 // Keep the original exception object/stack if delegate throws.
                try {
                    val result = delegate.handleBuffer(buffer, pts, units)
                    val consumed = buffer.position() - start
                    if (consumed > 0) {
                        acceptedBytes += consumed
                        lastAcceptedMs = SystemClock.elapsedRealtime() // RETURN time, not entry.
                    }
                    outcome = if (result) 1 else 2
                    return result
                } finally {
                    writes.end(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis(),
                        buffer.position() - start, outcome)
                }
            }

            override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
                val position = delegate.getCurrentPositionUs(sourceEnded)
                positionVersion++
                if (position >= 0 && position > sinkPositionUs) {
                    lastPositionAdvanceMs = SystemClock.elapsedRealtime()
                }
                sinkPositionUs = position
                positionVersion++
                return position
            }

            override fun setVolume(volume: Float) {
                delegate.setVolume(volume)
                gain = volume
            }

            override fun play() {
                lifecycleVersion++
                try { delegate.play(); playing = true }
                finally { lifecycleVersion++ }
            }

            override fun pause() {
                lifecycleVersion++
                try { delegate.pause(); playing = false }
                finally { lifecycleVersion++ }
            }

            private fun resetObservation() {
                track = null
                sinkPositionUs = AudioSink.CURRENT_POSITION_NOT_SET
                lastPositionAdvanceMs = -1L
                inputPeak = -1f; outputPeak = -1f
                hardware = "track=unbound"; hardwareAtMs = -1L
                inputSampleAt = Long.MIN_VALUE; outputSampleAt = Long.MIN_VALUE
                acceptedBytes = 0; lastAcceptedMs = -1
                writes.reset()
                // Called only by the owner after a successful flush/reset/release.
            }

            override fun flush() {
                lifecycleVersion++
                try { delegate.flush(); resetObservation() }
                finally { lifecycleVersion++ }
            }
            override fun reset() {
                lifecycleVersion++
                try { delegate.reset(); resetObservation() }
                finally { lifecycleVersion++ }
            }
            override fun release() {
                lifecycleVersion++
                try { delegate.release(); resetObservation(); playing = false }
                finally { lifecycleVersion++ }
            }
        }
    }
}
