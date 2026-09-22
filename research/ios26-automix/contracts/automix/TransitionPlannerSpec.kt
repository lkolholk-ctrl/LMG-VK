// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.automix

import apple.music.contracts.analysis.Criteria
import apple.music.contracts.analysis.Tonality
import apple.music.contracts.analysis.VocalPenalty

/*
 * TransitionPlannerSpec.kt — deterministic skeleton of the iOS 26 Apple Music TransitionPlanner
 * (clean-room, behavior-only). No Apple source text copied; everything below is re-expressed
 * pseudocode from disassembly/pseudocode/raw resources.
 *
 * PROVENANCE (main):
 *  - deepseek_analysis/10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md (PLC; canonical spec)
 *  - deepseek_analysis/10_final_closure/FINAL_BLOCKED_QUESTIONS.md §1 (#2 closure: param_1)
 *  - deepseek_analysis/10_final_closure/FINAL_RED_TEAM_AUDIT.md §10 (#2 independent verification)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.2
 *  - deepseek_analysis/06_android_port/ANDROID_AUTOMIX.md (algorithmic cross-check)
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 * RULE: every unknown step is TODO("STATUS: UNKNOWN — needs runtime/device: ..."); never invented.
 */

object TransitionPlannerSpec {

    // -----------------------------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------------------------

    /**
     * `TransitionPlanner.transition(from:to:criterias:) -> Result<Transition, FailureReason>`.
     * Symbol `_$s015_SonicKit_MusicB9_Packages17TransitionPlannerV10transition4from2to8criterias6...KF`
     * @`0x272267700`. Provenance: PLC §1.1. [EXACT] symbol; literal call-site NOT FOUND (MC §3).
     *
     * STATUS: UNKNOWN — needs runtime/device: literal call-site / call-level field binding.
     */
    fun transition(
        outgoing: apple.music.contracts.analysis.SongAnalysis,
        incoming: apple.music.contracts.analysis.SongAnalysis,
        criteria: Criteria,
        expandedTempoRange: Boolean = false,
    ): Result<TransitionPlan> =
        TODO("STATUS: UNKNOWN — needs runtime/device: full transition() flow not reconstructable statically")

    // -----------------------------------------------------------------------------------------
    // §4 Gates
    // -----------------------------------------------------------------------------------------

    /**
     * Complexity gate (`FUN_27222a728`):
     * ```
     * pass1 = maximumComplexity >= algorithmComplexity          // context+0x78 byte
     * pass2 = membership(algorithmComplexity, reducedSet(outgoing))
     *      && membership(algorithmComplexity, reducedSet(incoming))
     * allowed = pass1 && pass2
     * ```
     * Provenance: PLC §4.1 (`ldrb w19,[x0]`; `ldrb w8,[x20,#0x78]`; `cmp/b.cs`). [EXACT]
     */
    fun complexityGateAllowed(
        maximumComplexity: Int,
        algorithmComplexity: Int,
        outgoingReducedSet: Set<Int>,
        incomingReducedSet: Set<Int>,
    ): Boolean =
        maximumComplexity >= algorithmComplexity &&
            algorithmComplexity in outgoingReducedSet &&
            algorithmComplexity in incomingReducedSet

    /**
     * Reduced-complexity arrays (static `[Transition.Complexity]` byte arrays, not bitmasks).
     * Provenance: PLC §4.2 raw dumps. [EXACT] bytes; reachability of "low" [STRONG_INFERENCE].
     */
    object ReducedComplexitySets {
        /** `0x2884aa4d0` = {0,1,2,3} — "All transitions allowed". [EXACT] */
        val ALL = setOf(0, 1, 2, 3)

        /** `0x2884aa4f8` = {0,1,2} — "low" duration-confidence index. [EXACT] bytes. */
        val LOW_CONFIDENCE = setOf(0, 1, 2)

        /** `0x2884aa520` = {0} — fallback only. [EXACT] */
        val NONE_CONFIDENCE = setOf(0)

        /** `0x2884aa548` = {0,3} — beat-matched or fallback. [EXACT] */
        val BEAT_MATCHED_OR_FALLBACK = setOf(0, 3)

        /** `0x2884aa570` = {0} — fallback only (spatial branch). [EXACT] */
        val SPATIAL_NONE = setOf(0)
    }

