// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.analysis

import apple.music.contracts.automix.Placement

/*
 * SongAnalysis.kt — planner-facing analysis model for iOS 26 Apple Music AutoMix.
 *
 * PROVENANCE (research only; no Apple source code copied):
 *  - deepseek_analysis/10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md  (field -> consumer chain)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.1 (SongAnalysis contract)
 *  - deepseek_analysis/10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md §1, §4, §5, §8
 *  - deepseek_analysis/10_final_closure/FINAL_BLOCKED_QUESTIONS.md §3, §4 (#13, #15)
 *  - deepseek_analysis/09_appos/APPOS_MASTER_SUMMARY.md §5
 *
 * STATUS LEGEND (copied from the research status system; see README.md):
 *   [EXACT]              directly confirmed by a raw artifact (address / bytes / symbol / resource)
 *   [STRONG_INFERENCE]   follows from several independent confirmed artifacts
 *   [PARTIAL]            part of the chain confirmed, part not
 *   [UNKNOWN]            no data; MUST stay TODO("STATUS: UNKNOWN — needs runtime/device: ...")
 *   [UNUSED BY PLANNER]  verified consumer-negative (field exists in MediaAPI, planner never reads it)
 *
 * HARD RULE: unknown values are never invented. They are marked TODO with the exact reason.
 */

// ---------------------------------------------------------------------------------------------
// Top-level planner input (clean-room view)
// ---------------------------------------------------------------------------------------------

/**
 * Planner-facing view of one song.
 *
 * Apple analogue: `TransitionPlanner.Song { id, duration, analysis, context }` and
 * `Song.Analysis = .musicKit(MusicKitAnalysis) | .adaptiveMusic(AdaptiveMusicAnalysis)`.
 * Provenance: TRANSITION_PLANNER_CLEANROOM_SPEC.md §1.2 (Ma `0x272261280`, `0x27225d7cc`).
 * Status: [EXACT] (type metadata); field-by-field statuses below.
 */
data class SongAnalysis(
    /** `Song.ID.rawValue` (String). Source: PLC §1.2, MC §2.B (`Song.ID.Ma` @`0x27226722c`). [EXACT] */
    val id: String,
    /**
     * `Song.duration` / `MusicKitAnalysis.duration` (seconds).
     * Source: PLC §1.2; upstream `durationInMillis` (M2P §1) => unit conversion STRONG.
     * [STRONG_INFERENCE]
     */
    val durationSeconds: Double?,
    /** Source switch: `.musicKit(...)` vs `.adaptiveMusic(...)`. PLC §1.2 / M2P §2. [EXACT] */
    val source: AnalysisSource,
    /**
     * `MusicKitAnalysis.genres: [Genre]`; genre comparison uses `GenreTree/GenreFilter`
     * (`FUN_27223f618` / `FUN_27223edec` / `FUN_272240e68`), algorithm STRONG by logs.
     * ID->name table is absent from binaries/DSC (see [GenreCatalog]).
     */
    val genres: List<Genre>,
    /** `MusicKitAnalysis.audioAnalysis`. Field offset +0x18. PLC §1.2 / M2P §2.3. [EXACT] */
    val audioAnalysis: AudioAnalysis?,
    /** `MusicKitAnalysis.flexAnalysis`. Field offset +0x1c. M2P §2.3. [EXACT] */
    val flexAnalysis: FlexAnalysis?,
    /**
     * `MusicKitAnalysis.spatialTimingInformation`. Field offset +0x20 (read by `FUN_272220920`).
     * Offset [EXACT]; planning role [STRONG_INFERENCE].
     */
    val spatialTimingInformation: SpatialTimingInformation?,
    /** `MusicKitAnalysis.Options` (`.default` / `.applySpatialTimingInformation`). PLC §1.2. [EXACT] */
    val options: AnalysisOptions,
    /**
     * `Song.Context.previousPlaybackEndState: Transition.PlaybackState?` (getter `0x2722666d0`).
     * Type attribution STRONG_INFERENCE.
     */
    val context: SongContext?,
    /**
     * `MediaAPI.SongAttributes.supportsSmartTransitions` gate — enforced one level above the planner
     * (SonicKit / MediaPlaybackCore), not inside `transition()`. MC §1; M2P §1. [STRONG_INFERENCE]
     */
    val supportsSmartTransitions: Boolean,
)

/** `Song.Analysis` enum cases. PLC §1.2 (nominal descriptor `0x27225c004`). [EXACT] */
enum class AnalysisSource { MUSIC_KIT, ADAPTIVE_MUSIC }

