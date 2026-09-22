// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.automix

import apple.music.contracts.dsp.AutomationEasing

/*
 * TransitionStyles.kt — catalog model for `TransitionStyles.json` (iOS 26).
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md (TS; canonical spec)
 *  - deepseek_analysis/10_final_closure/transition_styles_normalized.json (machine extraction; 14 styles)
 *  - deepseek_analysis/10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md §11 (dispatch 8/9/12)
 *  - deepseek_analysis/09_appos/APPOS_TRANSITION_STYLES.md
 *
 * Resource identity (EXACT):
 *   appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json
 *   sha256 fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120, 55905 bytes
 *
 * This file defines the data model + metadata registry. Per-automation numeric values
 * (74 automations, 55 instructions) are NOT duplicated here: they live in the resource and are
 * loaded at runtime. Duplicating 200+ raw numbers as Kotlin literals would add no accuracy.
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

// ---------------------------------------------------------------------------------------------
// Resource identity
// ---------------------------------------------------------------------------------------------

/** Loader / resource identity. `FUN_27224e480` <- `TransitionPlanner.init(configuration:)` @`0x2722673e4`. [EXACT] */
object TransitionStylesResource {
    const val RESOURCE_NAME = "TransitionStyles"
    const val RESOURCE_EXTENSION = "json"
    const val SHA256 = "fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120"
    const val SIZE_BYTES = 55905

    /** Root is a JSON array of 14 objects; sparse ids 5, 13..32, 34..43 are absent. [EXACT] */
    val STYLE_IDS: List<Int> = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 33, 44)

    /**
     * Missing-resource error: `SmartTransitionsError` tag `0xd`, log
     * "Transition style catalog JSON resource missing...". [STRONG_INFERENCE] (TS §0).
     */
    const val MISSING_RESOURCE_ERROR_TAG = 0xd
}

// ---------------------------------------------------------------------------------------------
// Raw-time / raw-value primitives of the resource
// ---------------------------------------------------------------------------------------------

/**
 * Raw `{relative, offsetInSeconds?}` time value from the resource.
 * `relative` is normalized 0..1 inside the transition; `offsetInSeconds` is optional seconds.
 * No absolute times are encoded. Provenance: TS §1. [EXACT] raw key/value shapes.
 */
data class StyleTime(
    val relative: Double,
    val offsetInSeconds: Double?,
)

/** Time-value kind used by automations: `{"default": {...}}` or mapped `parameterName` (style 10). */
enum class StyleValueKind { DEFAULT, MAPPED }

/**
 * Automation time or value value-spec:
 * `{"default": {relative, offsetInSeconds?}}` or `{"default": <number>}`; mapped values carry
 * `parameterName` (`"beat_length"`, exactly 2 occurrences, style 10). Provenance: TS §1. [EXACT] shape;
 * mapped value resolution [PARTIAL].
 */
data class StyleValueSpec(
    val kind: StyleValueKind,
    /** Time payload when used as a time; null for numeric values. */
    val time: StyleTime? = null,
    /** Numeric payload when used as a value. */
    val value: Double? = null,
    /** Mapped parameter name, only `"beat_length"` observed. */
    val parameterName: String? = null,
)

/** Instruction names present in the catalog (6 values). Provenance: TS §3. [EXACT] */
enum class StyleInstructionName { TIME_STRETCHING, AU_LOWPASS, AU_HIPASS, AU_DELAY, AU_REVERB2, GAIN }

/**
 * Instruction placement window: `instructions.<side>[i].placement.{start,end}` — window in which the
 * instruction applies, in the same relative/offset time base as ramp times. Provenance: TS §1. [EXACT].
 */
data class StylePlacementWindow(val start: StyleTime, val end: StyleTime)

/**
 * One catalog automation (`automations[{parameterId,startTime,endTime,startValue,endValue,interpolation}]`).
 * Raw keys [EXACT] (TS §1.1); field<->Swift binding [STRONG_INFERENCE].
 */
data class StyleAutomation(
    val index: Int,
    val parameterId: String,
    val startTime: StyleValueSpec,
    val endTime: StyleValueSpec,
    val startValue: StyleValueSpec,
    val endValue: StyleValueSpec,
    /** Resource string: linear | ease-out-2 | ease-out-0.5 | ease-in-0.5 | ease-in-4 | ease-out-4. */
    val interpolation: String,
    val interpolationEasing: AutomationEasing,
)

/** One catalog instruction (`name`, `placement`, `automations`). Provenance: TS §1/§3. [EXACT] shape. */
data class StyleInstruction(
    val index: Int,
    val name: StyleInstructionName,
    val placement: StylePlacementWindow,
    val automations: List<StyleAutomation>,
)

/** Per-side instruction lists. */
data class StyleInstructions(
    val outgoing: List<StyleInstruction>,
    val incoming: List<StyleInstruction>,
)

