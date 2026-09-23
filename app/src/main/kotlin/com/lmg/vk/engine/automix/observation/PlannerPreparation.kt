package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.util.Collections

/** These describe preparation/inspection, never transition eligibility. */
enum class PlannerPreparationStatus { INSUFFICIENT_ANALYSIS, PREPARED, INVALID_ANALYSIS, RESOURCE_LIMIT }
enum class PlannerPreparationIssue(val bit: Long) {
    DURATION_MISSING(1L shl 0), AUDIO_ABSENT(1L shl 1), AUDIO_UNRESOLVED(1L shl 2),
    FLEX_ABSENT(1L shl 3), FLEX_UNRESOLVED(1L shl 4), VIDEO_EVENTS_MISSING(1L shl 5),
    EVENT_ARRAYS_MISSING(1L shl 6), EMPTY_STRUCTURE(1L shl 7), NO_STABLE_REGIONS(1L shl 8),
    MALFORMED_REGIONS(1L shl 9), INVALID_ANALYSIS(1L shl 10), RESOURCE_LIMIT(1L shl 11),
    REVERSED_EVENTS(1L shl 12), DUPLICATE_EVENTS(1L shl 13), OUTSIDE_TRACK(1L shl 14),
}
enum class PreparedAnalysisAvailability { ABSENT, LINKAGE_ONLY, RESOLVED }
/** Native enum tags, NOT MIDI pitch classes. No Kotlin key or harmony mapping. */
data class PreparedTonality(val tonicTag: Int, val modeTag: Int)
data class PreparedTonalityMap(val main: PreparedTonality, val beginning: PreparedTonality?, val ending: PreparedTonality?)
/** Original stable-map range; not a chosen transition, cue or execution command. */
data class StableRegionPreview(
    val sourceMapIndex: Int, val startEvent: Int, val endEvent: Int,
    val startSeconds: Double, val endSeconds: Double, val beatsPerBar: Int,
    val averageTempoBpm: Double, val insideResolvedTrack: Boolean?,
)
data class PreparedStructureSummary(
    val events: Int, val downbeats: Int, val bars: Int, val segments: Int, val sections: Int,
    val stableRegions: Int, val usableStableRegions: Int, val boundedStableRegions: Int,
    val malformedRegions: Int, val outsideTrackRegions: Int,
    val duplicateTimes: Int, val reversedTimes: Int,
    val stablePreview: List<StableRegionPreview>, val previewTruncated: Boolean,
)
data class PreparedPlannerTrack(
    val status: PlannerPreparationStatus,
    val issues: Set<PlannerPreparationIssue>,
    val audioAvailability: PreparedAnalysisAvailability,
    val flexAvailability: PreparedAnalysisAvailability,
    val supportsSmartTransitions: Boolean?, val resolvedDurationMs: Long?,
    val tonality: PreparedTonalityMap?, val loudnessPoints: Int?, val vocalIntervals: Int?,
    val flexEvents: Int?, val structure: PreparedStructureSummary?,
)
data class PlannerPreparationReport(
    val outgoing: PreparedPlannerTrack, val incoming: PreparedPlannerTrack,
    val outgoingResponseSha256: String, val incomingResponseSha256: String,
) {
    // Explicitly impossible to turn this diagnostic into a playable plan.
    val canExecute: Boolean get() = false
    val selectedStyleId: Int? get() = null
    val selectionBlock: String get() = "REGION_PAIR_RULES_UNVERIFIED"
}

internal object PlannerPreparation {
    const val MAX_WORDS = 328
    const val MAX_EVENTS = 4096
    const val MAX_PREVIEW = 16
    const val TRACK_WORDS = 32
    const val REGION_WORDS = 8
    private val digest = Regex("[0-9a-f]{64}")
    private fun bad(): Nothing = throw ObservationFailure(ObservationReason.BRIDGE_CONTRACT_MISMATCH)
    private fun wire(value: Boolean) { if (!value) bad() }

