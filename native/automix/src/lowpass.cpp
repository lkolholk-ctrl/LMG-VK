#include "lmg/automix/lowpass.h"
#include "lmg/automix/highpass.h"
#include <cmath>
#include <limits>
#include <cstdint>
#include <stdexcept>

namespace lmg::automix {
namespace {
BiquadCoefficients passCoefficients(float cutoffHz, float resonanceDb, double sampleRate, bool high) {
  if (!std::isfinite(cutoffHz) || !std::isfinite(resonanceDb) || !std::isfinite(sampleRate) ||
      sampleRate <= 0 || sampleRate > std::numeric_limits<float>::max())
    throw std::invalid_argument("Invalid pass filter input");
  const float rate = static_cast<float>(sampleRate);
  if (rate <= 0) throw std::invalid_argument("Pass filter sample rate underflows float");

  // LP 0x234f6845c..0x234f68490; HP 0x234fc56f4..0x234fc5728.
  const double frequency = cutoffHz <= 10.0f ? 10.0f : cutoffHz;
  double normalized = (frequency + frequency) / static_cast<double>(rate);
  if (normalized > 0.99) normalized = 0.99;
  const double angle = normalized * 0x1.921fb54442d18p+1; // __TEXT 0x234ffcb68
  // 0x234f6849c..0x234f684b4; branch island resolves to libsystem_m ___exp10.
  const double q = std::pow(10.0, static_cast<double>(resonanceDb) * 0.05);
  if (!std::isfinite(q) || q <= 0) throw std::invalid_argument("Pass filter resonance overflow");

  // Dispatcher kind=1, gain=0: 0x234fee264..0x234fee298, 0x234fee408 and
  // normalization/stores 0x234fee4f4..0x234fee51c. Keep division then multiplication.
  const double cosine = std::cos(angle);
  const double alpha = std::sin(angle) / (q + q);
  // HP kind=2: 0x234fee3d4..0x234fee414. Same denominator, distinct numerator.
  const double numerator = (high ? cosine + 1.0 : 1.0 - cosine) * 0.5;
  const double middle = high ? numerator * -2.0 : numerator + numerator;
  const double reciprocal = 1.0 / (alpha + 1.0);
  BiquadCoefficients c{(cosine * -2.0) * reciprocal, reciprocal * (1.0 - alpha),
                        reciprocal * numerator, reciprocal * middle, reciprocal * numerator};
  for (double value : c)
    if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite pass filter coefficients");
  return c;
}
} // namespace

LowpassCoefficients lowpassCoefficients(float cutoffHz, float resonanceDb, double sampleRate) {
  return passCoefficients(cutoffHz, resonanceDb, sampleRate, false);
}
HighpassCoefficients highpassCoefficients(float cutoffHz, float resonanceDb, double sampleRate) {
  return passCoefficients(cutoffHz, resonanceDb, sampleRate, true);
}

bool BiquadSection::process(const float* input, float* output, std::size_t frames) noexcept {
  if (frames > static_cast<std::size_t>(std::numeric_limits<std::int32_t>::max()) ||
      (frames && (!input || !output))) return false;
  // The machine routine primes four samples, loops in groups of five, then emits
  // four primed samples. The remaining 0..4 samples take its scalar tail path.
  const auto large = frames < 4 ? 0 : frames - (frames - 4) % 5;
  const auto& c = coefficients_;
  for (std::size_t i = 0; i < frames; ++i) {
    const double x = input[i];
    double y;
    if (i < large) {
      const double product = c[2] * x;
      y = std::fma(c[3], x1_, product);
    } else {
      const double product = c[3] * x1_;
      y = std::fma(c[2], x, product);
    }
    y = std::fma(c[4], x2_, y);
    y = std::fma(-c[0], y1_, y);
    y = std::fma(-c[1], y2_, y);
    output[i] = static_cast<float>(y);
    x2_ = x1_; x1_ = x; y2_ = y1_; y1_ = y;
  }
  // Strict open interval from 0x234f89938..0x234f89990. This happens even for an
  // empty block. NaN/infinity also fall outside it. Output is not clipped.
  auto clean = [](double x) noexcept {
    const double magnitude = std::abs(x);
    return magnitude < 1e15 && magnitude > 1e-15 ? x : 0.;
  };
  x1_ = clean(x1_); x2_ = clean(x2_); y1_ = clean(y1_); y2_ = clean(y2_);
  return true;
}
} // namespace lmg::automix
