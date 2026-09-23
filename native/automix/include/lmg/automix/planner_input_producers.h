#pragma once

#include "lmg/automix/planner_candidate_observation.h"
#include <cstdint>
#include <optional>
#include <vector>

namespace lmg::automix {
// Native catalog lookup only. Raw JSON.duration -> internal maximumBarCount is
// NOT asserted here. Callers must supply decoded INTERNAL records and the exact
// requested-ID order from the strategy/context producer (not a sorted set).
enum class PlannerStyleResolutionStatus : std::uint8_t { resolved, missingStyle, resourceLimit };
struct PlannerStyleResolution {
  PlannerStyleResolutionStatus status = PlannerStyleResolutionStatus::missingStyle;
  std::vector<PlannerCandidateStyle> styles;
  std::int64_t maximumBars = 0;
  bool completeForRequestedIds = false;
};
PlannerStyleResolution resolvePlannerStyleRequests(
    const std::vector<std::int64_t>& requestedIds,
    const std::vector<PlannerCandidateStyle>& internalCatalog);

// MusicKit duration helper results (seconds), NOT final Criteria/context bounds.
// PreviousPlaybackEndState, early/late placement and spatial mapping stay above
// this layer. Do not substitute cloud duration for a resolved playback duration.
struct PlannerMusicKitDurationEnvelope {
  double preferredOffset;
  double maximumOutgoingDuration;
  double maximumIncomingDuration;
};
PlannerMusicKitDurationEnvelope plannerMusicKitDurationEnvelope(double resolvedDurationSeconds);

// MusicKit scalar composite: an absent/invalid main removes the entire map;
// invalid optional edges stay absent. Values outside [0,1] are not clamped.
std::optional<CloudComposite<double>> normalizePlannerMusicKitScalarComposite(
    const std::optional<CloudComposite<double>>& raw);
// Scoped to the recovered MusicKit MAIN-component consumer. This is not a
// universal provider policy for adaptive/spatial analyses. Edges do not replace
// a missing main. The caller must establish the MusicKit provider branch.
PlannerCandidateTonalBinding plannerMusicKitMainTonalComponents(const CloudSongAnalysis& song);

// Provider-derived limits for initial endpoint discovery, separate from the
// later candidate-placement constraints. Zero is a real bound, not "unknown".
struct PlannerSeedDiscoveryBounds {
  double minimumOutgoingEnd;
  double maximumIncomingEnd;
};
enum class PlannerSeedProductionStatus : std::uint8_t {
  produced, noSeeds, insufficientStructure, invalidPreparation,
  unresolvedPlaybackDuration, resourceLimit
};
struct PlannerSeedProduction {
  PlannerSeedProductionStatus status = PlannerSeedProductionStatus::noSeeds;
  std::vector<PlannerCandidateSeed> seeds;
  std::size_t outgoingEnds = 0, incomingEnds = 0;
  std::size_t outgoingStable = 0, incomingStable = 0, outgoingNonSilent = 0;
  std::size_t pairAttempts = 0, scaleMisses = 0, truncationMisses = 0;
  std::uint64_t workUnits = 0;
  bool completeForResolvedInputs = false;
  static constexpr bool canExecute = false;
};
// Bounds are LMG resource limits, not Apple's candidate-count thresholds.
// Preserve the existing selector's 64-seed interface: no truncation/deduplication
// and no partial result when the complete generated input does not fit.
inline constexpr std::uint64_t kPlannerProducerWorkBudget = kPlannerCandidateWorkBudget;

// These helpers operate on bounded validated native structures/maps. They are
// exported for focused source-rule tests and do not infer a whole-song profile.
std::vector<std::size_t> plannerOutgoingSeedEnds(const SongStructure&, double minimumEnd);
std::vector<std::size_t> plannerIncomingSeedEnds(const SongStructure&, double maximumEnd,
                                               std::int64_t maximumBars);
std::optional<StructureRegion> plannerStableSeedSuffix(const SongStructure&, StructureRegion);
std::optional<double> plannerSeedMeanBarTempo(const SongStructure&, StructureRegion);
std::optional<double> plannerLoudnessLinearSlope(const PlannerLoudnessMap&);
bool plannerOutgoingSeedNonSilent(const PlannerLoudnessMap*, region_algebra::PlannerSongTimeRange);

// Source endpoint pools -> suffixes -> stable/no-fade outgoing filter ->
// outgoing-major Cartesian traversal -> per-pair tempo scaling -> truncation.
// Uses full preparations, never the 16-item transport preview. Maps are borrowed
// only during the call. Invalid host-domain data throws; resource exhaustion
// discards ALL seeds/counters rather than exposing a prefix as the full set.
PlannerSeedProduction producePlannerCandidateSeeds(
    const PlannerSongPreparation& outgoing, const PlannerSongPreparation& incoming,
    const std::vector<PlannerCandidateStyle>& resolvedStyles,
    const PlannerSeedDiscoveryBounds& bounds,
    std::uint64_t workBudget = kPlannerProducerWorkBudget);
} // namespace lmg::automix
