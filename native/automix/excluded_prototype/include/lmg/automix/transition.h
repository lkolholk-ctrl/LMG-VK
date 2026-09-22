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

// Prepared two-deck execution, independent effect state and bounded scratch PCM.
// Constructor validates the complete plan and allocates off the audio thread.
// No time stretching: non-unity ts_rate is rejected, never silently discarded.
class TransitionRenderer {
 public:
  TransitionRenderer(std::uint32_t sampleRate, std::size_t channels,
                     DeckSchedule outgoing, DeckSchedule incoming,
                     std::size_t scratchFrames = 256);
  ~TransitionRenderer();
  TransitionRenderer(const TransitionRenderer&) = delete;
  TransitionRenderer& operator=(const TransitionRenderer&) = delete;
  // Aligned interleaved finite float PCM, null deck input drains its tails.
  // Exact output/input alias is supported, partial overlap is not. No limiter.
  // Invalid calls do not write output or advance either deck.
  bool render(const float* outgoing, const float* incoming, float* output,
              std::size_t frames, Frame firstFrame) noexcept;
  bool reset(Frame firstFrame = 0) noexcept;

 private:
  struct Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace lmg::automix
