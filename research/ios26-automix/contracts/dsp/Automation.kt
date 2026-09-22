// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.dsp

/*
 * Automation.kt — automation ramp / schedule model (iOS 26 SmartTransitions DSP).
 *
 * PROVENANCE:
 *  - deepseek_analysis/06_android_port/ANDROID_DSP_ENGINE.md §4 (curve table, schedules) [audited]
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.5 (AutomationRamp)
 *  - deepseek_analysis/10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md §7 (curve bytes)
 *  - deepseek_analysis/10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md §8 (units)
 *  - deepseek_analysis/09_appos/APPOS_MASTER_SUMMARY.md §11 #22 (logarithmic)
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

// ---------------------------------------------------------------------------------------------
// Curves
// ---------------------------------------------------------------------------------------------

/**
 * Automation curve (`curveByte` on Apple's `AutomationRamp`, field offset 0x20).
 *
 * Apple evaluator semantics (`FUN_272275f84` / DSP_CONSTANTS, [EXACT] instruction sequence):
 * ```
 * if (t1 <= t) return endValue
 * if (t0 >= t) return startValue
 * p = clamp((t - t0) / (t1 - t0), 0, 1)
 * value = startValue + y(p) * (endValue - startValue)
 * ```
 *
 * Formula column is from the audited Android DSP engine report §4.1:
 *   0x00 ease-in-0.5  `1 - sqrt(1 - p)`        [EXACT]
 *   0x01 ease-in-2    `p * p`                  [EXACT]
 *   0x02..0x3f ease-in-4  `pow(p, 4.0)`        [EXACT formula; exact byte inside range STRONG]
 *   0x40 ease-out-0.5 `sqrt(p)`                [EXACT]
 *   0x41 ease-out-2   `1 - (1 - p)^2`          [EXACT]
 *   0x42..0x7f ease-out-4 `1 - pow(1 - p, 4.0)` [EXACT formula; exact byte STRONG]
 *   0x80 linear       `p`                      [EXACT]
 *   0x81 logarithmic  geometric interpolation (log2/exp2 family) [formula structure EXACT,
 *                     final closure MAA §11 #22; catalog uses it 0 times]
 *   0x82..0xff logarithmic id, observed as `p` [EXACT by observed behavior]
 *
 * Nomenclature note: the report observes that "ease-in-*" ids map to the complementary form and
 * "ease-out-*" to the direct form; the naming may be inverted relative to intuition [INFERRED].
 * The evaluator dispatches on `curveByte`, not on the string.
 *
 * This enum carries curve-engine math only. The excluded disputed crossfade derivation (styles 1/2)
 * is listed in README.md and must not be implemented here.
 */
enum class AutomationEasing(
    /** Apple `curveByte`; for ranged families the representative first byte of the documented range. */
    val curveByte: Int,
    /** Documented byte range when the family spans multiple bytes. */
    val curveByteRange: IntRange,
    val formula: String,
    val status: String,
    val usedInStyleCatalog: Boolean,
) {
    EASE_IN_0_5(0x00, 0x00..0x00, "1 - sqrt(1 - p)", "EXACT", true),
    EASE_IN_2(0x01, 0x01..0x01, "p * p", "EXACT", false),
    EASE_IN_4(0x02, 0x02..0x3f, "pow(p, 4.0)", "EXACT formula; exact byte within range STRONG_INFERENCE", true),
    EASE_OUT_0_5(0x40, 0x40..0x40, "sqrt(p)", "EXACT", true),
    EASE_OUT_2(0x41, 0x41..0x41, "1 - (1 - p)^2", "EXACT", true),
    EASE_OUT_4(0x42, 0x42..0x7f, "1 - pow(1 - p, 4.0)", "EXACT formula; exact byte within range STRONG_INFERENCE", true),
    LINEAR(0x80, 0x80..0x80, "p", "EXACT", true),
    LOGARITHMIC(0x81, 0x81..0xff, "log2/exp2 geometric interpolation", "structure EXACT; catalog 0 uses", false);

    companion object {
        /** Apple `AutomationCurveEasingStyle.id` strings are "0.5" / "2" / "4" (EXACT); byte table is primary. */
        fun forCurveByte(byte: Int): AutomationEasing =
            entries.firstOrNull { byte in it.curveByteRange } ?: LINEAR
    }
}

