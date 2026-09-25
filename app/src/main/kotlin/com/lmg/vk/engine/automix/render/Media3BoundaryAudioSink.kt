package com.lmg.vk.engine.automix.render

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.LmgPcmBoundaryListener
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import java.nio.ByteBuffer

/** Original sink owns playback/gain. Default is pass-through; an explicitly requested
 * non-executable cue probe may temporarily withhold unoffered codec buffers. */
@UnstableApi
class Media3BoundaryAudioSink internal constructor(
    private val delegate: AudioSink,
    private val boundary: RenderBoundaryEndpoint,
) : AudioSink by delegate, LmgPcmBoundaryListener {
    private var token: Any? = null
    private var capturedTimeline: Timeline? = null
    private var capturedPeriod: MediaPeriodId? = null
    private var capturedOffset = Long.MIN_VALUE
    private var identity: RenderOutputIdentity? = null
    private var lastFormat: Format? = null
    private var lastIdentityMapping = true
    private var pcm: RenderPcmFormat? = null
    private var offloaded = false
    private var rateIsUnity = true
    private var skipsSilence = false

    override fun onLmgPcmOutputBoundary(
        streamToken: Any, timeline: Timeline, mediaPeriodId: MediaPeriodId?,
        format: Format?, rendererOffsetUs: Long, rendererPositionUs: Long,
        identityChannelMapping: Boolean, tunneling: Boolean,
    ) {
        // This timeline/id belong to the OUTPUT queue entry. Do not use Player.currentMediaItem
        // or MediaCodecRenderer's current INPUT period: old codec output may still be draining.
        if (token !== streamToken || capturedTimeline !== timeline || capturedPeriod != mediaPeriodId ||
            capturedOffset != rendererOffsetUs) {
            token=streamToken;capturedTimeline=timeline;capturedPeriod=mediaPeriodId
            capturedOffset=rendererOffsetUs
            identity=resolve(timeline,mediaPeriodId,rendererOffsetUs)
        }
        if (lastFormat != format || lastIdentityMapping != identityChannelMapping) {
            lastFormat=format;lastIdentityMapping=identityChannelMapping
            pcm=format?.let {
                val width=if(it.sampleMimeType != MimeTypes.AUDIO_RAW)0 else when(it.pcmEncoding) {
                    C.ENCODING_PCM_16BIT -> 2
                    C.ENCODING_PCM_FLOAT -> 4
                    else -> 0
                }
                RenderPcmFormat(it.sampleRate,it.channelCount,width,identityChannelMapping,
                    it.encoderDelay,it.encoderPadding)
            }
        }
        if(rendererPositionUs==C.TIME_UNSET || rendererOffsetUs==C.TIME_UNSET) {
            boundary.outputContext(streamToken,null,pcm,0)
        } else boundary.outputContext(streamToken,identity,pcm,rendererPositionUs,tunneling||offloaded)
        if(!rateIsUnity || skipsSilence)mark(RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED)
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        // Configuration may drain or flush internally. Never infer that it cleared a partial buffer.
        boundary.cuePort.cancel(CueProbeReason.CONFIGURATION_CHANGED)
        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean =
        boundary.forwardWithCueGate(buffer,presentationTimeUs,encodedAccessUnitCount) {
            delegate.handleBuffer(buffer,presentationTimeUs,encodedAccessUnitCount)
        }

    private fun mark(reason: RenderBoundaryStatus) {
        boundary.cuePort.cancel(CueProbeReason.PLAYBACK_UNSUPPORTED)
        boundary.diagnostic { boundary.fault(reason) }
    }
    private fun resetBoundary(reason: RenderBoundaryStatus = RenderBoundaryStatus.OUTPUT_RESET,
        discard: Boolean = false) {
        boundary.cuePort.cancel(if (reason == RenderBoundaryStatus.ROUTE_CHANGED)
            CueProbeReason.ROUTE_CHANGED else CueProbeReason.OUTPUT_RESET, discard)
        boundary.diagnostic { boundary.reset(reason) }
        token=null;capturedTimeline=null;capturedPeriod=null;identity=null
    }
    override fun flush() { resetBoundary(discard=true);delegate.flush() }
    override fun reset() { resetBoundary(discard=true);delegate.reset() }
    override fun release() { resetBoundary(discard=true);delegate.release() }
    override fun pause() {
        boundary.cuePort.cancel(CueProbeReason.PAUSED)
        resetBoundary();delegate.pause()
    }
    override fun handleDiscontinuity() { resetBoundary();delegate.handleDiscontinuity() }
    override fun playToEndOfStream() { resetBoundary();delegate.playToEndOfStream() }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        rateIsUnity=playbackParameters.speed==1f && playbackParameters.pitch==1f
        if(!rateIsUnity)
            mark(RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED)
        delegate.setPlaybackParameters(playbackParameters)
    }
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        skipsSilence=skipSilenceEnabled
        if(skipSilenceEnabled)mark(RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED)
        delegate.setSkipSilenceEnabled(skipSilenceEnabled)
    }
    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        resetBoundary(RenderBoundaryStatus.ROUTE_CHANGED);delegate.setAudioAttributes(audioAttributes)
    }
    override fun setAudioSessionId(audioSessionId: Int) {
        resetBoundary(RenderBoundaryStatus.ROUTE_CHANGED);delegate.setAudioSessionId(audioSessionId)
    }
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        resetBoundary(RenderBoundaryStatus.ROUTE_CHANGED);delegate.setPreferredDevice(audioDeviceInfo)
    }
    override fun enableTunnelingV21() {
        resetBoundary(RenderBoundaryStatus.ROUTE_CHANGED);delegate.enableTunnelingV21()
    }
    override fun disableTunneling() {
        resetBoundary(RenderBoundaryStatus.ROUTE_CHANGED);delegate.disableTunneling()
    }
    override fun setOffloadMode(offloadMode: Int) {
        offloaded=offloadMode!=AudioSink.OFFLOAD_MODE_DISABLED
        if(offloaded)mark(RenderBoundaryStatus.UNSUPPORTED_FORMAT)
        delegate.setOffloadMode(offloadMode)
    }

    private fun resolve(timeline: Timeline, id: MediaPeriodId?, offset: Long): RenderOutputIdentity? {
        if(id==null || id.isAd || timeline.isEmpty || offset==C.TIME_UNSET)return null
        val index=timeline.getIndexOfPeriod(id.periodUid)
        if(index==C.INDEX_UNSET)return null
        val period=timeline.getPeriod(index,Timeline.Period())
        if(period.windowIndex !in 0 until timeline.windowCount)return null
        val window=timeline.getWindow(period.windowIndex,Timeline.Window())
        // Initial support is one unclipped finite period per occurrence. Multi-period HLS,
        // ads/live and unbound period offsets cannot be guessed from media IDs.
        if(window.isLive || window.isDynamic || window.firstPeriodIndex!=window.lastPeriodIndex ||
            period.positionInWindowUs!=0L)return null
        val item=window.mediaItem
        val local=item.localConfiguration ?: return null
        val clipping=item.clippingConfiguration
        if(clipping.startPositionMs!=0L || clipping.endPositionMs!=C.TIME_END_OF_SOURCE ||
            clipping.relativeToDefaultPosition || clipping.relativeToLiveWindow || item.mediaId.isBlank())return null
        val uri=local.uri.toString()
        if(uri.isBlank())return null
        val key=RenderSourceKey(window.uid,item.mediaId,uri,local.customCacheKey,
            item.mediaMetadata.extras?.getString("lmg.automix.recordingRevision"))
        return RenderOutputIdentity(key,id.periodUid,id.windowSequenceNumber,period.positionInWindowUs,offset)
    }

    companion object {
        fun wrap(delegate: AudioSink, controller: RenderBoundaryController?): AudioSink {
            if(controller==null)return delegate
            return try {
                check(LmgPcmBoundaryListener.protocolVersion()==1)
                Media3BoundaryAudioSink(delegate,controller.newEndpoint())
            }catch(_: Exception){controller.close();delegate}
            catch(_: LinkageError){controller.close();delegate}
        }
    }
}
