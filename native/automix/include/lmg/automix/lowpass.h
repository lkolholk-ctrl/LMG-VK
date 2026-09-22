#pragma once
#include <array>
#include <cstddef>

namespace lmg::automix {
// Recovered section storage order at 0x234fee508..0x234fee51c:
// feedback a1/a2, feedforward b0/b1/b2; denominator a0 normalized to one.
using BiquadCoefficients = std::array<double, 5>;
using LowpassCoefficients = BiquadCoefficients;

// libEmbeddedSystemAUs 23A341: AULopassFactory -> kernel update 0x234f68434.
// Parameter values are float, sample rate is narrowed through float by the AU.
// Finite parameters and a positive float-representable rate are host prerequisites.
// Throws invalid_argument on invalid host input / nonfinite coefficient result.
// Platform libm rounding is not claimed bit-identical to Apple's libsystem_m.
LowpassCoefficients lowpassCoefficients(float cutoffHz, float resonanceDb, double sampleRate);

// Recovered kernel section 0x234f8975c, with its block-boundary state cleanup.
// Own one per channel. Coefficients may be replaced between calls without clearing
// history, as in the AU generation-counter update. No allocations or locks.
class BiquadSection {
 public:
  explicit BiquadSection(BiquadCoefficients coefficients) noexcept : coefficients_(coefficients) {}
  void coefficients(BiquadCoefficients c) noexcept { coefficients_ = c; }
  void reset() noexcept { x1_ = x2_ = y1_ = y2_ = 0; }
  // Finite PCM and coefficients are caller prerequisites. Input/output may alias
  // exactly, not partially. Null buffers only valid for zero frames.
  bool process(const float* input, float* output, std::size_t frames) noexcept;
 private:
  BiquadCoefficients coefficients_;
  double x1_ = 0, x2_ = 0, y1_ = 0, y2_ = 0;
};
using LowpassSection = BiquadSection;
} // namespace lmg::automix