/** Evaluate `y(p)` for a documented curve. Implemented exactly where the formula is EXACT. */
fun AutomationEasing.ease(p: Double): Double = when (this) {
    AutomationEasing.EASE_IN_0_5 -> 1.0 - kotlin.math.sqrt(1.0 - p)
    AutomationEasing.EASE_IN_2 -> p * p
    AutomationEasing.EASE_IN_4 -> p * p * p * p
    AutomationEasing.EASE_OUT_0_5 -> kotlin.math.sqrt(p)
    AutomationEasing.EASE_OUT_2 -> 1.0 - (1.0 - p) * (1.0 - p)
    AutomationEasing.EASE_OUT_4 -> 1.0 - (1.0 - p) * (1.0 - p) * (1.0 - p) * (1.0 - p)
    AutomationEasing.LINEAR -> p
    AutomationEasing.LOGARITHMIC ->
        TODO("STATUS: UNKNOWN — needs runtime/device: logarithmic f/g mapping per-byte (0x81 family)")
}

/** `p = clamp((t - t0) / (t1 - t0), 0..1)` with the exact boundary shortcuts. [EXACT] */
fun automationProgress(t: Double, t0: Double, t1: Double): Double {
    if (t1 <= t) return 1.0
    if (t0 >= t) return 0.0
    return ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
}

/** `value = start + y(p) * (end - start)`. [EXACT] */
fun evaluate(easing: AutomationEasing, startValue: Double, endValue: Double, t: Double, t0: Double, t1: Double): Double {
    val p = automationProgress(t, t0, t1)
    return startValue + easing.ease(p) * (endValue - startValue)
}

// ---------------------------------------------------------------------------------------------
// Ramp / point / automation containers
// ---------------------------------------------------------------------------------------------

/**
 * Apple `AutomationRamp` layout (0x28 B): `{startValue@0, endValue@8, t0@0x10, t1@0x18,
 * curveByte@0x20}` (DSP_CONSTANTS §4; ANDROID_DSP_ENGINE §4). Field offsets [EXACT].
 *
 * `t0`/`t1` are normalized `0..1` resource times with an optional `offsetInSeconds`, or songTime
 * seconds on the continuous schedule; the mapping is performed by the planner [PARTIAL] (DSPR §C).
 */
data class AutomationRamp(
    val startValue: Double,
    val endValue: Double,
    val t0: Double,
    val t1: Double,
    val easing: AutomationEasing,
)

/** `ContinuousSchedule.AutomationPoint {value, songTime, curve}` (ANDROID_DSP_ENGINE §4.3). [EXACT] type. */
data class AutomationPoint(val value: Double, val songTime: Double, val easing: AutomationEasing)

/** Apple `Automation {parameter, points, ramps}` (ADSP §4.2/§4.3). [EXACT] type. */
data class Automation(
    val parameter: AutomationEffectParameterId,
    val points: List<AutomationPoint> = emptyList(),
    val ramps: List<AutomationRamp> = emptyList(),
    val startValue: Double? = null,
    val endValue: Double? = null,
)

// ---------------------------------------------------------------------------------------------
// Schedules
// ---------------------------------------------------------------------------------------------

/**
 * `Transition.SteppedSchedule` (ADSP §4.2, [EXACT]):
 *  - `defaultStepDuration = 0.2 s` (raw `0x3fc999999999999a` @`0x2722a1010`);
 *  - `validStepDurationRange = 0.0001..1.0` (raw `0x3f1a36e2eb1c432d` / `fmov 1.0`);
 *  - fields: `automationSchedule`, `timeStretchingSchedule`, `playbackAlignmentSchedule`.
 */
