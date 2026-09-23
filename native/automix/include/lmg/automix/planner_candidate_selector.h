#pragma once

#include "lmg/automix/planner_loudness.h"
#include "lmg/automix/planner_vocals.h"
#include "lmg/automix/planner_region_algebra.h"
#include <cstddef>
#include <cstdint>
#include <optional>
#include <vector>

namespace lmg::automix {
// Explicit-input, BeatMatched-only observation. NOT seed discovery, a complete
// strategy dispatcher, a style-to-schedule compiler, or permission to play PCM.
// Structure/maps and their order must remain immutable for the duration of call.
struct PlannerCandidateSong {
  const SongStructure* structure = nullptr;
  const PlannerVocalMap* vocals = nullptr;
  const PlannerLoudnessMap* loudness = nullptr;
  // Already resolved source-provider components. Their selection from a cloud
  // main/beginning/ending composite is NOT inferred by this layer.
  std::optional<PlannerTonality> tonality;
  std::optional<double> melodicness;
  bool tonalComponentsResolved = false;
};
struct PlannerCandidateSeed {
  StructureRegion outgoing;
  StructureRegion incoming;
  region_algebra::PlannerRegionUnit incomingUnit = region_algebra::PlannerRegionUnit::bars;
  // Source-scaled/truncated pair entering 272234380, NOT raw discovery output.
  TempoBinaryScale incomingScale = TempoBinaryScale::one;
};
struct PlannerCandidateStyle {
  std::int64_t id;
  // Source internal style field at +0x18 with optional tag +0x20. This is NOT
  // inferred from TransitionStyles.json.duration. Absence resolves to four.
  std::optional<std::int64_t> suffixBars;
};
struct PlannerCandidateBounds {
  region_algebra::PlannerResolvedPlacementBounds placement;
  // Inclusive start ranges already resolved by the Criteria/context layer.
  region_algebra::PlannerSongTimeRange outgoingStarts, incomingStarts;
};
struct PlannerCandidateFeatures {
  BeatMatchedScoreInputs scoreInputs;
  std::uint8_t normalTempoTag = 0xfc, expandedTempoTag = 0xfc;
  std::optional<PlannerTempo> outgoingTempo, incomingTempo;
  std::optional<PlannerVocalWindow> leadingVocalWindow;
};
struct PlannerScoredCandidate {
  std::size_t seedIndex = 0, styleIndex = 0;
  std::int64_t styleId = 0;
  StructureRegion outgoing{}, incoming{};
  TempoBinaryScale incomingScale = TempoBinaryScale::one;
  double outgoingStart = 0, outgoingEnd = 0, incomingStart = 0, incomingEnd = 0;
  double score = 0;
  PlannerCandidateFeatures features;
};
enum class PlannerCandidateStatus : std::uint8_t {
  selected = 0, noPositiveCandidate = 1, insufficientStructure = 2,
  unresolvedTonalityBinding = 3, resourceLimit = 4,
  invalidPreparation = 5, unresolvedPlaybackDuration = 6
};
// LMG diagnostic flags, NOT recovered Apple FailureReason enum values. Reasons
// describe discarded candidates in the PROVIDED seed/style scope, including
// when another candidate won. They do not change source scoring or its order.
enum class PlannerCandidateRejection : std::uint64_t {
  unsupportedStyle = 1ULL << 0, regionUnavailable = 1ULL << 1,
  placement = 1ULL << 2, tempoUnavailable = 1ULL << 3,
  normalTempoMismatch = 1ULL << 4, normalTempoMustFail = 1ULL << 5,
  expandedTempoMismatch = 1ULL << 6, barRatioUnavailable = 1ULL << 7,
  matchingBarsUnavailable = 1ULL << 8, fewerThanEightBars = 1ULL << 9,
  tonalityMismatch = 1ULL << 10, vocalConflict = 1ULL << 11,
  finalScoreNonPositive = 1ULL << 12
};
struct PlannerCandidateSelection {
  PlannerCandidateStatus status = PlannerCandidateStatus::noPositiveCandidate;
  std::size_t attempted = 0, constructed = 0, unsupportedStyles = 0,
              regionMisses = 0, placementMisses = 0, nonPositive = 0;
  std::optional<PlannerScoredCandidate> winner;
  std::uint64_t rejectionReasons = 0;
  bool completeForProvidedSeeds = false;
  // Deliberately no executable field that callers can set to true.
  static constexpr bool canExecute = false;
};
// LMG host bounds, not recovered Apple algorithm constants. An exceeded budget
// returns resourceLimit and NEVER publishes a provisional winner.
inline constexpr std::size_t kPlannerCandidateMaxSeeds = 64;
inline constexpr std::size_t kPlannerCandidateMaxStyles = 14;
inline constexpr std::uint64_t kPlannerCandidateWorkBudget = 16'000'000;

// Scalar/vector pieces with independent source fixtures. Missing bar ranges are
// represented by nullopt, not zero. Count scaling is the OPPOSITE of the
// incoming beat budget: half -> incomingBars/2; two -> incomingBars*2.
std::optional<double> plannerCandidateBarRatio(std::optional<std::int64_t> outgoingBars,
    std::optional<std::int64_t> incomingBars, TempoBinaryScale);
std::optional<std::int64_t> plannerCandidateMatchingBars(
    std::optional<std::int64_t> outgoingBars, std::optional<std::int64_t> incomingBars,
    TempoBinaryScale);
double plannerCandidateEndDelta(double outgoingEnd, double incomingEnd);
// Values are native strengths 0..4, sentinel 5 = absent. Align SUFFIXES of the
// two vectors, skip absent pairs, and test their max(min(a,b)) > veryLow.
bool plannerCandidateVocalConflict(const std::vector<std::uint8_t>& outgoing,
                                  const std::vector<std::uint8_t>& incoming);
// Every seed is evaluated in caller-supplied order, then each style in supplied
// order. Source math/first-on-tie selection is delegated to planner_scoring.
// Invalid host-domain data throws; valid-but-unavailable regions are skipped.
PlannerCandidateSelection selectPlannerRegionCandidates(
    const PlannerCandidateSong& outgoing, const PlannerCandidateSong& incoming,
    const std::vector<PlannerCandidateSeed>& seeds,
    const std::vector<PlannerCandidateStyle>& styles,
    const PlannerCandidateBounds& bounds,
    std::uint64_t workBudget = kPlannerCandidateWorkBudget);
} // namespace lmg::automix
