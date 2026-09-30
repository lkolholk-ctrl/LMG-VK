package com.lmg.vk.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** One Sound Check state per sink/playlist occurrence. Native mixed output never
 * receives this second normalization stage. Output storage is prepared at configure. */
@UnstableApi
class VolumeNormalizationProcessor(private val sink: SinkAudioState? = null) : BoundedPcmAudioProcessor() {
    private val kernel=PcmNormalizationKernel()
    private var width=0
    private var channels=0
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        width=when(inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        if(inputAudioFormat.channelCount !in 1..2 || inputAudioFormat.sampleRate !in 8000..192000)
            return AudioProcessor.AudioFormat.NOT_SET
        channels=inputAudioFormat.channelCount
        kernel.configure(inputAudioFormat.sampleRate,channels,width)
        prepareOutputStorage()
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        if(!inputBuffer.hasRemaining())return
        val bytes=boundedBytes(inputBuffer,channels*width)
        val start=inputBuffer.position()
        val output=replaceOutputBuffer(bytes)
        kernel.process(inputBuffer,start,bytes/(channels*width),output,
            PlayerSettings.volumeNormalization.value,
            sink?.streamVersion ?: 0L, nativeOwned=sink?.mixedOutput==true)
        inputBuffer.position(start+bytes)
        output.position(bytes);output.flip()
    }
    override fun onFlush()=kernel.reset()
    override fun onReset()=kernel.reset()
}