    fun calculate(outgoing: ByteArray, outgoingId: String, incoming: ByteArray, incomingId: String,
                  outgoingDurationMs: Long?, incomingDurationMs: Long?,
                  outgoingSha256: String, incomingSha256: String): PlannerPreparationReport {
        val mask = (if (outgoingDurationMs != null) 1 else 0) or (if (incomingDurationMs != null) 2 else 0)
        val raw = try {
            NativeObservationBridge.preparePair(outgoing, outgoingId.encodeToByteArray(),
                incoming, incomingId.encodeToByteArray(),
                longArrayOf(outgoingDurationMs ?: 0L, incomingDurationMs ?: 0L), mask)
        } catch (_: LinkageError) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "PREPARATION_INPUT_REJECTED")
        } catch (_: IllegalStateException) {
            throw ObservationFailure(ObservationReason.NATIVE_FAILURE)
        }
        return decode(raw, outgoingSha256, incomingSha256)
    }

    /** Versioned, bounded transport validation only. All music math stays native. */
    internal fun decode(raw: LongArray, outgoingSha256: String, incomingSha256: String): PlannerPreparationReport {
        wire(digest.matches(outgoingSha256) && digest.matches(incomingSha256))
        wire(raw.size in 72..MAX_WORDS)
        wire(raw[0] == 1L && raw[1] == raw.size.toLong() && raw[2] == 2L &&
            raw[3] == TRACK_WORDS.toLong() && raw[4] == REGION_WORDS.toLong() &&
            raw[5] == MAX_PREVIEW.toLong() && raw[6] == 0L && raw[7] == -1L)
        var cursor = 8
        fun track(): PreparedPlannerTrack {
            wire(cursor + TRACK_WORDS <= raw.size)
            val h = raw.copyOfRange(cursor, cursor + TRACK_WORDS)
            cursor += TRACK_WORDS
            fun count(slot: Int, maximum: Int = MAX_EVENTS): Int {
                wire(h[slot] in 0L..maximum.toLong()); return h[slot].toInt()
            }
            wire(h[0] in 0L..3L && h[1] in 0L..32767L && h[5] in 0L..255L)
            wire(h[2] in 0L..2L && h[3] in 0L..2L && h[4] in 0L..2L)
            wire(h[30] == 0L && h[31] == 0L)
            val mask = h[5].toInt()
            fun has(bit: Int) = mask and bit != 0
            wire(has(16) == has(32))
            wire((!has(64) && !has(128)) || has(2))
            if (has(1)) wire(h[6] in 1L..9007199254740991L) else wire(h[6] == 0L)
            val duration = if (has(1)) h[6] else null
            val loudness = count(7, 16384)
            val vocals = count(8)
            val flex = count(9)
            if (!has(4)) wire(loudness == 0)
            if (!has(8)) wire(vocals == 0)
            if (!has(16)) wire(flex == 0)
            val events = count(10); val downbeats = count(11)
            val bars = count(12); val segments = count(13); val sections = count(14)
            val stable = count(15); val usable = count(16); val bounded = count(17)
            val malformed = count(18, 4 * MAX_EVENTS); val outside = count(19, 4 * MAX_EVENTS)
            val duplicates = count(20); val reversed = count(21)
            wire(events == flex && downbeats <= events && bars == (downbeats - 1).coerceAtLeast(0))
            wire(sections <= segments && segments <= bars && stable <= bars / 5)
            wire(bounded <= usable && usable <= stable)
            wire(malformed <= bars + segments + sections + stable && outside <= bars + segments + sections + stable)
            wire(duplicates + reversed <= (events - 1).coerceAtLeast(0))
            if (!has(1)) wire(bounded == 0 && outside == 0)
            if (!has(32)) wire((10..21).all { h[it] == 0L })
            fun tonality(slot: Int, present: Boolean): PreparedTonality? {
                if (!present) { wire(h[slot] == 0L && h[slot + 1] == 0L); return null }
                wire(h[slot] in 0L..11L && h[slot + 1] in 0L..2L)
                return PreparedTonality(h[slot].toInt(), h[slot + 1].toInt())
            }
            val main = tonality(22, has(2)); val beginning = tonality(24, has(64)); val ending = tonality(26, has(128))
            val previewCount = count(28, MAX_PREVIEW)
            wire(h[29] in 0L..1L && previewCount == minOf(usable, MAX_PREVIEW))
            wire((h[29] == 1L) == (usable > previewCount))
            wire(cursor + previewCount * REGION_WORDS <= raw.size)
            var previousMapIndex = -1
            val preview = ArrayList<StableRegionPreview>(previewCount)
            repeat(previewCount) {
                val p = raw.copyOfRange(cursor, cursor + REGION_WORDS); cursor += REGION_WORDS
                wire(p[0] >= 0 && p[0] < stable && p[0] > previousMapIndex)
                wire(p[1] >= 0 && p[1] < p[2] && p[2] < events)
                val start = Double.fromBits(p[3]); val end = Double.fromBits(p[4]); val bpm = Double.fromBits(p[6])
                wire(start.isFinite() && end.isFinite() && start >= 0 && end > start)
                wire(p[5] in 1L..MAX_EVENTS.toLong() && bpm.isFinite() && bpm > 0)
                wire(p[7] in 0L..2L)
                val bounds = when (p[7]) { 0L -> null; 1L -> true; else -> false }
                if (duration == null) wire(bounds == null)
                else wire(bounds == (end <= duration.toDouble() / 1000.0))
                previousMapIndex = p[0].toInt()
                preview.add(StableRegionPreview(p[0].toInt(), p[1].toInt(), p[2].toInt(),
                    start, end, p[5].toInt(), bpm, bounds))
            }
            val issues = PlannerPreparationIssue.entries.filter { h[1] and it.bit != 0L }.toSet()
            val status = PlannerPreparationStatus.entries[h[0].toInt()]
            wire((status == PlannerPreparationStatus.PREPARED) == (bounded > 0))
            wire((PlannerPreparationIssue.DUPLICATE_EVENTS in issues) == (duplicates > 0))
            wire((PlannerPreparationIssue.REVERSED_EVENTS in issues) == (reversed > 0))
            wire((PlannerPreparationIssue.MALFORMED_REGIONS in issues) == (malformed > 0))
            wire((PlannerPreparationIssue.OUTSIDE_TRACK in issues) == (outside > 0))
            if (status == PlannerPreparationStatus.INVALID_ANALYSIS) wire(PlannerPreparationIssue.INVALID_ANALYSIS in issues)
            if (status == PlannerPreparationStatus.RESOURCE_LIMIT) wire(PlannerPreparationIssue.RESOURCE_LIMIT in issues)
            if (status == PlannerPreparationStatus.INVALID_ANALYSIS || status == PlannerPreparationStatus.RESOURCE_LIMIT) {
                wire(mask and 254 == 0) // No partially prepared maps may escape failure.
            }
            return PreparedPlannerTrack(status, Collections.unmodifiableSet(issues),
                PreparedAnalysisAvailability.entries[h[2].toInt()], PreparedAnalysisAvailability.entries[h[3].toInt()],
                when (h[4]) { 0L -> null; 1L -> false; else -> true }, duration,
                main?.let { PreparedTonalityMap(it, beginning, ending) },
                if (has(4)) loudness else null, if (has(8)) vocals else null, if (has(16)) flex else null,
                if (has(32)) PreparedStructureSummary(events, downbeats, bars, segments, sections,
                    stable, usable, bounded, malformed, outside, duplicates, reversed,
                    Collections.unmodifiableList(preview), h[29] == 1L) else null)
        }
        val outgoing = track(); val incoming = track()
        wire(cursor == raw.size)
        return PlannerPreparationReport(outgoing, incoming, outgoingSha256, incomingSha256)
    }
}
