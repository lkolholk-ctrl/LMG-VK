#pragma once
#include "lmg/automix/planner_input_producers.h"

namespace lmg::automix {
// Higher-level strategy/genre/support/spatial guards are not inferred from BPM
// or supportsSmartTransitions alone. Unknown strategy eligibility is not a positive gate result.
enum class PlannerBeatMatchedEligibility : std::uint8_t { unresolved, allowed, denied };
struct PlannerResolvedProducerContext {
  PlannerSeedDiscoveryBounds discovery;
  PlannerCandidateBounds candidates;
};
enum class PlannerProducedStatus : std::uint8_t {
  observed, eligibilityUnresolved, ineligible, styleUnavailable,
  insufficientStructure, invalidPreparation, unresolvedPlaybackDuration, resourceLimit
};
struct PlannerProducedObservation {
  PlannerProducedStatus status = PlannerProducedStatus::eligibilityUnresolved;
  PlannerSeedProduction production;
  std::optional<PlannerCandidateSelection> selection;
  bool completeForResolvedScope = false;
  static constexpr bool canExecute = false;
};
// Complete native composition for RESOLVED MusicKit inputs: no caller-selected
// seed pairs or tonal values. Native catalog records, requested strategy IDs,
// Criteria/context and eligibility must already have their actual source
// bindings. This API does NOT synthesize them from JSON.duration, enum numeric
// order, the current player position, or an invented fallback policy.
// Preparations and tonal components are made from the SAME two owned cloud
// objects here; no mixing maps from a different recording/generation.
PlannerProducedObservation observeProducedMusicKitCandidates(
    const CloudSongAnalysis& outgoing, const CloudSongAnalysis& incoming,
    std::optional<std::int64_t> outgoingDurationMs,
    std::optional<std::int64_t> incomingDurationMs,
    const std::vector<std::int64_t>& requestedStyleIds,
    const std::vector<PlannerCandidateStyle>& internalCatalog,
    const PlannerResolvedProducerContext& context,
    PlannerBeatMatchedEligibility eligibility,
    std::uint64_t workBudget = kPlannerProducerWorkBudget);
} // namespace lmg::automix
