#pragma once
#include "lmg/automix/time_pitch_fft_complex.h"
#include <cstdint>
#include <vector>
namespace lmg::automix {
// Prepared original-radix real transform for the TimePitch processor. Raw vDSP
// normalization: forward gives 2*DFT; inverse(forward(x)) gives 2*N*x. Real/imag
// each contain N/2 floats; real[0] is DC and imag[0] is Nyquist. No allocation in
// forward/inverse. Pointers must be valid for their documented lengths.
class TimePitchRealFft {
 public:
  explicit TimePitchRealFft(std::uint32_t n);
  std::uint32_t fftSize() const noexcept { return n_; }
  void forward(const float* time, float* real, float* imag) noexcept;
  void inverse(const float* real, const float* imag, float* time) noexcept;
 private:
  std::uint32_t n_;
  TimePitchComplexFft complex_;
  std::vector<float> twiddles_, realWork_, imagWork_;
};
} // namespace lmg::automix
