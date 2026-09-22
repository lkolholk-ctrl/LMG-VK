#include "lmg/automix/time_pitch_fft.h"
#include "lmg/automix/time_pitch_fft_real.h"
#include <stdexcept>
namespace lmg::automix {
namespace {
std::uint32_t validSize(std::uint32_t n) {
  if(n<256 || n>8192 || (n & (n-1)))
    throw std::invalid_argument("TimePitch real FFT length must be 256..8192 power of two");
  return n;
}
}
TimePitchRealFft::TimePitchRealFft(std::uint32_t n)
    : n_(validSize(n)), complex_(n_/2), twiddles_(timePitchRealFftTwiddles(n_)),
      realWork_(n_/2), imagWork_(n_/2) {}
void TimePitchRealFft::forward(const float* time,float* real,float* imag) noexcept {
  // Source ctoz with input stride 2: deinterleave adjacent real-time samples.
  for(std::uint32_t j=0;j<n_/2;++j) {real[j]=time[2*j];imag[j]=time[2*j+1];}
  complex_.transform(real,imag,false);
  timePitchRealFftPost(real,imag,n_,twiddles_.data());
}
void TimePitchRealFft::inverse(const float* real,const float* imag,float* time) noexcept {
  // Source real prepass supports disjoint output, keeping caller spectra const.
  timePitchRealFftPre(real,imag,realWork_.data(),imagWork_.data(),n_,twiddles_.data());
  complex_.transform(realWork_.data(),imagWork_.data(),true);
  for(std::uint32_t j=0;j<n_/2;++j) {time[2*j]=realWork_[j];time[2*j+1]=imagWork_[j];}
}
} // namespace lmg::automix