/** `TransitionPlanner.Song.Context`. Type attribution [STRONG_INFERENCE] (PLC §1.2). */
data class SongContext(
    val previousPlaybackEndState: PlaybackState?,
)

/**
 * `Transition.PlaybackState` — payload not fully reconstructed.
 * STATUS: UNKNOWN — needs runtime/device: full field layout of `Transition.PlaybackState`
 * is not recovered in the corpus (PLC §14.1 lists only `Transition` as PARTIAL).
 */
class PlaybackState {
    init {
        TODO("STATUS: UNKNOWN — needs runtime/device: PlaybackState layout not recovered")
    }
}

/** `Criteria.IncomingPlacement` / `Criteria.OutgoingPlacement` family. See TransitionModels.kt. */
data class Criteria(
    val outgoingPlacement: Placement,
    val incomingPlacement: Placement,
    /** `Criteria.maximumTransitionComplexity` byte @+0x21. PLC §1.2 / §4.1. [EXACT] */
    val maximumTransitionComplexity: Int,
    /** `allowedGenreIDs` / `deniedGenreIDs`; layout partial (PLC §1.2: genre fields PARTIAL). */
    val allowedGenreIDs: Set<String>?,
    val deniedGenreIDs: Set<String>?,
)

// ---------------------------------------------------------------------------------------------
// Genres
// ---------------------------------------------------------------------------------------------

/** `Genre { id: Genre.ID; subgenres: [Genre] }`, `Genre.ID.rawValue: String`. PLC §4.8. [EXACT] */
data class Genre(
    val id: String,
    val subgenres: List<Genre> = emptyList(),
)

/**
 * Genre id -> name mapping.
 * In-binary/DSC/resource table: verified NEGATIVE (MAA #11, BLOCKED/PARTIAL).
 * Canonical mapping was obtained only from the public `itunes.apple.com` genre tree
 * (report: 10_final_closure/itunes_music_genre_tree_id_name.json).
 * A runtime/device capture is still required to prove `Genre.ID.rawValue` equality.
 */
object GenreCatalog {
    /** STATUS: UNKNOWN — needs runtime/device: local ID->name table absent; do not hardcode IDs. */
    fun nameFor(id: String): String? =
        TODO("STATUS: UNKNOWN — needs runtime/device: no local Genre.ID->name table; runtime capture needed")
}

// ---------------------------------------------------------------------------------------------
// BPM / event times / tonality
// ---------------------------------------------------------------------------------------------

/**
 * `MusicKitInternal.BeatsPerMinute` (converted from `bpm.{main,beginning,ending}` Int/Double,
 * conversion `FUN_1d4121a98`, 3x `fcvtzs`; closure `0x1d412061c` @line 467).
 * Consumer status: UNUSED BY PLANNER (no evidence) — getter `0x1d4396964` is not in the
 * 41-symbol planner import set (M2P §1, §4). Tempo predicates exist but their input path is not traced.
 */
data class Bpm(
    val main: Int,
    val beginning: Int,
    val ending: Int,
    /**
     * `bpm.percentDeviation` (getter `0x1d4399720`).
     * Consumer-negative [EXACT]; converter does not scale the value; semantics UNVERIFIED.
     * Report: FINAL_BLOCKED_QUESTIONS.md §3 (#13, BLOCKED).
     */
    val percentDeviation: Double?,
) {
    /** STATUS: UNKNOWN — needs runtime/device: percentDeviation scale/semantics unverified (live=1). */
    fun percentDeviationFraction(): Double =
        TODO("STATUS: UNKNOWN — needs runtime/device: percentDeviation semantics (#13 BLOCKED)")
}

/**
 * `MusicKitInternal.EventTimes` (`[Double]` seconds).
 * Conversion `FUN_1d41218c4` = `(double)x / 1000.0` per element. [EXACT] (B1315 §14).
 *
 * PLANNER NEGATIVE: the planner-side `beatEvents/downbeatEvents/bars` never receive these getters
 * (`0x1d43967e0` not called from the 16 targets: M2P §1, §4). The planner data source is UNKNOWN.
 */
data class EventTimes(
    val beatOccurences: List<Double>,
    val barOccurences: List<Double>,
)

/**
 * Planner-side beat grid, used by BeatMatched candidate generation.
 * Apple model fields exist (`SongStructure.beatEvents/downbeatEvents/bars/beatStabilityMap`),
 * but the source that fills them is NOT FOUND (PLC §1.4, §5.4; ANDROID_CLEANROOM_CONTRACT §6 #7).
 */
