#pragma once
#include <cstdint>
#include <vector>
namespace lmg::automix {
struct QueueRateAnchor {
  std::int64_t unscaledFrame, scaledFrame;
  double scaledOrigin, rate;
};
// Immutable anchor table and single-owner read high-water mark. This is the
// queue clock converter, not TimePitch's quadratic mapping or its history ring.
class QueueRateHistory {
 public:
  QueueRateHistory(std::vector<QueueRateAnchor> anchors,std::int64_t highWater);
  std::int64_t toUnscaled(double scaledFrame);
  double toScaled(std::int64_t unscaledFrame,double* rate=nullptr) noexcept;
  std::int64_t highWater() const noexcept {return highWater_;}
 private:
  std::vector<QueueRateAnchor> anchors_;
  std::int64_t highWater_;
};
}
