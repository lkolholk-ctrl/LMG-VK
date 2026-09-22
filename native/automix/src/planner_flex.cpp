#include "lmg/automix/planner_flex.h"
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
std::optional<FlexEvent> flexEventFromScore(double time, std::int64_t score) {
  FlexTimeScale scale;
  if (score >= 200 && score < 300) scale = FlexTimeScale::shortTime;
  else if (score >= 400 && score < 500) scale = FlexTimeScale::medium;
  else if (score >= 600 && score < 700) scale = FlexTimeScale::longTime;
  else if (score >= 800 && score < 900) scale = FlexTimeScale::extraLong;
  else return std::nullopt;
  return FlexEvent{time, scale, static_cast<double>(score % 100) / 100.0};
}

std::optional<std::vector<FlexEvent>> normalizeCloudFlexEvents(const CloudVideoEvents& raw) {
  if (!raw.timeInSeconds || !raw.score) return std::nullopt;
  const auto& times = *raw.timeInSeconds;
  const auto& scores = *raw.score;
  if (scores.size() < times.size())
    throw std::invalid_argument("Flex video-event scores shorter than times");
  std::vector<FlexEvent> result;
  result.reserve(times.size());
  for (std::size_t i = 0; i < times.size(); ++i) {
    if (!std::isfinite(times[i])) throw std::invalid_argument("Nonfinite Flex event time");
    const double score = scores[i];
    // 2^63 is exactly representable; INT64_MAX converted to double rounds up to
    // it, so comparing against a double-cast INT64_MAX would permit UB here.
    if (!std::isfinite(score) || score < -0x1p63 || score >= 0x1p63 || std::trunc(score) != score)
      throw std::invalid_argument("Flex score is not a signed64 integer");
    if (auto event = flexEventFromScore(times[i], static_cast<std::int64_t>(score)))
      result.push_back(*event);
  }
  return result;
}
} // namespace lmg::automix
