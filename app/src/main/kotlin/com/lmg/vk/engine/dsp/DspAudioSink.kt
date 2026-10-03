package com.lmg.vk.engine.dsp

import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/** Distinguish an actual seek/route reset from DefaultAudioSink's internal
 * processor flush at a drained, same-format gapless boundary. */
@UnstableApi
class DspAudioSink(private val sink: AudioSink, private val dsp: DspAudioProcessor) : ForwardingAudioSink(sink) {
    // Kotlin interface delegation does not forward Java default methods. Media3's
    // adapter forwards both configure entry points and future sink API additions.
    // Encoded passthrough/offload cannot run our PCM effects. Ask the renderer to decode.
    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW) sink.getFormatSupport(format)
        else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        AudioOffloadSupport.DEFAULT_UNSUPPORTED
    override fun setOffloadMode(offloadMode: Int) { sink.setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED) }
    override fun flush() { dsp.markDiscontinuity(); sink.flush() }
    override fun handleDiscontinuity() { dsp.markDiscontinuity(); sink.handleDiscontinuity() }
    override fun reset() { dsp.markDiscontinuity(); sink.reset() }
    override fun release() { sink.release(); dsp.reset() }
}
