package com.lmg.vk.engine.automix.render

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink

@UnstableApi
object RenderBoundarySinkFactory {
    fun wrap(sink: AudioSink, controller: RenderBoundaryController?,
        processorState: com.lmg.vk.engine.SinkAudioState? = null,
        floatOutputEnabled: Boolean = false): AudioSink {
        if(controller==null)return sink
        return try { Media3BoundaryAudioSink.wrap(sink,controller,processorState,floatOutputEnabled) }
        catch(_: LinkageError){controller.close();sink}
        catch(_: Exception){controller.close();sink}
    }
}
