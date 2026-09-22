#include "lmg/automix/gain.h"
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>

namespace lmg::automix {
bool multiplyGain(const float* input, float* output, std::size_t frames, float gain) noexcept {
  if ((frames && (!input || !output)) || frames > std::numeric_limits<std::uint32_t>::max() ||
      !std::isfinite(gain)) return false;
  for (std::size_t i = 0; i < frames; ++i) output[i] = input[i] * gain;
  return true;
}

bool multiplyAddGain(const float* input, const float* addend, float* output,
                     std::size_t frames, float gain) noexcept {
  if ((frames && (!input || !addend || !output)) || frames > std::numeric_limits<std::uint32_t>::max() ||
      !std::isfinite(gain)) return false;
  for (std::size_t i = 0; i < frames; ++i) output[i] = std::fma(input[i], gain, addend[i]);
  return true;
}

bool multiplyGainRamp(const float* input, float* output, std::size_t frames,
                      float& start, float increment) noexcept {
  if ((frames && (!input || !output)) || frames > std::numeric_limits<std::uint32_t>::max() ||
      !std::isfinite(start) || !std::isfinite(increment)) return false;
  if (!frames) return true;
  float current = start;
  std::size_t position = 0;
  auto scalar = [&]() {
    const float value = input[position] * current;
    current = current + increment;
    output[position++] = value;
  };
  if (frames >= 4) {
    // Only destination alignment gates the unit-stride vector branch.
    while (position < frames && (reinterpret_cast<std::uintptr_t>(output + position) & 15)) scalar();
    if (position == frames) { start = current; return true; }
    using Vector = std::array<float, 4>;
    Vector ramp;
    for (unsigned lane = 0; lane < 4; ++lane)
      ramp[lane] = std::fma(float(lane), increment, current);
    const float step4 = 4.0f * increment;
    if (frames - position >= 32) {
      const float step8 = 8.0f * increment, step16 = 16.0f * increment;
      const float step32 = step16 + step16;
      std::array<Vector, 8> groups;
      groups[0] = ramp;
      for (unsigned lane = 0; lane < 4; ++lane) {
        groups[1][lane] = ramp[lane] + step4;
        groups[2][lane] = ramp[lane] + step8;
        groups[3][lane] = groups[2][lane] + step4;
        groups[4][lane] = ramp[lane] + step16;
        groups[5][lane] = groups[1][lane] + step16;
        groups[6][lane] = groups[2][lane] + step16;
        groups[7][lane] = groups[3][lane] + step16;
      }
      do {
        for (unsigned group = 0; group < 8; ++group)
          for (unsigned lane = 0; lane < 4; ++lane) {
            output[position] = input[position] * groups[group][lane];
            ++position;
          }
        for (auto& group : groups)
          for (auto& value : group) value = value + step32;
      } while (frames - position >= 32);
      ramp = groups[0];
    }
    while (frames - position >= 4) {
      for (unsigned lane = 0; lane < 4; ++lane) {
        output[position] = input[position] * ramp[lane];
        ++position;
        ramp[lane] = ramp[lane] + step4;
      }
    }
    current = ramp[0];
  }
  while (position < frames) scalar();
  start = current;
  return true;
}

bool applyGainBlock(const float* input, float* output, std::size_t frames, GainBlock block) noexcept {
  if ((frames && (!output || (!input && !block.inputSilent))) ||
      frames > std::numeric_limits<std::uint32_t>::max() || block.rampFrames > frames ||
      !std::isfinite(block.start) || !std::isfinite(block.increment) || !std::isfinite(block.constantGain))
    return false;
  if (!frames) return true;
  if (block.inputSilent) {
    for (std::size_t i = 0; i < frames; ++i) output[i] = 0;
    return true;
  }
  multiplyGainRamp(input, output, block.rampFrames, block.start, block.increment);
  return multiplyGain(input + block.rampFrames, output + block.rampFrames,
                      frames - block.rampFrames, block.constantGain);
}
}  // namespace lmg::automix
