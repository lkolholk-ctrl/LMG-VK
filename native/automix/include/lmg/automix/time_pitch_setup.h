#pragma once
#include <cstdint>
#include <vector>
namespace lmg::automix {
// Recovered NewTimePitch initialization, not a complete PCM stretcher.
struct TimePitchGeometry {
  std::uint32_t fftSize;
  std::uint32_t halfFftSize;
  std::uint32_t log2FftSize;
  std::uint32_t inputRingSize;
  std::uint32_t outputRingSize;
  float inverseFftSize;
};
// Quality is the original AU's integer quality property (default 128).
TimePitchGeometry timePitchGeometry(double sampleRate, std::uint32_t maximumFrames,
                                   std::uint32_t quality = 128);
// Exact recovered window arithmetic; calculated outside the audio callback.
std::vector<float> timePitchAnalysisWindow(std::uint32_t fftSize);
// Original normal-mode latency; scheduled/offline mode reports zero.
double timePitchLatencySeconds(const TimePitchGeometry&, double sampleRate,
                               float playbackRate, bool scheduledOrOffline);
}
