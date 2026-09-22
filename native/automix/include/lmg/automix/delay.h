#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace lmg::automix {
struct DelayParameters {
  float wetPercent = 0, seconds = 1, feedbackPercent = 50, lowpassHz = 2500;
};
// AUDelayFactory 0x234fdf30c -> allocator 0x234fde9b8 -> process 0x234fdeabc.
// One prepared instance per channel; no allocation during process. Parameters
// are sampled once per call, as in the recovered kernel. Not a fractional delay.
class DelayKernel {
 public:
  // Host PCM support bound, not an inferred AudioUnit parameter limit.
  explicit DelayKernel(double sampleRate); // 8..192 kHz
  // AU reset clears the ring/accumulator, preserving the circular write index.
  void reset() noexcept;
  // Positive blocks require valid finite input/output PCM; exact in-place is OK.
  // Invalid settings/buffers leave state/output unchanged. silence, when supplied,
  // is only cleared when the AU's measured output peak exceeds 1e-6.
  bool process(const float* input, float* output, std::size_t frames,
               DelayParameters parameters, bool* silence = nullptr) noexcept;
 private:
  double rate_;
  std::vector<float> ring_;
  std::uint32_t write_;
  float accumulator_ = 0;
};
} // namespace lmg::automix
