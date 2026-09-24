#pragma once
#include "lmg/automix/planner_produced_observation.h"
#include <string_view>

namespace lmg::automix {
// LMG observation transport, NOT an Apple default-profile or Criteria decoder.
inline constexpr std::int64_t kPlannerBindingRequestMagic = 0x4c4d47535131;
inline constexpr std::int64_t kPlannerBindingResponseMagic = 0x4c4d47535231;
inline constexpr std::size_t kPlannerBindingRequestHeader = 24;
inline constexpr std::size_t kPlannerBindingMaxRequest = 80;
inline constexpr std::size_t kPlannerBindingResponseWords = 48;
struct PlannerSelectionBindingRequest {
  std::int64_t generation = 0, revision = 0;
  bool explicitResolvedScope = false;
  std::optional<std::int64_t> outgoingDurationMs, incomingDurationMs;
  std::uint64_t workBudget = kPlannerProducerWorkBudget;
  PlannerBeatMatchedEligibility eligibility = PlannerBeatMatchedEligibility::unresolved;
  std::vector<std::int64_t> requestedIds;
  std::vector<PlannerCandidateStyle> catalog;
  PlannerResolvedProducerContext context{};
};
// Exactly 24 + requestedCount + 3*recordCount signed64 words. No pointers/handles.
// Default mode carries no guessed records, eligibility or placement numbers.
PlannerSelectionBindingRequest decodePlannerSelectionBinding(const std::vector<std::int64_t>&);
// Validates the request again. No caller-supplied seeds or tonal predicates.
std::vector<std::int64_t> observePlannerSelectionBinding(
    const std::vector<std::int64_t>& request,
    const CloudSongAnalysis& outgoing, const CloudSongAnalysis& incoming);
// Shared encoder for a completed native composition. Stage4a reuses the SAME
// selected object rather than rerunning selection or accepting a Kotlin winner.
std::vector<std::int64_t> encodePlannerSelectionBindingResult(
    const std::vector<std::int64_t>& request, const PlannerProducedObservation&);
// Raw production entry: existing strict JSON decoder, then unchanged composition.
// The Kotlin owner verifies the canonical catalog before constructing any request.
// Explicit internal records remain independently resolved inputs, NOT JSON.duration.
std::vector<std::int64_t> observePlannerSelectionBindingJson(
    const std::vector<std::int64_t>& request,
    std::string_view outgoing, std::string_view outgoingId,
    std::string_view incoming, std::string_view incomingId);
} // namespace lmg::automix
