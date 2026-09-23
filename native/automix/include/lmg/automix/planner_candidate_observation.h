#pragma once
#include "lmg/automix/planner_candidate_selector.h"
#include "lmg/automix/planner_observation.h"

namespace lmg::automix {
// A caller-resolved provider result, NOT a compatibility predicate. The binding
// of main/beginning/ending to the source provider remains an explicit obligation.
struct PlannerCandidateTonalBinding {
  bool resolved = false;
  std::optional<PlannerTonality> tonality;
  std::optional<double> melodicness;
};
// The exact, immutable preparation objects must own the event indices supplied
// in seeds. No reference is retained after this call. The 16-item diagnostic
// preview is deliberately not consulted. Status PREPARED does not authorize
// playback, infer source identity, or bypass higher-level support/genre gates.
PlannerCandidateSelection selectPreparedPlannerCandidates(
    const PlannerSongPreparation& outgoing, const PlannerSongPreparation& incoming,
    const PlannerCandidateTonalBinding& outgoingTonal,
    const PlannerCandidateTonalBinding& incomingTonal,
    const std::vector<PlannerCandidateSeed>& seeds,
    const std::vector<PlannerCandidateStyle>& styles,
    const PlannerCandidateBounds& bounds,
    std::uint64_t workBudget = kPlannerCandidateWorkBudget);
} // namespace lmg::automix
