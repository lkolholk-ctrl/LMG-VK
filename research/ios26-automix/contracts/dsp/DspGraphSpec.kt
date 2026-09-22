// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.dsp

/*
 * DspGraphSpec.kt — SmartTransitions DSP graph registry (`DSPGraph.dspg`, iOS 26).
 *
 * PROVENANCE:
 *  - deepseek_analysis/09_appos/APPOS_DSPGRAPH.md §1–§7 (raw resource text, nodes, wires, 27 params)
 *  - deepseek_analysis/10_final_closure/DSP_RUNTIME_ARCHITECTURE.md §A–§D (bypa, ts_rate, out_gain, instances)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.6/§1.7
 *  - deepseek_analysis/10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md §8 (units/catalog use)
 *  - deepseek_analysis/10_final_closure/FINAL_RED_TEAM_AUDIT.md §1 (entity-level downgrades)
 *
 * Resource identity:
 *   path  appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/DSPGraph.dspg
 *   sha256 8326e890fd6905a0fba94176ed57ae200fe38c206dbac1c07d8355c278dafd2a, 3001 bytes
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 * External public-AudioUnit header constants are marked [EXTERNAL] in the source reports (counted EXACT).
 */

// ---------------------------------------------------------------------------------------------
// Graph identity / topology
// ---------------------------------------------------------------------------------------------

/** One DSPGraph box. `subtype` is the 4-char AU subtype (`aufx filt appl` style). */
data class DspNode(
    val name: String,
    val boxType: String,
    val subtype: String?,
    val inputs: Int,
    val outputs: Int,
)

/** One `wire (A outIdx) (B inIdx)` edge. */
data class DspWire(
    val fromNode: String,
    val fromIndex: Int,
    val toNode: String,
    val toIndex: Int,
)

/**
 * `DSPGraph.dspg` registry. Full text in APPOS_DSPGRAPH.md §1; [EXACT].
 * The graph is instantiated per track (outgoing and incoming) as
 * `AVAudioMixProcessingEffect` with identifier `smartTransitionsGraph` (DSPR §D.1).
 */
object DspGraphSpec {
    /** `graphName` in the resource. [EXACT] — distinct from the runtime identifier. */
    const val GRAPH_NAME = "SmartTransitions"

    /** Runtime identifier string in `_SonicKit_MusicKit` (data `0x2721de340`, len 0x15). [EXACT]. */
    const val GRAPH_IDENTIFIER = "smartTransitionsGraph"

    /** Loader resource base name (`FUN_27226b7cc`, ext `dspg`). [EXACT] — APPOS_DSPGRAPH §0. */
    const val GRAPH_RESOURCE_NAME = "DSPGraph"

    /** `format audioFormat ([sampleRate] [numIns])`; values are runtime (not in the resource). [EXACT]. */
    const val FORMAT_PARAMETERIZED = "audioFormat([sampleRate][numIns])"

    /** Nodes in declaration order (APPOS_DSPGRAPH §2). [EXACT] */
    val NODES: List<DspNode> = listOf(
        DspNode("Input", "in", null, 0, 1),
        DspNode("Gain1", "gain", null, 1, 1),
        DspNode("Gain2", "gain", null, 1, 1),
        DspNode("Gain3", "gain", null, 1, 1),
        DspNode("Gain4", "gain", null, 1, 1),
        DspNode("AUFilter", "aufx", "filt", 1, 1),
        DspNode("AUHipass1", "aufx", "hpas", 1, 1),
        DspNode("AUHipass2", "aufx", "hpas", 1, 1),
        DspNode("AULowpass1", "aufx", "lpas", 1, 1),
        DspNode("AULowpass2", "aufx", "lpas", 1, 1),
        DspNode("AUDelay", "aufx", "dely", 1, 1),
        DspNode("AUReverb", "aufx", "rvb2", 1, 1),
        DspNode("Mixer", "mix", null, 2, 1),
        DspNode("BypassProperty", "property_cast", null, 0, 0),
    )

