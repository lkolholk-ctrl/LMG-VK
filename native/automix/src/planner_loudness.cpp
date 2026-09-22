#include "lmg/automix/planner_loudness.h"
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
namespace {
void finite(double value) {
  if (!std::isfinite(value)) throw std::invalid_argument("Planner loudness value must be finite");
}
void validWindow(PlannerLoudnessWindow window) {
  finite(window.start); finite(window.end);
  if (window.start > window.end) throw std::invalid_argument("Reversed loudness window");
}
}
std::optional<PlannerLoudnessMap> makePlannerLoudnessMap(
    const std::vector<double>& values, std::optional<double> samplingFrequency,
    std::optional<double> songDuration) {
  if (values.empty()) return std::nullopt;
  double frequency;
  if (samplingFrequency) {
    finite(*samplingFrequency); frequency = *samplingFrequency;
  } else {
    if (!songDuration) return std::nullopt;
    finite(*songDuration);
    if (!(*songDuration > 0)) return std::nullopt;
    frequency = static_cast<double>(values.size()) / *songDuration;
    if (std::round(frequency) == 2.0) frequency = 2.0;
  }
  finite(frequency);
  if (!(frequency > 0)) return std::nullopt;
  const double interval = 1.0 / frequency;
  finite(interval);
  PlannerLoudnessMap result;
  result.reserve(values.size());
  for (std::size_t i = 0; i < values.size(); ++i) {
    finite(values[i]);
    const double time = interval * static_cast<double>(i);
    finite(time);
    result.push_back({values[i], time});
  }
  return result;
}
std::optional<double> meanPlannerLoudness(const PlannerLoudnessMap& map,
                                        PlannerLoudnessWindow window) {
  validWindow(window);
  double sum = 0.0;
  std::size_t count = 0;
  for (const auto& point : map) {
    finite(point.songTime);
    if (point.songTime >= window.start && point.songTime <= window.end) {
      finite(point.value); sum = sum + point.value; ++count;
    }
  }
  if (count == 0) return std::nullopt;
  finite(sum);
  return sum / static_cast<double>(count);
}
std::optional<double> trailingIncomingLoudnessRatio(
    const PlannerLoudnessMap* map, bool vocalMapAvailable,
    PlannerLoudnessWindow incomingRegion) {
  if (!vocalMapAvailable || !map) return std::nullopt;
  validWindow(incomingRegion);
  const auto leading = meanPlannerLoudness(*map, incomingRegion);
  if (!leading || !(*leading < 0)) return std::nullopt;
  const double duration = incomingRegion.end - incomingRegion.start;
  const double end = duration + incomingRegion.end;
  const auto trailing = meanPlannerLoudness(*map, {incomingRegion.end, end});
  if (!trailing || !(*trailing < 0)) return std::nullopt;
  const double ratio = *leading / *trailing;
  finite(ratio);
  return ratio;
}
} // namespace lmg::automix
