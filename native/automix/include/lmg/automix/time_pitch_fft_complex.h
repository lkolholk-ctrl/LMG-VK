#pragma once
#include <cstddef>
#include <vector>
namespace lmg::automix {
// Source split-complex radix backend used by the real TimePitch FFT. Size is
// the complex half-size (128..4096), not the real-frame length. Constructor
// prepares all twiddles/workspace; transform never allocates or normalizes.
class TimePitchComplexFft {
 public:
  explicit TimePitchComplexFft(std::size_t complexSize);
  std::size_t size() const noexcept { return size_; }
  void transform(float* real, float* imag, bool inverse) noexcept;
 private:
  std::size_t size_;
  unsigned logSize_;
  std::vector<float> stageTwiddles_, finalTwiddles_, workReal_, workImag_;
};
} // namespace lmg::automix
