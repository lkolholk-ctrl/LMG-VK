// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.automix

/*
 * TransitionModels.kt — shared planner models for iOS 26 Apple Music AutoMix.
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md §2, §3, §9, §12, §14
 *  - deepseek_analysis/10_final_closure/FINAL_BLOCKED_QUESTIONS.md §1 (#2 tie-breaker closure)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.2/§1.3
 *  - deepseek_analysis/10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md §2.1
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

// ---------------------------------------------------------------------------------------------
// Complexity / Algorithm
// ---------------------------------------------------------------------------------------------

/**
 * `Transition.Complexity` — exactly 4 cases 0..3 (field-witness symbols `0x272a29f0/f4/f8/fc`;
 * `encode` switch 0..3 @`0x27228ecbc`). Rank 4 in `FUN_27228e824` is a sentinel, NOT a case.
 * Provenance: PLC §9.1; RT A1 / RC A1. [EXACT]
 */
enum class Complexity(val rank: Int) {
    FALLBACK(0),
    CROSS_FADE(1),
    CROSS_FADE_WITH_EFFECTS(2),
    TIME_STRETCHED_CROSS_FADE_WITH_EFFECTS(3),
}

/**
 * `Transition.Algorithm` — exactly 4 cases (`description` @`0x27228b36c`). [EXACT]
 * Provenance: PLC §9.2; strings: "Beat-Matched Filtered Cross-Fade", "Smart Cross-Fade",
 * "Dead-Air Removal", "Fallback Cross-Fade".
 */
enum class Algorithm(val description: String) {
    BEAT_MATCHED_FILTERED_CROSS_FADE("Beat-Matched Filtered Cross-Fade"),
    SMART_CROSS_FADE("Smart Cross-Fade"),
    DEAD_AIR_REMOVAL("Dead-Air Removal"),
    FALLBACK_CROSS_FADE("Fallback Cross-Fade"),
}

/**
 * Algorithm -> Complexity mapping (property of the algorithm, not the style id).
 * FallbackCrossFade=0, DeadAirRemoval=1, SmartCrossFade=2, BeatMatchedFilteredCrossFade=3.
 * Provenance: PLC §9.3 (`strb wzr` @`0x272245264`, `0x272244adc`, `mov w8,#0x2` @`0x27224579c`,
 * `mov w8,#0x3` @`0x272242458`). [EXACT]
 */
fun Algorithm.complexity(): Complexity = when (this) {
    Algorithm.FALLBACK_CROSS_FADE -> Complexity.FALLBACK
    Algorithm.DEAD_AIR_REMOVAL -> Complexity.CROSS_FADE
    Algorithm.SMART_CROSS_FADE -> Complexity.CROSS_FADE_WITH_EFFECTS
    Algorithm.BEAT_MATCHED_FILTERED_CROSS_FADE -> Complexity.TIME_STRETCHED_CROSS_FADE_WITH_EFFECTS
}

// ---------------------------------------------------------------------------------------------
// Time primitives
// ---------------------------------------------------------------------------------------------

/** `SongTime { rawValue: Double }`; planner times are STRONG_INFERENCE seconds (PLC §1.2, #2 §1.5). */
data class SongTime(val rawValue: Double)

/** `Range<SongTime>` (mangling `SNyAA0hI0VG`). PLC §3.3 / §14.2. [EXACT] type. */
data class SongTimeRange(val lower: SongTime, val upper: SongTime) {
    val duration: Double get() = upper.rawValue - lower.rawValue
}

/**
 * Generic half-open/closed time range used by placement / summary.
 * Summary layout stores `Range<SongTime>` at +0x10/+0x18 (outgoing) and +0x20/+0x28 (incoming).
 * PLC §14.2. [EXACT]
 */
data class TimeRange<T>(val lower: T, val upper: T)

// ---------------------------------------------------------------------------------------------
// Placement (criteria inputs)
// ---------------------------------------------------------------------------------------------

/**
 * `Criteria.IncomingPlacement` / `Criteria.OutgoingPlacement` case tables.
 * Provenance: PLC §3.1 (fieldmd + encode/from symbols). [EXACT] (types/cases/raw keys).
 *
 * - IncomingPlacement: `early(EarlyPlacementConstraint)` (tag 0)
 * - OutgoingPlacement: `early(EarlyPlacementConstraint)` (tag 0) | `late(LatePlacementConstraint)` (tag 1)
 * - Incoming EarlyPlacementConstraint: `after(SongTime)` | `within(Range<SongTime>)` | `inSong`
 * - Outgoing EarlyPlacementConstraint: `after(SongTime)`
 * - Outgoing LatePlacementConstraint: `after(SongTime)` | `inSong`
 */
