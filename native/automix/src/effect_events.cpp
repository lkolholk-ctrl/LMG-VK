#include "lmg/automix/effect_events.h"
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
std::optional<std::uint32_t> effectParameterAddress(std::string_view id) {
  for (unsigned char c : id)
    if (c > 0x7f) throw std::invalid_argument("Non-ASCII effect parameter ID");
  if (id.size() != 4) return std::nullopt;
  std::uint32_t address = 0;
  for (unsigned char c : id) address = (address << 8) | c;
  return address;
}

std::vector<TimedEffectEvent> compileTimedEffectEvents(const std::vector<SteppedAutomation>& automations) {
  struct Earlier {
    bool operator()(MediaTime a, MediaTime b) const { return compareMediaTimes(a, b) < 0; }
  };
  std::map<MediaTime, std::map<std::uint32_t, float>, Earlier> events;
  // 2721b5a04..18 loads the default provider's dictionary (280c813d0).
  // Its initializer 2721af178 seeds every FourCC parameter at time zero.
  auto& defaults = events[automationMediaTime(0)];
  for (const auto& p : allEffectParameters())
    if (const auto address = effectParameterAddress(p.id))
      defaults[*address] = static_cast<float>(p.defaultValue);
  const auto& output = effectParameter("out_gain");
  std::size_t writes = 0;
  for (const auto& automation : automations) {
    const auto& p = automation.parameter;
    if (p.id == output.id && p.minimum == output.minimum && p.maximum == output.maximum &&
        p.defaultValue == output.defaultValue && p.styleParameterId == output.styleParameterId)
      continue;
    const auto address = effectParameterAddress(p.id);
    if (!address) continue;
    for (const auto& point : automation.points) {
      // Resource and finite guards belong to LMG; narrowing itself matches fcvt.
      if (++writes > 1000000) throw std::length_error("Too many effect writes");
      if (!std::isfinite(point.value) || std::abs(point.value) > std::numeric_limits<float>::max())
        throw std::invalid_argument("Unrepresentable effect value");
      const auto time = automationMediaTime(point.time.stretchedSong);
      events[time][*address] = static_cast<float>(point.value);
    }
  }
  std::vector<TimedEffectEvent> result;
  result.reserve(events.size());
  for (auto& entry : events) result.push_back({entry.first, std::move(entry.second)});
  return result;
}
}  // namespace lmg::automix
