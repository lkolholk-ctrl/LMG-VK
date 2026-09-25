# Apple Automix Engine Context for GPT-6 Astra Pro

## 1. Project & Task Objective
LMG-VK is a high-performance modern Android music player (Media3 / ExoPlayer 1.4+, Kotlin 2.x, Coroutines, Jetpack Compose).
We are implementing **Apple Music-style Automix** (Smart Transitions):
1. **Apple Audio Analysis Parser**: Parse Apple Music catalog analysis response (`audio-analysis`, `flexml-analysis`, `fades`, `loudnessCurve`) to extract exact transition points (`cueStartMs`, `crossfadeDurationMs`, `entryOffsetMs`).
2. **Local Fallback Plan**: If Apple metadata is missing for a track, use local `SmartTransitionFinder` / energy map.
3. **Equal-Power Seamless Crossfade**: Replace linear volume fading with equal-power ($\cos / \sin$) curves on dual `ExoPlayer` instances (`playerA` and `playerB`), maintaining perceived acoustic loudness and eliminating dips or pauses.
4. **Playback Orchestration**: Accurately pre-buffer track B in the second ExoPlayer, trigger transition at `cueStartMs`, fade out A / fade in B, and seamlessly swap active/inactive players upon completion.

---

## 2. Source Code

### File 1: `AppleAnalysisClient.kt`
Path: `app/src/main/kotlin/com/lmg/vk/engine/automix/AppleAnalysisClient.kt`
```kotlin
package com.lmg.vk.engine.automix

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Catalog analysis transport. The caller supplies a resolved Apple catalog ID
 * and a current developer token.
 * URL endpoint requests: extend=supportsSmartTransitions&include=audio-analysis,flexml-analysis
 * &extend%5Baudio-analysis%5D=fades,loudnessCurve
 */
class AppleAnalysisClient(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    fun newCall(songId: String, storefront: String, developerToken: String): AnalysisCall {
        require(songId.isNotEmpty() && songId.length <= 32 && songId.all { it in '0'..'9' })
        require(storefront.length == 2 && storefront.all { it in 'a'..'z' })
        require(developerToken.isNotBlank() && developerToken.length <= 16384 &&
            developerToken.all { it.code in 33..126 })
        val url = URL("https://amp-api.music.apple.com/v1/catalog/$storefront/songs/$songId" +
            "?extend=supportsSmartTransitions&include=audio-analysis,flexml-analysis" +
            "&extend%5Baudio-analysis%5D=fades,loudnessCurve")
        return AnalysisCall(songId, url, developerToken, openConnection)
    }

    class AnalysisCall internal constructor(
        val songId: String,
        val url: URL,
        private val token: String,
        private val openConnection: (URL) -> HttpURLConnection,
    ) {
        private val executed = AtomicBoolean()
        private val cancelled = AtomicBoolean()
        private val connection = AtomicReference<HttpURLConnection?>()

        fun cancel() {
            cancelled.set(true)
            connection.getAndSet(null)?.disconnect()
        }
        private fun ensureActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw IOException("Analysis request cancelled")
        }

        fun execute(): String {
            check(executed.compareAndSet(false, true)) { "Analysis call already executed" }
            ensureActive()
            val conn = openConnection(url)
            connection.set(conn)
            try {
                ensureActive()
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Origin", "https://music.apple.com")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Accept-Encoding", "identity")
                val code = conn.responseCode
                ensureActive()
                if (code != HttpURLConnection.HTTP_OK) throw IOException("Analysis HTTP $code")
                val inStream = conn.inputStream
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var read: Int
                while (inStream.read(buf).also { read = it } != -1) {
                    ensureActive()
                    out.write(buf, 0, read)
                    if (out.size() > 4 * 1024 * 1024) throw IOException("Analysis response exceeded 4MB limit")
                }
                return out.toString(Charsets.UTF_8.name())
            } finally {
                connection.getAndSet(null)?.disconnect()
            }
        }
    }
}
```

---

### File 2: `CrossfadeController.kt` (Dual-ExoPlayer Fader)
Path: `app/src/main/kotlin/com/lmg/vk/playback/CrossfadeController.kt`
```kotlin
package com.lmg.vk.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * Dual-player crossfade controller.
 * playerA = current active, playerB = next/incoming.
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
```

---

### File 3: `PlaybackService.kt` (Snippet of Dual-Player Wiring)
Path: `app/src/main/kotlin/com/lmg/vk/playback/PlaybackService.kt`
```kotlin
// In PlaybackService.kt:
private var activePlayer: ExoPlayer? = null
private var crossfadeController: CrossfadeController? = null

override fun onCreate() {
    super.onCreate()
    crossfadeController = CrossfadeController(onFinish = ::onCrossfadeFinish)
    val playerA = buildPlayer(effectEngines[0]!!)
    val playerB = buildPlayer(effectEngines[1]!!)
    crossfadeController?.attach(playerA, playerB)
    activePlayer = playerA

    mediaLibrarySession = MediaLibrarySession.Builder(this, playerA, LibrarySessionCallback())
        .setSessionActivity(sessionActivityPi)
        .build()
}

private fun onCrossfadeFinish() {
    crossfadeController?.onReset()
}
```

---

### File 4: `SmartTransitionFinder.kt` (Local Fallback Analysis)
Path: `app/src/main/kotlin/com/lmg/vk/automix/SmartTransitionFinder.kt`
```kotlin
package com.lmg.vk.automix

import kotlin.math.max
import kotlin.math.min

object SmartTransitionFinder {

    data class TransitionPlan(
        val compatibility: Float,
        val transitionStartMs: Long,
        val crossfadeDurationMs: Long,
        val entryOffsetMs: Long,
        val transitionType: Int,
        val bpmA: Float?,
        val bpmB: Float?,
        val keyA: KeyDetector.KeyResult?,
        val keyB: KeyDetector.KeyResult?,
        val debugInfo: String
    )

    fun findTransition(
        energyA: EnergyAnalyzer.TrackEnergy,
        energyB: EnergyAnalyzer.TrackEnergy,
        durationA: Long,
        durationB: Long
    ): TransitionPlan {
        val bpmA = energyA.bpm
        val bpmB = energyB.bpm
        val keyA = energyA.key
        val keyB = energyB.key

        val bpmCompat = BPMDetector.bpmCompatibility(bpmA, bpmB)
        val keyCompat = KeyDetector.keyCompatibility(keyA, keyB)
        val energyCompat = EnergyAnalyzer.energyCompatibility(energyA.avgEnergy, energyB.avgEnergy)

        val outroA = energyA.outroStartMs.takeIf { it > 0 } ?: (durationA - 8000L).coerceAtLeast(0L)
        val introB = energyB.introEndMs.takeIf { it > 0 } ?: 0L

        val duration = 6000L.coerceIn(3000L, 12000L)
        val startMs = (outroA - 2000L).coerceIn(0L, (durationA - duration).coerceAtLeast(0L))

        return TransitionPlan(
            compatibility = (bpmCompat + keyCompat + energyCompat) / 3f,
            transitionStartMs = startMs,
            crossfadeDurationMs = duration,
            entryOffsetMs = introB,
            transitionType = 0,
            bpmA = bpmA,
            bpmB = bpmB,
            keyA = keyA,
            keyB = keyB,
            debugInfo = "start=$startMs dur=$duration entry=$introB"
        )
    }
}
```