sealed interface Placement {
    data class Early(val constraint: EarlyPlacementConstraint) : Placement
    data class Late(val constraint: LatePlacementConstraint) : Placement
}

/** Incoming/outgoing early placement constraint. PLC §3.1. [EXACT] */
sealed interface EarlyPlacementConstraint {
    data class After(val time: SongTime) : EarlyPlacementConstraint
    data class Within(val range: TimeRange<SongTime>) : EarlyPlacementConstraint
    data object InSong : EarlyPlacementConstraint
}

/** Outgoing late placement constraint. PLC §3.1. [EXACT] */
sealed interface LatePlacementConstraint {
    data class After(val time: SongTime) : LatePlacementConstraint
    data object InSong : LatePlacementConstraint
}

/** `StylingPlacementPair { earlyToEarly, lateToEarly }` (2 cases, no payload). PLC §3.1. [EXACT] */
enum class StylingPlacementPair { EARLY_TO_EARLY, LATE_TO_EARLY }

// ---------------------------------------------------------------------------------------------
// Styling regions (candidate halves)
// ---------------------------------------------------------------------------------------------

/**
 * `TransitionPlanner.StylingRegion` enum: `structured(StructuredStylingRegion)` |
 * `unstructured(UnstructuredStylingRegion)`; size 0x52 B, VWT `0x2884ad418`, tag @+0x51.
 * Provenance: FINAL_BLOCKED_QUESTIONS.md §1.1. [EXACT]
 */
sealed interface StylingRegion {
    /** `StructuredStylingRegion { representation: beatRange | barRange }` (0x51 B, VWT `0x2884ad398`). */
    data class Structured(val representation: StructuredRepresentation) : StylingRegion

    /** `UnstructuredStylingRegion { songTimeRange: Range<SongTime> }` (0x10 B, VWT `0x2884ad4a8`). */
    data class Unstructured(val songTimeRange: SongTimeRange) : StylingRegion
}

/** `StylingRegion.Representation` enum: `beatRange(SongBeatRange) | barRange(SongBarRange)`. [EXACT] */
sealed interface StructuredRepresentation {
    data class BeatRange(val range: TimeRange<SongTime>) : StructuredRepresentation
    data class BarRange(val range: TimeRange<SongTime>) : StructuredRepresentation
}

/**
 * `StructuredStylingRegionPair` (0xaa B): outgoing @+0x00 (0x51), incoming @+0x58 (0x51),
 * `tempoRatio` @+0xa9 (1 B). Builder `FUN_272234b0c`; `tempoRatio` scales the incoming region
 * x2/x1/div 2 (`FUN_272234b8c-ba0`, `strb w24,[x19,#0xa9]`). [EXACT] offsets; scale usage [EXACT].
 */
data class StylingRegionPair(
    val outgoing: StylingRegion,
    val incoming: StylingRegion,
    /** `TransitionPlanner.TempoBinaryRatio`: oneToTwo | oneToOne | twoToOne. */
    val tempoRatio: TempoBinaryRatio,
)

/**
 * End time `T_end` of a styling region, used by the score tie-breaker.
 *
 * `param_1 = T_end(outgoing) - T_end(incoming)` (`fsub d0,d0,d8` @`0x27222d628`):
 *  - unstructured: `songTimeRange.upperBound.rawValue` (payload +8);
 *  - structured: computed event end via `FUN_27222bc88` (`stp d8,d0,[x19]`, out[1]).
 * Provenance: FINAL_BLOCKED_QUESTIONS.md §1.2-§1.3 (#2 CLOSED). [EXACT] arithmetic;
 * units (seconds) [STRONG_INFERENCE].
 */
fun StylingRegion.endTime(): Double = when (this) {
    is StylingRegion.Unstructured -> songTimeRange.upper.rawValue
    is StylingRegion.Structured -> when (val r = representation) {
        is StructuredRepresentation.BeatRange -> r.range.upper.rawValue
        is StructuredRepresentation.BarRange -> r.range.upper.rawValue
    }
}

/**
 * Score tie-breaker contribution: `param_1 * 0.001`, literal `0x3f50624dd2f1a9fc` @`0x27222d680`.
 * Only added when `base * product(factors) > 0.0`. [EXACT] (PLC §10.1, §12.1).
 */
