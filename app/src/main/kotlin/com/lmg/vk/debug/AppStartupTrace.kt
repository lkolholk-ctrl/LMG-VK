package com.lmg.vk.debug

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import com.lmg.vk.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object AppStartupTrace {
    @Volatile private var active: Session? = null
    private val started = AtomicBoolean()

    fun start(application: Application) {
        if (!BuildConfig.DEBUG || !started.compareAndSet(false, true)) return
        val session = Session(application)
        active = session
        session.start()
    }

    fun beginElapsed(name: String): Span? {
        val session = active?.takeIf { it.running() } ?: return null
        return Span(session, name)
    }

    suspend fun <T> elapsed(name: String, block: suspend () -> T): T {
        val span = beginElapsed(name)
        try {
            return block()
        } finally {
            span?.end()
        }
    }

    fun <T> measure(name: String, block: () -> T): T = AppPerformanceCapture.measure(name) capture@ {
        val session = active?.takeIf { it.running() } ?: return@capture block()
        val start = System.nanoTime()
        val cpu = Debug.threadCpuTimeNanos()
        val thread = if (Looper.myLooper() == Looper.getMainLooper()) "ui" else "worker"
        try {
            block()
        } finally {
            val end = System.nanoTime()
            session.stats.work("$name.wall.$thread", start, end)
            session.stats.work("$name.cpu.$thread", start, start + (Debug.threadCpuTimeNanos() - cpu).coerceAtLeast(0))
        }
    }

    fun mark(name: String) {
        AppPerformanceCapture.mark(name)
        active?.takeIf { it.running() }?.mark(name)
    }

    internal class Span(private val session: Session, private val name: String) {
        private val start = System.nanoTime()
        private val id = session.sequence.incrementAndGet()
        private val ended = AtomicBoolean()
        private val systemTrace = AppPerformanceCapture.beginAsync(name)

        init { session.pending[id] = name to start }

        fun end() {
            if (!ended.compareAndSet(false, true)) return
            systemTrace?.close()
            session.pending.remove(id)
            if (session.running()) session.stats.work("$name.elapsed", start, System.nanoTime())
        }
    }

    internal class Session(private val application: Application) : Application.ActivityLifecycleCallbacks {
        val stats = StartupTraceStats(System.nanoTime(), 60)
        val sequence = AtomicLong()
        val pending = ConcurrentHashMap<Long, Pair<String, Long>>()
        private val closed = AtomicBoolean()
        private val thread = HandlerThread("AppStartupTrace", Process.THREAD_PRIORITY_BACKGROUND)
        private lateinit var worker: Handler
        private val main = Handler.createAsync(Looper.getMainLooper())
        private val windows = linkedMapOf<Window, Window.OnFrameMetricsAvailableListener>()
        private val resumed = mutableSetOf<Activity>()
        @Volatile private var foreground = false
        private val pendingHeartbeat = AtomicLong()
        private val stallGate = StartupStallGate()
        private val events = mutableListOf<String>()
        private var lastTickMs = SystemClock.uptimeMillis()
        private var lastRuntimeMs = lastTickMs
        private var lastCpuMs = Process.getElapsedCpuTime()
        private var lastGc = gcStats()

        fun running(): Boolean = !closed.get() && System.nanoTime() - stats.startedNs < 60_000_000_000L

        fun mark(name: String) {
            val event = "atMs=${(System.nanoTime() - stats.startedNs) / 1_000_000} $name"
            synchronized(events) { if (events.size < 160) events.add(event) }
        }

        fun start() {
            thread.start()
            worker = Handler(thread.looper)
            application.registerActivityLifecycleCallbacks(this)
            DebugLog.add("APP_STARTUP begin seconds=60 sdk=${Build.VERSION.SDK_INT}")
            worker.post(tick)
            worker.postDelayed({ close() }, 60_000)
        }

        private val heartbeat = Runnable {
            val posted = pendingHeartbeat.getAndSet(0)
            if (posted > 0 && running()) {
                val end = System.nanoTime()
                val wait = (SystemClock.uptimeMillis() - posted).coerceAtLeast(0) * 1_000_000L
                stats.work("main_queue_wait.elapsed", end - wait, end)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                if (!running()) return
                val now = SystemClock.uptimeMillis()
                val pendingAt = pendingHeartbeat.get()
                if (stallGate.shouldSample(now, pendingAt, now - lastTickMs, foreground)) {
                    val stack = runCatching { Looper.getMainLooper().thread.stackTrace }.getOrDefault(emptyArray())
                    val frames = stack.take(16).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                    mark("main_sample waitMs=${now - pendingAt} stack=$frames")
                }
                if (foreground && pendingAt == 0L && pendingHeartbeat.compareAndSet(0, now)) main.post(heartbeat)
                if (now - lastRuntimeMs >= 5_000) runtimeSample(now)
                lastTickMs = now
                worker.postDelayed(this, 100)
            }
        }

        private fun runtimeSample(now: Long) {
            val cpu = Process.getElapsedCpuTime()
            val gc = gcStats()
            val delta = gc.entries.joinToString(" ") { (key, value) -> "$key=${value - (lastGc[key] ?: value)}" }
            val runtime = Runtime.getRuntime()
            mark("runtime intervalMs=${now - lastRuntimeMs} processCpuMs=${cpu - lastCpuMs} foreground=$foreground heapUsedKb=${(runtime.totalMemory() - runtime.freeMemory()) / 1024} $delta")
            lastRuntimeMs = now
            lastCpuMs = cpu
            lastGc = gc
        }

        private fun gcStats(): Map<String, Long> = runCatching {
            listOf("art.gc.gc-count", "art.gc.gc-time", "art.gc.blocking-gc-count", "art.gc.blocking-gc-time", "art.gc.bytes-allocated")
                .mapNotNull { key -> Debug.getRuntimeStat(key)?.toLongOrNull()?.let { key to it } }.toMap()
        }.getOrDefault(emptyMap())

        private fun close() {
            if (!closed.compareAndSet(false, true)) return
            if (active === this) active = null
            worker.removeCallbacks(tick)
            runtimeSample(SystemClock.uptimeMillis())
            val unfinished = pending.values.toList()
            pending.clear()
            main.post {
                application.unregisterActivityLifecycleCallbacks(this)
                windows.forEach { (window, listener) -> runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
                windows.clear()
                resumed.clear()
                main.removeCallbacks(heartbeat)
                pendingHeartbeat.set(0)
                worker.post {
                    synchronized(events) { events.toList() }.forEach { DebugLog.add("APP_STARTUP $it") }
                    stats.report().forEach { DebugLog.add("APP_STARTUP $it") }
                    unfinished.take(20).forEach { (name, start) ->
                        DebugLog.add("APP_STARTUP unfinished=$name atMs=${(start - stats.startedNs) / 1_000_000}")
                    }
                    DebugLog.add("APP_STARTUP end=timeout seconds=60")
                    thread.quitSafely()
                }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (!running()) return
            mark("activity_created=${activity.javaClass.simpleName}")
            val window = activity.window ?: return
            if (windows.containsKey(window)) return
            @Suppress("DEPRECATION")
            val hz = (if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay)
                ?.refreshRate?.takeIf { it.isFinite() && it > 0f } ?: 60f
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
                if (running()) {
                    val timestamp = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP).takeIf { it > 0 } ?: System.nanoTime()
                    val intended = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                    val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
                    stats.frame(timestamp, metrics.getMetric(FrameMetrics.TOTAL_DURATION),
                        deadline.takeIf { it > 0 } ?: (1_000_000_000.0 / hz).toLong(),
                        metrics.getMetric(FrameMetrics.DRAW_DURATION), metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                        metrics.getMetric(FrameMetrics.SYNC_DURATION),
                        if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1L,
                        if (intended > 0) (timestamp - intended).coerceAtLeast(0) else 0L,
                        metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L, dropped)
                }
            }
            runCatching { window.addOnFrameMetricsAvailableListener(listener, worker) }
                .onSuccess { windows[window] = listener }
                .onFailure { mark("frame_listener_error=${it.javaClass.simpleName}") }
        }

        override fun onActivityResumed(activity: Activity) {
            resumed.add(activity)
            foreground = true
            mark("activity_resumed=${activity.javaClass.simpleName}")
        }

        override fun onActivityPaused(activity: Activity) {
            resumed.remove(activity)
            foreground = resumed.isNotEmpty()
            if (!foreground) {
                main.removeCallbacks(heartbeat)
                pendingHeartbeat.set(0)
            }
            mark("activity_paused=${activity.javaClass.simpleName}")
        }

        override fun onActivityDestroyed(activity: Activity) {
            windows.remove(activity.window)?.let { listener ->
                runCatching { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            }
            resumed.remove(activity)
            foreground = resumed.isNotEmpty()
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
