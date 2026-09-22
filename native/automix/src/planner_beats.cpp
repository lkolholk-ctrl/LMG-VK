#include "lmg/automix/planner_beats.h"
#include <cmath>
#include <limits>
#include <set>
#include <stdexcept>

namespace lmg::automix {
std::vector<StructureEvent> initialStructureEvents(const std::vector<FlexEvent>& source) {
  if (source.size() > static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))
    throw std::invalid_argument("Too many structure events");
  std::vector<StructureEvent> result;
  result.reserve(source.size());
  std::optional<std::int64_t> downbeat, segment, section;
  auto increment = [](std::optional<std::int64_t>& index) { index = index ? *index + 1 : 0; };
  for (const auto& event : source) {
    if (!std::isfinite(event.timeInSeconds)) throw std::invalid_argument("Nonfinite structure time");
    const auto tag = static_cast<unsigned>(event.timeScale);
    if (tag > 3) throw std::invalid_argument("Invalid Flex time-scale tag");
    if (tag >= 1) increment(downbeat);
    if (tag >= 2) increment(segment);
    if (tag >= 3) increment(section);
    result.push_back({event.timeInSeconds, static_cast<StructureEventKind>(tag),
                      static_cast<std::int64_t>(result.size()), downbeat, segment, section});
  }
  return result;
}

namespace {
std::vector<std::size_t> eventIndices(const std::vector<StructureEvent>& events, unsigned minimumKind) {
  std::vector<std::size_t> result;
  for (std::size_t i = 0; i < events.size(); ++i)
    if (static_cast<unsigned>(events[i].kind) >= minimumKind) result.push_back(i);
  return result;
}
std::vector<std::size_t> normalizedSections(const std::vector<StructureEvent>& events) {
  const auto downbeats = eventIndices(events, 1);
  const auto sections = eventIndices(events, 3);
  std::int64_t phase = 0;
  if (!sections.empty()) {
    auto reference = sections.front();
    for (std::size_t i = 1; i < sections.size(); ++i) {
      if ((*events[sections[i]].downbeatIndex - *events[sections[i-1]].downbeatIndex) % 4 == 0) {
        reference = sections[i-1];
        break;
      }
    }
    phase = *events[reference].downbeatIndex % 4;
  }
  std::vector<std::size_t> candidates;
  for (const auto index : downbeats) {
    const auto delta = *events[index].downbeatIndex - phase;
    if (delta >= 0 && delta % 4 == 0) candidates.push_back(index);
  }
  const std::set<std::size_t> originalSections(sections.begin(), sections.end());
  std::vector<std::size_t> selected;
  for (const auto original : sections) {
    std::optional<std::size_t> before, after;
    const auto time = events[original].songTime;
    // These are last/first satisfying entries in source order, not a global
    // distance sort. This also preserves the original handling of unusual order.
    for (const auto candidate : candidates) {
      if (events[candidate].songTime <= time) before = candidate;
      if (!after && time <= events[candidate].songTime) after = candidate;
    }
    auto choice = before ? before : after;
    if (before && after &&
        (events[*after].songTime - time) + -1e-9 <= time - events[*before].songTime)
      choice = after;
    // Do not steal another original section's downbeat. Exact self-matches also
    // take this path, with identical result. No candidate means retain original.
    if (!choice || originalSections.count(*choice)) choice = original;
    if (selected.empty() || *events[selected.back()].downbeatIndex < *events[*choice].downbeatIndex)
      selected.push_back(*choice);
  }
  return selected;
}
std::vector<StructureRegion> adjacentRegions(const std::vector<std::size_t>& indices) {
  std::vector<StructureRegion> regions;
  for (std::size_t i = 1; i < indices.size(); ++i) regions.push_back({indices[i-1], indices[i]});
  return regions;
}
std::vector<BeatStabilityRegion> stableRegions(const SongStructure& structure) {
  std::vector<BeatStabilityRegion> result;
  if (structure.bars.empty()) return result;
  auto duration = [&](std::size_t bar) {
    const auto region = structure.bars[bar];
    return structure.events[region.endEvent].songTime - structure.events[region.startEvent].songTime;
  };
  auto beats = [&](std::size_t bar) {
    const auto region = structure.bars[bar];
    return structure.events[region.endEvent].beatIndex - structure.events[region.startEvent].beatIndex;
  };
  std::size_t start = 0, count = 1;
  auto beatCount = beats(0);
  double total = duration(0), mean = total;
  auto finish = [&](std::size_t endBar) {
    if (count < 5) return;
    // Exact helper2722196e0 and caller: barDuration/beats, 60/beatDuration,
    // add value to itself, FRINTX, multiply by one half.
    const double beatDuration = mean / static_cast<double>(beatCount);
    const double bpm = 60.0 / beatDuration;
    const double quantized = std::nearbyint(bpm + bpm) * 0.5;
    result.push_back({{structure.bars[start].startEvent, structure.bars[endBar].endEvent}, beatCount, quantized});
  };
  for (std::size_t bar = 1; bar < structure.bars.size(); ++bar) {
    const double currentDuration = duration(bar);
    if (std::abs(currentDuration - mean) > 0x1.47ae150451a6fp-5 || beats(bar) != beatCount) {
      finish(bar - 1);
      start = bar;
      count = 1;
      beatCount = beats(bar);
      total = currentDuration;
    } else {
      ++count;
      total += currentDuration;
    }
    mean = total / static_cast<double>(count);
  }
  finish(structure.bars.size() - 1);
  return result;
}
} // namespace

SongStructure songStructureFromFlexEvents(const std::vector<FlexEvent>& input) {
  const auto initial = initialStructureEvents(input);
  const auto sections = normalizedSections(initial);
  std::vector<FlexEvent> reclassified;
  reclassified.reserve(input.size());
  std::size_t next = 0;
  for (std::size_t i = 0; i < input.size(); ++i) {
    auto tag = input[i].timeScale;
    if (next < sections.size() && sections[next] == i) {
      tag = FlexTimeScale::extraLong;
      ++next;
    } else if (tag == FlexTimeScale::extraLong) {
      tag = FlexTimeScale::medium;
    }
    reclassified.push_back({input[i].timeInSeconds, tag, input[i].amplitude});
  }
  SongStructure result;
  result.events = initialStructureEvents(reclassified);
  result.beatEvents = eventIndices(result.events, 0);
  result.downbeatEvents = eventIndices(result.events, 1);
  result.segmentBoundaryEvents = eventIndices(result.events, 2);
  result.sectionBoundaryEvents = eventIndices(result.events, 3);
  result.bars = adjacentRegions(result.downbeatEvents);
  result.segments = adjacentRegions(result.segmentBoundaryEvents);
  result.sections = adjacentRegions(result.sectionBoundaryEvents);
  result.beatStabilityMap = stableRegions(result);
  return result;
}
} // namespace lmg::automix
