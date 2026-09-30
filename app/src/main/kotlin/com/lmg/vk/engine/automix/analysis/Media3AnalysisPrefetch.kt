package com.lmg.vk.engine.automix.analysis

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.engine.automix.AppleSongAnalysisParser
import com.lmg.vk.engine.automix.observation.Media3ObservationPipeline
import com.lmg.vk.engine.automix.observation.MetadataProbeReport
import com.lmg.vk.engine.automix.observation.MusicKitIncomingCriteria
import com.lmg.vk.engine.automix.observation.MusicKitOutgoingCriteria
import com.lmg.vk.engine.automix.observation.MusicKitSourceContext
import com.lmg.vk.engine.automix.observation.MusicKitSourceKnowledge
import com.lmg.vk.engine.automix.observation.ObservationPhase
import com.lmg.vk.engine.automix.observation.ObservationScopeSubmission
import com.lmg.vk.engine.automix.observation.ObservationSide
import com.lmg.vk.engine.automix.observation.ObservationState
import com.lmg.vk.engine.automix.observation.ObservationSubmission
import com.lmg.vk.engine.automix.observation.ObservationTicket
import com.lmg.vk.engine.automix.observation.PlannerObservationScope
import com.lmg.vk.engine.automix.observation.ResolvedPlannerEligibility
import com.lmg.vk.engine.lyrics.apple.AppleLyricsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** No extra Player listener or audio-thread work. Observation already chooses the
 * real successor with Timeline/repeat/shuffle, so indices are never guessed here. */
@UnstableApi
internal class Media3AnalysisPrefetch(
    private val player: Player,
    ownerScope: CoroutineScope,
    state: StateFlow<ObservationState<MetadataProbeReport>>,
    submit: suspend (ObservationTicket, String, ByteArray) -> ObservationSubmission,
    private val bindScope: suspend (ObservationTicket, PlannerObservationScope) -> ObservationScopeSubmission,
    log: (String) -> Unit,
) {
    private val scope = CoroutineScope(ownerScope.coroutineContext + kotlinx.coroutines.SupervisorJob(ownerScope.coroutineContext[Job]))
    private val client = RuAutoMixAnalysisClient(apiKey = { AppleLyricsConfig.API_KEY })
    private val defaultSourceContext = MusicKitSourceContext.create(
        MusicKitOutgoingCriteria.LateInSong,
        MusicKitIncomingCriteria.InSong,
        3,
        ResolvedPlannerEligibility.ALLOWED,
        MusicKitSourceKnowledge.ABSENT,
        MusicKitSourceKnowledge.ABSENT,
        MusicKitSourceKnowledge.ABSENT,
        MusicKitSourceKnowledge.ABSENT,
    )
    private val store = AnalysisPrefetchStore(scope, client::newCall, validate = { response ->
        withContext(Dispatchers.Default) {
            when (val parsed = AppleSongAnalysisParser().parse(response.copyBytes(), response.songId)) {
                is AppleSongAnalysisParser.Result.Parsed -> null
                is AppleSongAnalysisParser.Result.Rejected ->
                    if (parsed.reason == AppleSongAnalysisParser.Reason.NATIVE_UNAVAILABLE) AnalysisFailure.NATIVE_UNAVAILABLE
                    else AnalysisFailure.INVALID_RESPONSE
            }
        }
    })
    private val coordinator = AnalysisPrefetchCoordinator<ObservationTicket>(scope, store,
        submit = { ticket, id, bytes ->
            val answer = submit(ticket, id, bytes)
            answer == ObservationSubmission.ACCEPTED || answer == ObservationSubmission.DUPLICATE
        },
        notify = { notice ->
            // No URLs, IDs, track text, source revision, payload fragments or API key.
            try { log("analysis-prefetch generation=${notice.ticket.generation} side=${notice.ticket.side}" +
                " status=${notice.status} reason=${notice.failure ?: "NONE"}") } catch (_: Exception) { }
        }, checkOwner = ::checkOwner, allowDiscoveredMatch = true,
        fallbackGenerator = { meta ->
            val synthId = LocalAutoMixAnalysisGenerator.deriveNumericId(meta.title, meta.artist, meta.durationMs)
            val synthBytes = LocalAutoMixAnalysisGenerator.generate(
                com.lmg.vk.engine.PlayerController.context,
                meta.title, meta.artist, meta.durationMs, synthId
            )
            RawAnalysis(synthId, synthBytes)
        })
    private val job = scope.launch {
        combine(state, PlayerSettings.autoMix) { snapshot, enabled -> snapshot to enabled }.collect { (snapshot, enabled) ->
            checkOwner()
            val active = enabled && snapshot.phase != ObservationPhase.SUSPENDED &&
                snapshot.phase != ObservationPhase.REJECTED && snapshot.phase != ObservationPhase.CLOSED
            if (active && snapshot.requests.isNotEmpty()) {
                for (ticket in snapshot.requests) {
                    bindScope(ticket, defaultSourceContext)
                }
            }
            // Next track first; fetch current too when not cached (first song/resume/new pair).
            val requests = if (active) snapshot.requests.sortedBy { if (it.side == ObservationSide.INCOMING) 0 else 1 }
                .mapNotNull(::resolve) else emptyList()
            coordinator.update(snapshot.generation, requests, active)
        }
    }
    private fun resolve(ticket: ObservationTicket): PrefetchRequest<ObservationTicket>? {
        checkOwner()
        if (ticket.windowIndex !in 0 until player.mediaItemCount) return null
        val item = player.getMediaItemAt(ticket.windowIndex)
        val local = item.localConfiguration ?: return null
        val revision = item.mediaMetadata.extras?.getString(Media3ObservationPipeline.RECORDING_REVISION_EXTRA)
        if (item.mediaId != ticket.source.mediaId || local.uri.toString() != ticket.source.uri ||
            local.customCacheKey != ticket.source.customCacheKey || revision != ticket.source.recordingRevision) return null
        val verifiedId = item.mediaMetadata.extras?.getString(VERIFIED_APPLE_SONG_ID_EXTRA)
        return try {
            if (!verifiedId.isNullOrBlank()) {
                PrefetchRequest(ticket, AnalysisLookup.ById(verifiedId), verifiedId)
            } else {
                val window = player.currentTimeline.getWindow(ticket.windowIndex, androidx.media3.common.Timeline.Window())
                val title = item.mediaMetadata.title?.toString() ?: return null
                val artist = item.mediaMetadata.artist?.toString() ?: return null
                PrefetchRequest(ticket, AnalysisLookup.ByMetadata(title, artist, window.durationMs), null)
            }
        } catch (_: IllegalArgumentException) { null }
    }
    fun close() {
        checkOwner(); coordinator.close(); job.cancel()
        // Cleanup outside the audio callback; closing the service's parent Job also
        // cancels in-flight HTTP work. Explicitly wipe the memory cache here.
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { store.close() }.invokeOnCompletion { scope.coroutineContext[Job]?.cancel() }
    }
    private fun checkOwner() = check(Looper.myLooper() === player.applicationLooper)
    companion object {
        /** Trusted provider assertion for THIS source/recording. Not set by search,
         * matching titles, ±6-second duration, or numeric mediaId. No ALLOWED policy is inferred. */
        const val VERIFIED_APPLE_SONG_ID_EXTRA = "lmg.automix.verifiedAppleSongId"
    }
}
