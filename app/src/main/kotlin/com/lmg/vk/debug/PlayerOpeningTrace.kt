package com.lmg.vk.debug

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import com.lmg.vk.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal object PlayerOpeningTrace {
    @Volatile private var active: Session? = null
    private val sequence = AtomicInteger()

    @Synchronized fun request() {
        if (!BuildConfig.DEBUG) return
        active?.close("replaced")
        Session(sequence.incrementAndGet()).also { active = it; it.start() }
    }

    @Synchronized fun attach(): AutoCloseable? {
        if (!BuildConfig.DEBUG) return null
        if (active?.running() != true) request()
        val session = active ?: return null
        session.markOnce("screen_attached")
        return AutoCloseable { session.close("leave") }
    }

    fun markOnce(name: String) {
        active?.takeIf { it.running() }?.markOnce(name)
    }

    fun <T> measure(name: String, block: () -> T): T {
        val session = active?.takeIf { it.running() } ?: return block()
        val started = System.nanoTime()
        val cpu = Debug.threadCpuTimeNanos()
        val previous = session.phase
        session.phase = name
        try {
            return block()
        } finally {
            session.phase = previous
            val ended = System.nanoTime()
            session.stats.work("$name.wall.ui", started, ended)
            session.stats.work("$name.cpu.ui", started, started + (Debug.threadCpuTimeNanos() - cpu).coerceAtLeast(0))
            session.markOnce("first_completed=$name")
        }
    }

    private class Session(val id: Int) {
        val stats = StartupTraceStats(System.nanoTime(), 5)
        @Volatile var phase = "outside_measured_phases"
        private val closed = AtomicBoolean()
        private val thread = HandlerThread("LyricsOpeningTrace", Process.THREAD_PRIORITY_BACKGROUND)
        private lateinit var worker: Handler
        private val main = Handler.createAsync(Looper.getMainLooper())
        private val pending = AtomicLong()
        private val gate = OpeningStallGate()
        private val events = mutableListOf<String>()
        private val firstEvents = mutableSetOf<String>()
        private var lastTick = SystemClock.uptimeMillis()

        fun running() = !closed.get() && System.nanoTime() - stats.startedNs < 5_000_000_000L

        fun markOnce(name: String) {
            synchronized(events) {
                if (firstEvents.add(name)) event(name)
            }
        }

        private fun event(name: String) {
            synchronized(events) {
                if (!closed.get() && events.size < 64) {
                    events.add("atMs=${(System.nanoTime() - stats.startedNs) / 1_000_000} $name")
                }
            }
        }

        fun start() {
            thread.start()
            worker = Handler(thread.looper)
            DebugLog.add("LYRICS_OPENING id=$id begin seconds=5 heartbeatMs=16 thresholdMs=40")
            worker.post(tick)
            worker.postDelayed({ close("timeout") }, 5_000)
        }

        private val heartbeat = Runnable {
            val posted = pending.getAndSet(0)
            if (posted > 0 && running()) {
                val ended = System.nanoTime()
                val wait = (SystemClock.uptimeMillis() - posted).coerceAtLeast(0)
                stats.work("main_queue_wait.elapsed", ended - wait * 1_000_000L, ended)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                if (!running()) return
                val now = SystemClock.uptimeMillis()
                val posted = pending.get()
                if (gate.shouldSample(now, posted, now - lastTick)) {
                    val started = System.nanoTime()
                    val observedPhase = phase
                    val ui = Looper.getMainLooper().thread
                    val state = ui.state
                    val stack = runCatching { ui.stackTrace }.getOrDefault(emptyArray())
                    val costUs = (System.nanoTime() - started) / 1000
                    if (pending.get() == posted) {
                        val frames = stack.take(32).joinToString(" <- ") {
                            "${it.className}.${it.methodName}:${it.lineNumber}"
                        }
                        event("main_sample waitMs=${now - posted} phase=$observedPhase state=$state sampleCostUs=$costUs stack=$frames")
                    }
                }
                if (posted == 0L && pending.compareAndSet(0, now)) main.post(heartbeat)
                lastTick = now
                worker.postDelayed(this, 16)
            }
        }

        fun close(reason: String) {
            if (!closed.compareAndSet(false, true)) return
            synchronized(PlayerOpeningTrace) { if (active === this) active = null }
            main.removeCallbacks(heartbeat)
            pending.set(0)
            worker.removeCallbacksAndMessages(null)
            worker.post {
                synchronized(events) { events.toList() }.forEach { DebugLog.add("LYRICS_OPENING id=$id $it") }
                stats.report().forEach { DebugLog.add("LYRICS_OPENING id=$id $it") }
                DebugLog.add("LYRICS_OPENING id=$id end=$reason")
                thread.quitSafely()
            }
        }
    }
}

internal fun Modifier.traceLyricsOpening(name: String): Modifier {
    if (!BuildConfig.DEBUG) return this
    return layout { measurable, constraints ->
        val placeable = PlayerOpeningTrace.measure("$name.measure") { measurable.measure(constraints) }
        layout(placeable.width, placeable.height) {
            PlayerOpeningTrace.measure("$name.place") { placeable.place(0, 0) }
        }
    }.drawWithContent {
        PlayerOpeningTrace.measure("$name.draw") { drawContent() }
    }
}
