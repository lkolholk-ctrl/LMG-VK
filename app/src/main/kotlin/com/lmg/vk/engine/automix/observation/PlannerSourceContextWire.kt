package com.lmg.vk.engine.automix.observation

/** Value transport only; source arithmetic/catalog interpretation remains in C++. */
internal object PlannerSourceContextWire {
    const val REQUEST_MAGIC = 0x4c4d47534d31L
    const val RESPONSE_MAGIC = 0x4c4d47534e31L

    fun request(generation: Long, revision: Long, context: MusicKitSourceContext,
                outgoingDurationMs: Long?, incomingDurationMs: Long?): LongArray {
        require(generation >= 0 && revision > 0)
        require(listOfNotNull(outgoingDurationMs, incomingDurationMs).all { it in 1L..9_007_199_254_740_991L })
        return LongArray(24).also { w ->
            w[0] = REQUEST_MAGIC; w[1] = 1; w[2] = 24; w[3] = generation; w[4] = revision
            if (outgoingDurationMs != null) { w[5] = w[5] or 1; w[6] = outgoingDurationMs }
            if (incomingDurationMs != null) { w[5] = w[5] or 2; w[7] = incomingDurationMs }
            w[9] = context.workBudget; w[10] = context.upperEligibility.ordinal.toLong()
            w[11] = context.outgoingSpatial.ordinal.toLong(); w[12] = context.incomingSpatial.ordinal.toLong()
            w[13] = context.outgoingPreviousState.ordinal.toLong(); w[14] = context.incomingPreviousState.ordinal.toLong()
            when (val c = context.outgoingCriteria) {
                is MusicKitOutgoingCriteria.EarlyAfter -> { w[15] = 0; w[16] = c.seconds.toRawBits() }
                is MusicKitOutgoingCriteria.LateAfter -> { w[15] = 1; w[16] = c.seconds.toRawBits() }
                MusicKitOutgoingCriteria.LateInSong -> w[15] = 2
            }
            when (val c = context.incomingCriteria) {
                is MusicKitIncomingCriteria.After -> { w[17] = 0; w[18] = c.seconds.toRawBits() }
                is MusicKitIncomingCriteria.Within -> { w[17] = 1; w[18] = c.lower.toRawBits(); w[19] = c.upper.toRawBits() }
                MusicKitIncomingCriteria.InSong -> w[17] = 2
            }
            w[20] = context.maximumComplexity.toLong()
        }
    }

