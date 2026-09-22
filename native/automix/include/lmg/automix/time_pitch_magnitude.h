#pragma once
#include <cstddef>
namespace lmg::automix {
// Source vDSP complex magnitude; reproduces ARM estimate/refinement arithmetic.
// Input and output arrays contain bins floats; no allocation or scratch needed.
void timePitchMagnitudes(const float* real,const float* imaginary,float* output,
                         std::size_t bins) noexcept;
}
