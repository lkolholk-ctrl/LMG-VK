#include "lmg/automix/planner_regions.h"
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
PlannerRegionInventory inspectPlannerRegions(const SongStructure& s,
                                              std::optional<double> duration) {
  if (duration && (!std::isfinite(*duration) || *duration <= 0))
    throw std::invalid_argument("Invalid resolved duration");
  if (s.events.size() > kPlannerObservationMaxEvents ||
      s.bars.size() > kPlannerObservationMaxEvents ||
      s.segments.size() > kPlannerObservationMaxEvents ||
      s.sections.size() > kPlannerObservationMaxEvents ||
      s.beatStabilityMap.size() > kPlannerObservationMaxEvents)
    throw std::length_error("Planner inventory limit");
  PlannerRegionInventory out;
  // Prefix counts avoid O(number-of-regions * event-count) rescans. Reversed
  // times INSIDE a region must not be hidden by plausible endpoint values.
  std::vector<std::size_t> reversals(s.events.size(), 0);
  for (std::size_t i = 0; i < s.events.size(); ++i) {
    if (!std::isfinite(s.events[i].songTime))
      throw std::invalid_argument("Nonfinite structure time");
    if (i == 0) continue;
    reversals[i] = reversals[i - 1];
    if (s.events[i].songTime == s.events[i - 1].songTime) ++out.duplicateTimes;
    if (s.events[i].songTime < s.events[i - 1].songTime) {
      ++out.reversedTimes;
      ++reversals[i];
    }
  }
  auto inspect = [&](StructureRegion r) {
    if (r.startEvent >= s.events.size() || r.endEvent >= s.events.size())
      throw std::invalid_argument("Region reference outside structure");
    const auto start = s.events[r.startEvent].songTime;
    const auto end = s.events[r.endEvent].songTime;
    const bool shape = r.startEvent < r.endEvent && start >= 0 && end > start &&
        reversals[r.endEvent] == reversals[r.startEvent];
    if (!shape) ++out.malformedRegions;
    const bool outside = duration && (start < 0 || end > *duration);
    if (outside) ++out.outsideTrackRegions;
    return shape;
  };
  for (const auto r : s.bars) inspect(r);
  for (const auto r : s.segments) inspect(r);
  for (const auto r : s.sections) inspect(r);
  for (std::size_t i = 0; i < s.beatStabilityMap.size(); ++i) {
    const auto& region = s.beatStabilityMap[i];
    const bool shape = inspect(region.events);
    const bool tempo = region.beatsPerBar > 0 &&
        region.beatsPerBar <= static_cast<std::int64_t>(kPlannerObservationMaxEvents) &&
        std::isfinite(region.averageTempoBpm) && region.averageTempoBpm > 0;
    if (shape && !tempo) ++out.malformedRegions;
    if (!shape || !tempo) continue;
    ++out.usableStableRegions;
    const double start = s.events[region.events.startEvent].songTime;
    const double end = s.events[region.events.endEvent].songTime;
    const auto bounds = static_cast<std::uint8_t>(!duration ? 0 : end <= *duration ? 1 : 2);
    if (bounds == 1) ++out.boundedStableRegions;
    if (out.stablePreview.size() < kPlannerObservationMaxPreview)
      out.stablePreview.push_back({i, region.events, start, end,
          region.beatsPerBar, region.averageTempoBpm, bounds});
  }
  return out;
}
} // namespace lmg::automix