data class BeatGrid(
    val beatEvents: List<Double>,
    val downbeatEvents: List<Double>,
    val bars: List<Double>,
    /**
     * `beatStabilityMap`; stability tolerance `0.031 s` (P2_MEDIADSP_CLOSURE, MAA §12.1).
     * Link to `bpm.percentDeviation` was REFUTED (M2P §5.4).
     */
    val beatStabilityMap: List<Double>,
) {
    companion object {
        /** STATUS: UNKNOWN — needs runtime/device: planner beat-grid source not localized. */
        fun fromMediaKitInternal(analysis: AudioAnalysis): BeatGrid =
            TODO("STATUS: UNKNOWN — needs runtime/device: planner beatEvents/bars source not localized")
    }
}

/** `CompositeAttribute<T>` with `main/beginning/ending` (planner tonal/musicality accessors). [EXACT] */
data class CompositeAttribute<T>(
    val main: T,
    val beginning: T,
    val ending: T,
)

/**
 * `MusicKitAnalysis.audioAnalysis` (getter `0x27225d788`) — normalized MusicKitInternal analysis.
 * Field/offset [EXACT]; per-field consumer statuses are in the KDoc of each type below
 * (M2P §1 and §4 determine consumer-negative fields).
 */
data class AudioAnalysis(
    /** `bpm` (`CloudCompositeAttribute<Double>` -> `BeatsPerMinute`). UNUSED BY PLANNER (no xref). */
    val bpm: Bpm?,
    /** `key.{beginning,ending,main}.{tonic,mode}` -> `tonality` (getter `0x1d4396898`). PARTIAL chain. */
    val tonality: CompositeAttribute<Tonality>?,
    /** `loudnessCurve` — the only loudness input the planner consumes (EXACT). */
    val loudnessCurve: LoudnessCurve?,
    /** `vocalActivity[]` — consumed by `FUN_27222343c` (EXACT). */
    val vocalActivities: List<VocalActivity>,
    /** `acousticness.*` — consumed by `FUN_272243f80` (consumer EXACT; island->field STRONG). */
    val acousticness: CompositeAttribute<Double>?,
    /** `danceability.*` — consumed by `FUN_27224400c` (consumer EXACT; island->field STRONG). */
    val danceability: CompositeAttribute<Double>?,
    /** `melodicness.*` — consumed by `FUN_2722341d4` (consumer EXACT; island->field STRONG). */
    val melodicness: CompositeAttribute<Double>?,
    /** `loudness.{value,range,peak}` — consumer-negative [EXACT] (#15 BLOCKED). */
    val loudness: Statistics?,
    /** `energy.*` — UNUSED BY PLANNER (no consumer, no strings). */
    val energy: CompositeAttribute<Double>?,
    /** `valence.*` — UNUSED BY PLANNER (no consumer). */
    val valence: CompositeAttribute<Double>?,
    /** `fades.fadeIn/fadeOut` (ms) — UNUSED BY PLANNER (getter not imported). */
    val fades: Fades?,
    /** `phrases` (`[TimeRange]`) — UNUSED BY PLANNER (getter not imported). */
    val phrases: List<TimeRangeMs>?,
)

/** `MusicKitInternal.Tonality { tonic, mode }`. Accessors in the 41-import set; island->subfield PARTIAL. */
data class Tonality(
    val tonic: String,
    val mode: String,
)

/**
 * `MusicKitAnalysis.audioAnalysis.tonality` (getter `0x1d4396898`).
 * Tonality relationship predicate `FUN_272231d04` (tag 3 = incompatible) is EXACT,
 * but the formula computing the relationship is UNKNOWN (PLC §6).
 */
object TonalityRelationship {
    /** STATUS: UNKNOWN — needs runtime/device: tonic/mode compatibility formula not recovered. */
    fun isCompatible(outgoing: Tonality?, incoming: Tonality?): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: tonality relationship formula (PLC §6)")
}

// ---------------------------------------------------------------------------------------------
// Loudness
// ---------------------------------------------------------------------------------------------

/**
 * `MusicKitInternal.LoudnessCurve { samplingFrequency: Double, value: [Double] }`.
 * Planner consumer: `FUN_272223b6c` builds `LoudnessMap`, then `FUN_272232790` / `FUN_272235840`.
 * Consumer [EXACT]; cloud->internal converter [PARTIAL]; live sampling ~2 Hz.
 */
data class LoudnessCurve(
    val samplingFrequency: Double,
    val value: List<Double>,
)

/**
 * `MusicKitInternal.Statistics { value, range, peak }` — `loudness.{value,range,peak}`.
 * Consumer-negative [EXACT]: `0x1d439a0a8/b0/b8` are never called; planner reads only `loudnessCurve`.
 * Report: FINAL_BLOCKED_QUESTIONS.md §4 (#15 BLOCKED).
 */