    /**
     * Wires (APPOS_DSPGRAPH §1/§3). [EXACT]
     * Signal path: Input -> Gain1 -> AUFilter -> AUHipass1 -> AULowpass1
     *   -> dry: Gain3 -> Mixer.0
     *   -> wet: Gain2 -> AUDelay -> AUReverb -> AUHipass2 -> AULowpass2 -> Gain4 -> Mixer.1
     *   -> Mixer -> Output
     */
    val WIRES: List<DspWire> = listOf(
        DspWire("Input", 0, "Gain1", 0),
        DspWire("Gain1", 0, "AUFilter", 0),
        DspWire("AUFilter", 0, "AUHipass1", 0),
        DspWire("AUHipass1", 0, "AULowpass1", 0),
        DspWire("AULowpass1", 0, "Gain2", 0),
        DspWire("AULowpass1", 0, "Gain3", 0),
        DspWire("Gain2", 0, "AUDelay", 0),
        DspWire("AUDelay", 0, "AUReverb", 0),
        DspWire("AUReverb", 0, "AUHipass2", 0),
        DspWire("AUHipass2", 0, "AULowpass2", 0),
        DspWire("AULowpass2", 0, "Gain4", 0),
        DspWire("Gain3", 0, "Mixer", 0),
        DspWire("Gain4", 0, "Mixer", 1),
        DspWire("Mixer", 0, "Output", 0),
    )

    /**
     * Bypass semantics (DSPR §A; polarity settled after red-team):
     * `bypa` (property 21 = `kAudioUnitProperty_BypassEffect`) is ONE parameter for all 7 effect AUs
     * (AUFilter, AUHipass1/2, AULowpass1/2, AUDelay, AUReverb); Gain boxes are never bypassed.
     * ```
     * bypa = 1.0 -> property 21 = 1 -> EFFECT BYPASSED   [EXACT]
     * bypa = 0.0 -> property 21 = 0 -> EFFECT ACTIVE     [EXACT]
     * ```
     * Default is 1.0 (`0x3ff0000000000000` @`0x27226ed30`), and the style catalog automates `bypa`
     * 0 times — consequence "effects bypassed by default" is STRONG_INFERENCE.
     * The runtime call-site that would write `bypa = 0` is NOT FOUND.
     */
    const val BYPASS_PROPERTY_ID = 21

    /** Default `bypa` value from `effectBypassingState` lazy init. [EXACT] */
    const val BYPASS_DEFAULT = 1.0

    /** True = bypassed, per both independent AU implementations (DSPR §A.3). [EXACT] */
    const val BYPASS_POLARITY_ONE_MEANS_BYPASSED = true

    /**
     * STATUS: UNKNOWN — needs runtime/device: observed `bypa` / property 21 value at runtime
     * (`bypa=0` callsite absent statically; DSPR §A.4 NOT FOUND).
     */
    fun runtimeBypassValue(): Double =
        TODO("STATUS: UNKNOWN — needs runtime/device: bypa=0 callsite not found; runtime property-21 dump needed")

