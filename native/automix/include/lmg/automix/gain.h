#pragma once
#include <cstddef>
#include <cstdint>

namespace lmg::automix {

// Recovered contiguous float ramp multiplier at 0x234b33380. Destination
// alignment affects rounding exactly as in its SIMD/scalar paths. Updates start
// to the routine's final accumulator; GainSmoother advances its own state once
// per slice, independently. Exact in-place allowed, partial overlaps unsupported.
bool multiplyGainRamp(const float* input, float* output, std::size_t frames,
                      float& start, float increment) noexcept;

// Control result for a single DSPGraph process slice. The first rampFrames
// samples use the recovered ramp-multiply operation; the rest use constantGain.
// This describes control state, not an implementation of vDSP_vrampmul.
struct GainBlock {
  float start = 1, increment = 0, constantGain = 1;
  std::uint32_t rampFrames = 0;
  bool inputSilent = false;
};

// Steady vDSP_vsmul and fused vDSP_vsma operations reached by GainBox/MixBox.
bool multiplyGain(const float* input, float* output, std::size_t frames, float gain) noexcept;
bool multiplyAddGain(const float* input, const float* addend, float* output,
                     std::size_t frames, float gain) noexcept;
// Apply one already-computed control block to each channel independently. Call
// beginBlock only once per slice, then apply this same block to all channels.
bool applyGainBlock(const float* input, float* output, std::size_t frames,
                    GainBlock block) noexcept;

// GenericGainBox<LinearGainPolicy> smoothing, independent of channel count.
// Default property 0xc1a is .020 seconds, minimum .002 seconds.
class GainSmoother {
 public:
  explicit GainSmoother(std::uint32_t sampleRate, float initialGain = 1);
  bool setGain(float gain) noexcept;
  void setMuted(bool muted) noexcept;
  bool setSmoothingSeconds(double seconds) noexcept;
  void reset() noexcept;
  GainBlock beginBlock(std::uint32_t frames, bool inputSilent = false) noexcept;
  float currentGain() const noexcept { return current_; }
 private:
  void synchronizeFirstWrite() noexcept;
  std::uint32_t sampleRate_;
  double seconds_ = .020;
  float requested_, current_, target_, increment_ = 0;
  bool muted_ = false, first_ = true, ready_ = true;
};

}  // namespace lmg::automix