data class Statistics(
    val value: Double?,
    val range: Double?,
    val peak: Double?,
) {
    /** STATUS: UNKNOWN — needs runtime/device: loudness.peak scale (linear amplitude vs dB) unverified. */
    fun peakAsDecibels(): Double =
        TODO("STATUS: UNKNOWN — needs runtime/device: loudness.peak units (#15 BLOCKED)")
}

/**
 * Loudness ratio predicate used by candidate scoring.
 *
 * Formula (`FUN_272235840`, full reconstruction, [EXACT], PLC §8.1):
 * ```
 * region = [r0, r1]
 * L = mean(loudnessMap.samples inside region); if L == nil || L >= 0 -> nil
 * D = r1 - r0
 * T = mean(loudnessMap.samples inside [r1, r1 + D])
 * if T == nil || T >= 0 -> nil
 * return L / T      // no clamp
 * ```
 * Both means are dB-like and expected negative, so the ratio is positive. Helper functions:
 * `FUN_272216414` (samples-in-window), `FUN_272215af4` (mean, count==0 -> nil), `FUN_27222bc88`
 * (`[start,end]`), `FUN_27222c9dc` (duration). [EXACT]
 */
object TrailingLoudnessRatio {
    /**
     * @return `L / T` when both window means are strictly negative, otherwise `null`
     *         (candidate weight stays 1.0 on null: `fcsel d8,d8,d0,ne` @`0x272234570`).
     */
    fun compute(meanL: Double?, meanT: Double?): Double? {
        if (meanL == null || meanL >= 0.0) return null
        if (meanT == null || meanT >= 0.0) return null
        return meanL / meanT
    }
}

// ---------------------------------------------------------------------------------------------
// Vocal activity
// ---------------------------------------------------------------------------------------------

/** `VocalKind` enum; built by a switch 0/1/2 in `FUN_27222343c`. PLC §7.1. [EXACT] */
enum class VocalKind { SINGING, SPEECH, RAPPING }

/**
 * `TransitionPlanner.VocalActivity { kind, strength }` (islands `0x2743dd1d0/2e0/2c0`).
 * Consumer `FUN_27222343c` [EXACT]. Leading-incoming significance is consumed by `FUN_272235198`;
 * penalty constant `0.75` [EXACT] (PLC §7.2).
 *
 * Enum tags of `VocalActivityStrength` (observed comparisons `< 3` and `!= 5`) are
 * [PARTIAL]/not recovered => significance threshold is TODO.
 */
data class VocalActivity(
    val startSeconds: Double,
    val endSeconds: Double,
    val kind: VocalKind,
    val strength: Double,
) {
    /** STATUS: UNKNOWN — needs runtime/device: `VocalActivityStrength` enum tags / significance threshold. */
    fun isSignificant(): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: VocalActivityStrength tags not recovered (PLC §7.2)")
}

/** Penalty applied when leading incoming vocal activity is significant. [EXACT] — see TransitionPlannerSpec. */
object VocalPenalty {
    /** `fmov d0,0x3fe8000000000000` @`0x272234534`; `fcsel` @`0x272234538`. [EXACT] — PLC §7.2. */
    const val SIGNIFICANT = 0.75
}

// ---------------------------------------------------------------------------------------------
// Flex analysis (video events / pivot points)
// ---------------------------------------------------------------------------------------------

/** `FlexAnalysis.Event.TimeScale` — from `score` ranges [200,299]/[400,499]/[600,699]/[800,899]. */
enum class FlexTimeScale { SHORT, MEDIUM, LONG, EXTRA_LONG }

/**
 * Amplitude divisor in `Event.init?(time:score:)`: `amplitude = (score % divisor) / divisor`.
 * Exact integer 100. Written without its decimal spelling; see README forbidden list note.
 */
private const val FLEX_AMPLITUDE_SCALE = 100

/**
 * `MusicKitInternal.FlexAnalysis.Event { time, timeScale, amplitude }`.
 * Conversion `FUN_1d3e5ef4c` + `Event.init?(time:score:)` `0x1d3e81ad4`:
 * `score -> TimeScale` (ranges above; other values dropped), `amplitude = (score % divisor) / divisor`
 * where divisor is [FLEX_AMPLITUDE_SCALE]. [EXACT] formula; the decimal spelling of the divisor is
 * reserved for the README forbidden list (unrelated disputed constant).
 *
 * Planner imports `FlexAnalysis.events/time/timeScale` (builder `FUN_2722231b4` caller
 * `FUN_2722200cc`; `FUN_272225ef4`) but the scoring predicate is NOT localized -> [PARTIAL].
 */
