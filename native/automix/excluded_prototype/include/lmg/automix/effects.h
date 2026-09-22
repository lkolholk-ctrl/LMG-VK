#pragma once

#include "lmg/automix/automation.h"

#include <cstddef>
#include <cstdint>
#include <memory>

namespace lmg::automix {

// Preparation-time settings. Units follow the recovered graph; algorithms
// are LMG implementations, not emulations of Apple's AudioUnits.
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

// One instance per deck, with separate state per channel. Construction and
// destruction allocate; process/reset do not. Single audio-thread ownership.
// Automation plans are immutable; values are evaluated once per output frame.
class TrackEffects {
 public:
  TrackEffects(std::uint32_t sampleRate, std::size_t channels,
               EffectSettings settings, double outputGain);
  TrackEffects(std::uint32_t sampleRate, std::size_t channels,
               EffectSettings settings, AutomationLane outputGain,
               std::vector<EffectAutomation> automation);
  ~TrackEffects();
  TrackEffects(const TrackEffects&) = delete;
  TrackEffects& operator=(const TrackEffects&) = delete;

  // Continuous interleaved finite PCM. Null input feeds silence AND drains tails.
  // Exact in-place is supported, partial overlap is not. No output limiter.
  // Invalid size/output returns false without advancing state or writing output.
  bool process(const float* input, float* output, std::size_t frames) noexcept;
  // Explicit timeline: must match the next expected frame. Discontinuities need
  // reset(firstFrame), which discards tails. Invalid calls leave state untouched.
  bool process(const float* input, float* output, std::size_t frames,
               Frame firstFrame) noexcept;
  // Clear all tails on seek/new track. Do not call concurrently with process.
  bool reset(Frame firstFrame = 0) noexcept;

 private:
  struct Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace lmg::automix
