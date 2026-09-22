#include "lmg/automix/styles.h"
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {
using Time = long double;
Time relative(const StyleTime& t, Time begin, Time end, std::uint32_t rate) {
  if (!std::isfinite(t.relative) || t.relative < 0 || t.relative > 1 ||
      (t.offsetInSeconds && !std::isfinite(*t.offsetInSeconds)))
    throw std::invalid_argument("Invalid relative style time");
  return begin + static_cast<Time>(t.relative) * (end - begin) +
      static_cast<Time>(t.offsetInSeconds.value_or(0)) * rate;
}
void window(Time begin, Time end, Time parentBegin, Time parentEnd) {
  if (!std::isfinite(begin) || !std::isfinite(end) || begin > end ||
      begin < parentBegin || end > parentEnd)
    throw std::invalid_argument("Style time window exceeds placement/transition");
}
Frame frame(Time offset, Frame origin) {
  const Time whole = std::floor(offset);
  const Time rounded = whole + (offset - whole >= .5L ? 1 : 0);
  // Round the local offset before adding the integer origin, preserving small
  // windows even when the absolute timeline exceeds double's exact integer range.
  if (!std::isfinite(rounded) || rounded < -static_cast<Time>(origin) ||
      rounded > static_cast<Time>(std::numeric_limits<Frame>::max() - origin))
    throw std::invalid_argument("Resolved style time outside frame timeline");
  return origin + static_cast<Frame>(rounded);
}
ResolvedSideTiming side(const std::vector<StyleInstruction>& instructions,
                       FrameWindow transition, std::uint32_t rate, StyleTimeBasis basis) {
  ResolvedSideTiming result;
  const Time duration = transition.end - transition.begin;
  for (const auto& i : instructions) {
    const Time begin = relative(i.placementStart, 0, duration, rate);
    const Time end = relative(i.placementEnd, 0, duration, rate);
    window(begin, end, 0, duration);
    result.placements.push_back({frame(begin, transition.begin), frame(end, transition.begin)});
    for (const auto& ramp : i.ramps) {
      const bool local = basis == StyleTimeBasis::InstructionRelative;
      const Time start = relative(ramp.start, local ? begin : 0, local ? end : duration, rate);
      const Time stop = relative(ramp.end, local ? begin : 0, local ? end : duration, rate);
      window(start, stop, begin, end);
      result.ramps.push_back({frame(start, transition.begin), frame(stop, transition.begin)});
    }
  }
  return result;
}
} // namespace
ResolvedStyleTiming resolveStyleTiming(const TransitionStyle& style, FrameWindow transition,
                                       std::uint32_t rate, StyleTimeBasis basis) {
  if (!rate || transition.begin < 0 || transition.end < transition.begin ||
      (basis != StyleTimeBasis::InstructionRelative && basis != StyleTimeBasis::TransitionRelative))
    throw std::invalid_argument("Invalid style timing context");
  ResolvedStyleTiming result{side(style.outgoing, transition, rate, basis),
                            side(style.incoming, transition, rate, basis), {}};
  if (style.offset) result.offsetAnchor = frame(relative(*style.offset, 0,
      static_cast<Time>(transition.end - transition.begin), rate), transition.begin);
  return result;
}
} // namespace lmg::automix