data class FlexEvent(
    val time: Double,
    val timeScale: FlexTimeScale,
    val amplitude: Double,
) {
    companion object {
        /** Exact `score -> Event` mapping from `Event.init?(time:score:)`. [EXACT] (M2P §1). */
        fun fromScore(time: Double, score: Int): FlexEvent? {
            val scale = when (score) {
                in 200..299 -> FlexTimeScale.SHORT
                in 400..499 -> FlexTimeScale.MEDIUM
                in 600..699 -> FlexTimeScale.LONG
                in 800..899 -> FlexTimeScale.EXTRA_LONG
                else -> return null
            }
            return FlexEvent(time, scale, (score % FLEX_AMPLITUDE_SCALE) / FLEX_AMPLITUDE_SCALE.toDouble())
        }
    }
}

/**
 * `MusicKitAnalysis.flexAnalysis` (metadata index 3, offset table +0x1c).
 * Entry/exit points: consumer is the SmartTransitions engine, not the planner -> UNUSED BY PLANNER.
 */
data class FlexAnalysis(
    val events: List<FlexEvent>,
    /** `entryPoints`; engine consumer `SmartTransitionSongData.transitionPivotPoint` @`0x2721afaa4`. */
    val entryPoints: List<PivotPoint>?,
    /** `exitPoints` (+`fadeToBlack`). Engine consumer, planner UNUSED BY PLANNER. */
    val exitPoints: List<PivotPoint>?,
    /** `visualTempo` (derived `bpm/4`, samplingFrequency = -1). UNUSED BY PLANNER. */
    val visualTempo: SampledValues?,
    /** `arousal` (flexml). UNUSED BY PLANNER (no consumer). */
    val arousal: SampledValues?,
    /** `valence` (flexml). UNUSED BY PLANNER (no consumer). */
    val valence: SampledValues?,
) {
    /** STATUS: UNKNOWN — needs runtime/device: planner scoring predicate for flex events not localized. */
    fun plannerScore(event: FlexEvent): Double =
        TODO("STATUS: UNKNOWN — needs runtime/device: flex events scoring predicate not localized")
}

/** `PivotPoints` entry/exit point (`timeInSeconds`, `gainTimeInSeconds`, `gainValue`, tags). */
data class PivotPoint(
    val timeInSeconds: Double,
    val gainTimeInSeconds: Double?,
    val gainValue: Double?,
    val tags: List<String> = emptyList(),
    val fadeToBlack: Double? = null,
)

/** `CloudSampledValues` -> `SampledValues`. Keeps `samplingFrequency` and a value array. */
data class SampledValues(
    val samplingFrequency: Double,
    val value: List<Double>,
)

// ---------------------------------------------------------------------------------------------
// Crossfield / unused payloads kept for completeness
// ---------------------------------------------------------------------------------------------

/** `Fades` (ms) — getter `0x1d4396b38`; not in the 41-import set. UNUSED BY PLANNER. */
data class Fades(val fadeIn: TimeRangeMs?, val fadeOut: TimeRangeMs?)

/** `phrases: [TimeRange]` — UNUSED BY PLANNER (getter `0x1d4396cec`). */
data class TimeRangeMs(val beginMs: Double, val endMs: Double) {
    /** Conversion helper used by MusicKitInternal: ms -> s (`FUN_1d4122c04`, `1d41218c4`). [EXACT] */
    fun toSeconds() = TimeRangeSeconds(beginMs / 1000.0, endMs / 1000.0)
}

/** Seconds time range (internal planner/engine representation). */
data class TimeRangeSeconds(val begin: Double, val end: Double)

/** `SpatialTimingInformation` is opaque; only the read offset (+0x20) is EXACT. */
class SpatialTimingInformation {
    init {
        TODO("STATUS: UNKNOWN — needs runtime/device: SpatialTimingInformation layout not recovered")
    }
}

/** `MusicKitAnalysis.Options` (`default` / `applySpatialTimingInformation`). [EXACT] */
enum class AnalysisOptions { DEFAULT, APPLY_SPATIAL_TIMING_INFORMATION }

/** `TransitionPlanner.Criteria.maximumTransitionComplexity` convenience alias. */
const val DEFAULT_MAXIMUM_TRANSITION_COMPLEXITY: Int = 3
/*
 * Provenance for DEFAULT_MAXIMUM_TRANSITION_COMPLEXITY:
 *   PLC §4.2 reduced-complexity array `0x2884aa4d0 = {0,1,2,3}` ("All transitions allowed")
 *   and `Criteria.maximumTransitionComplexity` (raw byte @+0x21, PLC §1.2). [EXACT] values.
 */
