#pragma once
#include <cmath>
#include <stdexcept>

namespace lmg::automix {

// Recovered planner configuration; stepped conversion is in stepped_schedule.h.
// Default policy is continuous; 0.2 seconds is only the default of
// the separately selected stepped mode. Do not impose it on every DSP graph.
class SchedulingPolicy {
 public:
  enum class Mode { Stepped = 0, Continuous = 1 };
  static constexpr double minimumStepDuration = .0001;
  static constexpr double maximumStepDuration = 1;
  static constexpr double defaultStepDuration = .2;

  SchedulingPolicy() noexcept = default;
  static SchedulingPolicy stepped(double seconds = defaultStepDuration) {
    if (!std::isfinite(seconds) || seconds < minimumStepDuration || seconds > maximumStepDuration)
      throw std::invalid_argument("Step duration outside recovered planner range");
    SchedulingPolicy policy;
    policy.mode_ = Mode::Stepped;
    policy.duration_ = seconds;
    return policy;
  }
  Mode mode() const noexcept { return mode_; }
  // Continuous mode has no step duration. Expose that distinction explicitly.
  double stepDuration() const {
    if (mode_ != Mode::Stepped) throw std::logic_error("Continuous policy has no step duration");
    return duration_;
  }
 private:
  Mode mode_ = Mode::Continuous;
  double duration_ = 0;
};
}  // namespace lmg::automix
