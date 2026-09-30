package com.lmg.vk.engine

import android.content.Context
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
    ): DefaultRenderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParameters: Boolean
            ): AudioSink {
                sinkCount.incrementAndGet()
                val state = routing.newSink()
                val dsp = com.lmg.vk.engine.dsp.DspAudioProcessor()
                val originalSink = DefaultAudioSink.Builder(context)
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
                return com.lmg.vk.engine.automix.render.RenderBoundarySinkFactory.wrap(output, renderBoundary, state, false)
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
