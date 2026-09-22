#include "lmg/automix/time_pitch_fft_complex.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <stdexcept>

namespace lmg::automix {
namespace {
std::uint64_t reversed(std::uint64_t x) noexcept {
  std::uint64_t result = 0;
  for (unsigned i = 0; i < 64; ++i) { result = (result << 1) | (x & 1); x >>= 1; }
  return result;
}
// 234b13060 / 234ac6a30 and the arithmetic after twiddle multiplies in
// 234b16f20 / 234b17830. Output quarter order is the source's binary order.
void butterfly4(const float* r, const float* i, float* ro, float* io) noexcept {
  const float ac = r[0] + r[2], ad = r[0] - r[2];
  const float bd = r[1] + r[3], bc = r[1] - r[3];
  const float ai = i[0] + i[2], aj = i[0] - i[2];
  const float bi = i[1] + i[3], bj = i[1] - i[3];
  ro[0] = ac + bd; ro[1] = ac - bd; ro[2] = ad - bj; ro[3] = ad + bj;
  io[0] = ai + bi; io[1] = ai - bi; io[2] = aj + bc; io[3] = aj - bc;
}
void twiddle(float& r, float& i, float c, float s) noexcept {
  const float rc = r * c, is = i * s, rs = r * s, ic = i * c;
  r = rc - is; i = rs + ic;
}
void stage4(float* real, float* imag, std::size_t n, const float* weights) noexcept {
  const std::size_t quarter = n / 4;
  for (std::size_t j = 0; j < quarter; ++j) {
    float r[4], i[4], ro[4], io[4];
    for (unsigned k = 0; k < 4; ++k) { r[k] = real[k*quarter+j]; i[k] = imag[k*quarter+j]; }
    if (weights) for (unsigned k = 1; k < 4; ++k) twiddle(r[k], i[k], weights[2*k-2], weights[2*k-1]);
    butterfly4(r, i, ro, io);
    for (unsigned k = 0; k < 4; ++k) { real[k*quarter+j] = ro[k]; imag[k*quarter+j] = io[k]; }
  }
}
// Scalar lanes of original 234aceb68..234acecc8; no reassociation.
void butterfly8(const float* r, const float* i, float* ro, float* io) noexcept {
  float v[32]{}; v[14] = 0x1.6a09e6p-1f;
  v[8] = r[0];
  v[30] = r[2];
  v[12] = r[4];
  v[10] = r[6];
  v[6] = r[1];
  v[16] = r[3];
  v[0] = r[5];
  v[22] = r[7];
  v[18] = v[8] + v[12];
  v[2] = v[8] - v[12];
  v[24] = v[30] + v[10];
  v[4] = v[30] - v[10];
  v[20] = v[6] + v[0];
  v[12] = v[16] + v[22];
  v[28] = v[18] + v[24];
  v[10] = v[20] + v[12];
  v[26] = i[0];
  v[18] = v[18] - v[24];
  v[24] = i[2];
  v[8] = v[28] + v[10];
  v[30] = i[4];
  v[10] = v[28] - v[10];
  v[28] = i[6];
  v[20] = v[20] - v[12];
  ro[0] = v[8];
  v[6] = v[6] - v[0];
  v[16] = v[16] - v[22];
  ro[1] = v[10];
  v[0] = v[26] + v[30];
  v[22] = i[1];
  v[26] = v[26] - v[30];
  v[8] = i[3];
  v[30] = v[24] + v[28];
  v[10] = i[5];
  v[24] = v[24] - v[28];
  v[12] = i[7];
  v[28] = v[22] + v[10];
  v[22] = v[22] - v[10];
  v[10] = v[8] + v[12];
  v[8] = v[8] - v[12];
  v[12] = v[0] + v[30];
  v[0] = v[0] - v[30];
  v[30] = v[28] + v[10];
  v[28] = v[28] - v[10];
  v[10] = v[12] + v[30];
  v[12] = v[12] - v[30];
  v[30] = v[18] - v[28];
  io[0] = v[10];
  v[28] = v[18] + v[28];
  v[18] = v[0] + v[20];
  io[1] = v[12];
  v[0] = v[0] - v[20];
  v[20] = v[2] - v[24];
  ro[2] = v[30];
  v[2] = v[2] + v[24];
  v[24] = v[26] + v[4];
  ro[3] = v[28];
  v[4] = v[26] - v[4];
  v[28] = v[6] - v[8];
  io[2] = v[18];
  v[26] = v[6] + v[8];
  v[6] = v[22] + v[16];
  io[3] = v[0];
  v[0] = v[22] - v[16];
  v[16] = v[28] - v[6];
  v[6] = v[28] + v[6];
  v[16] = v[16] * v[14];
  v[18] = v[6] * v[14];
  v[22] = v[20] + v[16];
  v[28] = v[20] - v[16];
  v[20] = v[24] + v[18];
  v[18] = v[24] - v[18];
  v[24] = v[26] + v[0];
  ro[4] = v[22];
  v[0] = v[26] - v[0];
  v[22] = v[24] * v[14];
  ro[5] = v[28];
  v[26] = v[0] * v[14];
  v[24] = v[2] - v[22];
  io[4] = v[20];
  v[28] = v[2] + v[22];
  v[20] = v[4] + v[26];
  io[5] = v[18];
  v[26] = v[4] - v[26];
  ro[6] = v[24];
  io[6] = v[20];
  ro[7] = v[28];
  io[7] = v[26];
}
} // namespace

TimePitchComplexFft::TimePitchComplexFft(std::size_t complexSize)
    : size_(complexSize), logSize_(0) {
  if (size_ < 128 || size_ > 4096 || (size_ & (size_-1)))
    throw std::invalid_argument("TimePitch complex FFT size must be 128..4096 power of two");
  for (auto n = size_; n > 1; n >>= 1) ++logSize_;
  stageTwiddles_.resize(6*(size_/16));
  finalTwiddles_.resize(24*(size_/16));
  workReal_.resize(size_); workImag_.resize(size_);
  constexpr double twoPi = 0x1.921fb54442d18p+2;
  for (std::size_t group = 0; group < size_/16; ++group) {
    // 234b225b4..5f8: rbit(group*4), UC VTF #64, 2*pi, [a,a+a,3*a].
    const double angle = std::ldexp(static_cast<double>(reversed(group*4)), -64) * twoPi;
    const double angles[3]{angle, angle+angle, angle*3.0};
    for (unsigned k = 0; k < 3; ++k) {
      stageTwiddles_[6*group+2*k] = static_cast<float>(std::cos(angles[k]));
      stageTwiddles_[6*group+2*k+1] = static_cast<float>(std::sin(angles[k]));
    }
  }
  const double step = twoPi / static_cast<double>(size_);
  for (std::size_t group = 0; group < size_/16; ++group) {
    for (unsigned lane = 0; lane < 4; ++lane) {
      const double angle = step * static_cast<double>(4*group+lane);
      const double angles[3]{angle, angle+angle, angle*3.0};
      for (unsigned k = 0; k < 3; ++k) {
        finalTwiddles_[24*group+8*k+lane] = static_cast<float>(std::cos(angles[k]));
        finalTwiddles_[24*group+8*k+4+lane] = static_cast<float>(std::sin(angles[k]));
      }
    }
  }
}

void TimePitchComplexFft::transform(float* real, float* imag, bool inverse) noexcept {
  // The original complex backend swaps the two planes for negative direction.
  float* r = inverse ? real : imag;
  float* i = inverse ? imag : real;
  std::size_t groups;
  if ((logSize_ & 1) == 0) {
    stage4(r, i, size_, nullptr); groups = 4;
  } else {
    const auto eighth = size_/8;
    for (std::size_t j = 0; j < eighth; ++j) {
      float ar[8], ai[8], ro[8], io[8];
      for (unsigned k = 0; k < 8; ++k) { ar[k] = r[k*eighth+j]; ai[k] = i[k*eighth+j]; }
      butterfly8(ar, ai, ro, io);
      for (unsigned k = 0; k < 8; ++k) { r[k*eighth+j] = ro[k]; i[k*eighth+j] = io[k]; }
    }
    groups = 8;
  }
  // Source computes every zero-twiddle leading group before nonzero groups.
  // These writes are disjoint from the corresponding nonzero group ranges.
  for (auto length = size_/groups; length >= 64; length /= 4)
    stage4(r, i, length, nullptr);
  for (auto length = size_/groups; length >= 64; length /= 4, groups *= 4)
    for (std::size_t group = 1; group < groups; ++group)
      stage4(r+group*length, i+group*length, length, stageTwiddles_.data()+6*group);
  // 234b17830 applies the same constant-twiddle radix to four adjacent lanes.
  for (std::size_t group = 0; group < size_/16; ++group)
    stage4(r+group*16, i+group*16, 16, stageTwiddles_.data()+6*group);

  // 234b1f7a0 combines the last radix with transpose and binary permutation.
  // Preallocated output planes avoid its in-place pair traversal while retaining
  // the exact per-lane multiply/add sequence and the source twiddle for each lane.
  constexpr unsigned order[4]{0,2,1,3};
  const std::size_t quarter = size_/4;
  const unsigned reverseBits = logSize_-4;
  for (std::size_t group = 0; group < size_/16; ++group) {
    const auto source = (reversed(group) >> (64-reverseBits))*4;
    for (unsigned lane = 0; lane < 4; ++lane) {
      float ar[4], ai[4], ro[4], io[4];
      for (unsigned k = 0; k < 4; ++k) {
        const auto index = order[lane]*quarter+source+k;
        ar[k] = r[index]; ai[k] = i[index];
      }
      for (unsigned k = 1; k < 4; ++k)
        twiddle(ar[k], ai[k], finalTwiddles_[24*group+8*(k-1)+lane],
                finalTwiddles_[24*group+8*(k-1)+4+lane]);
      butterfly4(ar, ai, ro, io);
      for (unsigned k = 0; k < 4; ++k) {
        const auto index = order[k]*quarter+group*4+lane;
        workReal_[index] = ro[k]; workImag_[index] = io[k];
      }
    }
  }
  std::copy(workReal_.begin(), workReal_.end(), r);
  std::copy(workImag_.begin(), workImag_.end(), i);
}
} // namespace lmg::automix
