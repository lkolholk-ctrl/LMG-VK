package com.lmg.vk.engine.automix.observation

import java.util.Collections

/** INTERNAL records already resolved by the caller. maximumBars is already an internal count, not a Kotlin reinterpretation of JSON.
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

/** Two immutable observation inputs. Neither represents permission to render PCM. */
sealed interface PlannerObservationScope

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
) : PlannerObservationScope {
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

/** Knowledge about the matched recording/provider, never guessed from a URL suffix.
 * ABSENT means explicitly confirmed, not "field wasn't copied into a DTO".
 */
enum class MusicKitSourceKnowledge { UNKNOWN, ABSENT, PRESENT }
sealed interface MusicKitOutgoingCriteria {
    data class EarlyAfter(val seconds: Double) : MusicKitOutgoingCriteria
    data class LateAfter(val seconds: Double) : MusicKitOutgoingCriteria
    data object LateInSong : MusicKitOutgoingCriteria
}
sealed interface MusicKitIncomingCriteria {
    data class After(val seconds: Double) : MusicKitIncomingCriteria
    data class Within(val lower: Double, val upper: Double) : MusicKitIncomingCriteria
    data object InSong : MusicKitIncomingCriteria
}
/** Raw caller intent and independently resolved provider facts. No bar counts,
 * discovery windows, internal style records, seeds, or compatibility scores.
 * This supported slice requires confirmed nonspatial/no-previous-state sources;
 * native code separately checks exact reference/playback duration correspondence.
 * Constructor/copy validation lives in init; factory defaults remain unresolved.
 */
data class MusicKitSourceContext(
    val outgoingCriteria: MusicKitOutgoingCriteria,
    val incomingCriteria: MusicKitIncomingCriteria,
    val outgoingSpatial: MusicKitSourceKnowledge,
    val incomingSpatial: MusicKitSourceKnowledge,
    val outgoingPreviousState: MusicKitSourceKnowledge,
    val incomingPreviousState: MusicKitSourceKnowledge,
    val upperEligibility: ResolvedPlannerEligibility,
    val maximumComplexity: Int,
    val workBudget: Long,
) : PlannerObservationScope {
    init {
        fun time(v: Double) { require(v.isFinite() && v >= 0) }
        when (outgoingCriteria) {
            is MusicKitOutgoingCriteria.EarlyAfter -> time(outgoingCriteria.seconds)
            is MusicKitOutgoingCriteria.LateAfter -> time(outgoingCriteria.seconds)
            MusicKitOutgoingCriteria.LateInSong -> Unit
        }
        when (incomingCriteria) {
            is MusicKitIncomingCriteria.After -> time(incomingCriteria.seconds)
            is MusicKitIncomingCriteria.Within -> {
                time(incomingCriteria.lower); time(incomingCriteria.upper)
                require(incomingCriteria.lower <= incomingCriteria.upper)
            }
            MusicKitIncomingCriteria.InSong -> Unit
        }
        require(maximumComplexity in 0..3 && workBudget in 0L..ResolvedPlannerScope.MAX_WORK)
    }
    override fun toString(): String = "MusicKitSourceContext(source intent, redacted)"
    companion object {
        fun create(
            outgoingCriteria: MusicKitOutgoingCriteria,
            incomingCriteria: MusicKitIncomingCriteria,
            maximumComplexity: Int,
            upperEligibility: ResolvedPlannerEligibility = ResolvedPlannerEligibility.UNRESOLVED,
            outgoingSpatial: MusicKitSourceKnowledge = MusicKitSourceKnowledge.UNKNOWN,
            incomingSpatial: MusicKitSourceKnowledge = MusicKitSourceKnowledge.UNKNOWN,
            outgoingPreviousState: MusicKitSourceKnowledge = MusicKitSourceKnowledge.UNKNOWN,
            incomingPreviousState: MusicKitSourceKnowledge = MusicKitSourceKnowledge.UNKNOWN,
            workBudget: Long = ResolvedPlannerScope.MAX_WORK,
        ): MusicKitSourceContext {
            return MusicKitSourceContext(outgoingCriteria, incomingCriteria, outgoingSpatial, incomingSpatial,
                outgoingPreviousState, incomingPreviousState, upperEligibility, maximumComplexity, workBudget)
        }
    }
}

/** Native preflight outcome. Not Apple's FailureReason or execution eligibility. */
enum class PlannerSourceContextResolution {
    RESOLVED, ELIGIBILITY_UNRESOLVED, INELIGIBLE, SPATIAL_UNSUPPORTED, PREVIOUS_STATE_UNSUPPORTED,
    REFERENCE_DURATION_MISSING, PLAYBACK_DURATION_MISSING, DURATION_MAPPING_REQUIRED, INVALID_INPUT,
    CATALOG_REJECTED, RESOURCE_LIMIT, SOURCE_FACTS_UNKNOWN, CRITERIA_REJECTED,
}
