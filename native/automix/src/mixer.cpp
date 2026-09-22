#include "lmg/automix/mixer.h"

#include <limits>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
void validateGain(const AutomationLane& lane) {
  auto check = [](double value) {
    if (value < 0 || value > 1) throw std::invalid_argument("Gain outside [0, 1]");
  };
  check(lane.initial());
  for (const auto& ramp : lane.ramps()) { check(ramp.from); check(ramp.to); }
}
}  // namespace

TrackGainPlan::TrackGainPlan(AutomationLane output, AutomationLane input,
                           AutomationLane send, AutomationLane dry, AutomationLane wet)
    : output_(std::move(output)), input_(std::move(input)), send_(std::move(send)),
      dry_(std::move(dry)), wet_(std::move(wet)) {
  for (const auto* lane : {&output_, &input_, &send_, &dry_, &wet_}) validateGain(*lane);
}

double TrackGainPlan::gainAt(Frame frame) const noexcept {
  // Input -> Gain1 -> dry Gain3 + wet Gain2/Gain4 -> external out_gain.
  return input_.valueAt(frame) *
      (dry_.valueAt(frame) + send_.valueAt(frame) * wet_.valueAt(frame)) * output_.valueAt(frame);
}

TwoDeckMixer::TwoDeckMixer(std::size_t channels, TrackGainPlan outgoing, TrackGainPlan incoming)
    : channels_(channels), outgoing_(std::move(outgoing)), incoming_(std::move(incoming)) {
  if (channels == 0) throw std::invalid_argument("Channel count must be positive");
}

bool TwoDeckMixer::render(const float* outgoing, const float* incoming, float* output,
                          std::size_t frames, Frame firstFrame) const noexcept {
  if (firstFrame < 0) return false;
  if (frames == 0) return true;
  if (output == nullptr || frames > std::numeric_limits<std::size_t>::max() / channels_ ||
      frames - 1 > static_cast<std::uint64_t>(std::numeric_limits<Frame>::max() - firstFrame)) {
    return false;
  }
  for (std::size_t frame = 0; frame < frames; ++frame) {
    const auto position = firstFrame + static_cast<Frame>(frame);
    const double a = outgoing_.gainAt(position);
    const double b = incoming_.gainAt(position);
    for (std::size_t channel = 0; channel < channels_; ++channel) {
      const std::size_t offset = frame * channels_ + channel;
      const double x = outgoing ? outgoing[offset] : 0.0;
      const double y = incoming ? incoming[offset] : 0.0;
      output[offset] = static_cast<float>(x * a + y * b);
    }
  }
  return true;
}

}  // namespace lmg::automix
