#include "lmg/automix/volume_ramps.h"
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
std::vector<TimedVolumeRamp> compileTimedVolumeRamps(
    const std::vector<SteppedAutomation>& automations) {
  const auto& volume = effectParameter("out_gain");
  std::vector<TimedVolumeRamp> result;
  auto append = [&](double from, double to, double start, double end) {
    if (!std::isfinite(from) || !std::isfinite(to) ||
        std::abs(from) > std::numeric_limits<float>::max() ||
        std::abs(to) > std::numeric_limits<float>::max())
      throw std::invalid_argument("Nonfinite volume ramp value");
    if (end < start) throw std::invalid_argument("Reversed volume ramp time range");
    if (result.size() == 1000000) throw std::length_error("Volume schedule too large");
    result.push_back({static_cast<float>(from), static_cast<float>(to),
                      automationMediaTime(start), automationMediaTime(end)});
  };
  for (const auto& a : automations) {
    const auto& p = a.parameter;
    if (p.id != volume.id || p.minimum != volume.minimum || p.maximum != volume.maximum ||
        p.defaultValue != volume.defaultValue || p.styleParameterId != volume.styleParameterId) continue;
    if (a.ramps.empty()) break;
    const auto& first = a.ramps.front();
    if (first.start.stretchedSong != 0)
      append(first.startValue, first.startValue, 0, first.start.stretchedSong);
    for (const auto& ramp : a.ramps)
      append(ramp.startValue, ramp.endValue, ramp.start.stretchedSong, ramp.end.stretchedSong);
    break;
  }
  return result;
}
}  // namespace lmg::automix
