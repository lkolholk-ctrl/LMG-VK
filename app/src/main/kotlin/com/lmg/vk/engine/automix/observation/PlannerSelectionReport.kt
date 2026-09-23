package com.lmg.vk.engine.automix.observation

/** LMG diagnostics. No enum here is asserted to equal Apple's FailureReason. */
enum class PlannerSelectionStatus {
    NEEDS_SOURCE_BINDINGS, OBSERVED, ELIGIBILITY_UNRESOLVED, INELIGIBLE, STYLE_UNAVAILABLE,
    INSUFFICIENT_STRUCTURE, INVALID_PREPARATION, UNRESOLVED_PLAYBACK_DURATION, RESOURCE_LIMIT,
}

data class ObservedPlannerCandidate internal constructor(
    val styleId: Int, val seedIndex: Int, val styleIndex: Int,
    val outgoingStartEvent: Int, val outgoingEndEvent: Int,
    val incomingStartEvent: Int, val incomingEndEvent: Int,
    val incomingScale: Int,
    val outgoingStartSeconds: Double, val outgoingEndSeconds: Double,
    val incomingStartSeconds: Double, val incomingEndSeconds: Double,
    val score: Double, val normalTempoTag: Int, val expandedTempoTag: Int,
)

/** Value-only observation; event indices are meaningless for another response or epoch. */
class PlannerSelectionReport internal constructor(
    val generation: Long, val bindingRevision: Long,
    val status: PlannerSelectionStatus,
    val explicitResolvedScope: Boolean,
    val missingSourceBindings: Int,
    val completeForResolvedScope: Boolean,
    val seedCount: Int, val attemptedCandidates: Int,
    val rejectionReasons: Long,
    val candidate: ObservedPlannerCandidate?,
) {
    val canExecute: Boolean get() = false
    val selectedStyleId: Int? get() = candidate?.styleId
    val candidateRejectionCodes: List<String>
        get() = REJECTION_CODES.filterIndexed { i, _ -> rejectionReasons and (1L shl i) != 0L }
    val missingSourceBindingCodes: List<String>
        get() = BINDING_CODES.filterIndexed { i, _ -> missingSourceBindings and (1 shl i) != 0 }

    private companion object {
        val BINDING_CODES = listOf("DEFAULT_PROFILE_UNVERIFIED", "CATALOG_FIELD_MAPPING_UNVERIFIED", "CRITERIA_BINDING_UNVERIFIED")
        val REJECTION_CODES = listOf("UNSUPPORTED_STYLE", "REGION_UNAVAILABLE", "PLACEMENT", "TEMPO_UNAVAILABLE",
            "NORMAL_TEMPO_MISMATCH", "NORMAL_TEMPO_MUST_FAIL", "EXPANDED_TEMPO_MISMATCH", "BAR_RATIO_UNAVAILABLE",
            "MATCHING_BARS_UNAVAILABLE", "FEWER_THAN_EIGHT_BARS", "TONALITY_MISMATCH", "VOCAL_CONFLICT", "NONPOSITIVE_SCORE")
    }
    override fun toString(): String = "PlannerSelectionReport(status=$status, scope=" +
        (if (explicitResolvedScope) "EXPLICIT_RESOLVED" else "UNRESOLVED_DEFAULT") + ", canExecute=false)"
}

/** Strict bounded transport only. It does not reproduce source math or score candidates. */
internal object PlannerSelectionWire {
    const val REQUEST_MAGIC = 0x4c4d47535131L
    const val RESPONSE_MAGIC = 0x4c4d47535231L

    fun request(generation: Long, revision: Long, scope: ResolvedPlannerScope?,
                outgoingDurationMs: Long?, incomingDurationMs: Long?): LongArray {
        require(generation >= 0 && revision >= 0)
        require(if (scope == null) revision == 0L else revision > 0L)
        require(listOfNotNull(outgoingDurationMs, incomingDurationMs).all { it in 1L..9_007_199_254_740_991L })
        val ids = scope?.requestedIds.orEmpty()
        val records = scope?.records.orEmpty()
        return LongArray(24 + ids.size + records.size * 3).also { w ->
            w[0] = REQUEST_MAGIC; w[1] = 1; w[2] = w.size.toLong(); w[3] = generation; w[4] = revision
            w[5] = if (scope == null) 0 else 1
            if (outgoingDurationMs != null) { w[6] = w[6] or 1; w[7] = outgoingDurationMs }
            if (incomingDurationMs != null) { w[6] = w[6] or 2; w[8] = incomingDurationMs }
            w[9] = scope?.workBudget ?: ResolvedPlannerScope.MAX_WORK
            if (scope != null) {
                w[10] = scope.eligibility.ordinal.toLong(); w[11] = ids.size.toLong(); w[12] = records.size.toLong()
                scope.bounds.values().forEachIndexed { i, v -> w[13 + i] = v.toRawBits() }
                var p = 24
                for (id in ids) w[p++] = id
                for (r in records) { w[p++] = r.id; w[p++] = if (r.maximumBars == null) 0 else 1; w[p++] = r.maximumBars ?: 0 }
            }
        }
    }

