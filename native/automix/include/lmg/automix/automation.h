#pragma once

#include <cstdint>
#include <vector>

namespace lmg::automix {

using Frame = std::int64_t;

// Recovered curve evaluator for an interior normalized position in (0, 1).
// Caller validates finite endpoints and positive logarithmic endpoints.
double curveValue(double from, double to, double position, std::uint8_t curve) noexcept;

// A frame contains one sample for each channel. Rounding is a port policy,
// not a reconstruction of Apple's normalized style-time resolver.
Frame secondsToFrame(double seconds, std::uint32_t sampleRate);

struct Ramp {
  Frame begin;
  Frame end;
  double from;
  double to;
  std::uint8_t curve;

  // Control-thread validation; reversed windows and invalid logarithm domains
  // are rejected. begin == end represents a step at begin.
  void validate() const;
  // Requires a validated ramp. No allocation, locking, or wall clock.
  double valueAt(Frame frame) const noexcept;
};

// Immutable after construction. Boundaries, held values in gaps and rejection
// of overlapping ramps are explicit LMG scheduling policies.
class AutomationLane {
 public:
  explicit AutomationLane(double initial, std::vector<Ramp> ramps = {});
  double valueAt(Frame frame) const noexcept;
  double initial() const noexcept { return initial_; }
  const std::vector<Ramp>& ramps() const noexcept { return ramps_; }

 private:
  double initial_;
  std::vector<Ramp> ramps_;
};

}  // namespace lmg::automix
