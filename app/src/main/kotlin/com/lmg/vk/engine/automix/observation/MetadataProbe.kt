package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.util.Collections

/** Explicit scalar projection; absent is not zero. No unit conversion or music math here. */
internal data class MetadataProbeTrack(
    val cloudDurationMs: Double?,
    val timelineDurationMs: Long?,
    val mainBpm: Double?,
    val beginningBpm: Double?,
    val endingBpm: Double?,
    val audioState: Int, // 0 absent, 1 linkage-only, 2 resolved
    val supportState: Int, // 0 unknown, 1 false, 2 true
)

enum class MetadataProbeIssue(val bit: Long) {
    OUT_AUDIO_ABSENT(1L shl 0), IN_AUDIO_ABSENT(1L shl 1),
    OUT_AUDIO_UNRESOLVED(1L shl 2), IN_AUDIO_UNRESOLVED(1L shl 3),
    OUT_SUPPORT_FALSE(1L shl 4), IN_SUPPORT_FALSE(1L shl 5),
    OUT_SUPPORT_UNKNOWN(1L shl 6), IN_SUPPORT_UNKNOWN(1L shl 7),
    OUT_DURATION_UNKNOWN(1L shl 8), IN_DURATION_UNKNOWN(1L shl 9),
    OUT_DURATION_MISMATCH(1L shl 10), IN_DURATION_MISMATCH(1L shl 11),
    MAIN_TEMPO_MISSING(1L shl 12), EDGE_TEMPO_MISSING(1L shl 13),
    INVALID_SCALAR(1L shl 14), REGIONS_REQUIRED(1L shl 15),
}

/** Never executable: no cue, DSP schedule, speed command or inferred style is exposed. */
data class MetadataProbeReport(
    val catalogSha256: String,
    val issues: Set<MetadataProbeIssue>,
    val outgoingDurationConfidence: Int?,
    val incomingDurationConfidence: Int?,
    val mainNormalTempoTag: Int?,
    val mainExpandedTempoTag: Int?,
    val edgeNormalTempoTag: Int?,
    val edgeExpandedTempoTag: Int?,
    val preparation: PlannerPreparationReport? = null,
) {
    val selectedStyleId: Int? get() = null
}

internal object MetadataProbe {
    /** The arrays are an ABI transport, not a second native analysis implementation. */
    fun calculate(outgoing: MetadataProbeTrack, incoming: MetadataProbeTrack, catalogSha256: String): MetadataProbeReport {
        val scalars = DoubleArray(10)
        var mask = 0
        var traits = 0
        for ((side, track) in listOf(outgoing, incoming).withIndex()) {
            val raw = listOf(track.cloudDurationMs, track.timelineDurationMs?.toDouble(),
                track.mainBpm, track.beginningBpm, track.endingBpm)
            raw.forEachIndexed { column, value ->
                if (value != null) {
                    val slot = side * 5 + column
                    scalars[slot] = value
                    mask = mask or (1 shl slot)
                }
            }
            check(track.audioState in 0..2 && track.supportState in 0..2)
            traits = traits or (track.audioState shl (side * 2)) or (track.supportState shl (4 + side * 2))
        }
        val result = try {
            NativeObservationBridge.probePair(scalars, mask, traits)
        } catch (_: LinkageError) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "INVALID_SCALAR_ENCODING")
        } catch (_: IllegalStateException) {
            throw ObservationFailure(ObservationReason.NATIVE_FAILURE)
        }
        return decode(result, catalogSha256)
    }

    internal fun decode(raw: LongArray, catalogSha256: String): MetadataProbeReport {
        val tempoTags = setOf(-1L, 0L, 1L, 2L, 128L, 129L, 130L, 252L)
        if (raw.size != 8 || raw[0] != 1L || raw[1] !in 0L..65535L ||
            raw[1] and MetadataProbeIssue.REGIONS_REQUIRED.bit == 0L ||
            raw[2] !in setOf(-1L, 0L, 2L) || raw[3] !in setOf(-1L, 0L, 2L) ||
            raw.drop(4).any { it !in tempoTags }
        ) throw ObservationFailure(ObservationReason.BRIDGE_CONTRACT_MISMATCH)
        val issues = MetadataProbeIssue.entries.filter { raw[1] and it.bit != 0L }.toSet()
        fun optional(index: Int) = raw[index].takeUnless { it == -1L }?.toInt()
        return MetadataProbeReport(catalogSha256, Collections.unmodifiableSet(issues),
            optional(2), optional(3), optional(4), optional(5), optional(6), optional(7))
    }
}
