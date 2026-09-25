package com.lmg.vk.debug

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.lmg.vk.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object PlayerStartupTrace {
    @Volatile private var active: Session? = null
    private val sequence = AtomicInteger()

    internal class Span(private val session: Session?, private val name: String, private val started: Long,
                        private val appSpan: AppStartupTrace.Span?) {
        fun end() {
            appSpan?.end()
            if (session != null && !session.closed.get()) session.stats.work(name, started, System.nanoTime())
        }
    }

    fun begin(name: String): Span? {
        val appSpan = AppStartupTrace.beginElapsed(name)
        val now = System.nanoTime()
        val session = active?.takeIf { !it.closed.get() && now - it.stats.startedNs < it.stats.seconds * 1_000_000_000L }
        if (session == null && appSpan == null) return null
        val thread = if (Looper.myLooper() == Looper.getMainLooper()) "ui" else "worker"
        return Span(session, "$name.$thread", now, appSpan)
    }

    fun mark(name: String) {
        AppStartupTrace.mark(name)
        val session = active ?: return
        if (!session.closed.get()) DebugLog.add("PLAYER_STARTUP id=${session.id} atMs=${(System.nanoTime() - session.stats.startedNs) / 1_000_000} $name")
    }

    fun attach(window: Window, refreshRate: Float, scope: String): AutoCloseable {
        active?.takeIf { !it.closed.get() }?.let {
            val borrowed = it
            mark("enter=$scope")
            return AutoCloseable { if (active === borrowed) mark("leave=$scope") }
        }
        val session = Session(window, refreshRate, scope, sequence.incrementAndGet())
        active = session
        session.start()
        AppStartupTrace.mark("enter=$scope")
        return AutoCloseable { session.close("leave=$scope") }
    }

    internal class Session(
        private val window: Window, refreshRate: Float, private val scope: String, val id: Int
    ) {
        val stats = StartupTraceStats(System.nanoTime())
        val closed = AtomicBoolean()
        private val hz = refreshRate.takeIf { it.isFinite() && it > 0f } ?: 60f
        private val budgetNs = (1_000_000_000.0 / hz).toLong()
        private val thread = HandlerThread("PlayerStartupTrace", Process.THREAD_PRIORITY_BACKGROUND)
        private lateinit var handler: Handler
        private val gcBefore = gcStats()
        private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            if (!closed.get()) {
                val timestamp = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP).takeIf { it > 0 } ?: System.nanoTime()
                val intended = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
                stats.frame(timestamp, metrics.getMetric(FrameMetrics.TOTAL_DURATION),
                    deadline.takeIf { it > 0 } ?: budgetNs,
                    metrics.getMetric(FrameMetrics.DRAW_DURATION), metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    metrics.getMetric(FrameMetrics.SYNC_DURATION),
                    if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1L,
                    if (intended > 0) (timestamp - intended).coerceAtLeast(0) else 0L,
                    metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L, dropped)
            }
        }

        fun start() {
            thread.start()
            handler = Handler(thread.looper)
            DebugLog.add("PLAYER_STARTUP id=$id enter=$scope sdk=${Build.VERSION.SDK_INT} hz=$hz seconds=${stats.seconds}")
            runCatching { window.addOnFrameMetricsAvailableListener(listener, handler) }
                .onFailure { DebugLog.add("PLAYER_STARTUP id=$id frameListener=${it.javaClass.simpleName}") }
            handler.postDelayed({ Handler(Looper.getMainLooper()).post { close("timeout") } }, stats.seconds * 1000L)
        }

        fun close(reason: String) {
            if (!closed.compareAndSet(false, true)) return
            if (active === this) active = null
            runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            handler.post {
                stats.report().forEach { DebugLog.add("PLAYER_STARTUP id=$id $it") }
                val after = gcStats()
                val gc = after.entries.joinToString(" ") { (key, value) -> "$key=${value - (gcBefore[key] ?: value)}" }
                DebugLog.add("PLAYER_STARTUP id=$id end=$reason $gc")
                thread.quitSafely()
            }
        }

        private fun gcStats(): Map<String, Long> = runCatching {
            listOf("art.gc.gc-count", "art.gc.gc-time", "art.gc.blocking-gc-count", "art.gc.blocking-gc-time")
                .mapNotNull { key -> Debug.getRuntimeStat(key)?.toLongOrNull()?.let { key to it } }.toMap()
        }.getOrDefault(emptyMap())
    }
}

@Composable
internal fun ObservePlayerStartup(key: Any, scope: String) {
    if (!BuildConfig.DEBUG) return
    val view = LocalView.current
    DisposableEffect(key, view) {
        val opening = if (scope == "Lyrics") PlayerOpeningTrace.attach() else null
        var context: Context? = view.context
        var activity: Activity? = null
        while (context != null) {
            if (context is Activity) { activity = context; break }
            val next = (context as? ContextWrapper)?.baseContext
            if (next === context) break
            context = next
        }
        val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
        val handle = window?.let { PlayerStartupTrace.attach(it, view.display?.refreshRate ?: 60f, scope) }
        onDispose { opening?.close(); handle?.close() }
    }
}
