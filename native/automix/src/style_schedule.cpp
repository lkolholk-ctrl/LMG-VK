#include "lmg/automix/styles.h"
#include <cmath>
#include <stdexcept>
#include <utility>
namespace lmg::automix {
namespace {
double value(const StyleValue& input,const std::map<std::string,double>& mapped) {
  auto result=input.fallback;
  if (input.parameterName) {
    const auto it=mapped.find(*input.parameterName);
    if (it!=mapped.end()) result=it->second;
  }
  if (!result || !std::isfinite(*result))
    throw std::invalid_argument("Unresolved or nonfinite style value");
  return *result;
}
}
std::vector<ContinuousAutomation> compileContinuousStyle(
    const std::vector<StyleInstruction>& instructions,SecondsWindow parent,
    StylePlaybackRates rates,const std::map<std::string,double>& mapped) {
  const auto windows=resolveStyleWindows(instructions,parent);
  std::vector<ContinuousAutomation> result;
  result.reserve(windows.size()+1);
  std::size_t index=0;
  for (const auto& instruction:instructions) for (const auto& ramp:instruction.ramps) {
    const auto& descriptor=styleEffectParameter(ramp.parameterId);
    const auto window=windows[index++];
    double first,last;
    std::uint8_t curve;
    if (descriptor.id=="ts_rate") {
      if (!std::isfinite(rates.start) || !std::isfinite(rates.end) || rates.start<=0 || rates.end<=0)
        throw std::invalid_argument("Invalid planner playback rates");
      first=rates.start; last=rates.end; curve=0x80;
    } else {
      first=value(ramp.from,mapped); last=value(ramp.to,mapped);
      curve=styleCurveByte(ramp.interpolation);
    }
    result.push_back({descriptor,{{first,window.begin,curve},{last,window.end,curve}}});
  }
  return withStyleBypass(std::move(result),parent.begin,parent.end);
}
}
