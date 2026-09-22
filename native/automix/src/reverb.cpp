#include "lmg/automix/reverb.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {

// The default 31-word/3-word-separation generator reached from 0x234fe4068.
// Instance-local state avoids mutating the application's libc RNG. The original
// calls a process-global generator; external initstate/interleaving is not modeled.
class GeometryRandom {
 public:
  explicit GeometryRandom(std::uint32_t seed) noexcept {
    state_[0] = seed;
    for (unsigned i = 1; i < 31; ++i) {
      std::int32_t value;
      std::memcpy(&value, &state_[i - 1], sizeof(value));
      if (value == 0) value = 123459876;
      const auto quotient = value / 127773;
      const auto product = std::int64_t(value) * 16807 -
                           std::int64_t(quotient) * 2147483647;
      auto bits = static_cast<std::uint32_t>(product);
      if (bits & 0x80000000u) bits += 0x7fffffffu;
      state_[i] = bits;
    }
    for (unsigned i = 0; i < 310; ++i) next();
  }
  std::uint32_t next() noexcept {
    const std::uint32_t sum = state_[front_] + state_[rear_];
    state_[front_] = sum;
    front_ = (front_ + 1) % 31;
    rear_ = (rear_ + 1) % 31;
    return sum >> 1;
  }
 private:
  std::array<std::uint32_t, 31> state_{};
  unsigned front_ = 3, rear_ = 0;
};

// 0x234fd4ac0 returns the first prime >= n. Trial division gives the same
// answer as its table/binary search for our bounded preparation-time domain.
std::uint32_t primeAtLeast(std::uint32_t n) noexcept {
  if (n <= 2) return 2;
  n |= 1;
  for (;; n += 2) {
    bool prime = true;
    for (std::uint32_t d = 3; d <= n / d; d += 2) {
      if (n % d == 0) { prime = false; break; }
    }
    if (prime) return n;
  }
}

void matrix(std::array<float, 16>& values) noexcept {
  // Exact four butterfly stages in 0x234fe431c/45b0/4834, including order.
  for (unsigned stride = 1; stride < 16; stride *= 2) {
    for (unsigned base = 0; base < 16; base += 2 * stride) {
      for (unsigned i = 0; i < stride; ++i) {
        const float a = values[base + i], b = values[base + i + stride];
        values[base + i] = a + b;
        values[base + i + stride] = a - b;
      }
    }
  }
}

}  // namespace

ReverbKernel::ReverbKernel(double rate, ReverbGeometry geometry) {
  // Host resource/validity limits, not claimed AudioUnit parameter clamps.
  if (!std::isfinite(rate) || rate < 8000 || rate > 192000 ||
      !std::isfinite(geometry.minSeconds) || !std::isfinite(geometry.maxSeconds) ||
      geometry.minSeconds < .0001f || geometry.maxSeconds > 1 ||
      geometry.maxSeconds < geometry.minSeconds)
    throw std::invalid_argument("Invalid reverb geometry");
  const float ratio = geometry.maxSeconds / geometry.minSeconds;
  const double factor = std::pow(double(ratio), 1.0 / 15.0);
  std::array<std::uint32_t, 16> requested{};
  const auto frames = [rate](double seconds) {
    return static_cast<std::uint32_t>(std::floor(std::fma(rate, seconds, .5)));
  };
  requested[0] = frames(geometry.minSeconds);
  requested[15] = frames(geometry.maxSeconds);
  GeometryRandom random(geometry.seed);
  double seconds = geometry.minSeconds;
  for (unsigned i = 1; i < 15; ++i) {
    seconds = factor * seconds;
    const double jitter = (double(random.next()) / 2147483647.0 - .5) * .8;
    const float perturbed = static_cast<float>(seconds * std::pow(factor, jitter));
    requested[i] = frames(double(perturbed));
  }
  for (unsigned i = 0; i < 16; ++i) {
    auto& line = lines_[i];
    line.delay = primeAtLeast(requested[i]);
    // The original performs a single adjacent-equality pass after allocation.
    if (i && line.delay == lines_[i - 1].delay)
      line.delay = primeAtLeast(line.delay + 1);
    std::uint32_t size = 1;
    while (size < line.delay) size *= 2;
    line.ring.resize(size);
    line.mask = size - 1;
    line.seconds = double(line.delay) / rate;
  }
  decay(1, .5f);
  reset();
}

