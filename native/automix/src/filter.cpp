#include "lmg/automix/filter.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {
constexpr double pi = 0x1.921fb54442d18p+1;
constexpr double maxAngle = .99 * pi;
constexpr BiquadCoefficients identity{0, 0, 1, 0, 0};

BiquadCoefficients normalize(double a0, double a1, double a2,
                             double b0, double b1, double b2) {
  const double reciprocal = 1.0 / a0;
  BiquadCoefficients result{a1 * reciprocal, reciprocal * a2,
                            reciprocal * b0, reciprocal * b1, reciprocal * b2};
  for (double value : result)
    if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite AUFilter coefficient");
  return result;
}

BiquadCoefficients edge(float frequency, float gain, double halfRate,
                        bool pass, bool high) {
  // Unlike standalone LP/HP, AUFilter uses the double sample rate and does not
  // impose the 10 Hz floor. Nonpositive angle returns an identity section.
  double angle = (double(frequency) / halfRate) * pi;
  if (angle <= 0) return identity;
  angle = std::min(angle, maxAngle);
  const double cosine = std::cos(angle);
  if (pass) {
    const double q = std::pow(10.0, double(gain) * .05);
    const double alpha = std::sin(angle) / (q + q);
    const double numerator = (high ? 1.0 - cosine : cosine + 1.0) * .5;
    const double middle = high ? numerator + numerator : numerator * -2.0;
    return normalize(alpha + 1.0, cosine * -2.0, 1.0 - alpha,
                     numerator, middle, numerator);
  }
  // 0x234f896c4/89710 -> dispatcher kinds 7/8. The precise stored Q constant
  // is 0.7071067811865475, not recomputed sqrt(.5).
  const double alpha = std::sin(angle) / (0.7071067811865475 + 0.7071067811865475);
  const double amplitude = std::pow(10.0, (double(gain) * .5) * .05);
  const double root = std::sqrt(amplitude);
  const double beta = alpha * (root + root);
  const double minus = amplitude + -1.0, plus = amplitude + 1.0;
  const double cm = cosine * minus, cp = cosine * plus;
  if (!high) {
    const double denominator = plus + cm, numerator = plus - cm;
    return normalize(denominator + beta, (minus + cp) * -2.0, denominator - beta,
                     amplitude * (numerator + beta), (amplitude + amplitude) * (minus - cp),
                     amplitude * (numerator - beta));
  }
  const double denominator = plus - cm, numerator = plus + cm;
  const double a1 = minus - cp;
  return normalize(denominator + beta, a1 + a1, denominator - beta,
                   amplitude * (numerator + beta), (amplitude * -2.0) * (minus + cp),
                   amplitude * (numerator - beta));
}

BiquadCoefficients peak(FilterBand band, double halfRate) {
  // 0x234f395e8 clamps frequency in Hz before angle calculation, then clamps
  // angle again. Both checks matter near the Nyquist limit.
  const double frequency = std::min(double(band.frequencyHz), halfRate * .99);
  double angle = (frequency / halfRate) * pi;
  if (angle <= 0 || band.bandwidthOctaves <= 0) return identity;
  angle = std::min(angle, maxAngle);
  const double bandwidth = double(band.bandwidthOctaves) * .34657359027997264;
  const double hyperbolic = std::sinh((bandwidth * angle) / std::sin(angle));
  const double q = 1.0 / (hyperbolic + hyperbolic);
  // Keep the reciprocal-Q round trip used by dispatcher kind 11.
  const double cosine = std::cos(angle);
  const double amplitude = std::pow(10.0, (double(band.gainDb) * .5) * .05);
  const double alpha = std::sin(angle) / (q + q);
  const double feed = alpha * amplitude, back = alpha / amplitude;
  const double middle = cosine * -2.0;
  return normalize(back + 1.0, middle, 1.0 - back, feed + 1.0, middle, 1.0 - feed);
}
}  // namespace

FilterCoefficients filterCoefficients(FilterParameters p, double sampleRate) {
  // Host validation, not AU parameter clamps. Invalid preparation never changes
  // a running kernel. The graph uses ordinary finite audio parameter ranges.
  if (!std::isfinite(sampleRate) || sampleRate < 8000 || sampleRate > 192000 ||
      !std::isfinite(p.lowFrequencyHz) || !std::isfinite(p.lowGainDb) ||
      !std::isfinite(p.highFrequencyHz) || !std::isfinite(p.highGainDb) ||
      std::abs(p.lowGainDb) > 100 || std::abs(p.highGainDb) > 100)
    throw std::invalid_argument("Invalid AUFilter parameters");
  for (const auto& band : p.bands)
    if (!std::isfinite(band.frequencyHz) || !std::isfinite(band.gainDb) ||
        !std::isfinite(band.bandwidthOctaves) || std::abs(band.gainDb) > 100)
      throw std::invalid_argument("Invalid AUFilter band");
  const double halfRate = sampleRate * .5;
  return {edge(p.lowFrequencyHz, p.lowGainDb, halfRate, p.highpass, false),
          edge(p.highFrequencyHz, p.highGainDb, halfRate, p.lowpass, true),
          peak(p.bands[0], halfRate), peak(p.bands[1], halfRate), peak(p.bands[2], halfRate)};
}

FilterKernel::FilterKernel(const FilterCoefficients& c) noexcept
    : sections_{BiquadSection(c[0]), BiquadSection(c[1]), BiquadSection(c[2]),
                BiquadSection(c[3]), BiquadSection(c[4])} {}

void FilterKernel::coefficients(const FilterCoefficients& c) noexcept {
  for (unsigned i = 0; i < sections_.size(); ++i) sections_[i].coefficients(c[i]);
}

bool FilterKernel::process(const float* input, float* output, std::size_t frames) noexcept {
  if (frames > static_cast<std::size_t>(std::numeric_limits<std::int32_t>::max()) ||
      (frames && (!input || !output))) return false;
  // 0x234f39304: low edge, three peaks, high edge. Each stage rounds to float.
  sections_[0].process(input, output, frames);
  for (unsigned i : {2u, 3u, 4u, 1u}) sections_[i].process(output, output, frames);
  return true;
}
}  // namespace lmg::automix
