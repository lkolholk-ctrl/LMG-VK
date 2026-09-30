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
import androidx.media3.exoplayer.audio.LmgTransitionGainSink
import androidx.media3.exoplayer.audio.LmgLivePlaybackSink
import androidx.media3.exoplayer.LmgLivePlaybackClient
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import java.nio.ByteBuffer

/** Original sink owns playback/gain. Default is pass-through; an explicitly requested
 * non-executable cue probe may temporarily withhold unoffered codec buffers. */
@UnstableApi
class Media3BoundaryAudioSink internal constructor(
    private val delegate: AudioSink,
    private val boundary: RenderBoundaryEndpoint,
    private val processorState: com.lmg.vk.engine.SinkAudioState? = null,
    private val floatOutputEnabled: Boolean = false,
) : AudioSink by delegate, LmgPcmBoundaryListener, LmgTransitionGainSink, LmgLivePlaybackSink {
    private val live = ForkLivePlaybackDriver.forController(boundary.controller)
    private val floatMeter = processorState?.let { com.lmg.vk.engine.PcmBandMeter(it) }
    private var tapBuffer: ByteBuffer? = null
    private var tapPts = Long.MIN_VALUE
    private var tapStart = 0
    private var tapFrames = 0
    private var meterReady = false
    private var meterSampleRate = 0
    private var meterChannels = 0
    private fun delegateWrite(buffer: ByteBuffer, pts: Long, units: Int, mixed: Boolean): Boolean {
        if (mixed) processorState?.mixedOutput = true
        val f = pcm
        val meter = floatMeter
        if (!floatOutputEnabled || !meterReady || meter == null || f?.bytesPerSample != 4)
            return delegate.handleBuffer(buffer, pts, units)
        if (tapBuffer !== buffer || tapPts != pts) {
            tapBuffer = buffer; tapPts = pts; tapStart = buffer.position(); tapFrames = 0
        }
        val result = delegate.handleBuffer(buffer, pts, units)
        // Account completed frames only; partial byte writes retain their prefix.
        val completed = (buffer.position() - tapStart) / f.bytesPerFrame
        if (completed > tapFrames && com.lmg.vk.engine.AudioReactor.hasListeners) {
            // Visualization must not turn an already successful AudioSink write into
            // a retry, which would replay accepted PCM. Never catch the delegate call.
            try { meter.process(buffer, tapStart + tapFrames * f.bytesPerFrame, completed - tapFrames) }
            catch (_: IllegalArgumentException) { meterReady=false }
            catch (_: IndexOutOfBoundsException) { meterReady=false }
        }
        tapFrames = completed
        if (result) tapBuffer = null
        return result
    }
    override fun getLmgLivePlaybackClient(): LmgLivePlaybackClient = live
    // The same existing delegate remains the only output resource. No replacement sink.
    internal val outputPort = SameSinkOutputPort(object : PcmOutputBackend {
        override fun write(buffer: ByteBuffer, ptsUs: Long): Boolean = delegateWrite(buffer, ptsUs, 1, true)
        override fun volume(value: Float) = delegate.setVolume(value)
        override fun positionUs(): Long = delegate.getCurrentPositionUs(false)
        override fun pending(): Boolean = delegate.hasPendingData()
        override fun finish(): Boolean { delegate.playToEndOfStream(); return true }
    })

    init {
        check(LmgTransitionGainSink.protocolVersion() == 1) { "Unsupported output gain protocol" }
        boundary.sameSinkOutputPort = outputPort
    }

    override fun onLmgTransitionGain(transitionGain: Float, playerGain: Float) {
        outputPort.transitionVolume(transitionGain, playerGain)
    }
    override fun onLmgPlayerGain(playerGain: Float) { outputPort.confirmedPlayerVolume(playerGain) }
    override fun setVolume(volume: Float) { outputPort.playerVolume(volume) }

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
            processorState?.bind(streamToken, identity?.source?.windowUid, identity?.source?.mediaId)
            tapBuffer = null
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
        if (floatOutputEnabled && pcm?.supported == true && pcm?.bytesPerSample == 4 &&
            (!meterReady || meterSampleRate != pcm!!.sampleRate || meterChannels != pcm!!.channels)) {
            floatMeter?.configure(pcm!!.sampleRate, pcm!!.channels, 4)
            meterSampleRate = pcm!!.sampleRate; meterChannels = pcm!!.channels; meterReady = true
        }
        outputPort.bindOutput(if (identity != null && pcm?.supported == true && !tunneling && !offloaded) streamToken else null, pcm)
        if(rendererPositionUs==C.TIME_UNSET || rendererOffsetUs==C.TIME_UNSET) {
            boundary.outputContext(streamToken,null,pcm,0)
        } else boundary.outputContext(streamToken,identity,pcm,rendererPositionUs,tunneling||offloaded)
        if(!rateIsUnity || skipsSilence)mark(RenderBoundaryStatus.PLAYBACK_PARAMETERS_UNSUPPORTED)
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        // Configuration may drain or flush internally. Never infer that it cleared a partial buffer.
        if (!live.allowIncomingStartup(boundary)) {
            live.willInvalidate(boundary)
            outputPort.invalidate()
            boundary.cuePort.cancel(CueProbeReason.CONFIGURATION_CHANGED)
        }
        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        boundary.capturePreroll(buffer,presentationTimeUs,encodedAccessUnitCount)
        if (live.discardIncomingPreroll(boundary,buffer,presentationTimeUs)) return true
        return boundary.forwardWithCueGate(buffer,presentationTimeUs,encodedAccessUnitCount) {
            live.checkLegacyWrite(boundary)
            outputPort.checkLegacyWrite()
            delegateWrite(buffer,presentationTimeUs,encodedAccessUnitCount, false)
        }
    }
    override fun isEnded(): Boolean = live.interceptedIsEnded(boundary) ?: delegate.isEnded()

    private fun mark(reason: RenderBoundaryStatus) {
        live.willInvalidate(boundary)
        outputPort.invalidate()
        boundary.cuePort.cancel(CueProbeReason.PLAYBACK_UNSUPPORTED)
        boundary.diagnostic { boundary.fault(reason) }
    }
    private fun resetBoundary(reason: RenderBoundaryStatus = RenderBoundaryStatus.OUTPUT_RESET,
        discard: Boolean = false) {
        live.willInvalidate(boundary)
        outputPort.invalidate()
        boundary.cuePort.cancel(if (reason == RenderBoundaryStatus.ROUTE_CHANGED)
            CueProbeReason.ROUTE_CHANGED else CueProbeReason.OUTPUT_RESET, discard)
        boundary.diagnostic { boundary.reset(reason) }
        token=null;capturedTimeline=null;capturedPeriod=null;identity=null
        processorState?.bind(null, null, null)
        floatMeter?.reset(); tapBuffer = null; meterReady = false
    }
    override fun flush() { resetBoundary(discard=true);delegate.flush();outputPort.afterActualReset() }
    override fun reset() { resetBoundary(discard=true);delegate.reset();outputPort.afterActualReset() }
    override fun release() { resetBoundary(discard=true);delegate.release();outputPort.afterActualReset() }
    override fun pause() {
        boundary.cuePort.cancel(CueProbeReason.PAUSED)
        resetBoundary();delegate.pause()
    }
    override fun handleDiscontinuity() {
        if (live.ignorePrerollDiscontinuity(boundary)) return
        resetBoundary();delegate.handleDiscontinuity()
    }
    override fun playToEndOfStream() {
        if (live.inputEnded(boundary)) return
        resetBoundary();delegate.playToEndOfStream()
    }
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
    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) {
        live.willInvalidate(boundary);outputPort.invalidate();delegate.setAuxEffectInfo(auxEffectInfo)
    }
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        live.willInvalidate(boundary);outputPort.invalidate();delegate.setOutputStreamOffsetUs(outputStreamOffsetUs)
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
        fun wrap(delegate: AudioSink, controller: RenderBoundaryController?,
            processorState: com.lmg.vk.engine.SinkAudioState? = null,
            floatOutputEnabled: Boolean = false): AudioSink {
            if(controller==null)return delegate
            return try {
                check(LmgPcmBoundaryListener.protocolVersion()==1)
                Media3BoundaryAudioSink(delegate,controller.newEndpoint(),processorState,floatOutputEnabled)
            }catch(_: Exception){controller.close();delegate}
            catch(_: LinkageError){controller.close();delegate}
        }
    }
}
