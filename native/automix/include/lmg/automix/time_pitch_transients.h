#pragma once
#include <cstdint>
namespace lmg::automix {
struct TimePitchRegions {
  std::uint32_t* peaks;
  std::uint32_t* starts;
  std::uint32_t* ends;
  std::uint32_t count = 0;
  std::uint32_t maximumBin = 0;
};
// All arrays have bins elements, prepared outside the callback. bins >= 5.
// Magnitudes are nonnegative spectral magnitudes from the recovered FFT path.
// Produces inclusive regions, updates pitch mapping and locks phase correction.
void timePitchApplyCoherence(const float* magnitudes, float* correctionCycles,
                             std::uint32_t* binMapping, std::uint32_t bins,
                             float pitchRatio, TimePitchRegions&) noexcept;
struct TimePitchTransientState {
  float position = 0;
  bool active = false;
  std::int32_t frameDebt = 0;
  double effectiveInputHop = 0;
  double previousInputHop = 0;
};
// Exact transient-preservation state machine following coherence. Does not
// allocate; phaseDifferenceScratch has bins elements and is reused across hops.
// Inputs/ranges come from the validated processor and its recovered hop clock.
void timePitchPreserveTransients(const float* magnitudes, const float* analysisCycles,
                                float* correctionCycles, float* phaseDifferenceScratch,
                                std::uint32_t bins, const TimePitchRegions&,
                                double rate, double outputHop,
                                TimePitchTransientState&) noexcept;
}