    fun decode(w: LongArray, original: LongArray, catalogIds: Set<Long>): PlannerSelectionReport {
        fun ensure(ok: Boolean) { if (!ok) throw ObservationFailure(ObservationReason.BRIDGE_CONTRACT_MISMATCH, "SOURCE_SCOPE_WIRE") }
        ensure(original.size == 24 && original[0] == REQUEST_MAGIC && original[1] == 1L && original[2] == 24L)
        ensure(original[3] >= 0 && original[4] > 0 && original[5] in 0L..3L && original[9] in 0L..ResolvedPlannerScope.MAX_WORK)
        ensure(original[10] in 0L..2L && (11..14).all { original[it] in 0L..2L } && original[15] in 0L..2L &&
            original[17] in 0L..2L && original[20] in 0L..3L && listOf(8, 21, 22, 23).all { original[it] == 0L })
        ensure((16..19).filter { it != 17 }.all { Double.fromBits(original[it]).let { v -> v.isFinite() && v >= 0 } })
        ensure(if (original[15] == 2L) original[16] == 0L else true)
        ensure(if (original[17] == 2L) original[18] == 0L && original[19] == 0L else
            if (original[17] == 0L) original[19] == 0L else Double.fromBits(original[18]) <= Double.fromBits(original[19]))
        for ((bit, index) in listOf(1L to 6, 2L to 7)) ensure(
            if (original[5] and bit != 0L) original[index] in 1L..9_007_199_254_740_991L else original[index] == 0L)
        ensure(w.size in 12..140 && w[0] == RESPONSE_MAGIC && w[1] == 1L && w[2] == w.size.toLong())
        ensure(w[3] == original[3] && w[4] == original[4] && w[5] in 0L..12L)
        ensure(w[8] == 3L && w[9] == 8L && w[10] == 9L && w[11] == 12L)
        val resolution = PlannerSourceContextResolution.entries[w[5].toInt()]
        val profile = listOf(w[9].toInt(), w[10].toInt(), w[11].toInt())
        if (resolution != PlannerSourceContextResolution.RESOLVED) {
            ensure(w.size == 12 && w[6] == 0L && w[7] == 0L)
            val status = when (resolution) {
                PlannerSourceContextResolution.ELIGIBILITY_UNRESOLVED -> PlannerSelectionStatus.ELIGIBILITY_UNRESOLVED
                PlannerSourceContextResolution.INELIGIBLE -> PlannerSelectionStatus.INELIGIBLE
                PlannerSourceContextResolution.RESOURCE_LIMIT -> PlannerSelectionStatus.RESOURCE_LIMIT
                PlannerSourceContextResolution.CATALOG_REJECTED -> PlannerSelectionStatus.STYLE_UNAVAILABLE
                PlannerSourceContextResolution.INVALID_INPUT, PlannerSourceContextResolution.CRITERIA_REJECTED -> PlannerSelectionStatus.INVALID_PREPARATION
                else -> PlannerSelectionStatus.NEEDS_SOURCE_BINDINGS
            }
            return PlannerSelectionReport(w[3], w[4], status, false,
                if (status == PlannerSelectionStatus.NEEDS_SOURCE_BINDINGS || status == PlannerSelectionStatus.ELIGIBILITY_UNRESOLVED) 4 else 0,
                false, 0, 0, 0, null, profile, resolution)
        }
        ensure(original[10] == ResolvedPlannerEligibility.ALLOWED.ordinal.toLong() && original[20] == 3L &&
            (11..14).all { original[it] == MusicKitSourceKnowledge.ABSENT.ordinal.toLong() } && original[5] == 3L)
        ensure(w[6] in 24L..80L && w[7] == 48L && w.size.toLong() == 12 + w[6] + w[7])
        val q = w.copyOfRange(12, 12 + w[6].toInt())
        ensure(q[0] == PlannerSelectionWire.REQUEST_MAGIC && q[1] == 1L && q[2] == q.size.toLong() &&
            q[3] == original[3] && q[4] == original[4] && q[5] == 1L && q[6] == original[5] &&
            q[7] == original[6] && q[8] == original[7] && q[9] == original[9] && q[10] == original[10])
        ensure(q[11] == 3L && q[12] in 3L..14L && q.size.toLong() == 27 + q[12] * 3 && q[22] == 0L && q[23] == 0L)
        ensure(q.slice(24..26) == listOf(8L, 9L, 12L))
        val bounds = q.slice(13..21).map(Double::fromBits)
        ensure(bounds.all { it.isFinite() && it >= 0 } && bounds[5] <= bounds[6] && bounds[7] <= bounds[8])
        val outSeconds = original[6].toDouble() / 1000.0
        val inSeconds = original[7].toDouble() / 1000.0
        ensure(listOf(0, 2, 3, 5, 6).all { bounds[it] <= outSeconds } && listOf(1, 4, 7, 8).all { bounds[it] <= inSeconds })
        // These are verbatim caller bounds, not a second implementation of provider geometry.
        ensure(q[18] == if (original[15] == 2L) 0L else original[16])
        ensure(q[19] == outSeconds.toRawBits())
        ensure(q[20] == if (original[17] == 2L) 0L else original[18])
        ensure(q[21] == if (original[17] == 1L) original[19] else inSeconds.toRawBits())
        val records = (27 until q.size step 3).map { at ->
            ensure(q[at] in 0L..Int.MAX_VALUE.toLong() && q[at + 1] in 0L..1L &&
                if (q[at + 1] == 0L) q[at + 2] == 0L else q[at + 2] in 0L..Int.MAX_VALUE.toLong())
            ResolvedPlannerStyle(q[at], if (q[at + 1] == 1L) q[at + 2] else null)
        }
        ensure(records.map { it.id }.toSet().size == records.size && records.map { it.id }.toSet() == catalogIds)
        val scope = try {
            ResolvedPlannerScope.create(listOf(8L, 9L, 12L), records,
                ResolvedPlannerBounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5], bounds[6], bounds[7], bounds[8]),
                ResolvedPlannerEligibility.ALLOWED, original[9])
        } catch (_: IllegalArgumentException) { throw ObservationFailure(ObservationReason.BRIDGE_CONTRACT_MISMATCH, "SOURCE_SCOPE_WIRE") }
        ensure(PlannerSelectionWire.request(original[3], original[4], scope, original[6], original[7]).contentEquals(q))
        return PlannerSelectionWire.decode(w.copyOfRange(12 + q.size, w.size), q).fromSourceContext(resolution)
    }
}
