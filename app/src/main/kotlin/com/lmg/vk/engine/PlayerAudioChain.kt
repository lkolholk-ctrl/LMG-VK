package com.lmg.vk.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import com.lmg.vk.debug.DebugLog
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Per-sink processor state for the two internal renderers of ONE service ExoPlayer.
 * Bass metering -> per-source DJ FX -> Sound Check -> user DSP. No role follows creation order. */
@UnstableApi
object PlayerAudioChain {

    /** Сколько раз собирался аудио-синк за жизнь процесса (см. лог в buildAudioSink). */
    private val sinkCount = java.util.concurrent.atomic.AtomicInteger(0)

    fun renderersFactory(
        context: Context,
        renderBoundary: com.lmg.vk.engine.automix.render.RenderBoundaryController? = null,
        routing: SinkAudioRouting = SinkAudioRouting(),
        diagnostics: com.lmg.vk.debug.PlaybackAudioDiagnostics? = null,
    ): DefaultRenderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParameters: Boolean
            ): AudioSink {
                val fallbackSinkId = sinkCount.getAndIncrement()
                val state = routing.newSink()
                val probe = diagnostics?.newProbe()
                val sinkId = probe?.id ?: fallbackSinkId
                val dsp = com.lmg.vk.engine.dsp.DspAudioProcessor(probe)
                val retainOutputs = Build.MANUFACTURER.equals("HONOR", ignoreCase = true)
                val provider = RetainingAudioOutputProvider(
                    AudioTrackAudioOutputProvider.Builder(context).build(),
                    enabled = retainOutputs,
                    onCheckout = { (it as? AudioTrackAudioOutput)?.audioTrack?.let { track -> probe?.attached(track) } },
                    trace = { DebugLog.add("AUDIO_OUTPUT t=${SystemClock.elapsedRealtime()} sink=$sinkId $it") },
                )
                DebugLog.add("AUDIO_OUTPUT sink=$sinkId retention=$retainOutputs media3=1.11.1-lmg32")
                val originalSink = DefaultAudioSink.Builder(context)
                    .setAudioOutputProvider(provider)
                    // Media3's float-output shortcut bypasses custom AudioProcessors.
                    // The DSP itself uses float32; this sink deliberately uses the PCM chain.
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(
                        arrayOf(
                            BassAudioProcessor(state),
                            DjFxAudioProcessor(state),
                            VolumeNormalizationProcessor(state),
                            dsp
                        )
                    )
                    .build()
                val output = com.lmg.vk.engine.dsp.DspAudioSink(originalSink, dsp)
                return com.lmg.vk.engine.automix.render.RenderBoundarySinkFactory.wrap(
                    probe?.wrap(output) ?: output, renderBoundary, state, false)
            }

            // Видео-рендерер НУЖЕН: видеоклипы Apple Music играют этим же
            // сервисным ExoPlayer (mp4 → SurfaceView в FullPlayer). Раньше метод
            // был намеренно пустым («audio-only — создание видео-рендереров на
            // холодном старте цепляло startup ANR») — из-за этого клип играл
            // только звуком, а Surface оставался чёрным. Конструктор
            // MediaCodecVideoRenderer лёгкий: сам кодек инициализируется только
            // когда в потоке реально есть видео-трек, на чистом аудио оверхеда
            // нет. Если startup ANR вдруг вернётся — профилировать причину, а
            // не снова выпиливать рендерер (это молча ломает клипы).
        }
}
