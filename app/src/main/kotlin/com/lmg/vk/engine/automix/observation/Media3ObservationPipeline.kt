package com.lmg.vk.engine.automix.observation

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only adapter for the existing AudioService listener. Does not create a
 * player, renderer, sink or audio processor; does not register a second listener.
 * The service forwards onEvents after individual callbacks have settled.
 */
@UnstableApi
class Media3ObservationPipeline(
    private val player: Player,
    private val isActiveBackend: () -> Boolean,
    ownerScope: CoroutineScope,
    openAsset: (String) -> InputStream,
    private val log: (String) -> Unit = { android.util.Log.d("LMG.AutoMix.Observe", it) },
) {
    private val calculator = AppleObservationCalculator(openAsset)
    private val pipeline = ObservationPipeline(ownerScope, calculator::decode, calculator::calculate, ::checkOwner)
    val state: StateFlow<ObservationState<MetadataProbeReport>> = pipeline.state
    private val logJob: Job

    init {
        // AudioService builds its one player and owns this adapter on Main.
        // Reject a different looper explicitly rather than silently accessing it.
        check(player.applicationLooper === Looper.getMainLooper())
        checkOwner()
        logJob = ownerScope.launch {
            state.collect { snapshot ->
                val report = snapshot.result
                val text = buildString {
                    append("observation generation=${snapshot.generation} phase=${snapshot.phase}")
                    append(" reason=${snapshot.reason} pending=${snapshot.requests.size}")
                    snapshot.detail?.let { append(" detail=$it") }
                    if (report != null) {
                        append(" style=none issues=${report.issues.joinToString(",") { it.name }}")
                        append(" mainTags=${report.mainNormalTempoTag}/${report.mainExpandedTempoTag}")
                        append(" edgeTags=${report.edgeNormalTempoTag}/${report.edgeExpandedTempoTag}")
                    }
                }
                // Logging is not allowed to break the playback/service lifecycle.
                try { log(text) } catch (_: Exception) { }
            }
        }
    }

    fun onPlayerEvents(eventPlayer: Player, events: Player.Events) {
        checkOwner()
        if (eventPlayer !== player) return
        val invalidate = events.contains(Player.EVENT_TIMELINE_CHANGED) ||
            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
            events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
            events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
            events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
            events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
            events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
        if (invalidate || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
            events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED) ||
            events.contains(Player.EVENT_PLAYER_ERROR) ||
            events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
        ) refresh(force = invalidate)
    }

    /** Also called after MediaSession backend replacement (which need not emit a Player event). */
    fun refresh(force: Boolean = false) {
        checkOwner()
        if (!isActiveBackend()) { pipeline.update(null, ObservationReason.WRONG_BACKEND, force); return }
        if (player.playerError != null) { pipeline.update(null, ObservationReason.PLAYER_ERROR, force); return }
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            pipeline.update(null, ObservationReason.STOPPED, force); return
        }
        if (!player.playWhenReady || player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
            pipeline.update(null, ObservationReason.PAUSED, force); return
        }
        if (player.isPlayingAd) { pipeline.update(null, ObservationReason.AD_UNSUPPORTED, force); return }
        val timeline = player.currentTimeline
        val currentIndex = player.currentMediaItemIndex
        if (timeline.isEmpty || currentIndex !in 0 until timeline.windowCount) {
            pipeline.update(null, ObservationReason.NO_PAIR, force); return
        }
        // Do not use currentIndex+1 or Player.nextMediaItemIndex: navigation may
        // ignore REPEAT_MODE_ONE. We need the actual automatic successor.
        val nextIndex = timeline.getNextWindowIndex(currentIndex, player.repeatMode, player.shuffleModeEnabled)
        if (nextIndex == C.INDEX_UNSET) { pipeline.update(null, ObservationReason.NO_PAIR, force); return }
        val outgoingWindow = timeline.getWindow(currentIndex, Timeline.Window())
        val incomingWindow = timeline.getWindow(nextIndex, Timeline.Window())
        if (outgoingWindow.isDynamic || incomingWindow.isDynamic || outgoingWindow.isLive || incomingWindow.isLive) {
            pipeline.update(null, ObservationReason.UNSUPPORTED_TIMELINE, force); return
        }
        val outgoing = snapshotTrack(outgoingWindow, currentIndex)
        val incoming = snapshotTrack(incomingWindow, nextIndex)
        if (outgoing == null || incoming == null) {
            pipeline.update(null, ObservationReason.UNSUPPORTED_SOURCE, force); return
        }
        pipeline.update(ObservationPair(outgoing, incoming, player.repeatMode, player.shuffleModeEnabled), force = force)
    }

    /**
     * Entry point for a response already fetched by the app's authorized provider.
     * Use the original state.requests ticket, not a reconstructed mediaId/index.
     * requestedSongId must describe this exact source/recording, not a title match.
     * No URL construction, token, HTTP request or recording-match inference here.
     */
    suspend fun submitAnalysis(ticket: ObservationTicket, requestedSongId: String, response: ByteArray): ObservationSubmission {
        if (response.size > ObservationPipeline.MAX_RESPONSE_BYTES) return ObservationSubmission.INPUT_TOO_LARGE
        val rejected = withContext(Dispatchers.Main.immediate) {
            pipeline.submissionRejection(ticket, requestedSongId, response.size)
        }
        if (rejected != null) return rejected
        // Defensive ownership off Main. Providers must not concurrently modify
        // their input while this suspend call is copying it.
        val privateBytes = withContext(Dispatchers.Default) { response.copyOf() }
        return withContext(Dispatchers.Main.immediate) { pipeline.submitOwned(ticket, requestedSongId, privateBytes) }
    }

    fun close() {
        checkOwner()
        pipeline.close()
        logJob.cancel()
    }

    private fun snapshotTrack(window: Timeline.Window, index: Int): ObservationTrack? {
        val item = window.mediaItem
        val local = item.localConfiguration ?: return null
        val clipping = item.clippingConfiguration
        // No guessed mapping from clipped/default-relative/live media to catalog time.
        if (clipping.startPositionMs != 0L || clipping.endPositionMs != C.TIME_END_OF_SOURCE ||
            clipping.relativeToDefaultPosition || clipping.relativeToLiveWindow
        ) return null
        if (item.mediaId.isBlank()) return null
        val uri = local.uri.toString().takeIf { it.isNotBlank() } ?: return null
        val source = ObservationSource(item.mediaId, uri, local.customCacheKey,
            item.mediaMetadata.extras?.getString(RECORDING_REVISION_EXTRA))
        val duration = window.durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        return ObservationTrack(window.uid, index, source, duration)
    }

    private fun checkOwner() { check(Looper.myLooper() === player.applicationLooper) }

    companion object {
        /** Optional provider-owned content revision. Never derive it from a track title. */
        const val RECORDING_REVISION_EXTRA = "lmg.automix.recordingRevision"
    }
}
