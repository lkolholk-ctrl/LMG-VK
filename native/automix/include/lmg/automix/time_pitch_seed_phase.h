#pragma once
#include <cstdint>
namespace lmg::automix {
// Original aligned vDSP_zvphas main SIMD path. bins is a multiple of 32;
// arrays are disjoint and have bins floats. Output is radians, not cycles.
void timePitchSeedPhaseRadians(const float* real,const float* imaginary,
                               float* radians,std::uint32_t bins) noexcept;
}