    /**
     * Complexity-confidence classes (`TransitionComplexityConfidence`): none=0, low=1, high=2.
     * PTR table `0x27a974888` -> [`0x2884aa520`, `0x2884aa4f8`, `0x2884aa4d0`].
     * Provenance: PLC §4.3. [EXACT]
     */
    enum class ComplexityConfidence(val index: Int, val reducedSet: Set<Int>) {
        NONE(0, ReducedComplexitySets.NONE_CONFIDENCE),
        LOW(1, ReducedComplexitySets.LOW_CONFIDENCE),
        HIGH(2, ReducedComplexitySets.ALL),
    }

    /** Duration delta that counts as "significant". `|Δt| >= 2.0 s` (`FUN_272238a20` @`0x272238a4c`). [EXACT] */
    const val DURATION_DELTA_SIGNIFICANT_SECONDS = 2.0

    /**
     * Duration-confidence from expected/actual durations (`FUN_272224eac`):
     * `flag & 1 == 0`: `|expected - actual| < 2.0` -> HIGH else NONE;
     * `flag & 1 == 1`: HIGH. Constant `0x4000000000000000` (=2.0) @`0x2722250f4` — threshold STRICT `<`.
     * The LOW index is not produced by this function (PLC §4.3). [EXACT]
     */
    fun durationConfidence(expected: Double, actual: Double, durationDataAvailable: Boolean): ComplexityConfidence =
        if (durationDataAvailable) {
            ComplexityConfidence.HIGH
        } else if (kotlin.math.abs(expected - actual) < 2.0) {
            ComplexityConfidence.HIGH
        } else {
            ComplexityConfidence.NONE
        }

    /** Spatial time-drift threshold `0.04` (raw `0x3fa47ae147ae147b`, `FUN_272225810`). [EXACT] — PLC §4.4. */
    const val SPATIAL_DRIFT_THRESHOLD_SECONDS = 0.04

    /**
     * Beat-stability tolerance `0.031 s` used by `beatStabilityMap`
     * (P2_MEDIADSP_CLOSURE; APPOS_MASTER_SUMMARY §12.1). [STRONG_INFERENCE]
     */
    const val BEAT_STABILITY_TOLERANCE_SECONDS = 0.031

    /**
     * Spatial confidence + drift -> reduced set (`FUN_272225204`; PLC §4.4):
     * ```
     * close = FUN_2722255d0(flags)   // 2=close, 1=low, 0=none
     * drift = FUN_272225810(drift, haveData) // have && drift <= 0.04 -> 2; else 1; no data -> 0
     * if close == 0 -> {0}
     * elif close == 1 && drift == 2 -> {0,3}
     * elif close == 2 && drift == 2 -> {0,1,2,3}
     * else -> {0}
     * ```
     * [EXACT] thresholds and branches.
     */
    fun spatialReducedSet(close: Int, driftScore: Int): Set<Int> = when {
        close == 0 -> ReducedComplexitySets.NONE_CONFIDENCE
        close == 1 && driftScore == 2 -> ReducedComplexitySets.BEAT_MATCHED_OR_FALLBACK
        close == 2 && driftScore == 2 -> ReducedComplexitySets.ALL
        else -> ReducedComplexitySets.NONE_CONFIDENCE
    }

    /**
     * Intersection of duration and spatial reduced sets (`FUN_272221994`).
     * Empty intersection logs "Duration-based and spatial transition complexities do not overlap."
     * and yields an empty array (STRONG_INFERENCE per PLC §4.4).
     */
    fun combinedReducedSet(duration: ComplexityConfidence, spatial: Set<Int>): Set<Int> =
        duration.reducedSet intersect spatial

    // -----------------------------------------------------------------------------------------
    // §5 Tempo matching
    // -----------------------------------------------------------------------------------------

    /**
     * Normal-mode tempo tolerance. Raw `0x3fc47ae147ae147b`, pool `0x272298f30`, load @`0x2722365c0`.
     * Provenance: PLC §5.2. [EXACT]
     */
    const val TEMPO_TOLERANCE_NORMAL = 0.16

    /**
     * Expanded-mode tempo tolerance. Raw `0x3fd25e353f7ced91`, pool `0x272298f38`, load @`0x2722365c8`,
     * selection `fcsel` @`0x2722365cc`. Provenance: PLC §5.2. [EXACT]
     */
    const val TEMPO_TOLERANCE_EXPANDED = 0.287

