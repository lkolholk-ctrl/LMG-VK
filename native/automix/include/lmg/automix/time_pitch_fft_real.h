#pragma once
#include <cstdint>
#include <vector>
namespace lmg::automix {
// Original real-FFT table and stride-one SIMD pre/post kernels. N is the
// real transform length, a power of two in [256,8192]. Split arrays hold N/2
// floats; imaginary[0] packs Nyquist. Calls allocate no memory.
std::vector<float> timePitchRealFftTwiddles(std::uint32_t n);
void timePitchRealFftPost(float* real, float* imaginary,
                          std::uint32_t n, const float* table) noexcept;
// Input may alias its corresponding output; otherwise all arrays are disjoint.
void timePitchRealFftPre(const float* real, const float* imaginary,
                         float* outputReal, float* outputImaginary,
                         std::uint32_t n, const float* table) noexcept;
} // namespace lmg::automix