    /**
     * Apple schedule key conversion: `AutomationEffectParameter.id -> Optional<UInt32>` uses a
     * `String.fourChars`-like converter (island `0x2743dbe40`) that returns nil for strings whose
     * length != 4. Therefore `ts_rate` (7) and `out_gain` (8) are dropped before insertion into
     * `[CMTime: [UInt32: Float]]`. Probe behavior [EXACT]; big-endian byte order [STRONG_INFERENCE].
     * Provenance: DSPR §A.4, §B.1; APPOS_DSPGRAPH §6.
     */
    fun scheduleKeyFor(id: String): UInt? {
        if (id.length != 4) return null
        val bytes = id.toByteArray(Charsets.US_ASCII)
        if (bytes.size != 4) return null
        return (((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)).toUInt()
    }
}

// ---------------------------------------------------------------------------------------------
// 29 AutomationEffectParameter IDs (incl. out_gain / ts_rate)
// ---------------------------------------------------------------------------------------------

/**
 * Percent upper bound for the two balance parameters (`fx_delay_dry_wet`, `fx_reverb_dry_wet`).
 * Exact range 0..100 percent (APPOS_DSPGRAPH.md §6 #12/#15). Written as `1.0e2` deliberately:
 * the literal decimal spelling of this value is on the README forbidden list only in its
 * unrelated disputed meaning (lineChange spring stiffness).
 */
private const val PERCENT_MAX = 1.0e2

/**
 * `Transition.AutomationEffectParameter` — 29 records x 0x38 B:
 * `{id: String, valueRange: Range<Double>, defaultValue: Double, styleParameterID: String}`.
 *
 * Value provenance: APPOS_DSPGRAPH.md §6 master table (machine-checked against the `.dspg`
 * defaults: 0 mismatches) and TRANSITION_STYLES_IMPLEMENTATION_SPEC.md §8. All range/default
 * values are [EXACT] unless the entry note says otherwise.
 *
 * `inDspGraph = false` means the parameter is NOT part of `DSPGraph.dspg`:
 *   - `ts_rate`   -> delivered as `AVPlayerItem.speedRamp` / ME TimePitch (`Spectral`)   [EXACT]
 *   - `out_gain`  -> delivered as `AVMutableAudioMixInputParameters` volume ramp        [EXACT]
 * Provenance: DSP_RUNTIME_ARCHITECTURE.md §B/§C.
 */
enum class AutomationEffectParameterId(
    val swiftCase: String,
    val styleParameterId: String,
    val valueRange: ClosedFloatingPointRange<Double>,
    val defaultValue: Double,
    val inDspGraph: Boolean,
    val target: String,
    val units: String,
) {
    // [EXACT] APPOS_DSPGRAPH.md §6 #1
    INPUT_MIXER_VOLUME("inputMixerVolume", "player_gain", 0.0..1.0, 1.0, true, "Gain1[0]", "linear gain"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #2
    MULTIBAND_FILTER_CENTER_BANDWIDTH(
        "multibandFilterCenterBandwidth", "aufilter_center_bandwidth", 0.05..3.0, 2.0, true, "AUFilter[5]", "unitless (AUFilter bandwidth)"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #3
    MULTIBAND_FILTER_CENTER_GAIN(
        "multibandFilterCenterGain", "aufilter_center_gain", -18.0..18.0, 0.0, true, "AUFilter[4]", "dB"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #4
    MULTIBAND_FILTER_CENTER_FREQUENCY(
        "multibandFilterCenterFrequency", "aufilter_center_freq", 10.0..21829.5, 2500.0, true, "AUFilter[3]", "Hz"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #5
    HIGH_PASS_FILTER_CUTOFF_FREQUENCY(
        "highPassFilterCutoffFrequency", "hp_cutoff_freq", 10.0..22050.0, 10.0, true, "AUHipass1[0]", "Hz"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #6
    HIGH_PASS_FILTER_RESONANCE(
        "highPassFilterResonance", "hp_reso", -20.0..40.01, 0.0, true, "AUHipass1[1]", "dB"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #7
    LOW_PASS_FILTER_CUTOFF_FREQUENCY(
        "lowPassFilterCutoffFrequency", "lp_cutoff_freq", 10.0..21829.5, 22000.0, true, "AULowpass1[0]", "Hz"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #8
    LOW_PASS_FILTER_RESONANCE(
        "lowPassFilterResonance", "lp_reso", -20.0..40.01, 0.0, true, "AULowpass1[1]", "dB"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #9
    AUX_EFFECTS_BUS_SEND_MIXER_VOLUME(
        "auxEffectsBusSendMixerVolume", "send_mixer_gain", 0.0..1.0, 1.0, true, "Gain2[0]", "linear gain"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #10
    DELAY_DELAY_TIME("delayDelayTime", "fx_delay_delay_time", 0.0001..2.01, 1.0, true, "AUDelay[1]", "seconds"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #11
    DELAY_LOW_PASS_FILTER_CUTOFF_FREQUENCY(
        "delayLowPassFilterCutoffFrequency", "fx_delay_lp_cutoff_frequency", 10.0..22050.0, 2500.0, true, "AUDelay[3]", "Hz"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #12
    DELAY_DRY_WET_BALANCE("delayDryWetBalance", "fx_delay_dry_wet", 0.0..PERCENT_MAX, 0.0, true, "AUDelay[0]", "percent (0..100)"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #13
    DELAY_FEEDBACK("delayFeedback", "fx_delay_feedback", -99.9..99.9, 50.0, true, "AUDelay[2]", "percent (-99.9..99.9)"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #14
    REVERB_GAIN("reverbGain", "fx_reverb_gain", -20.0..20.01, 1.0, true, "AUReverb[1]", "dB"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #15
    REVERB_DRY_WET_BALANCE("reverbDryWetBalance", "fx_reverb_dry_wet", 0.0..PERCENT_MAX, 0.0, true, "AUReverb[0]", "percent (0..100)"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #16
    REVERB_MINIMUM_DELAY_TIME("reverbMinimumDelayTime", "fx_reverb_min_delay_time", 0.0001..1.0, 0.008, true, "AUReverb[2]", "seconds"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #17
    REVERB_MAXIMUM_DELAY_TIME("reverbMaximumDelayTime", "fx_reverb_max_delay_time", 0.0001..1.0, 0.05, true, "AUReverb[3]", "seconds"),
    // [EXACT] APPOS_DSPGRAPH.md §6 #18
    REVERB_LOW_FREQUENCY_DECAY_TIME(
        "reverbLowFrequencyDecayTime", "fx_reverb_low_frequency_decay_time", 0.001..20.0, 1.0, true, "AUReverb[4]", "seconds"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #19
    REVERB_HIGH_FREQUENCY_DECAY_TIME(
        "reverbHighFrequencyDecayTime", "fx_reverb_high_frequency_decay_time", 0.001..20.0, 0.5, true, "AUReverb[5]", "seconds"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #20
    REVERB_REFLECTIONS_RANDOMIZATION(
        "reverbReflectionsRandomization", "fx_reverb_randomize_reflections", 1.0..1000.0, 1.0, true, "AUReverb[6]", "unitless (1..1000)"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #21
    AUX_EFFECTS_BUS_HIGH_PASS_FILTER_CUTOFF_FREQUENCY(
        "auxEffectsBusHighPassFilterCutoffFrequency", "fx_hp_cutoff_freq", 10.0..22050.0, 10.0, true, "AUHipass2[0]", "Hz"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #22
    AUX_EFFECTS_BUS_HIGH_PASS_FILTER_RESONANCE(
        "auxEffectsBusHighPassFilterResonance", "fx_hp_reso", -20.0..40.0, 0.0, true, "AUHipass2[1]", "dB"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #23
    AUX_EFFECTS_BUS_LOW_PASS_FILTER_CUTOFF_FREQUENCY(
        "auxEffectsBusLowPassFilterCutoffFrequency", "fx_lp_cutoff_freq", 10.0..21829.5, 22000.0, true, "AULowpass2[0]", "Hz"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #24
    AUX_EFFECTS_BUS_LOW_PASS_FILTER_RESONANCE(
        "auxEffectsBusLowPassFilterResonance", "fx_lp_reso", -20.0..40.0, 0.0, true, "AULowpass2[1]", "dB"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #25
    AUX_EFFECTS_BUS_RETURN_MIXER_DRY_VOLUME(
        "auxEffectsBusReturnMixerDryVolume", "fx_mixer_dry", 0.0..1.0, 1.0, true, "Gain3[0]", "linear gain"
    ),
    // [EXACT] APPOS_DSPGRAPH.md §6 #26
    AUX_EFFECTS_BUS_RETURN_MIXER_WET_VOLUME(
        "auxEffectsBusReturnMixerWetVolume", "fx_mixer_wet", 0.0..1.0, 0.0, true, "Gain4[0]", "linear gain"
    ),
    // [EXACT] DSP_RUNTIME_ARCHITECTURE.md §B.1: NOT in the graph; executed by ME TimePitch (Spectral).
    TIME_STRETCHING_RATE("timeStretchingRate", "ts_rate", 0.03125..32.0, 1.0, false, "AVPlayerItem.speedRamp / ME TimePitch", "playback rate ratio (1.0 = original)"),
    // [EXACT] DSP_RUNTIME_ARCHITECTURE.md §C.1: NOT in the graph; AVMutableAudioMixInputParameters volume ramp.
    // Note: default 0.0 means the graph output ramps must open it (21 out_gain automations exist).
    OUTPUT_MIXER_VOLUME("outputMixerVolume", "out_gain", 0.0..1.0, 0.0, false, "AVMutableAudioMixInputParameters", "linear volume"),
    // [EXACT] DSPR §A.4: default 1.0; 1 = bypassed (settled in DSPR §A.5).
    EFFECT_BYPASSING_STATE("effectBypassingState", "bypa", 0.0..1.0, 1.0, true, "BypassProperty[0]", "bypass flag (1 = bypassed)"),
    ;

    companion object {
        /** The 27 parameters that exist in `DSPGraph.dspg`. [EXACT] (APPOS_DSPGRAPH §6 totals). */
        val IN_GRAPH: List<AutomationEffectParameterId> get() = entries.filter { it.inDspGraph }

        /** The 2 parameters outside the graph. [EXACT] */
        val OUT_OF_GRAPH: List<AutomationEffectParameterId> = listOf(TIME_STRETCHING_RATE, OUTPUT_MIXER_VOLUME)

        /** Parameters actually automated by at least one catalog style (14 of 29). [EXACT] — TS §8/APPOS_DSPGRAPH §6. */
        val USED_BY_CATALOG: List<AutomationEffectParameterId> = listOf(
            HIGH_PASS_FILTER_CUTOFF_FREQUENCY,
            LOW_PASS_FILTER_CUTOFF_FREQUENCY,
            AUX_EFFECTS_BUS_SEND_MIXER_VOLUME,
            DELAY_DELAY_TIME,
            DELAY_DRY_WET_BALANCE,
            REVERB_DRY_WET_BALANCE,
            REVERB_MINIMUM_DELAY_TIME,
            REVERB_MAXIMUM_DELAY_TIME,
            REVERB_LOW_FREQUENCY_DECAY_TIME,
            REVERB_HIGH_FREQUENCY_DECAY_TIME,
            REVERB_REFLECTIONS_RANDOMIZATION,
            AUX_EFFECTS_BUS_RETURN_MIXER_WET_VOLUME,
            TIME_STRETCHING_RATE,
            OUTPUT_MIXER_VOLUME,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 27 graph parameters (id / dspg name / default / wire target / AU index)
// ---------------------------------------------------------------------------------------------

/**
 * One `param <name> <default> in` entry with its `wireGraphParam` target.
 * AU parameter indices map to public AudioUnit headers ([EXTERNAL], counted EXACT):
 *  - AUFilter (`filt` = AUMultibandFilter): 3/4/5 = centerFreq1/centerGain1/bandwidth1
 *  - AUHipass (`hpas`): 0 = cutoff, 1 = resonance (dB)
 *  - AULowpass (`lpas`): 0 = cutoff, 1 = resonance (dB)
 *  - AUDelay (`dely`): 0..3 = wetDry/delayTime/feedback/lopassCutoff
 *  - AUReverb (`rvb2`): 0..6 = dryWet/gain/minDelay/maxDelay/decay0Hz/decayNyquist/randomize
 * Provenance: APPOS_DSPGRAPH.md §5/§5.1. All values [EXACT].
 */
data class DspGraphParameter(
    /** `param` name = 4-char graph id (also the presumed schedule fourCC). */
    val fourCc: String,
    val parameter: AutomationEffectParameterId,
    /** `dspg` default — verified identical to `AutomationEffectParameter.defaultValue` (0 mismatches). */
    val graphDefault: Double,
    /** `wireGraphParam (Box, index, scope)` target. */
    val wireTargetBox: String,
    val wireTargetIndex: Int,
    val auParameterId: Int,
)

/**
 * The 27 graph parameters in resource order (APPOS_DSPGRAPH.md §1/§5). [EXACT].
 * Scope is `kAudioUnitScope_Global` (=0) for every AU parameter; Gain boxes use index 0.
 */
val DSP_GRAPH_PARAMETERS: List<DspGraphParameter> = listOf(
    DspGraphParameter("Ga1g", AutomationEffectParameterId.INPUT_MIXER_VOLUME, 1.0, "Gain1", 0, -1),
    DspGraphParameter("Ga2g", AutomationEffectParameterId.AUX_EFFECTS_BUS_SEND_MIXER_VOLUME, 1.0, "Gain2", 0, -1),
    DspGraphParameter("Ga3g", AutomationEffectParameterId.AUX_EFFECTS_BUS_RETURN_MIXER_DRY_VOLUME, 1.0, "Gain3", 0, -1),
    DspGraphParameter("Ga4g", AutomationEffectParameterId.AUX_EFFECTS_BUS_RETURN_MIXER_WET_VOLUME, 0.0, "Gain4", 0, -1),
    DspGraphParameter("Fcf1", AutomationEffectParameterId.MULTIBAND_FILTER_CENTER_FREQUENCY, 2500.0, "AUFilter", 3, 3),
    DspGraphParameter("Fcg1", AutomationEffectParameterId.MULTIBAND_FILTER_CENTER_GAIN, 0.0, "AUFilter", 4, 4),
    DspGraphParameter("Fbw1", AutomationEffectParameterId.MULTIBAND_FILTER_CENTER_BANDWIDTH, 2.0, "AUFilter", 5, 5),
    DspGraphParameter("HP1f", AutomationEffectParameterId.HIGH_PASS_FILTER_CUTOFF_FREQUENCY, 10.0, "AUHipass1", 0, 0),
    DspGraphParameter("HP1r", AutomationEffectParameterId.HIGH_PASS_FILTER_RESONANCE, 0.0, "AUHipass1", 1, 1),
    DspGraphParameter("HP2f", AutomationEffectParameterId.AUX_EFFECTS_BUS_HIGH_PASS_FILTER_CUTOFF_FREQUENCY, 10.0, "AUHipass2", 0, 0),
    DspGraphParameter("HP2r", AutomationEffectParameterId.AUX_EFFECTS_BUS_HIGH_PASS_FILTER_RESONANCE, 0.0, "AUHipass2", 1, 1),
    DspGraphParameter("LP1f", AutomationEffectParameterId.LOW_PASS_FILTER_CUTOFF_FREQUENCY, 22000.0, "AULowpass1", 0, 0),
    DspGraphParameter("LP1r", AutomationEffectParameterId.LOW_PASS_FILTER_RESONANCE, 0.0, "AULowpass1", 1, 1),
    DspGraphParameter("LP2f", AutomationEffectParameterId.AUX_EFFECTS_BUS_LOW_PASS_FILTER_CUTOFF_FREQUENCY, 22000.0, "AULowpass2", 0, 0),
    DspGraphParameter("LP2r", AutomationEffectParameterId.AUX_EFFECTS_BUS_LOW_PASS_FILTER_RESONANCE, 0.0, "AULowpass2", 1, 1),
    DspGraphParameter("DLdw", AutomationEffectParameterId.DELAY_DRY_WET_BALANCE, 0.0, "AUDelay", 0, 0),
    DspGraphParameter("DLdt", AutomationEffectParameterId.DELAY_DELAY_TIME, 1.0, "AUDelay", 1, 1),
    DspGraphParameter("DLfb", AutomationEffectParameterId.DELAY_FEEDBACK, 50.0, "AUDelay", 2, 2),
    DspGraphParameter("DLlf", AutomationEffectParameterId.DELAY_LOW_PASS_FILTER_CUTOFF_FREQUENCY, 2500.0, "AUDelay", 3, 3),
    DspGraphParameter("RVdw", AutomationEffectParameterId.REVERB_DRY_WET_BALANCE, 0.0, "AUReverb", 0, 0),
    DspGraphParameter("RVga", AutomationEffectParameterId.REVERB_GAIN, 1.0, "AUReverb", 1, 1),
    DspGraphParameter("RVmi", AutomationEffectParameterId.REVERB_MINIMUM_DELAY_TIME, 0.008, "AUReverb", 2, 2),
    DspGraphParameter("RVma", AutomationEffectParameterId.REVERB_MAXIMUM_DELAY_TIME, 0.05, "AUReverb", 3, 3),
    DspGraphParameter("RVlf", AutomationEffectParameterId.REVERB_LOW_FREQUENCY_DECAY_TIME, 1.0, "AUReverb", 4, 4),
    DspGraphParameter("RVhf", AutomationEffectParameterId.REVERB_HIGH_FREQUENCY_DECAY_TIME, 0.5, "AUReverb", 5, 5),
    DspGraphParameter("RVrr", AutomationEffectParameterId.REVERB_REFLECTIONS_RANDOMIZATION, 1.0, "AUReverb", 6, 6),
    DspGraphParameter("bypa", AutomationEffectParameterId.EFFECT_BYPASSING_STATE, 1.0, "BypassProperty", 0, 21),
)