    /** `60.0` BPM -> period conversion constant (`0x404e000000000000`). PLC §5.1. [EXACT] */
    const val SECONDS_PER_MINUTE = 60.0

    /** Packed tempo-incompatibility tag `0xfc` (`mov w19,#0xfc` @`0x272219ff0`). PLC §5.1. [EXACT] */
    const val TEMPO_INCOMPATIBLE_TAG = 0xfc

    /** Tolerance selection helper (duplicate gate `FUN_272243850` has the same literals). PLC §5.2. [EXACT] */
    fun tempoTolerance(expanded: Boolean): Double =
        if (expanded) TEMPO_TOLERANCE_EXPANDED else TEMPO_TOLERANCE_NORMAL

    /**
     * Tempo comparison `FUN_272219ff0` (PLC §5.1 [EXACT]):
     * ```
     * if (!hasA || !hasB) return COMPATIBLE        // "First ... tempo not available" -> compatible
     * a = f(tempoA); b = g(tempoB)                 // 60.0/bpm <-> bpm by representation flags
     * return if (|a - b| <= tol) COMPATIBLE else INCOMPATIBLE   // result tag 0xfc = incompatible
     * ```
     * Special cases from logs: multiple outgoing tempos -> COMPATIBLE.
     *
     * STATUS: UNKNOWN — needs runtime/device: what drives the `60.0/bpm <-> bpm` representation
     * flag pair (`f`/`g`) is not traced (PLC §5.4: bpm getter is not consumed by the planner).
     */
    fun tempoRelationship(
        tolerance: Double,
        tempoA: Double?,
        tempoB: Double?,
        multipleOutgoingTempos: Boolean = false,
    ): TempoRelationship {
        if (tempoA == null || tempoB == null) return TempoRelationship.COMPATIBLE
        if (multipleOutgoingTempos) return TempoRelationship.COMPATIBLE
        return if (kotlin.math.abs(tempoA - tempoB) <= tolerance) {
            TempoRelationship.COMPATIBLE
        } else {
            TempoRelationship.INCOMPATIBLE
        }
    }

    /**
     * `FUN_27221a300`: choose the `TempoBinaryScaleFactor` case (half/one/two) minimizing
     * `|tempo_candidate - tempo_ref|`; empty list -> ONE (tag 1). Arithmetic [EXACT] (PLC §5.3).
     */
    fun bestBinaryScaleFactor(tempoRef: Double, candidates: List<Double>): TempoBinaryScaleFactor {
        if (candidates.isEmpty()) return TempoBinaryScaleFactor.ONE
        var best = TempoBinaryScaleFactor.ONE
        var bestDelta = Double.MAX_VALUE
        val options = listOf(
            TempoBinaryScaleFactor.HALF to tempoRef * 0.5,
            TempoBinaryScaleFactor.ONE to tempoRef,
            TempoBinaryScaleFactor.TWO to tempoRef * 2.0,
        )
        for ((factor, candidate) in options) {
            val delta = kotlin.math.abs(candidate - tempoRef)
            if (delta < bestDelta) {
                bestDelta = delta
                best = factor
            }
        }
        return best
    }

    // -----------------------------------------------------------------------------------------
    // §6 Tonality (melodicness fallback) — relationship formula UNKNOWN
    // -----------------------------------------------------------------------------------------

    /**
     * Tonality fallback (`FUN_272231d04`, PLC §6):
     * ```
     * if relationship == incompatible (tag 3):
     *     if melodicness insignificant(outgoing) && insignificant(incoming): compatible
     *     else: incompatible
     * else: compatible
     * ```
     * Rule/logs [EXACT]; combination ("both insignificant") [STRONG_INFERENCE];
     * the tonality relationship formula itself is UNKNOWN.
     */
    fun tonalityCompatible(
        relationship: TempoRelationship,
        outgoingMelodicnessInsignificant: Boolean,
        incomingMelodicnessInsignificant: Boolean,
    ): Boolean = when (relationship) {
        TempoRelationship.COMPATIBLE -> true
        TempoRelationship.INCOMPATIBLE ->
            outgoingMelodicnessInsignificant && incomingMelodicnessInsignificant
    }