data class SteppedSchedule(
    /** Apple `defaultStepDuration`; contract default. [EXACT] */
    val defaultStepDuration: Double = DEFAULT_STEP_DURATION,
    /** Apple `validStepDurationRange`. [EXACT] */
    val validStepDurationRange: ClosedFloatingPointRange<Double> = VALID_STEP_DURATION_RANGE,
    val automations: List<Automation> = emptyList(),
    val timeStretching: TimeStretchingSchedule = TimeStretchingSchedule(),
    val playbackAlignment: List<SynchronizedPlaybackTimeRange> = emptyList(),
) {
    companion object {
        /** `0x3fc999999999999a` @`0x2722a1010` (ADSP §4.2/MAA §12.2). [EXACT] */
        const val DEFAULT_STEP_DURATION = 0.2

        /** `0.0001..1.0` (`0x3f1a36e2eb1c432d` / 1.0). [EXACT] */
        val VALID_STEP_DURATION_RANGE = 0.0001..1.0

        /** Android compiler may choose a smaller step but keeps the contract clamp (PORT DESIGN). */
        fun clampStepDuration(seconds: Double): Double =
            seconds.coerceIn(VALID_STEP_DURATION_RANGE)
    }
}

/**
 * `Transition.TimeStretchingSchedule`: per-side stepping.
 * Getters: `outgoingSongSteps` @`0x272281544`, `incomingSongSteps` @`0x2722817bc`.
 * Provenance: DSP_RUNTIME_ARCHITECTURE.md §B.3. [EXACT] fields/types.
 */
data class TimeStretchingSchedule(
    val outgoingSongSteps: List<TimeStretchingStep> = emptyList(),
    val incomingSongSteps: List<TimeStretchingStep> = emptyList(),
)

/**
 * `TimeStretchingStep { playbackRate: Double @0x272282538; timeRange: PlaybackTimeRange @0x27228255c }`.
 * Delivery to playback: `AVPlayerItem.speedRamp` executed by ME TimePitch (`Spectral`), NOT the DSPGraph.
 * DSPR §B.3 [EXACT] types/logs; the automations->steps function is NOT FOUND.
 */
data class TimeStretchingStep(
    val playbackRate: Double,
    val timeRange: PlaybackTimeRange,
)

/** `PlaybackTimeRange` / `Transition.TimeStretchingState` time base. DSPR §B.3/B.5. [EXACT] types. */
data class PlaybackTimeRange(val songTime: Double, val stretchedSongTime: Double)

/** `Transition.SteppedSchedule.SynchronizedPlaybackTimeRange` — exact formula NOT traced (DSPR §B.5 PARTIAL). */
data class SynchronizedPlaybackTimeRange(
    val alignmentTime: Double,
    val transitionStartTime: Double,
) {
    init {
        TODO("STATUS: UNKNOWN — needs runtime/device: alignment/pivot formula PARTIAL (DSPR §B.5)")
    }
}

/**
 * `Transition.ContinuousSchedule.Automation {parameter, points, ramps, startValue, endValue,
 * songTimeRange?}` (ADSP §4.3). [EXACT] type.
 */
data class ContinuousSchedule(
    val automations: List<Automation> = emptyList(),
    val songSchedule: SongSchedule? = null,
)

/** `SongSchedule {songTimeRange, transitionTimeRange, referenceSongTime, referencePlaybackTime}`. [EXACT] type. */
data class SongSchedule(
    val songTimeRange: ClosedFloatingPointRange<Double>,
    val transitionTimeRange: ClosedFloatingPointRange<Double>,
    val referenceSongTime: Double,
    val referencePlaybackTime: Double,
)

/**
 * `Transition.SchedulingPolicy` mirror into the DSP layer:
 * `.continuous(ContinuousSchedule)` | `.stepped(SteppedSchedule)` (PLC §1.2). [EXACT]
 */
sealed interface DspSchedulingPolicy {
    data class Continuous(val schedule: ContinuousSchedule) : DspSchedulingPolicy
    data class Stepped(val schedule: SteppedSchedule) : DspSchedulingPolicy
}