fun tieBreakerContribution(outgoing: StylingRegion, incoming: StylingRegion): Double =
    (outgoing.endTime() - incoming.endTime()) * 0.001

// ---------------------------------------------------------------------------------------------
// Tempo primitives
// ---------------------------------------------------------------------------------------------

/**
 * `TransitionPlanner.TempoBinaryScaleFactor` — 3 cases: `half` (0.5), `one` (identity), `two` (2.0).
 * Constants in `FUN_27221a300` (`0x3fe0000000000000` / `0x4000000000000000`); `allCases` @
 * `0x2884aa438 = 00 01 02` (typing STRONG_INFERENCE). Provenance: PLC §5.3. [EXACT] values.
 */
enum class TempoBinaryScaleFactor(val multiplier: Double) {
    HALF(0.5),
    ONE(1.0),
    TWO(2.0),
}

/**
 * `TransitionPlanner.TempoBinaryRatio` — `oneToTwo` | `oneToOne` | `twoToOne` (fieldmd #41).
 * Provenance: PLC §5.3. [EXACT] cases.
 */
enum class TempoBinaryRatio { ONE_TO_TWO, ONE_TO_ONE, TWO_TO_ONE }

/** Tempo comparison outcome, mirroring the packed result of `FUN_272219ff0` (0xfc = incompatible). */
enum class TempoRelationship { COMPATIBLE, INCOMPATIBLE }

// ---------------------------------------------------------------------------------------------
// Candidate
// ---------------------------------------------------------------------------------------------

/**
 * One transition candidate.
 *
 * Apple layout: candidate record 0x100 B with `score` @+0xF8 (Double); Fallback record 0x120 B
 * with `score` @+0x118. Winner selection: `score <= 0` skipped, strictly `>`, ties keep the
 * earlier candidate (`FUN_27222e800`). Provenance: PLC §2.4/§2.5. [EXACT]
 *
 * Factor composition per style id (PLC §10.3):
 *  - id 0x8 : tempo; leading-vocal (0.75); bar-count ratio; trailing-loudness          -> base 10.0
 *  - id 0x9 : tempo; tonality; min-bar-count; vocal relationship; leading-vocal; trailing-loudness -> base 15.0
 *  - id 0xc : (normal ? expanded : 0) x2; min-bar-count; leading-vocal; trailing-loudness -> base 10.0
 *  - DeadAir: base 2.0; Fallback: base 1.0; Scheduling sites: base 3.0
 */
data class TransitionCandidate(
    /** `TransitionStyle.id`: 8 / 9 / 12 (0xc) are the only dispatch branches. [EXACT] */
    val styleId: Int,
    val algorithm: Algorithm,
    val complexity: Complexity,
    /** Region halves (0x51 B each in Apple records; see StylingRegion). */
    val outgoingRegion: StylingRegion,
    val incomingRegion: StylingRegion,
    /** Multiplicative weights written in order: tempo, leading-vocal, bar-count, trailing-loudness. */
    val factors: List<Double>,
    /** Base score: 10.0 | 15.0 | 2.0 | 1.0 | 3.0 (per branch). [EXACT] */
    val base: Double,
    /** `base * product(factors)` when positive, plus `tieBreaker * 0.001`. [EXACT] formula. */
    val tieBreaker: Double,
) {
    /**
     * Scorer `FUN_27222d644` (EXACT):
     * `score = base; for w in weights: score *= w; if score <= 0: return score;`
     * `return param_1 * 0.001 + score`.
     */
    fun score(): Double {
        var s = base
        for (w in factors) s *= w
        if (s <= 0.0) return s
        return tieBreaker * 0.001 + s
    }
}

// ---------------------------------------------------------------------------------------------
// Timing accuracy / musical compatibility / failure
// ---------------------------------------------------------------------------------------------

/**
 * `Transition.TimingAccuracy.SongIssues` — OptionSet, rawValue Int:
 * `0` no issues, `1` `stereoTimeInaccurate`, `2` `spatialTimeInaccurate`.
 * Provenance: PLC §4.5 / §14.4 (`0x272289d20/d28/d30`). [EXACT]
 */
data class TimingAccuracy(val rawValue: Int = 0) {
    val stereoTimeInaccurate: Boolean get() = rawValue and 1 != 0
    val spatialTimeInaccurate: Boolean get() = rawValue and 2 != 0