/** One catalog style object. Raw schema keys [EXACT]; Swift field binding [STRONG_INFERENCE]. */
data class TransitionStyle(
    val id: Int,
    val name: String,
    /** Raw key `offset` (bind to `TransitionStyle.startTime` is STRONG_INFERENCE). */
    val offset: StyleTime?,
    /**
     * Raw key `duration` (only styles 8/9/12: values 8/8/16).
     * Semantics `maximumBarCount` [STRONG_INFERENCE]; unit (bars vs seconds) [UNKNOWN].
     */
    val duration: Int?,
    val instructions: StyleInstructions,
)

// ---------------------------------------------------------------------------------------------
// Interpolation name -> curve mapping
// ---------------------------------------------------------------------------------------------

/**
 * Mapping from resource interpolation string to the audited curve table.
 * Curve formulas are EXACT (`FUN_272275f84` evaluator); string->curveByte binding is
 * STRONG_INFERENCE for the ranged families (TS §7).
 * `logarithmic` occurs 0 times in the catalog (TS §4).
 */
fun interpolationEasing(name: String): AutomationEasing = when (name) {
    "linear" -> AutomationEasing.LINEAR
    "ease-out-2" -> AutomationEasing.EASE_OUT_2
    "ease-out-0.5" -> AutomationEasing.EASE_OUT_0_5
    "ease-in-0.5" -> AutomationEasing.EASE_IN_0_5
    "ease-in-2" -> AutomationEasing.EASE_IN_2
    "ease-in-4" -> AutomationEasing.EASE_IN_4
    "ease-out-4" -> AutomationEasing.EASE_OUT_4
    "logarithmic" -> AutomationEasing.LOGARITHMIC
    else -> throw IllegalArgumentException("Unknown TransitionStyles interpolation: $name")
}

// ---------------------------------------------------------------------------------------------
// Reachability / metadata registry (all 14 styles)
// ---------------------------------------------------------------------------------------------

/** Reachability class per TS §2.1. */
enum class StyleReachability {
    /** id 8/9/12 — the only branches of `FUN_272234380`; BeatMatched path. [EXACT] */
    REACHABLE_BEAT_MATCHED,

    /** id 6/7/10/11 — BM names, ids never compared -> zero-fill candidate. [STRONG_INFERENCE] */
    LEGACY_UNREACHABLE,

    /** id 0..4/33/44 — no consumer found in the iOS 26 planner (corpus negative). [PARTIAL/NOT FOUND] */
    NO_CONSUMER_FOUND,
}

/**
 * Style metadata row (id, name, offset, duration, reachability).
 * Values machine-extracted from the resource (EXACT) + reachability from TS §2/§2.1.
 */
data class TransitionStyleMeta(
    val id: Int,
    val name: String,
    val reachability: StyleReachability,
    val offsetRelative: Double?,
    val offsetInSeconds: Double?,
    val durationBarsOrUnknownUnit: Int?,
    /** Outgoing / incoming instruction counts (EXACT). */
    val outgoingInstructionCount: Int,
    val incomingInstructionCount: Int,
    /** Outgoing / incoming automation counts (EXACT). */
    val outgoingAutomationCount: Int,
    val incomingAutomationCount: Int,
)

/**
 * All 14 catalog styles. Counts: 14 styles, 55 instructions (31 out / 24 in), 74 automations
 * (46 out / 28 in). Provenance: TS §2 (machine-generated from raw JSON); reachability TS §2.1.
 * Every row [EXACT] for values; reachability classes as annotated.
 */
