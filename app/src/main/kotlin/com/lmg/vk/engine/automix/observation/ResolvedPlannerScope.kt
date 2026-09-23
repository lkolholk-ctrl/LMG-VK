package com.lmg.vk.engine.automix.observation

import java.util.Collections

/** INTERNAL records already resolved by the caller. maximumBars is not JSON.duration.
 * null denotes known absence of the internal option, not an unresolved mapping.
 */
data class ResolvedPlannerStyle(val id: Long, val maximumBars: Long?)

/** Nine source-provider/Criteria bounds in seconds. No music calculation in Kotlin. */
data class ResolvedPlannerBounds(
    val minimumOutgoingDiscoveryEnd: Double,
    val maximumIncomingDiscoveryEnd: Double,
    val minimumOutgoingStart: Double,
    val minimumOutgoingEnd: Double,
    val maximumIncomingEnd: Double,
    val outgoingStartLower: Double,
    val outgoingStartUpper: Double,
    val incomingStartLower: Double,
    val incomingStartUpper: Double,
) {
    internal fun values(): List<Double> = listOf(minimumOutgoingDiscoveryEnd, maximumIncomingDiscoveryEnd,
        minimumOutgoingStart, minimumOutgoingEnd, maximumIncomingEnd,
        outgoingStartLower, outgoingStartUpper, incomingStartLower, incomingStartUpper)
}

enum class ResolvedPlannerEligibility { UNRESOLVED, ALLOWED, DENIED }
enum class ObservationScopeSubmission { ACCEPTED, DUPLICATE, CONFLICT, STALE, CLOSED, SERVICE_UNAVAILABLE }

/**
 * Immutable, explicitly resolved MusicKit scope; not an assertion of an Apple default.
 * No seeds, main/edge choices, tonality booleans, or mutable wire arrays are accepted.
 * The provider must resolve profile order, internal records, final Criteria and the
 * full strategy eligibility. supportsSmartTransitions alone is NOT that resolution.
 */
class ResolvedPlannerScope private constructor(
    requestedIds: List<Long>, records: List<ResolvedPlannerStyle>,
    val bounds: ResolvedPlannerBounds,
    val eligibility: ResolvedPlannerEligibility,
    val workBudget: Long,
) {
    val requestedIds: List<Long> = Collections.unmodifiableList(requestedIds.toList())
    val records: List<ResolvedPlannerStyle> = Collections.unmodifiableList(records.toList())
    override fun toString(): String = "ResolvedPlannerScope(explicit, redacted)"
    override fun equals(other: Any?): Boolean = other is ResolvedPlannerScope &&
        requestedIds == other.requestedIds && records == other.records && bounds == other.bounds &&
        eligibility == other.eligibility && workBudget == other.workBudget
    override fun hashCode(): Int = listOf(requestedIds, records, bounds, eligibility, workBudget).hashCode()

    companion object {
        const val MAX_WORK = 16_000_000L
        fun create(
            requestedIds: List<Long>, records: List<ResolvedPlannerStyle>, bounds: ResolvedPlannerBounds,
            eligibility: ResolvedPlannerEligibility, workBudget: Long = MAX_WORK,
        ): ResolvedPlannerScope {
            require(requestedIds.size <= 14 && records.size <= 14)
            val ids = requestedIds.toList()
            val catalog = records.toList()
            require(ids.size <= 14 && catalog.size <= 14)
            require(ids.all { it in 0L..Int.MAX_VALUE.toLong() })
            require(catalog.all { it.id in 0L..Int.MAX_VALUE.toLong() && (it.maximumBars == null || it.maximumBars >= 0) })
            require(catalog.map { it.id }.toSet().size == catalog.size)
            require(bounds.values().all { it.isFinite() && it >= 0 })
            require(bounds.outgoingStartLower <= bounds.outgoingStartUpper &&
                bounds.incomingStartLower <= bounds.incomingStartUpper)
            require(workBudget in 0L..MAX_WORK)
            return ResolvedPlannerScope(ids, catalog, bounds, eligibility, workBudget)
        }
    }
}
