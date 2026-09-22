#pragma once

#include "lmg/automix/automation.h"

#include <cstddef>
#include <cstdint>
#include <memory>

namespace lmg::automix {

// Preparation-time metadata from the recovered graph. This structure alone
// does not implement the effect graph; see the individual kernels and evidence.
struct EffectSettings {
  bool bypass = true;  // DSPGraph bypa=1 bypasses all seven effects.
  double inputGain = 1, sendGain = 1, dryGain = 1, wetGain = 0;
  double centerHz = 2500, bandwidthOctaves = 2, centerGainDb = 0;
  double highpassHz = 10, highpassResonanceDb = 0;
  double lowpassHz = 22000, lowpassResonanceDb = 0;
  double delaySeconds = 1, delayLowpassHz = 2500;
  double delayWetPercent = 0, delayFeedbackPercent = 50;
  double reverbGainDb = 1, reverbWetPercent = 0;
  double reverbMinSeconds = .008, reverbMaxSeconds = .05;
  double reverbLowDecaySeconds = 1, reverbHighDecaySeconds = .5;
  std::uint32_t reverbSeed = 1;
  double wetHighpassHz = 10, wetHighpassResonanceDb = 0;
  double wetLowpassHz = 22000, wetLowpassResonanceDb = 0;
};

// Typed member binding, e.g. {&EffectSettings::lowpassHz, AutomationLane(...)}.
// Reverb geometry (min/max delay and seed) and bypass remain preparation-only.
// Each supported double member may appear at most once. Validated off callback.
struct EffectAutomation {
  double EffectSettings::* parameter;
  AutomationLane lane;
};

} // namespace lmg::automix
