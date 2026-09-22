#pragma once
#include "lmg/automix/planner_flex.h"
#include <cstddef>

namespace lmg::automix {
enum class StructureEventKind : std::uint8_t { beat, downbeat, segmentBoundary, sectionBoundary };
struct StructureEvent {
  double songTime;
  StructureEventKind kind;
  std::int64_t beatIndex;
  std::optional<std::int64_t> downbeatIndex, segmentIndex, sectionIndex;
};

// Exact first stage 272225ef4. MusicKit structure comes from Flex video events,
// NOT audio-analysis.beats. Higher categories also increment lower-level indices.
// This is explicitly pre-normalization: section snapping is a later operation.
std::vector<StructureEvent> initialStructureEvents(const std::vector<FlexEvent>& events);

// Event references are indices into SongStructure.events, not detached copies.
struct StructureRegion { std::size_t startEvent, endEvent; };
struct BeatStabilityRegion {
  StructureRegion events;
  std::int64_t beatsPerBar;
  double averageTempoBpm;
};
struct SongStructure {
  std::vector<StructureEvent> events;
  std::vector<std::size_t> beatEvents, downbeatEvents, segmentBoundaryEvents, sectionBoundaryEvents;
  std::vector<StructureRegion> bars, segments, sections;
  std::vector<BeatStabilityRegion> beatStabilityMap;
};

// Complete MusicKit Flex -> SongStructure branch: initial hierarchy, section
// alignment to a four-downbeat phase, event reclassification, adjacent regions
// and stable-bar runs. Flex amplitudes are not consumed by this source path.
SongStructure songStructureFromFlexEvents(const std::vector<FlexEvent>& events);
} // namespace lmg::automix