    fun decode(w: LongArray, request: LongArray): PlannerSelectionReport {
        fun ensure(ok: Boolean) {
            if (!ok) throw ObservationFailure(ObservationReason.BRIDGE_CONTRACT_MISMATCH, "SELECTION_WIRE")
        }
        ensure(request.size in 24..80 && request[0] == REQUEST_MAGIC && request[2] == request.size.toLong())
        ensure(request[1] == 1L && request[3] >= 0 && request[4] >= 0 && request[5] in 0L..1L &&
            request[6] in 0L..3L && request[9] in 0L..ResolvedPlannerScope.MAX_WORK &&
            request[11] in 0L..14L && request[12] in 0L..14L &&
            request.size.toLong() == 24 + request[11] + 3 * request[12])
        ensure(w.size == 48)
        ensure(w[0] == RESPONSE_MAGIC && w[1] == 1L && w[2] == 48L)
        ensure(w[3] == request[3] && w[4] == request[4] && w[5] == request[5])
        ensure(w[6] in 0L..8L && w[8] in 0L..1L && w[9] == 0L && w[10] in 0L..1L)
        ensure((44..47).all { w[it] == 0L })
        val explicit = request[5] == 1L
        ensure(if (explicit) w[6] != 0L && w[7] == 0L else w[6] == 0L && w[7] == 7L)
        val complete = w[8] == 1L
        ensure(complete == (w[6] == 1L))
        fun noWinner() {
            ensure(w[10] == 0L && (28..30).all { w[it] == -1L } && (31..40).all { w[it] == 0L } &&
                w[41] == -1L && w[42] == -1L)
        }
        var c: ObservedPlannerCandidate? = null
        if (!complete) {
            ensure((11..26).all { w[it] == 0L } && w[27] == -1L && w[43] == 0L)
            noWinner()
        } else {
            ensure(w[11] in 0L..64L && (12..16).all { w[it] in 0L..4096L } &&
                (17..19).all { w[it] in 0L..64L } && (20..25).all { w[it] in 0L..896L })
            ensure(w[14] <= w[12] && w[15] <= w[13] && w[16] <= w[14])
            ensure(w[17] == w[16] * w[15] && w[11] + w[18] + w[19] == w[17])
            ensure(w[20] == w[11] * request[11] && w[21] + w[22] + w[23] + w[24] == w[20] && w[25] <= w[21])
            ensure(w[26] in 0L..8191L && w[43] in 0L..request[9])
            ensure(w[27] in 0L..1L && (w[27] == 0L) == (w[10] == 1L))
            if (w[10] == 1L) {
                ensure(w[28] in setOf(8L, 9L, 12L) && w[29] >= 0 && w[29] < w[11] &&
                    w[30] >= 0 && w[30] < request[11])
                ensure(24 + w[30] < request.size && request[24 + w[30].toInt()] == w[28])
                ensure((31..34).all { w[it] in 0L..4095L } && w[31] < w[32] && w[33] < w[34] && w[35] in 0L..2L)
                val times = (36..39).map { Double.fromBits(w[it]) }
                val score = Double.fromBits(w[40])
                ensure(times.all { it.isFinite() && it >= 0 } && times[0] < times[1] && times[2] < times[3])
                ensure(score.isFinite() && score > 0 && w[21] > w[25])
                ensure(request[6] == 3L && times[1] <= request[7].toDouble() / 1000.0 &&
                    times[3] <= request[8].toDouble() / 1000.0)
                val tags = setOf(0L, 1L, 2L, 128L, 129L, 130L, 252L)
                ensure(w[41] in tags && w[42] in tags)
                c = ObservedPlannerCandidate(w[28].toInt(), w[29].toInt(), w[30].toInt(),
                    w[31].toInt(), w[32].toInt(), w[33].toInt(), w[34].toInt(), w[35].toInt(),
                    times[0], times[1], times[2], times[3], score, w[41].toInt(), w[42].toInt())
            } else noWinner()
        }
        return PlannerSelectionReport(w[3], w[4], PlannerSelectionStatus.entries[w[6].toInt()], explicit,
            w[7].toInt(), complete, w[11].toInt(), w[20].toInt(), w[26], c)
    }
}
