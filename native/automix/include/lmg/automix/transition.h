#pragma once

#include "lmg/automix/effects.h"
#include <string>
#include <string_view>

namespace lmg::automix {

// Bridge from the Kotlin/research parameter IDs. Times must already be resolved
// to the shared output-frame timeline; this does not choose styles or beat grids.
struct ParameterSchedule {
  std::string parameterId;
  AutomationLane lane;
};
struct DeckSchedule {
  EffectSettings settings;
  // Exactly one out_gain is required. Missing effect lanes use settings.
  std::vector<ParameterSchedule> parameters;
};

std::uint8_t styleCurveByte(std::string_view interpolation);

} // namespace lmg::automix