    /** STATUS: UNKNOWN — needs runtime/device: tonic/mode compatibility formula (PLC §6). */
    fun tonalityRelationship(outgoing: Tonality?, incoming: Tonality?): TempoRelationship =
        TODO("STATUS: UNKNOWN — needs runtime/device: tonality relationship formula not recovered")

    // -----------------------------------------------------------------------------------------
    // §4.7 Musicality bands (polarity per RT A3 / M2P §2.4)
    // -----------------------------------------------------------------------------------------

    object Musicality {
        /** `FUN_272243f80`: `v < 0.85 || 1.0 < v` -> TRUE = insignificant. Bands [EXACT] (PLC §4.7). */
        fun acousticnessInsignificant(v: Double): Boolean = v < 0.85 || 1.0 < v

        /** `FUN_27224400c`: `0.3 <= v <= 1.0` -> TRUE = significant. Bands [EXACT]. */
        fun danceabilitySignificant(v: Double): Boolean = v in 0.3..1.0

        /** `FUN_2722341d4`: `v < 0.25 || 1.0 < v` -> TRUE = insignificant. Bands [EXACT]. */
        fun melodicnessInsignificant(v: Double): Boolean = v < 0.25 || 1.0 < v

        /**
         * Musicality compatibility composition `FUN_272242fd0` (logs EXACT; outgoing/incoming
         * binding STRONG_INFERENCE per RT §4.3). Returns null when compatibility itself fails.
         *
         * STATUS: UNKNOWN — needs runtime/device: exact incompatible-combination rule not fully traced.
         */
        fun compatible(
            outgoingAcousticness: Double?,
            incomingAcousticness: Double?,
            outgoingDanceability: Double?,
            incomingDanceability: Double?,
        ): Boolean? =
            TODO("STATUS: UNKNOWN — needs runtime/device: musicality compatibility composition (PLC §4.7)")
    }

    // -----------------------------------------------------------------------------------------
    // §4.6 / §8.3 Structural + loudness constants
    // -----------------------------------------------------------------------------------------

    /** Minimum matching bar count gate: `if (7 < matchingBarCount) return 1` (`FUN_272235fd8`). [EXACT] */
    const val MIN_MATCHING_BARS = 8

    /** `FUN_272235fd8`. [EXACT] (PLC §4.6). */
    fun minimumBarCountSatisfied(matchingBarCount: Int): Boolean = 7 < matchingBarCount

    /** Silence threshold `value <= -30.0` in `FUN_272232790` @`0x272232884`. Value [EXACT]; role STRONG. */
    const val SILENCE_THRESHOLD_DB = -30.0

    /** Fade analysis window `[x-10.0, x]` (`FUN_272238be8` @`0x272238c0c`). Value [EXACT]; role PARTIAL. */
    const val FADE_WINDOW_SECONDS = -10.0

    /** Clamp helper `<= min(x, 10.0)` (`FUN_272238e38` @`0x272238e60`). Value [EXACT]; role PARTIAL. */
    const val LOUDNESS_CLAMP_MAX = 10.0

    /** Average-loudness multiplier for non-silent regions (`FUN_272232790` @`0x2722327e8`). Role STRONG. */
    const val AVERAGE_LOUDNESS_MULTIPLIER = 1.0

    // -----------------------------------------------------------------------------------------
    // §4.10 Duration limits (values EXACT, semantics PARTIAL)
    // -----------------------------------------------------------------------------------------

    /**
     * Duration constants in `FUN_2722210e0` / `FUN_27222171c` (CN §4): 60.0, 30.0, 2.0, 0.5, -30.0.
     * Values [EXACT]; exact semantics ("Maximum ... transition duration for long/short song") [PARTIAL].
     */
    object DurationLimits {
        const val LONG_SONG_LIMIT_SECONDS = 60.0
        const val SHORT_SONG_LIMIT_SECONDS = 30.0
        const val MIN_TRANSITION_SECONDS = 2.0
        const val OFFSET_SECONDS = 0.5
        const val NEGATIVE_MARGIN_SECONDS = -30.0

        /** STATUS: UNKNOWN — needs runtime/device: exact rule wiring these limits into the plan. */
        fun clampTransitionDuration(requested: Double): Double =
            TODO("STATUS: UNKNOWN — needs runtime/device: duration-limit semantics PARTIAL (PLC §4.10)")
    }

