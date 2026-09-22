#include "lmg/automix/gain.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {
bool validGain(float gain) noexcept {
  // Host limit for the graph's 0..1 gain parameters; the original setter itself
  // stores the supplied float without a clamp.
  return std::isfinite(gain) && gain >= 0 && gain <= 1;
}
}

GainSmoother::GainSmoother(std::uint32_t rate, float gain)
    : sampleRate_(rate), requested_(gain), current_(gain), target_(gain) {
  if (rate < 8000 || rate > 192000 || !validGain(gain))
    throw std::invalid_argument("Invalid gain smoother configuration");
}

void GainSmoother::synchronizeFirstWrite() noexcept {
  // setParameter 0x18f5c41a8..41e4: before first process (or immediately after
  // reset), write both current and target. Later writes only change requested.
  if (first_) current_ = target_ = requested_ * (muted_ ? 0.0f : 1.0f);
}

bool GainSmoother::setGain(float gain) noexcept {
  if (!validGain(gain)) return false;
  requested_ = gain;
  synchronizeFirstWrite();
  return true;
}

void GainSmoother::setMuted(bool muted) noexcept {
  muted_ = muted;
  synchronizeFirstWrite();
}

bool GainSmoother::setSmoothingSeconds(double seconds) noexcept {
  if (!std::isfinite(seconds) || seconds * sampleRate_ > std::numeric_limits<std::uint32_t>::max())
    return false;
  seconds_ = std::max(seconds, .002); // property setter 0x18f5c3e08..3e20
  return true;
}

void GainSmoother::reset() noexcept {
  current_ = requested_ * (muted_ ? 0.0f : 1.0f);
  first_ = ready_ = true;
}

GainBlock GainSmoother::beginBlock(std::uint32_t frames, bool silent) noexcept {
  first_ = false;
  if (ready_) target_ = requested_ * (muted_ ? 0.0f : 1.0f);
  GainBlock block{current_, 0, target_, 0, silent};
  // Input silence skips both vector processing and ramp advancement in
  // 0x18f3bfee8 -> 0x18f3bff7c -> 0x18f3c0198.
  if (!silent && current_ != target_) {
    if (ready_) {
      const auto rampLength = static_cast<std::uint32_t>(seconds_ * double(sampleRate_));
      increment_ = (target_ - current_) / float(rampLength);
    }
    ready_ = false;
    const float end = std::fma(increment_, float(frames), current_);
    const bool overshoot = (increment_ > 0 && end > target_) ||
                           (increment_ < 0 && end < target_);
    block.increment = increment_;
    if (overshoot) {
      const float remaining = std::abs((target_ - current_) / increment_);
      // ARM fcvtzu saturates; C++ out-of-range float->integer would be undefined.
      block.rampFrames = double(remaining) >= double(std::numeric_limits<std::uint32_t>::max())
          ? std::numeric_limits<std::uint32_t>::max() : static_cast<std::uint32_t>(remaining);
      current_ = target_;
    } else {
      block.rampFrames = frames;
      current_ = end;
    }
  }
  // New writes during an unfinished ramp remain queued until a later slice.
  // They do not restart its slope: process only reads requested when ready.
  if (current_ == target_) ready_ = true;
  return block;
}

}  // namespace lmg::automix
