#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace lmg::automix::offline {
// These are OFFLINE bench limits/policies, not Apple scheduling constants.
struct Format {
  std::uint32_t sampleRate = 48000;
  unsigned channels = 2;
  void validate() const;
};
struct Metrics {
  std::size_t frames = 0, peakFrame = 0, samplesAboveUnity = 0;
  double peak = 0, rms = 0, mean = 0;
};
struct Difference { double maxAbsolute = 0, rms = 0; std::size_t atSample = 0; };
std::size_t checkedSamples(std::size_t frames, unsigned channels);
std::size_t roundedFrames(double seconds, std::uint32_t sampleRate);
Metrics measure(const std::vector<float>& pcm, Format format,
                std::size_t firstFrame = 0, std::size_t endFrame = static_cast<std::size_t>(-1));
Difference compare(const std::vector<float>& a, const std::vector<float>& b);
// Pure deterministic test signals, not music recordings or vendor fixtures.
std::vector<float> signal(Format format, std::size_t frames, std::uint32_t seed = 1);
std::vector<float> impulse(Format format, std::size_t frames, std::size_t frame, float amplitude = 0.125f);
// No clipping, normalization, resampling or dithering. Create-only little-endian
// IEEE float WAV; invalid input must fail before a file is published.
void writeFloatWav(const std::string& path, const std::vector<float>& pcm, Format format);
std::vector<float> readFloatWav(const std::string& path, Format expected);
void writeTextExclusive(const std::string& path, const std::string& text);
std::string jsonNumber(double value);
} // namespace lmg::automix::offline
