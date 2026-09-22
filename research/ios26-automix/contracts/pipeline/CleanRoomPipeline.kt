// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.pipeline

import apple.music.contracts.dsp.DspGraphSpec
import apple.music.contracts.dsp.SteppedSchedule
import apple.music.contracts.dsp.TimeStretchingStep

/*
 * CleanRoomPipeline.kt — stage interfaces for the SmartTransitions audio pipeline.
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §2 (Track A/B block/edge spec)
 *  - deepseek_analysis/10_final_closure/DSP_RUNTIME_ARCHITECTURE.md §B–§D (order, per-track instances)
 *  - deepseek_analysis/06_android_port/MEDIA3_INTEGRATION.md (port seams, no code copied)
 *  - deepseek_analysis/06_android_port/ANDROID_ARCHITECTURE.md §1/§3 (pipeline flow)
 *
 * ORDER FACTS (do not "fix" downstream by assumption):
 *  - Apple-proven position of TimePitch is DOWNSTREAM of the DSP/mix effect -> STRONG_INFERENCE.
 *  - The contract order `time-stretch -> per-track DSP` (Track A/B below) is a PORT DESIGN decision;
 *    stage interfaces are order-independent.
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

/** Evidence classification used by every pipeline descriptor. */
enum class EvidenceStatus { EXACT, STRONG_INFERENCE, PARTIAL, UNKNOWN, PORT_DESIGN }

/** One of the two simultaneously played tracks. */
enum class TrackId { OUTGOING, INCOMING }

/** Pipeline stage kinds. */
enum class StageKind { DECODE, TIME_STRETCH, PER_TRACK_DSP, GAIN_RAMP, MIXER_OUTPUT }

/** Stage descriptor carrying both Apple evidence and port binding (contract §2.2/§2.3). */
data class StageDescriptor(
    val kind: StageKind,
    val appleBehavior: String,
    val appleStatus: EvidenceStatus,
    val portBinding: String,
    val portStatus: EvidenceStatus,
)

/** Static description of the pipeline; mirrors the contract. */
val PIPELINE_DESCRIPTORS: List<StageDescriptor> = listOf(
    StageDescriptor(
        kind = StageKind.DECODE,
        appleBehavior = "AVPlayerItem / MPC item decode (decoder itself not decompiled)",
        appleStatus = EvidenceStatus.EXACT,
        portBinding = "Media3 MediaCodecAudioRenderer / decoder",
        portStatus = EvidenceStatus.EXACT,
    ),
    StageDescriptor(
        kind = StageKind.TIME_STRETCH,
        appleBehavior = "TimeStretchingSchedule.{outgoing,incoming}SongSteps -> AVPlayerItem.speedRamp -> " +
            "ME TimePitch (Spectral); position relative to DSP is downstream [STRONG_INFERENCE]",
        appleStatus = EvidenceStatus.STRONG_INFERENCE,
        portBinding = "SpeedChangingAudioProcessor + SpeedProvider; spectral-quality C++ node is OPEN",
        portStatus = EvidenceStatus.PORT_DESIGN,
    ),
    StageDescriptor(
        kind = StageKind.PER_TRACK_DSP,
        appleBehavior = "Per-track AVAudioMixProcessingEffect (2 allocations, 2 schedules); graph " +
            "\"SmartTransitions\"; effects bypassed by default (bypa=1.0)",
        appleStatus = EvidenceStatus.EXACT,
        portBinding = "2 x DspGraphAudioProcessor; explicit bypass runtime contract",
        portStatus = EvidenceStatus.PORT_DESIGN,
    ),
    StageDescriptor(
        kind = StageKind.GAIN_RAMP,
        appleBehavior = "AVMutableAudioMixInputParameters volume ramps from out_gain automations " +
            "(2 independent mixes, CMTime timescale 1e9)",
        appleStatus = EvidenceStatus.EXACT,
        portBinding = "GainProcessor / GainProvider (do not duplicate PlayerAudioFadeControl)",
        portStatus = EvidenceStatus.EXACT,
    ),
    StageDescriptor(
        kind = StageKind.MIXER_OUTPUT,
        appleBehavior = "ME mixer bus -> output; MPC smartTransitionWillBegin/DidEnd orchestration",
        appleStatus = EvidenceStatus.PARTIAL,
        portBinding = "2 renderers + secondary sink + PlayerAudioFadeControl + DefaultAudioSink -> AudioTrack",
        portStatus = EvidenceStatus.PORT_DESIGN,
    ),
)

