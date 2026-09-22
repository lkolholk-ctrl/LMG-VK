#include "lmg/automix/time_pitch_setup.h"
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
namespace {
void sampleRateDomain(double rate) {
  if (!std::isfinite(rate) || rate < 8000 || rate > 192000)
    throw std::invalid_argument("time-pitch sample rate outside supported host range");
}
std::uint32_t ceilingPowerOfTwo(std::uint32_t n) {
  --n; n |= n >> 1; n |= n >> 2; n |= n >> 4; n |= n >> 8; n |= n >> 16;
  return n + 1;
}
}
TimePitchGeometry timePitchGeometry(double sampleRate, std::uint32_t maximumFrames,
                                   std::uint32_t quality) {
  sampleRateDomain(sampleRate);
  if (maximumFrames == 0 || maximumFrames > 16384)
    throw std::invalid_argument("time-pitch maximum frames outside supported host range");
  // AU Initialize 0x234f9e5e0..0x234f9e644: strict threshold comparisons.
  std::uint32_t n = sampleRate < 8192 ? 512 : sampleRate < 16384 ? 1024 :
                    sampleRate < 32768 ? 2048 : sampleRate < 65536 ? 4096 : 8192;
  if (quality < 33) n >>= 1;
  // DSP constructor 0x234f43d48..0x234f43e2c.
  std::uint32_t logarithm = 0;
  for (auto size = n; size > 1; size >>= 1) ++logarithm;
  return {n, n / 2, logarithm, ceilingPowerOfTwo(2 * n + maximumFrames),
          ceilingPowerOfTwo(n + maximumFrames), 1.0f / static_cast<float>(n)};
}
std::vector<float> timePitchAnalysisWindow(std::uint32_t fftSize) {
  if (fftSize < 256 || fftSize > 8192 || (fftSize & (fftSize - 1)))
    throw std::invalid_argument("unsupported time-pitch FFT size");
  // The binary constant is 0x401921fb60000000, not full-precision 2*pi.
  const double step = 6.283185482025146484375 / static_cast<double>(fftSize);
  std::vector<float> result(fftSize);
  for (std::uint32_t i = 0; i < fftSize; ++i) {
    const double angle = step * static_cast<double>(i);
    result[i] = static_cast<float>(std::fma(-0.5, std::cos(angle), 0.5));
  }
  return result;
}
double timePitchLatencySeconds(const TimePitchGeometry& geometry, double sampleRate,
                               float playbackRate, bool scheduledOrOffline) {
  sampleRateDomain(sampleRate);
  if (!std::isfinite(playbackRate) || playbackRate < 0.03125f || playbackRate > 32.0f)
    throw std::invalid_argument("time-pitch rate outside recovered range");
  if (scheduledOrOffline) return 0;
  // AU latency getter 0x234f9d54c..0x234f9d56c. Preserve operation order.
  const double rate = static_cast<double>(playbackRate);
  const double factor = (1.0 / rate) + 1.0;
  const double samples = factor * static_cast<double>(geometry.halfFftSize);
  return samples / sampleRate;
}
}
