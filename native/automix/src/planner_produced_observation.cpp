#include "lmg/automix/planner_produced_observation.h"
#include <cmath>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
PlannerProducedObservation state(PlannerProducedStatus value) {
  PlannerProducedObservation r; r.status = value; return r;
}
void finite(double value) { if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite producer context"); }
void validateContext(const PlannerResolvedProducerContext& c,
                     std::optional<std::int64_t> outMs, std::optional<std::int64_t> inMs) {
  const auto& p = c.candidates.placement;
  for (auto value : {c.discovery.minimumOutgoingEnd, c.discovery.maximumIncomingEnd,
                    p.minimumOutgoingStart, p.minimumOutgoingEnd, p.maximumIncomingEnd}) {
    finite(value); if (value < 0) throw std::invalid_argument("Negative resolved producer bound");
  }
  for (auto range : {c.candidates.outgoingStarts, c.candidates.incomingStarts}) {
    finite(range.start); finite(range.end);
    if (range.start < 0 || range.end < range.start) throw std::invalid_argument("Invalid resolved start range");
  }
  for (auto value : {outMs, inMs})
    if (value && (*value <= 0 || *value > 9007199254740991LL)) throw std::invalid_argument("Invalid producer duration");
  if (outMs) {
    const auto duration = static_cast<double>(*outMs)/1000.0;
    if (c.discovery.minimumOutgoingEnd > duration || p.minimumOutgoingStart > duration ||
        p.minimumOutgoingEnd > duration || c.candidates.outgoingStarts.end > duration)
      throw std::invalid_argument("Outgoing producer context outside track");
  }
  if (inMs) {
    const auto duration = static_cast<double>(*inMs)/1000.0;
    if (c.discovery.maximumIncomingEnd > duration || p.maximumIncomingEnd > duration ||
        c.candidates.incomingStarts.end > duration)
      throw std::invalid_argument("Incoming producer context outside track");
  }
}
PlannerProducedStatus failure(PlannerSeedProductionStatus s) {
  switch (s) {
    case PlannerSeedProductionStatus::resourceLimit: return PlannerProducedStatus::resourceLimit;
    case PlannerSeedProductionStatus::invalidPreparation: return PlannerProducedStatus::invalidPreparation;
    case PlannerSeedProductionStatus::unresolvedPlaybackDuration: return PlannerProducedStatus::unresolvedPlaybackDuration;
    case PlannerSeedProductionStatus::insufficientStructure: return PlannerProducedStatus::insufficientStructure;
    default: throw std::logic_error("Unexpected seed status");
  }
}
}
PlannerProducedObservation observeProducedMusicKitCandidates(
    const CloudSongAnalysis& outgoing, const CloudSongAnalysis& incoming,
    std::optional<std::int64_t> outgoingDurationMs, std::optional<std::int64_t> incomingDurationMs,
    const std::vector<std::int64_t>& requestedStyleIds,
    const std::vector<PlannerCandidateStyle>& internalCatalog,
    const PlannerResolvedProducerContext& context, PlannerBeatMatchedEligibility eligibility,
    std::uint64_t workBudget) {
  if (eligibility == PlannerBeatMatchedEligibility::unresolved) return state(PlannerProducedStatus::eligibilityUnresolved);
  if (eligibility == PlannerBeatMatchedEligibility::denied) return state(PlannerProducedStatus::ineligible);
  if (eligibility != PlannerBeatMatchedEligibility::allowed) throw std::invalid_argument("Unknown planning eligibility");
  if (workBudget > kPlannerProducerWorkBudget || workBudget == 0) return state(PlannerProducedStatus::resourceLimit);
  validateContext(context, outgoingDurationMs, incomingDurationMs);
  auto styles = resolvePlannerStyleRequests(requestedStyleIds, internalCatalog);
  if (styles.status == PlannerStyleResolutionStatus::resourceLimit) return state(PlannerProducedStatus::resourceLimit);
  if (styles.status != PlannerStyleResolutionStatus::resolved) return state(PlannerProducedStatus::styleUnavailable);
  // The existing preparation owns conversion and input/resource validation.
  const auto out = preparePlannerSongObservation(outgoing, outgoingDurationMs);
  const auto in = preparePlannerSongObservation(incoming, incomingDurationMs);
  auto produced = producePlannerCandidateSeeds(out, in, styles.styles, context.discovery, workBudget);
  if (produced.status != PlannerSeedProductionStatus::produced && produced.status != PlannerSeedProductionStatus::noSeeds)
    return state(failure(produced.status));
  // Short-circuit no seeds is an actual complete result within this resolved
  // scope, not unresolved metadata disguised as an empty successful selection.
  PlannerCandidateSelection selected;
  if (produced.seeds.empty()) selected.completeForProvidedSeeds = true;
  else {
    const auto at = plannerMusicKitMainTonalComponents(outgoing);
    const auto zt = plannerMusicKitMainTonalComponents(incoming);
    selected = selectPreparedPlannerCandidates(out, in, at, zt, produced.seeds, styles.styles,
                                               context.candidates, workBudget - produced.workUnits);
    if (selected.status == PlannerCandidateStatus::resourceLimit) return state(PlannerProducedStatus::resourceLimit);
    if (!selected.completeForProvidedSeeds) {
      if (selected.status == PlannerCandidateStatus::invalidPreparation) return state(PlannerProducedStatus::invalidPreparation);
      if (selected.status == PlannerCandidateStatus::unresolvedPlaybackDuration) return state(PlannerProducedStatus::unresolvedPlaybackDuration);
      if (selected.status == PlannerCandidateStatus::insufficientStructure) return state(PlannerProducedStatus::insufficientStructure);
      throw std::logic_error("Incomplete resolved native selector result");
    }
  }
  PlannerProducedObservation result;
  result.status = PlannerProducedStatus::observed;
  result.production = std::move(produced);
  result.selection = std::move(selected);
  result.completeForResolvedScope = true;
  return result;
}
} // namespace lmg::automix
