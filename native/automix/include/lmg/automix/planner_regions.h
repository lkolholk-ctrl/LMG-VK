#pragma once
#include "lmg/automix/planner_beats.h"
#include <optional>

namespace lmg::automix {
// LMG diagnostic inventory, NOT Apple's candidate region generator. Never
// sorts, snaps, clips, rescales, pairs or selects source regions.
struct ObservedStableRegion {
  std::size_t mapIndex;
  StructureRegion events; // still indexes the owning SongStructure
  double startSeconds, endSeconds;
  std::int64_t beatsPerBar;
  double averageTempoBpm;
  // 0 duration unknown, 1 inside resolved track, 2 outside resolved track.
  std::uint8_t trackBounds;
};
struct PlannerRegionInventory {
  std::size_t duplicateTimes = 0, reversedTimes = 0;
  // Counts across bars, segments, sections and stability regions. A source
  // range can occur in more than one collection, and is counted in each.
  std::size_t malformedRegions = 0, outsideTrackRegions = 0;
  std::size_t usableStableRegions = 0, boundedStableRegions = 0;
  std::vector<ObservedStableRegion> stablePreview;
};
inline constexpr std::size_t kPlannerObservationMaxEvents = 4096;
inline constexpr std::size_t kPlannerObservationMaxPreview = 16;

// Bounds/resource checks belong to the host. They do not redefine recovered
// source normalization. Preview is the FIRST usable entries in source order,
// never a ranked/truncated candidate search. Invalid references throw.
PlannerRegionInventory inspectPlannerRegions(const SongStructure& structure,
                                              std::optional<double> durationSeconds);
} // namespace lmg::automix
