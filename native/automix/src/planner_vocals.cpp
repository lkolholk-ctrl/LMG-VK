#include "lmg/automix/planner_vocals.h"
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
namespace {
void validTimes(double start, double end) {
  if (!std::isfinite(start) || !std::isfinite(end))
    throw std::invalid_argument("Vocal activity time must be finite");
}
void validTags(const PlannerVocalActivity& activity) {
  if (activity.kindTag > 2 || activity.strengthTag > 4)
    throw std::invalid_argument("Unknown source planner vocal enum tag");
}
}
PlannerVocalMap makePlannerVocalMap(const std::vector<PlannerVocalActivity>& activities) {
  PlannerVocalMap result;
  result.reserve(activities.size());
  for (const auto& activity : activities) {
    validTimes(activity.start, activity.end);
    if (activity.end < activity.start) continue;
    validTags(activity);
    result.push_back(activity);
  }
  return result;
}
std::optional<std::uint8_t> plannerVocalStrength(
    const PlannerVocalMap& map, PlannerVocalWindow window) {
  validTimes(window.start, window.end);
  if (window.end < window.start) throw std::invalid_argument("Reversed vocal window");
  std::optional<std::uint8_t> result;
  for (const auto& activity : map) {
    validTimes(activity.start, activity.end); validTags(activity);
    if (activity.end < activity.start)
      throw std::invalid_argument("Unprepared reversed vocal activity");
    if (activity.start <= window.end && activity.end >= window.start &&
        (!result || *result < activity.strengthTag)) result = activity.strengthTag;
  }
  return result;
}
bool leadingIncomingVocalSignificant(
    const PlannerVocalMap* map, std::optional<PlannerVocalWindow> leadingWindow) {
  if (!map || !leadingWindow) return false;
  const auto strength = plannerVocalStrength(*map, *leadingWindow);
  return strength && *strength >= 3;
}
std::optional<PlannerVocalMap> normalizeCloudVocalActivities(
    const std::optional<std::vector<CloudVocalActivity>>& activities) {
  if (!activities) return std::nullopt;
  PlannerVocalMap result;
  result.reserve(activities->size());
  auto seconds = [](double ms) {
    // +2^63 cannot be converted to signed int64; -2^63 can.
    if (!std::isfinite(ms) || ms < -0x1p63 || ms >= 0x1p63 || std::trunc(ms) != ms)
      throw std::invalid_argument("Cloud vocal milliseconds outside signed Int domain");
    return static_cast<double>(static_cast<std::int64_t>(ms)) / 1000.0;
  };
  for (const auto& cloud : *activities) {
    if (!cloud.time.startInMilliseconds || !cloud.time.endInMilliseconds) continue;
    const double start = seconds(*cloud.time.startInMilliseconds);
    const double end = seconds(*cloud.time.endInMilliseconds);
    if (end < start) continue;
    std::uint8_t strength = 2, kind = 0;
    if (cloud.strength) {
      if (*cloud.strength == "very-low") strength = 0;
      else if (*cloud.strength == "low") strength = 1;
      else if (*cloud.strength == "medium") strength = 2;
      else if (*cloud.strength == "high") strength = 3;
      else if (*cloud.strength == "very-high") strength = 4;
    }
    if (cloud.kind) {
      if (*cloud.kind == "speech") kind = 1;
      else if (*cloud.kind == "rapping") kind = 2;
    }
    result.push_back({start, end, kind, strength});
  }
  return result;
}
} // namespace lmg::automix
