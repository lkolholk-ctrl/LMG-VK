#pragma once
#include "lmg/automix/lowpass.h"

namespace lmg::automix {

struct FilterBand {
  float frequencyHz;
  float gainDb;
  float bandwidthOctaves;
};

// Standalone AUFilter defaults (constructor 0x234f3a4e0). DSPGraph overrides
// the first band's frequency/gain/bandwidth through parameter IDs 3/4/5.
struct FilterParameters {
  bool highpass = false;  // false selects a low shelf, not bypass
  float lowFrequencyHz = 100, lowGainDb = 0;
  std::array<FilterBand, 3> bands{{{625, 0, 2}, {2500, 0, 2}, {5000, 0, 2}}};
  bool lowpass = false;  // false selects a high shelf, not bypass
  float highFrequencyHz = 10000, highGainDb = 0;
};

// Storage order: low edge, high edge, three peak bands. Processing order differs.
using FilterCoefficients = std::array<BiquadCoefficients, 5>;
FilterCoefficients filterCoefficients(FilterParameters parameters, double sampleRate);

// One per channel. Reuses the recovered biquad's double histories, float stage
// outputs and block-end cleanup; no coefficient-update history reset.
class FilterKernel {
 public:
  explicit FilterKernel(const FilterCoefficients& c) noexcept;
  void coefficients(const FilterCoefficients& c) noexcept;
  void reset() noexcept { for (auto& section : sections_) section.reset(); }
  bool process(const float* input, float* output, std::size_t frames) noexcept;
 private:
  std::array<BiquadSection, 5> sections_;
};

}  // namespace lmg::automix