    // -----------------------------------------------------------------------------------------
    // §7 Vocal rules
    // -----------------------------------------------------------------------------------------

    /**
     * Leading-incoming vocal significance -> weight. `FUN_272235198` + `fcsel` @`0x272234538`:
     * significant -> 0.75, otherwise 1.0. Provenance: PLC §7.2. [EXACT]
     */
    fun leadingVocalWeight(significant: Boolean): Double =
        if (significant) VocalPenalty.SIGNIFICANT else 1.0

    /**
     * Vocal activity relationship (`FUN_272236188`, binary factor; logs "Vocal activity relationship:
     * Incompatible. ... map not available."). Factor [STRONG_INFERENCE]; compatibility condition [PARTIAL].
     *
     * STATUS: UNKNOWN — needs runtime/device: vocal-map compatibility condition not recovered.
     */
    fun vocalRelationshipCompatible(outgoingMapAvailable: Boolean, incomingMapAvailable: Boolean): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: vocal relationship condition PARTIAL (PLC §7.3)")

    // -----------------------------------------------------------------------------------------
    // §10 Scoring / §2.5 winner / §13 fallback
    // -----------------------------------------------------------------------------------------

    /** Base score for style id 0x8 and 0xc (4 factors), `0x4024000000000000` @`0x2722345ec`, @`0x272234a24`. [EXACT] */
    const val BASE_STYLE_8 = 10.0

    /** Base score for style id 0x9 (6 factors), `0x402e000000000000` @`0x27223472c`. [EXACT] */
    const val BASE_STYLE_9 = 15.0

    /** DeadAir call-site base, `0x272238868`. [EXACT] */
    const val BASE_DEAD_AIR = 2.0

    /** Fallback call-site base, `0x3ff0000000000000` @`0x272239b48`. [EXACT] */
    const val BASE_FALLBACK = 1.0

    /** Scheduling call-sites base (`FUN_27223c6cc`). [EXACT] */
    const val BASE_SCHEDULING = 3.0

    /** Tie-breaker scale `0.001` (`0x3f50624dd2f1a9fc` @`0x27222d680`). [EXACT] */
    const val TIE_BREAKER_SCALE = 0.001

    /** Fallback planning duration 2.0 s (`0x4000000000000000`), hard-coded in `FUN_272239248`. [EXACT] */
    const val FALLBACK_DURATION_SECONDS = 2.0

    /** Fallback region shift `outgoing - 2.0` (`0x2722395e0`). Value/instruction [EXACT]; full role PARTIAL. */
    const val FALLBACK_OUTGOING_SHIFT_SECONDS = -2.0

    /**
     * Winner selection `FUN_27222e800` (PLC §2.5, [EXACT]):
     * ```
     * winner = null
     * for c in candidates:
     *     if c.score <= 0.0: continue
     *     if winner == null || c.score > winner.score: winner = c   // strict '>'
     * // ties keep the earlier candidate
     * ```
     */
    fun selectWinner(candidates: List<TransitionCandidate>): TransitionCandidate? {
        var winner: TransitionCandidate? = null
        for (candidate in candidates) {
            val score = candidate.score()
            if (score <= 0.0) continue
            if (winner == null || score > winner.score()) winner = candidate
        }
        return winner
    }

    /**
     * Style dispatch: `FUN_272234380` serves ONLY style ids 8 / 9 / 0xc; any other id zero-fills a
     * 0x100-byte candidate (which is then never selected). Provenance: PLC §2.1/§11.3. [EXACT]
     */
    fun candidateBuilderSupported(styleId: Int): Boolean = styleId == 8 || styleId == 9 || styleId == 12

