#pragma once
#include <cstddef>
namespace lmg::automix {
// Exact four-lane phase-analysis path selected by DSP state +0x9a6 == 1.
// Arrays are split-complex FFT bins (N/2); previousAnalysisCycles is updated.
// The function does not perform FFT, coherence, transient handling or synthesis.
// Prepare and validate sizes outside the callback; buffers must contain bins
// elements and must not overlap, except previousAnalysisCycles' own update.
void timePitchAnalyzePhase(const float* real, const float* imaginary,
                           float* previousAnalysisCycles, const float* synthesisCycles,
                           float* correctionCycles, std::size_t bins,
                           float inverseFftSize, double inputHop, double outputHop,
                           float pitchRatio) noexcept;
struct TimePitchHopState {
  double inputTime = 0;
  double outputTime = 0;
  double previousRoundedInputHop = 0;
  double effectiveInputHop = 0;
};
struct TimePitchHop {
  double inputFrames;
  double outputFrames;
  double nextInputTime;
  double nextOutputTime;
};
// Source arithmetic for validated FFT size, smoothness [3,32], rate [1/32,32].
// Calling these functions advances the time clock once, after one DSP hop.
TimePitchHop timePitchAdvanceHop(TimePitchHopState&, unsigned fftSize,
                                float smoothness, double rate) noexcept;
// mappedNextInputTime is evaluated at current outputTime + the rounded output
// hop by the separate source-time mapper; the DSP does not choose that mapper.
TimePitchHop timePitchAdvanceMappedHop(TimePitchHopState&, unsigned fftSize,
                                      float smoothness, double mappedNextInputTime) noexcept;
}
