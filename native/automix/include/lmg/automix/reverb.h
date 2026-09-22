#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace lmg::automix {

// Graph defaults, not the standalone AudioUnit's defaults. Geometry is prepared
// off the audio thread; changing it creates a new, cleared tank.
struct ReverbGeometry {
  float minSeconds = .008f;
  float maxSeconds = .05f;
  std::uint32_t seed = 1;
};
struct ReverbParameters {
  float wetPercent = 0;
  float gainDb = 1;
  float lowDecaySeconds = 1;
  float highDecaySeconds = .5f;
};

// Recovered 16-line matrix kernel. Planar mono->mono, mono->stereo or
// stereo->stereo; stereo input is averaged before entering the common tank.
// Does not implement AudioUnit bus negotiation or its bypass/tail wrapper.
// Decay value changes clear history, as in the AU parameter-write path. A
// scheduler repeating a decay write with an unchanged value must call reset().
class ReverbKernel {
 public:
  explicit ReverbKernel(double sampleRate, ReverbGeometry geometry = {});
  bool process(const float* left, const float* right, float* outLeft,
               float* outRight, std::size_t frames,
               ReverbParameters parameters = {}) noexcept;
  void reset() noexcept;
  std::array<std::uint32_t, 16> delayFrames() const noexcept;

 private:
  struct Line {
    std::vector<float> ring;
    std::uint32_t delay = 0, mask = 0, read = 0, write = 0;
    double seconds = 0;
    float damping = 0, feedback = 0, history = 0;
  };
  void decay(float low, float high) noexcept;
  std::array<Line, 16> lines_;
  float low_ = 0, high_ = 0, normalization_ = 1;
  float outputCurrent_ = 1, outputPrevious_ = 0;
  float previousLeft_ = 0, previousRight_ = 0;
};

// Audio-thread, block-boundary bypass. Setters and processing must be serialized.
// The AU's bypass copy path freezes the tank; resuming does not clear or fade it.
// Concurrent AudioUnit property writes and bus negotiation are not represented.
class ReverbEffect {
 public:
  explicit ReverbEffect(double sampleRate, ReverbGeometry geometry = {},
                        bool bypassed = false);
  void setBypassed(bool value) noexcept { bypassed_ = value; }
  bool bypassed() const noexcept { return bypassed_; }
  bool process(const float* left, const float* right, float* outLeft,
               float* outRight, std::size_t frames,
               ReverbParameters parameters = {}) noexcept;
  void reset() noexcept { kernel_.reset(); }

 private:
  ReverbKernel kernel_;
  bool bypassed_;
};

}  // namespace lmg::automix