void ReverbKernel::decay(float low, float high) noexcept {
  low_ = low;
  high_ = high;
  high = std::min(high, low);
  const double ratio = double(low / high);
  const double spectral = 1.0 - ratio * ratio;
  float sum = 0;
  for (auto& line : lines_) {
    const double exponent = (line.seconds * -3.0) / double(low);
    const double amplitude = std::max(std::pow(10.0, exponent), .0001);
    const double damping = std::min(spectral * (exponent * 0.575646273248512), .99);
    const double feedback = amplitude * (1.0 - damping) * .25;
    line.damping = static_cast<float>(damping);
    line.feedback = static_cast<float>(feedback);
    sum = sum + line.feedback;
  }
  normalization_ = 1.0f / sum;
  const double highRatio = double(high / low);
  const float shelf = static_cast<float>((1.0 - highRatio) / (highRatio + 1.0));
  outputCurrent_ = static_cast<float>(1.0 / (1.0 - double(shelf)));
  outputPrevious_ = -shelf * outputCurrent_;
}

bool ReverbKernel::process(const float* left, const float* right, float* outLeft,
                           float* outRight, std::size_t count,
                           ReverbParameters p) noexcept {
  if ((count && (!left || !outLeft)) || (right && !outRight) ||
      count > std::numeric_limits<std::uint32_t>::max() ||
      !std::isfinite(p.wetPercent) || p.wetPercent < 0 || p.wetPercent > 100 ||
      !std::isfinite(p.gainDb) || p.gainDb < -96 || p.gainDb > 20 ||
      !std::isfinite(p.lowDecaySeconds) || p.lowDecaySeconds < .001f || p.lowDecaySeconds > 100 ||
      !std::isfinite(p.highDecaySeconds) || p.highDecaySeconds < .001f || p.highDecaySeconds > 100)
    return false;
  if (low_ != p.lowDecaySeconds || high_ != p.highDecaySeconds) {
    decay(p.lowDecaySeconds, p.highDecaySeconds);
    // AU parameter writes 2..6 invalidate/reinitialize the tank, including
    // decay writes: 0x234fe4cec -> 0x234fe3bf4. Preserve wet/gain-only tails.
    reset();
  }
  // Float multiply before exp10f, then float multiply by the normalization.
  const float gain = std::pow(10.0f, p.gainDb * .05f) * normalization_;
  const float wet = p.wetPercent * .01f;
  for (std::size_t frame = 0; frame < count; ++frame) {
    std::array<float, 16> values;
    for (unsigned i = 0; i < 16; ++i) {
      auto& line = lines_[i];
      const float delayed = line.ring[line.read++ & line.mask] * line.feedback;
      const float history = line.damping * line.history;
      values[i] = delayed + history;
      line.history = values[i];
    }
    matrix(values);
    const float l = left[frame], r = right ? right[frame] : l;
    const float currentL = values[1] * outputCurrent_;
    const float historyL = outputPrevious_ * previousLeft_;
    const float filteredL = (currentL + historyL) * gain;
    previousLeft_ = values[1];
    outLeft[frame] = l + wet * (filteredL - l);
    if (outRight) {
      const float currentR = values[2] * outputCurrent_;
      const float historyR = outputPrevious_ * previousRight_;
      const float filteredR = (currentR + historyR) * gain;
      previousRight_ = values[2];
      outRight[frame] = r + wet * (filteredR - r);
    }
    const float input = right ? (l + r) * .5f : l;
    for (unsigned i = 0; i < 16; ++i) {
      auto& line = lines_[i];
      line.ring[line.write++ & line.mask] = input + values[i];
    }
  }
  return true;
}

void ReverbKernel::reset() noexcept {
  for (auto& line : lines_) {
    std::fill(line.ring.begin(), line.ring.end(), 0);
    line.read = (0u - line.delay) & line.mask;
    line.write = 0;
    line.history = 0;
  }
  previousLeft_ = previousRight_ = 0;
}

std::array<std::uint32_t, 16> ReverbKernel::delayFrames() const noexcept {
  std::array<std::uint32_t, 16> result{};
  for (unsigned i = 0; i < 16; ++i) result[i] = lines_[i].delay;
  return result;
}

ReverbEffect::ReverbEffect(double rate, ReverbGeometry geometry, bool bypassed)
    : kernel_(rate, geometry), bypassed_(bypassed) {}

bool ReverbEffect::process(const float* left, const float* right, float* outLeft,
                           float* outRight, std::size_t count,
                           ReverbParameters parameters) noexcept {
  if (!bypassed_)
    return kernel_.process(left, right, outLeft, outRight, count, parameters);
  if ((count && (!left || !outLeft)) || (right && !outRight) ||
      count > std::numeric_limits<std::uint32_t>::max())
    return false;
  for (std::size_t i = 0; i < count; ++i) {
    const float l = left[i], r = right ? right[i] : l;
    outLeft[i] = l;
    if (outRight) outRight[i] = r;
  }
  return true;
}

}  // namespace lmg::automix
