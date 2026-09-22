#pragma once
#include "lmg/automix/media_analysis.h"
#include "lmg/automix/planner_flex.h"
#include "lmg/automix/planner_beats.h"
#include "lmg/automix/planner_loudness.h"
#include "lmg/automix/planner_vocals.h"
#include "lmg/automix/planner_scoring.h"

namespace lmg::automix {
struct PlannerTonalityMap {
  PlannerTonality main;
  std::optional<PlannerTonality> beginning, ending;
};
std::optional<PlannerTonality> normalizeCloudTonality(const CloudKey&);
std::optional<PlannerTonalityMap> normalizeCloudTonalityMap(const CloudComposite<CloudKey>&);
// Recovered analysis maps and Flex-derived structure; not a transition plan.
struct PlannerAnalysisMaps {
  std::optional<PlannerTonalityMap> tonality;
  std::optional<PlannerLoudnessMap> loudness;
  std::optional<PlannerVocalMap> vocals;
  std::optional<std::vector<FlexEvent>> flexEvents;
  std::optional<SongStructure> structure;
};
// Use the caller's resolved song duration in seconds. Raw catalog milliseconds
// are not silently substituted for a playback duration. Missing/unresolved
// relationship attributes remain unavailable; present empty arrays stay present
// where their recovered converter preserves them.
PlannerAnalysisMaps preparePlannerAnalysisMaps(const CloudSongAnalysis& song,
                                               std::optional<double> songDurationSeconds);
}  // namespace lmg::automix
