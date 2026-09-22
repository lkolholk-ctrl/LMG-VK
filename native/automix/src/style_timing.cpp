#include "lmg/automix/styles.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
SecondsWindow resolveStyleWindow(SecondsWindow parent, StyleTime start, StyleTime end) {
  if (!std::isfinite(parent.begin) || !std::isfinite(parent.end) || parent.end<parent.begin ||
      !std::isfinite(parent.end-parent.begin))
    throw std::invalid_argument("Invalid style parent window");
  const auto point=[&](StyleTime time) {
    const double offset=time.offsetInSeconds.value_or(0);
    if (!std::isfinite(time.relative) || time.relative<0 || time.relative>1 || !std::isfinite(offset))
      throw std::invalid_argument("Invalid normalized style time");
    // Preserve the ARM multiply, inner add, outer add order (no FMA).
    const double value=parent.begin+(offset+(parent.end-parent.begin)*time.relative);
    if (!std::isfinite(value)) throw std::invalid_argument("Style time overflow");
    return std::max(parent.begin,std::min(parent.end,value));
  };
  const double first=point(start), last=point(end);
  if (last<first) {
    const double midpoint=first+(last-first)*.5;
    return {midpoint,midpoint};
  }
  return {first,last};
}
std::vector<SecondsWindow> resolveStyleWindows(const std::vector<StyleInstruction>& instructions,
                                               SecondsWindow parent) {
  // Validate the parent even with no instructions.
  resolveStyleWindow(parent,{0,std::nullopt},{1,std::nullopt});
  std::vector<SecondsWindow> result;
  for (const auto& instruction:instructions) {
    const auto placement=resolveStyleWindow(parent,instruction.placementStart,instruction.placementEnd);
    for (const auto& ramp:instruction.ramps)
      result.push_back(resolveStyleWindow(placement,ramp.start,ramp.end));
  }
  return result;
}
}
