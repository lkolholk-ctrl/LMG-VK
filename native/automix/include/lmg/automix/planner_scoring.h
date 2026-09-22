#pragma once

#include <cstddef>
#include <array>
#include <cstdint>
#include <optional>
#include <vector>

namespace lmg::automix {

// Recovered algorithm complexity values; these are not transition style IDs.
enum class PlannerAlgorithm : std::uint8_t { fallback=0, deadAir=1, smart=2, beatMatched=3 };
enum class PlannerConfidence : std::uint8_t { none=0, low=1, high=2 };
using PlannerComplexities = std::vector<PlannerAlgorithm>;
// Constructor 2722461cc: protocol table entries, not enum numeric order.
inline constexpr std::array<PlannerAlgorithm,4> kPlannerAlgorithmOrder{
    PlannerAlgorithm::beatMatched,PlannerAlgorithm::smart,
    PlannerAlgorithm::deadAir,PlannerAlgorithm::fallback};
// Complexity-only eligibility in catalog order. Each strategy still has its
// own source guards and candidate-building rules before it can succeed.
PlannerComplexities plannerEligibleAlgorithms(PlannerAlgorithm maximum,
    const PlannerComplexities& outgoing,const PlannerComplexities& incoming);
PlannerConfidence plannerDurationConfidence(double expected,double actual,bool notDownloaded);
PlannerConfidence plannerSpatialDriftConfidence(std::optional<double> driftSeconds);
PlannerComplexities plannerDurationComplexities(PlannerConfidence);
PlannerComplexities plannerSpatialComplexities(PlannerConfidence closeMatch,PlannerConfidence drift);
bool plannerComplexityAllowed(PlannerAlgorithm algorithm,PlannerAlgorithm maximum,
    const PlannerComplexities& outgoing,const PlannerComplexities& incoming);

// Native planner enum bytes, not MIDI pitch classes or cloud key strings.
struct PlannerTonality {std::uint8_t tonic,mode;};
// Original relationship tag: 0 identical, 1 matching cross-mode table index,
// 2 adjacent same-mode table indices (including 11/0), 3 incompatible.
std::uint8_t plannerTonalityRelationship(std::optional<PlannerTonality> outgoing,
    std::optional<PlannerTonality> incoming) noexcept;
bool plannerMelodicnessInsignificant(std::optional<double> value);
bool plannerTonalitiesCompatible(std::optional<PlannerTonality> outgoing,
    std::optional<PlannerTonality> incoming,std::optional<double> outgoingMelodicness,
    std::optional<double> incomingMelodicness);

// Source Tempo representation, not an optional-presence bit: tag 0 = BPM,
// tag 1 = seconds per beat. MediaAPI-to-planner extraction remains separate.
struct PlannerTempo {
  double value;
  bool secondsPerBeat = false;
};
enum class TempoBinaryScale : std::uint8_t { half = 0, one = 1, two = 2 };
struct PlannerTempoMatch {
  TempoBinaryScale scale;
  bool compatible;
  bool exact;
  // Source 272219ff0 enum encoding: exact 0..2, approximate 0x80..82,
  // incompatible 0xfc. Scale has no source payload when incompatible.
  std::uint8_t sourceTag;
};
constexpr double kPlannerTempoTolerance = 0.16;
constexpr double kPlannerExpandedTempoTolerance = 0.287;
PlannerTempo applyTempoBinaryScale(PlannerTempo tempo, TempoBinaryScale scale);
PlannerTempoMatch matchPlannerTempos(PlannerTempo reference, PlannerTempo candidate,
                                    double tolerance);

// Weights are multiplied in supplied order. No clipping; a nonpositive product
// receives no tie-breaker. tieBreakDelta is a recovered region-field difference;
// its physical interpretation is intentionally not assigned here.
double plannerCandidateScore(double base, const std::vector<double>& weights,
                             double tieBreakDelta);
std::optional<std::size_t> bestPlannerCandidate(const std::vector<double>& scores);
struct PlannerCatalogSelection {
  PlannerAlgorithm algorithm;
  std::size_t candidateIndex;
};
// Arrays indexed by algorithm complexity (0..3). Eligibility is the complete
// strategy predicate result, not just the maximum-complexity gate. Candidate
// builders retain their own order. Choose the best positive candidate within
// the FIRST successful eligible strategy in the recovered catalog order.
std::optional<PlannerCatalogSelection> selectPlannerCatalogCandidate(
    const std::array<bool,4>& eligible,
    const std::array<std::vector<double>,4>& candidateScores);

// Already computed candidate predicates; this API does not fabricate missing
// region/beat/vocal/tonality analysis. Fields are used only by their source style.
struct BeatMatchedScoreInputs {
  bool normalTempoCompatible = false;
  bool expandedTempoCompatible = false;
  bool tonalityCompatible = false;
  std::optional<std::int64_t> matchingBarCount;
  bool vocalRelationshipIncompatible = false;
  bool leadingIncomingVocalSignificant = false;
  std::optional<double> barCountRatio;
  std::optional<double> trailingIncomingLoudnessRatio;
  double tieBreakDelta = 0;
};
// Only the three styles accepted by source candidate builder 272234380.
// nullopt means this builder cannot create a candidate for the supplied ID.
std::optional<double> beatMatchedStyleScore(std::int64_t styleId,
                                          const BeatMatchedScoreInputs& inputs);

} // namespace lmg::automix
