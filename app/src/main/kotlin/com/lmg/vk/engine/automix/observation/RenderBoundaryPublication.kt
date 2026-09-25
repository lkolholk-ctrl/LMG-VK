package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.render.RenderBoundaryController
import com.lmg.vk.engine.automix.render.RenderBoundaryPlan
import com.lmg.vk.engine.automix.render.RenderSourceKey

/** Called synchronously on the observation owner BEFORE StateFlow publication. No Player calls. */
internal fun publishRenderBoundary(controller: RenderBoundaryController,
    state: ObservationState<MetadataProbeReport>, pair: ObservationPair?, revision: Long) {
    try {
        if(state.phase==ObservationPhase.CLOSED){controller.close();return}
        val selection=state.result?.selection
        val schedule=selection?.schedule
        if(state.phase!=ObservationPhase.OBSERVED || pair==null ||
            selection?.scheduleStatus!=PlannerScheduleStatus.COMPILED || schedule==null ||
            schedule.generation!=state.generation || schedule.bindingRevision!=revision) {
            controller.invalidate(state.generation,revision);return
        }
        fun source(track:ObservationTrack)=RenderSourceKey(track.windowUid,track.source.mediaId,
            track.source.uri,track.source.customCacheKey,track.source.recordingRevision)
        controller.offer(RenderBoundaryPlan.create(state.generation,revision,source(pair.outgoing),
            source(pair.incoming),schedule.outgoing.startPlaybackSongTimeSeconds,
            schedule.incoming.startPlaybackSongTimeSeconds,schedule.styleId,encodeLiveSchedule(schedule)))
    }catch(_: Exception){controller.close()}
}
