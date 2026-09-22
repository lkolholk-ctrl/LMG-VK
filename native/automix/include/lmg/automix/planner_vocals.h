#pragma once
#include "lmg/automix/media_analysis.h"
#include <cstdint>
#include <optional>
#include <vector>

namespace lmg::automix {
// Source planner tags: kind 0=singing,1=speech,2=rapping;
// strength 0=veryLow,1=low,2=medium,3=high,4=veryHigh.
struct PlannerVocalActivity {
  double start;
  double end;
  std::uint8_t kindTag;     // source 0..2
  std::uint8_t strengthTag; // source 0..4; 5 is an optional-result sentinel
};
using PlannerVocalMap = std::vector<PlannerVocalActivity>;
struct PlannerVocalWindow { double start; double end; };
// Source builder retains input order and drops reversed intervals.
PlannerVocalMap makePlannerVocalMap(const std::vector<PlannerVocalActivity>& activities);
// Maximum strength among intervals intersecting the CLOSED window; touching
// endpoints count. Missing overlap is nullopt (source optional enum tag 5).
std::optional<std::uint8_t> plannerVocalStrength(
    const PlannerVocalMap& map, PlannerVocalWindow window);
// Window must come from source region/beat selection, not an invented duration.
// Missing map/window/strength => false; source strength 3 or 4 => true.
bool leadingIncomingVocalSignificant(
    const PlannerVocalMap* map, std::optional<PlannerVocalWindow> leadingWindow);
// Proven MusicKit cloud conversion: missing/unknown strength defaults to medium,
// missing/unknown kind to singing. Missing start/end drops the entry. A missing
// array stays absent; present empty/fully filtered arrays stay present. Millisecond
// fields must be integral finite signed-64-bit values (the original Int domain).
std::optional<PlannerVocalMap> normalizeCloudVocalActivities(
    const std::optional<std::vector<CloudVocalActivity>>& activities);
} // namespace lmg::automix
