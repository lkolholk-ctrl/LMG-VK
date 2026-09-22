#pragma once
#include <cstdint>
namespace lmg::automix {
struct TimePitchPackedEdges { float dc, nyquist; };
// Default four-lane synthesis path. buffers have bins=N/2 elements. The second
// channel of a coherent pair passes updateRotation=false and reuses rotations.
TimePitchPackedEdges timePitchSynthesize(float* real, float* imaginary,
    const float* analysisCycles, const float* correctionCycles, float* synthesisCycles,
    float* rotationReal, float* rotationImaginary, std::uint32_t bins,
    double outputHop, float inverseFftSize, bool updateRotation) noexcept;
// Called when pitch changes, before any coherence-specific remapping.
std::uint32_t timePitchPrepareBinMapping(std::uint32_t* mapping,std::uint32_t bins,
                                        float pitchRatio) noexcept;
// Four-lane scatter semantics, including colliding destinations, are preserved.
void timePitchRemapSpectrum(const float* real,const float* imaginary,float* outputReal,
    float* outputImaginary,const std::uint32_t* mapping,std::uint32_t bins,
    TimePitchPackedEdges edges) noexcept;
// Validated power-of-two ring, ringSize >= fftSize; frame/window do not alias
// the ring. The original multiply-add's rounding depends on segment alignment.
void timePitchOverlapAdd(float* ring,std::uint32_t ringSize,std::uint64_t cursor,
                          const float* frame,const float* window,std::uint32_t fftSize) noexcept;
// 234f46510..65a4: equal-hop path. Input and previousFrame have N samples;
// previousFrame is separate storage retained for phase reseeding on resume.
// Smoothness is the validated source parameter [3,32]. No FFT or allocation.
void timePitchIdentityWindow(const float* input, float* previousFrame,
    const float* window, std::uint32_t fftSize, float smoothness,
    float* outputRing, std::uint32_t ringSize, std::uint64_t cursor) noexcept;
}
