package com.lmg.vk.engine.automix

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource

/** Identity of the configured decoded stream, independent of renderer slot or sink order. */
@UnstableApi
data class AutoMixStreamIdentity(
    val mediaId: String,
    val periodUid: Any,
    val windowSequenceNumber: Long,
    val periodPositionInWindowUs: Long,
) {
    /** Preserve microseconds here; conversion to a DSP frame clock is a separate binding. */
    fun mediaItemTimeUs(presentationTimeUs: Long, streamOffsetUs: Long): Long {
        require(presentationTimeUs != C.TIME_UNSET && streamOffsetUs != C.TIME_UNSET)
        return Math.addExact(Math.subtractExact(presentationTimeUs, streamOffsetUs), periodPositionInWindowUs)
    }

    companion object {
        /**
         * media3-lmg 1.5.1-lmg30 has configure(Format, int, int[]), not AudioSinkConfig.
         * Format/buffer/channel mapping cannot identify a period or a repeat instance.
         * A future renderer bridge must supply its own captured Timeline + MediaPeriodId;
         * missing metadata stays unresolved. Never substitute player.currentMediaItem,
         * Format.id, renderer index, or sink callback order. Also usable by newer forks.
         */
        fun from(timeline: Timeline?, mediaPeriodId: MediaSource.MediaPeriodId?): AutoMixStreamIdentity? {
            val id = mediaPeriodId ?: return null
            if (id.isAd || timeline == null) return null
            val index = timeline.getIndexOfPeriod(id.periodUid)
            if (index == C.INDEX_UNSET) return null
            val period = timeline.getPeriod(index, Timeline.Period(), true)
            if (period.positionInWindowUs == C.TIME_UNSET) return null
            val window = timeline.getWindow(period.windowIndex, Timeline.Window())
            val mediaId = window.mediaItem.mediaId.takeIf { it.isNotEmpty() } ?: return null
            return AutoMixStreamIdentity(mediaId, id.periodUid, id.windowSequenceNumber, period.positionInWindowUs)
        }
    }
}
