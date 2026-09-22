#include "lmg/automix/delay.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
DelayKernel::DelayKernel(double rate) : rate_(rate), write_(0) {
  if (!std::isfinite(rate) || rate < 8000 || rate > 192000)
    throw std::invalid_argument("Unsupported delay PCM rate");
  // 0x234fdea30..0x234fdea74: ceil power of two of truncated (2*rate+10).
  const auto required = static_cast<std::uint32_t>(std::fma(rate, 2., 10.));
  std::uint32_t count = 1;
  while (count < required) count <<= 1;
  ring_.resize(count, 0);
  write_ = count - 1;
}
void DelayKernel::reset() noexcept {
  std::fill(ring_.begin(), ring_.end(), 0.f);
  accumulator_ = 0;
}
bool DelayKernel::process(const float* input, float* output, std::size_t frames,
                          DelayParameters p, bool* silence) noexcept {
  if (frames > std::numeric_limits<std::uint32_t>::max() ||
      (frames && (!input || !output)) ||
      !std::isfinite(p.wetPercent) || p.wetPercent < 0 || p.wetPercent > 100 ||
      !std::isfinite(p.seconds) || !std::isfinite(p.feedbackPercent) || !std::isfinite(p.lowpassHz))
    return false;
  const float cutoff = std::clamp(p.lowpassHz, 10.f, 24000.f);
  const float normalized = static_cast<float>((double(cutoff) + double(cutoff)) / rate_);
  float seconds = double(p.seconds) < .0001 ? .0001f : p.seconds;
  if (seconds > 2.f) seconds = 2.f;
  auto distance = static_cast<std::uint32_t>(rate_ * double(seconds));
  distance = std::clamp(distance, 1u, static_cast<std::uint32_t>(ring_.size() - 1));
  float feedback = static_cast<float>(double(p.feedbackPercent) * .01);
  if (double(feedback) < -.999) feedback = -.999f;
  if (double(feedback) > .999) feedback = .999f;
  const auto capacity = static_cast<std::uint32_t>(ring_.size());
  auto read = (write_ + capacity - distance) % capacity;
  const double exponent = double(normalized > 1.f ? 1.f : normalized) * -0x1.921fb54442d18p+1;
  const double pole = std::exp(exponent); // 0x236f45790 -> libsystem_m _exp
  const float damping = static_cast<float>(1. - pole);
  const float wet = static_cast<float>(double(p.wetPercent) * .01);
  const float wetGain = std::sqrt(wet);
  const float dryGain = static_cast<float>(std::sqrt(1. - double(wet)));
  float peak = 0;
  for (std::size_t i = 0; i < frames; ++i) {
    const float x = input[i];
    const float delayed = ring_[read];
    if (++read == capacity) read = 0;
    // The previous accumulator supplies output/feedback. The newly read delay
    // sample only affects the NEXT output sample. Preserve float rounding order.
    const float filtered = accumulator_ * damping;
    const float sum = delayed + accumulator_;
    accumulator_ = sum - filtered;
    ring_[write_] = std::fma(filtered, feedback, x);
    if (++write_ == capacity) write_ = 0;
    const float wetSample = wetGain * filtered;
    const float y = std::fma(dryGain, x, wetSample);
    const float magnitude = std::abs(y);
    if (magnitude > peak) peak = magnitude;
    output[i] = y;
  }
  if (silence && double(peak) > 1e-6) *silence = false;
  return true;
}
} // namespace lmg::automix
