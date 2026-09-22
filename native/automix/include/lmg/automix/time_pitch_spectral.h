#pragma once
#include "lmg/automix/time_pitch_fft.h"
#include "lmg/automix/time_pitch_transients.h"
#include <array>
#include <vector>
namespace lmg::automix {
// Prepared spectral branch of 234f46400. Each call consumes one already chosen
// N-sample frame per channel and adds its N-sample synthesis to caller rings.
// This is not an input-pull/output-drain adapter. Clock and ring ownership stay
// with the streaming processor. Stereo channels share the leading rotation.
class TimePitchSpectral {
 public:
  TimePitchSpectral(std::uint32_t fftSize, std::uint32_t channels);
  void reset() noexcept;
  const float* analysisWindow() const noexcept {return window_.data();}
  // Call outside rendering, with pitch in [1/32,32].
  void setPitch(float pitch);
  void processIdentity(const float* const* frames, float* const* outputRings,
      std::uint32_t ringSize, std::uint64_t outputCursor, float smoothness) noexcept;
  // Source +0x9a2 controls reseeding; the streaming owner calls this only when
  // that flag requires it. It is not automatically done after every identity hop.
  void seedFromPreviousIdentity() noexcept;
  // Validated disjoint planes and power-of-two output rings >= N. The caller
  // supplies source clock fields and retains the resulting transient state.
  // The owner performs source-required reseeding before entering this branch.
  void process(const float* const* frames, float* const* outputRings,
               std::uint32_t ringSize, std::uint64_t outputCursor,
               double rate, double outputHop, bool coherence,
               bool preserveTransients, TimePitchTransientState&) noexcept;
 private:
  std::uint32_t n_, bins_, channels_;
  float pitch_ = 1;
  TimePitchRealFft fft_;
  std::vector<float> window_, frame_, real_, imag_, remapReal_, remapImag_;
  std::vector<float> magnitude_, correction_, scratch_, rotationReal_, rotationImag_;
  std::array<std::vector<float>,2> analysis_, synthesis_, previousIdentity_;
  std::vector<std::uint32_t> mapping_, peaks_, starts_, ends_;
};
} // namespace lmg::automix
