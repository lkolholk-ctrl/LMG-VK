#include "lmg/automix/time_pitch_fft_real.h"
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
std::vector<float> timePitchRealFftTwiddles(std::uint32_t n) {
  if (n < 256 || n > 8192 || (n & (n-1)))
    throw std::invalid_argument("unsupported real FFT length");
  std::vector<float> result(n/2);
  // 234ac3068: divide the float 2*pi first, then multiply float indices.
  const float step = 0x1.921fb6p+2f / static_cast<float>(n);
  for (std::uint32_t i=0; i<n/4; ++i) {
    const float angle = static_cast<float>(i+1)*step;
    result[i] = std::cos(angle);
    result[n/4+i] = static_cast<float>(std::sin(static_cast<double>(angle)));
  }
  return result;
}
namespace {
// 234aca520 / 234ad0cd8: explicit four-lane loads and store order retain
// the overlapping centre group's last write. SIMD operations do not use FMA.
void realKernel(const float* real, const float* imaginary, float* outReal,
                float* outImaginary, std::uint32_t n, const float* table,
                bool inverse) noexcept {
  const float r = inverse ? real[0] : real[0]+real[0];
  const float im = inverse ? imaginary[0] : imaginary[0]+imaginary[0];
  outReal[0] = r+im;
  outImaginary[0] = r-im;
  for (std::uint32_t base=1; base<=n/4; base+=4) {
    float loReal[4],loImag[4],hiReal[4],hiImag[4];
    for (std::uint32_t lane=0; lane<4; ++lane) {
      const auto k=base+lane, j=n/2-k;
      const float a=real[k], b=imaginary[k], c=real[j], d=imaginary[j];
      const float sumImag=b+d, difference=inverse ? a-c : c-a;
      const float sumReal=a+c, differenceImag=b-d;
      const float cosImag=table[k-1]*sumImag;
      const float cosDifference=table[k-1]*difference;
      const float sinDifference=table[n/4+k-1]*difference;
      const float sinImag=table[n/4+k-1]*sumImag;
      const float crossReal=cosImag+sinDifference;
      const float crossImag=cosDifference-sinImag;
      loReal[lane]=inverse ? sumReal-crossReal : sumReal+crossReal;
      loImag[lane]=crossImag+differenceImag;
      hiReal[lane]=inverse ? sumReal+crossReal : sumReal-crossReal;
      hiImag[lane]=crossImag-differenceImag;
    }
    for (std::uint32_t lane=0; lane<4; ++lane) outReal[base+lane]=loReal[lane];
    for (std::uint32_t lane=0; lane<4; ++lane) outImaginary[base+lane]=loImag[lane];
    for (std::uint32_t lane=0; lane<4; ++lane) outReal[n/2-base-lane]=hiReal[lane];
    for (std::uint32_t lane=0; lane<4; ++lane) outImaginary[n/2-base-lane]=hiImag[lane];
  }
}
}
void timePitchRealFftPost(float* real,float* imaginary,std::uint32_t n,const float* table) noexcept {
  realKernel(real,imaginary,real,imaginary,n,table,false);
}
void timePitchRealFftPre(const float* real,const float* imaginary,float* outReal,float* outImaginary,std::uint32_t n,const float* table) noexcept {
  realKernel(real,imaginary,outReal,outImaginary,n,table,true);
}
} // namespace lmg::automix
