#pragma once
#include "lmg/automix/lowpass.h"

namespace lmg::automix {
using HighpassCoefficients = BiquadCoefficients;
// AUHipassFactory -> kernel update 0x234fc56cc -> coefficient dispatcher kind 2.
// Input prerequisites and platform-libm rounding limits match lowpassCoefficients.
HighpassCoefficients highpassCoefficients(float cutoffHz, float resonanceDb, double sampleRate);
// HP and LP kernels both call 0x234f8975c. No substitute processing algorithm.
using HighpassSection = BiquadSection;
} // namespace lmg::automix