/** PCM source abstraction produced by decode. */
interface PcmSource {
    val sampleRateHz: Int
    val channelCount: Int
    /** Read the next float frames; return count or -1 on end. [PORT DESIGN] */
    fun read(destination: FloatArray): Int
}

/** Stage 1: decode the media item into a PCM source. [EXACT] existence; [PORT DESIGN] interface. */
interface DecodeStage {
    fun open(track: TrackId): PcmSource
}

/**
 * Stage 2: optional time-stretch.
 * Apple executor: ME TimePitch (Spectral) driven by `AVPlayerItem.speedRamp` built from
 * `TimeStretchingStep{playbackRate,timeRange}`. DSPR §B. [EXACT] mechanism; automations->steps NOT FOUND.
 */
interface TimeStretchStage {
    /** True when the plan contains `ts_rate` automations (styles 6..12; reachable 8/9/12). [EXACT] */
    val isActive: Boolean
    /** Steps for one side of the transition. [EXACT] type. */
    fun steps(track: TrackId): List<TimeStretchingStep>

    /**
     * STATUS: UNKNOWN — needs runtime/device: planner function turning `ts_rate` automations into
     * `TimeStretchingStep`s is not localized (DSPR NOT FOUND #3).
     */
    fun compileFromAutomations(parameterId: String): List<TimeStretchingStep> =
        TODO("STATUS: UNKNOWN — needs runtime/device: automations->TimeStretchingStep function NOT FOUND")
}

/** Stage 3: per-track DSP graph instance. [EXACT] per-track allocation; [PORT DESIGN] host interface. */
interface DspStage {
    /** Instantiate the graph for one side. Identifier: [DspGraphSpec.GRAPH_IDENTIFIER]. */
    fun instantiate(track: TrackId): DspGraphInstance

    /** Apply an automation schedule (stepped/continuous) to this instance. [EXACT] concept. */
    fun applySchedule(instance: DspGraphInstance, schedule: SteppedSchedule)

    /**
     * Whether effect AUs are bypassed. Default follows `bypa = 1.0` (BYPASSED).
     * STATUS: UNKNOWN — needs runtime/device: actual runtime bypa value (no `bypa=0` callsite found).
     */
    fun effectsBypassed(): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: runtime bypa/property-21 value not observed")
}

/** One per-track graph instance handle. [PORT DESIGN] host type; graph content from DspGraphSpec. */
data class DspGraphInstance(
    val identifier: String = DspGraphSpec.GRAPH_IDENTIFIER,
    val track: TrackId,
)

/**
 * Stage 4: external gain ramp (out_gain), owned by the player-item layer, not the graph.
 * Apple: `setVolumeRampFromStartVolume:toEndVolume:timeRange:` with CMTime timescale 1e9 (DSPR §C.2). [EXACT]
 */
interface GainStage {
    /** Linear volume ramp points (0.0..1.0) for one side. [EXACT] units. */
    fun ramps(track: TrackId): List<VolumeRampPoint>
}

/** One linear volume ramp segment. Apple timescale 1e9, linear Float volume 0..1. [EXACT] semantics. */
data class VolumeRampPoint(
    val startSeconds: Double,
    val endSeconds: Double,
    val startVolume: Float,
    val endVolume: Float,
)

/**
 * Stage 5: mixer / output handoff (Media3 fork seam). Contract §2.2 A4/B4: PARTIAL.
 */
interface MixerStage {
    fun prepare(tracks: List<TrackId>)
    fun release()
}

/**
 * The full pipeline composition. Execution order is the port decision (Track A/B diagram in the
 * contract §2.1); stage implementations do not encode the order.
 */
interface CrossfadePipeline {
    val decode: DecodeStage
    val timeStretch: TimeStretchStage
    val dsp: DspStage
    val gain: GainStage
    val mixer: MixerStage

    /**
     * TODO (PORT DESIGN): wire one crossfade execution.
     * Required exact behaviors:
     *  - 2 per-side DSP instances (never a shared one); aux delay/reverb state is per instance;
     *  - per-side volume ramps from `out_gain`;
     *  - staged automation delivery on the selected policy (Stepped default 0.2 s, clamp 0.0001..1.0);
     *  - fallback when no DSP plan: processor inactive (passthrough).
     * STATUS: UNKNOWN — needs runtime/device: relative order `gain` vs `dsp` on the item level is PARTIAL.
     */
    fun execute(tracks: List<TrackId>): Unit =
        TODO("STATUS: UNKNOWN — needs runtime/device: item-level gain/effect relative order PARTIAL (contract E3)")
}