    companion object {
        val NONE = TimingAccuracy(0)
        val STEREO = TimingAccuracy(1)
        val SPATIAL = TimingAccuracy(2)
    }
}

/**
 * `Transition.MusicalCompatibility` — getters `outgoingSongIssues` @`0x272288494`,
 * `incomingSongIssues` @`0x27228849c`, `songIssues` @`0x2722884a4`; `SongIssues` is
 * `OptionSet { rawValue: Int }`. Type/fields [EXACT]; bit semantics of
 * `MusicalCompatibility.SongIssues` [UNKNOWN] (PLC §14.3).
 */
data class MusicalCompatibility(
    val outgoingSongIssues: SongIssues,
    val incomingSongIssues: SongIssues,
    val songIssues: SongIssues,
)

/**
 * `MusicalCompatibility.SongIssues` OptionSet payload.
 * STATUS: UNKNOWN — needs runtime/device: concrete bit meanings not enumerated (PLC §14.3).
 */
data class SongIssues(val rawValue: Int) {
    fun isCompatible(): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: MusicalCompatibility.SongIssues bit semantics")
}

/**
 * `TransitionPlanner.FailureReason` — exactly 2 cases (constructors `0x27229c7fc/0x27229c800`).
 * Provenance: PLC §14.5. [EXACT]
 */
sealed class FailureReason : Exception() {
    data class MusicalCompatibilityFailure(val compatibility: MusicalCompatibility) : FailureReason()
    data class TimingAccuracyFailure(val accuracy: TimingAccuracy) : FailureReason()
}

// ---------------------------------------------------------------------------------------------
// Transition summary / plan
// ---------------------------------------------------------------------------------------------

/**
 * `Transition.Summary` — layout 0x50 B (`_type_layout_string` @`0x2722a2570`).
 * Assembly `FUN_27226838c` writes (PLC §14.2, [EXACT] offsets; name binding STRONG):
 *  +0x00/+0x08 strategy; +0x10/+0x18 outgoing Range<SongTime>; +0x20/+0x28 incoming;
 *  +0x30/+0x38 musicalCompatibility; +0x40/+0x48 timingAccuracy.
 * Caveat: one branch copies 0x51 bytes while layout is 0x50 (unexplained, RT A6).
 */
data class TransitionSummary(
    val strategy: Strategy,
    val outgoingSongTimeRange: SongTimeRange,
    val incomingSongTimeRange: SongTimeRange,
    val musicalCompatibility: MusicalCompatibility,
    val timingAccuracy: TimingAccuracy,
)

/**
 * `Transition.Strategy` — 16-byte tag+payload value.
 * STATUS: UNKNOWN — needs runtime/device: full field layout not recovered (PLC §14.1 PARTIAL).
 */
class Strategy {
    init {
        TODO("STATUS: UNKNOWN — needs runtime/device: Transition.Strategy layout not fully recovered")
    }
}

/**
 * Complete planner result (clean-room assembled view).
 *
 * Apple: `Transition` layout 0x80 B (`_type_layout_string` @`0x2722a2710`); field set
 * `strategy`, `summary`, `schedule`, `dspGraph` is [PARTIAL] (PLC §14.1).
 * Composition of this class is [PORT DESIGN]; constituent types carry their own status.
 */
data class TransitionPlan(
    val algorithm: Algorithm,
    val complexity: Complexity,
    val styleId: Int,
    val summary: TransitionSummary,
    val schedule: ScheduleView,
    /** `Transition.DSPGraph` identifier, resource name, graph name (see DspGraphSpec). */
    val dspGraphIdentifier: String = "smartTransitionsGraph",
) {
    /** STATUS: UNKNOWN — needs runtime/device: literal call-site of transition(...) not found (MC §3, #9). */
    fun followUp(): TransitionPlan =
        TODO("STATUS: UNKNOWN — needs runtime/device: follow-up semantics (FUN_272269220, noFollowUp)")
}

/** Minimal schedule view; concrete automation model lives in `dsp/Automation.kt`. */
data class ScheduleView(
    val policy: SchedulingPolicy,
)

/** `Transition.SchedulingPolicy`: `.continuous(ContinuousSchedule)` | `.stepped(Double)`. PLC §1.2. [EXACT] */
sealed interface SchedulingPolicy {
    data object Continuous : SchedulingPolicy
    data class Stepped(val stepSeconds: Double) : SchedulingPolicy
}