val TRANSITION_STYLE_METADATA: List<TransitionStyleMeta> = listOf(
    // name/offset/duration/counts EXACT (TS §3, style 0); reachability NO CONSUMER FOUND [PARTIAL]
    TransitionStyleMeta(0, "Gapless", StyleReachability.NO_CONSUMER_FOUND, 0.0, 0.0, null, 1, 1, 1, 1),
    TransitionStyleMeta(1, "constant-power cross-fade", StyleReachability.NO_CONSUMER_FOUND, 0.0, null, null, 1, 1, 1, 1),
    TransitionStyleMeta(2, "constant-power long fade-out short fade-in", StyleReachability.NO_CONSUMER_FOUND, 1.0, -0.3, null, 1, 1, 1, 1),
    TransitionStyleMeta(3, "overlap", StyleReachability.NO_CONSUMER_FOUND, null, null, null, 0, 0, 0, 0),
    TransitionStyleMeta(4, "Ease-in Ring-out", StyleReachability.NO_CONSUMER_FOUND, 0.0, null, null, 0, 1, 0, 1),
    TransitionStyleMeta(6, "Beat-matched long fade-out short fade-in - Same Bar num", StyleReachability.LEGACY_UNREACHABLE, 0.0, null, null, 2, 2, 2, 2),
    TransitionStyleMeta(7, "Beat-matched long fade-out dynamic fade-in", StyleReachability.LEGACY_UNREACHABLE, 0.0, null, null, 2, 2, 2, 2),
    TransitionStyleMeta(8, "BM - Filter high to low", StyleReachability.REACHABLE_BEAT_MATCHED, 0.0, null, 8, 3, 4, 3, 4),
    TransitionStyleMeta(9, "BM - Filter expansion", StyleReachability.REACHABLE_BEAT_MATCHED, 0.0, null, 8, 4, 4, 4, 4),
    TransitionStyleMeta(10, "BM - Filter long out short in + delay", StyleReachability.LEGACY_UNREACHABLE, 0.0, null, null, 4, 3, 7, 3),
    TransitionStyleMeta(11, "BM - Filter long out short in + Reverb", StyleReachability.LEGACY_UNREACHABLE, 0.0, null, null, 4, 4, 10, 4),
    TransitionStyleMeta(12, "BM - Long filter high to low", StyleReachability.REACHABLE_BEAT_MATCHED, 0.0, null, 16, 3, 4, 3, 4),
    TransitionStyleMeta(33, "overlap + Reverb", StyleReachability.NO_CONSUMER_FOUND, null, null, null, 1, 0, 6, 0),
    TransitionStyleMeta(44, "Ease-in Ring-out + Reverb", StyleReachability.NO_CONSUMER_FOUND, 0.0, null, null, 1, 1, 6, 1),
)

/** Lookup by id. [EXACT] keys. */
fun transitionStyleMeta(id: Int): TransitionStyleMeta? = TRANSITION_STYLE_METADATA.firstOrNull { it.id == id }

// ---------------------------------------------------------------------------------------------
// Style semantics that are NOT resolved (never invent)
// ---------------------------------------------------------------------------------------------

/** Style semantics facade; unknown behaviors are TODO. */
object StyleSemantics {
    /**
     * Duration unit: `maximumBarCount` semantics is STRONG_INFERENCE, but the unit (bars vs seconds)
     * was NOT FOUND (TS §5.2/§10.1).
     * STATUS: UNKNOWN — needs runtime/device: `duration` unit not established.
     */
    fun durationToSeconds(style: TransitionStyle): Double =
        TODO("STATUS: UNKNOWN — needs runtime/device: style duration unit (bars vs seconds) NOT FOUND")

    /**
     * `beat_length` runtime resolution (2 occurrences, style 10). Resolver source is NOT FOUND;
     * on failure the parameter default is used; error "ID and/or default value missing."
     * [STRONG_INFERENCE] (TS §6/§10.3).
     * STATUS: UNKNOWN — needs runtime/device: beat_length value source not localized.
     */
    fun resolveBeatLength(style: TransitionStyle): Double =
        TODO("STATUS: UNKNOWN — needs runtime/device: beat_length resolver NOT FOUND")

    /**
     * Algorithm binding for non-BeatMatched styles (0..4, 33, 44) is NOT FOUND (TS §2.1/§10.4).
     * Only 8/9/12 are bound to the BeatMatched path by the dispatch (EXACT).
     * STATUS: UNKNOWN — needs runtime/device: non-BM style -> algorithm binding absent.
     */
    fun algorithmFor(styleId: Int): Algorithm? =
        TODO("STATUS: UNKNOWN — needs runtime/device: non-BM style algorithms NOT FOUND")

    /**
     * Style dispatch: unknown ids are zero-filled (candidate never selected); only 8/9/0xc are
     * served by `FUN_272234380`. [EXACT] (TS §5/§6).
     */
    fun supportedByDispatch(styleId: Int): Boolean = styleId == 8 || styleId == 9 || styleId == 12
}

// ---------------------------------------------------------------------------------------------
// Loader model
// ---------------------------------------------------------------------------------------------

/**
 * Loader contract for the catalog.
 *
 * Apple: `FUN_27224e480` loaded from `Bundle(for: _SonicKit_MusicKit_Packages_Locator)`,
 * name "TransitionStyles", ext "json" (PLC §11.2). [EXACT] name/ext/log.
 * This interface is the port-side seam: the implementation uses the platform JSON decoder
 * (PORT DESIGN, no JSON library is assumed by this contract).
 */
interface TransitionStylesLoader {
    /**
     * Parse the resource text into the catalog.
     * Implementation is a port decision; the returned model must preserve all values 1:1.
     */
    fun load(resourceText: String): List<TransitionStyle>
}

/**
 * Reference loader entry point.
 * TODO (PORT DESIGN): bind to the port's JSON decoder; the model in this file is the schema.
 */
object TransitionStylesCatalog {
    fun load(resourceText: String, loader: TransitionStylesLoader): List<TransitionStyle> =
        loader.load(resourceText)
}