    /**
     * Fallback CrossFade candidate (`FUN_272239248`):
     * - requires `outgoingDuration >= 2.0` and `incomingDuration >= 2.0`, else no candidate
     *   (logs "...less than Fallback Cross-Fade duration");
     * - region built on a fixed 2.0-second window; `score = 1.0 + param_1 * 0.001`;
     * - record 0x120 B with score @+0x118.
     * Provenance: PLC §13.1. [EXACT]
     */
    fun fallbackCandidate(
        outgoingDurationSeconds: Double,
        incomingDurationSeconds: Double,
        tieBreaker: Double,
    ): TransitionCandidate? {
        if (outgoingDurationSeconds < FALLBACK_DURATION_SECONDS) return null
        if (incomingDurationSeconds < FALLBACK_DURATION_SECONDS) return null
        val outgoing = StylingRegion.Unstructured(
            SongTimeRange(
                SongTime(outgoingDurationSeconds - FALLBACK_DURATION_SECONDS),
                SongTime(outgoingDurationSeconds),
            )
        )
        val incoming = StylingRegion.Unstructured(
            SongTimeRange(SongTime(0.0), SongTime(FALLBACK_DURATION_SECONDS))
        )
        return TransitionCandidate(
            styleId = -1, // no "Fallback" style exists in the catalog (PLC §13.2, EXACT negative)
            algorithm = Algorithm.FALLBACK_CROSS_FADE,
            complexity = Complexity.FALLBACK,
            outgoingRegion = outgoing,
            incomingRegion = incoming,
            factors = emptyList(),
            base = BASE_FALLBACK,
            tieBreaker = tieBreaker,
        )
    }

    /**
     * Catalog iteration `DefaultStylingStrategyCatalog` (`FUN_2722469f0`) tries all algorithms; if
     * all fail -> "All algorithms: Styling result not identified." -> "Transition Planner: No
     * transition." -> `Result.failure`. Logs/calls [EXACT]; iteration order STRONG_INFERENCE.
     *
     * STATUS: UNKNOWN — needs runtime/device: algorithm fallback order (PLC §13.2).
     */
    fun catalogFallbackOrder(): List<Algorithm> =
        TODO("STATUS: UNKNOWN — needs runtime/device: styling strategy iteration order STRONG_INFERENCE only")

    // -----------------------------------------------------------------------------------------
    // §4.5 Timing accuracy + §4.8 genre gate + §4.9 time signature
    // -----------------------------------------------------------------------------------------

    /**
     * `FUN_272221f08` -> `Transition.TimingAccuracy.SongIssues`: 0 none, 1 stereo, 2 spatial.
     * Logs: "Stereo timing inaccurate. Transition complexities reduced.", "Spatial timing
     * inaccurate...", "None. ...not subject to limitations." Provenance: PLC §4.5. [EXACT]
     */
    fun timingAccuracy(stereoInaccurate: Boolean, spatialInaccurate: Boolean): TimingAccuracy {
        var raw = 0
        if (stereoInaccurate) raw = raw or 1
        if (spatialInaccurate) raw = raw or 2
        return TimingAccuracy(raw)
    }

    /**
     * Genre gate `Fun_27223f618`: compares main/ancestors/descendants/significant genre ids
     * against allowed/denied. Types [EXACT]; comparison algorithm [STRONG_INFERENCE] (PLC §4.8).
     *
     * STATUS: UNKNOWN — needs runtime/device: exact GenreTree comparison not reconstructed.
     */
    fun genreGateAllowed(
        outgoingGenreIds: Set<String>,
        incomingGenreIds: Set<String>,
        allowedGenreIDs: Set<String>?,
        deniedGenreIDs: Set<String>?,
    ): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: genre comparison algorithm STRONG_INFERENCE (PLC §4.8)")

    /**
     * Time signature compatibility `FUN_272243340` ("Time signature compatibility...");
     * compatible when the first signature is unavailable (CG §7, logs EXACT).
     * Note: `FUN_272234380` does NOT call it (P1 §1.5 C1, verified negative).
     *
     * STATUS: UNKNOWN — needs runtime/device: call position in the styling chain PARTIAL.
     */
    fun timeSignatureCompatible(firstSignature: Any?, secondSignature: Any?): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: time-signature call position PARTIAL (PLC §4.9)")

    /**
     * Early guard `FUN_272267e70`: validates durations/tempos; on violation raises
     * `SmartTransitionsError` tag 6. Code/tag [EXACT]; condition set [PARTIAL].
     *
     * STATUS: UNKNOWN — needs runtime/device: guard condition composition not reconstructed.
     */
    fun earlyGuardPasses(outgoingDurationSeconds: Double, incomingDurationSeconds: Double): Boolean =
        TODO("STATUS: UNKNOWN — needs runtime/device: early guard condition PARTIAL (PLC §4.11)")
}
