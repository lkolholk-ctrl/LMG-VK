#include "lmg/automix/automation.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <utility>

namespace lmg::automix {

Frame secondsToFrame(double seconds, std::uint32_t sampleRate) {
  if (!std::isfinite(seconds) || seconds < 0 || sampleRate == 0) {
    throw std::invalid_argument("Invalid time or sample rate");
  }
  const long double frames = std::round(static_cast<long double>(seconds) * sampleRate);
  // Use exclusive 2^63, exactly representable even where long double == double.
  if (frames >= std::ldexp(1.0L, 63)) {
    throw std::overflow_error("Frame position exceeds int64 range");
  }
  return static_cast<Frame>(frames);
}

void Ramp::validate() const {
  if (begin < 0 || end < begin || !std::isfinite(from) || !std::isfinite(to)) {
    throw std::invalid_argument("Invalid automation ramp");
  }
  if (curve == 0x81 && (from <= 0 || to <= 0)) {
    throw std::invalid_argument("Logarithmic endpoints must be positive");
  }
}

double Ramp::valueAt(Frame frame) const noexcept {
  // Order matches FUN_272275f84: end boundary precedes start boundary.
  if (frame >= end) return to;
  if (frame <= begin) return from;
  double p = static_cast<double>(frame - begin) / static_cast<double>(end - begin);
  return curveValue(from, to, p, curve);
}

double curveValue(double from, double to, double p, std::uint8_t curve) noexcept {
  if (curve == 0x81) {
    // 0x27227606c..084 transforms VALUES, 0x2722760e0..0e8 interpolates,
    // 0x272276120 returns exp2 directly. There is no second linear blend.
    return std::exp2(std::log2(from) + p * (std::log2(to) - std::log2(from)));
  }
  if (curve < 0x40) {
    if (curve == 0) p = 1.0 - std::sqrt(1.0 - p);
    else if (curve == 1) p *= p;
    else p = std::pow(p, 4.0);
  } else if (curve < 0x80) {
    if (curve == 0x40) p = std::sqrt(p);
    else if (curve == 0x41) p = 1.0 - (1.0 - p) * (1.0 - p);
    else p = 1.0 - std::pow(1.0 - p, 4.0);
  }
  // 0x80 and 0x82..0xff take the linear path in the observed evaluator.
  if (p <= 0) return from;
  if (p >= 1) return to;
  return from + p * (to - from);
}

AutomationLane::AutomationLane(double initial, std::vector<Ramp> ramps)
    : initial_(initial), ramps_(std::move(ramps)) {
  if (!std::isfinite(initial_)) throw std::invalid_argument("Non-finite initial value");
  for (const auto& ramp : ramps_) ramp.validate();
  std::sort(ramps_.begin(), ramps_.end(), [](const Ramp& a, const Ramp& b) {
    return a.begin < b.begin;
  });
  for (std::size_t i = 1; i < ramps_.size(); ++i) {
    if (ramps_[i].begin < ramps_[i - 1].end || ramps_[i].begin == ramps_[i - 1].begin) {
      throw std::invalid_argument("Overlapping or ambiguous automation ramps");
    }
  }
}

double AutomationLane::valueAt(Frame frame) const noexcept {
  auto next = std::upper_bound(ramps_.begin(), ramps_.end(), frame,
      [](Frame position, const Ramp& ramp) { return position < ramp.begin; });
  if (next == ramps_.begin()) return initial_;
  return (--next)->valueAt(frame);
}

}  // namespace lmg::automix
