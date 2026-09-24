#include "lmg/automix/planner_source_bindings.h"
#include <cmath>
#include <type_traits>

namespace lmg::automix {
const std::array<std::int64_t, 3>& plannerDefaultBeatMatchedStyleIds() noexcept {
  static constexpr std::array<std::int64_t, 3> ids{8, 9, 12};
  return ids;
}
namespace {
bool valid(double v) noexcept { return std::isfinite(v) && v >= 0.0; }
PlannerCriteriaRangeResult failure(PlannerCriteriaResolution s) noexcept { return {s,std::nullopt}; }
PlannerCriteriaRangeResult resolved(double a,double b) noexcept {
  return {PlannerCriteriaResolution::resolved,PlannerCriteriaTimeRange{a,b}};
}
}
PlannerCriteriaRangeResult resolvePlannerIncomingCriteria(
    std::optional<double> duration,const PlannerIncomingCriteria& criteria) noexcept {
  if (criteria.valueless_by_exception()) return failure(PlannerCriteriaResolution::invalidInput);
  const bool payloadValid=std::visit([](const auto& c) noexcept {
    using T=std::decay_t<decltype(c)>;
    if constexpr(std::is_same_v<T,PlannerCriteriaAfter>) return valid(c.time);
    else if constexpr(std::is_same_v<T,PlannerCriteriaWithin>)
      return valid(c.lower) && valid(c.upper) && c.lower <= c.upper;
    else return true;
  },criteria);
  if (!payloadValid || (duration && !valid(*duration))) return failure(PlannerCriteriaResolution::invalidInput);
  if (!duration) return failure(PlannerCriteriaResolution::durationUnavailable);
  return std::visit([d=*duration](const auto& c) noexcept -> PlannerCriteriaRangeResult {
    using T=std::decay_t<decltype(c)>;
    if constexpr(std::is_same_v<T,PlannerCriteriaAfter>) {
      if(c.time > d) return failure(PlannerCriteriaResolution::invalidInput);
      return resolved(c.time,d);
    } else if constexpr(std::is_same_v<T,PlannerCriteriaWithin>) {
      if(c.lower > d || c.upper > d) return failure(PlannerCriteriaResolution::invalidInput);
      return resolved(c.lower,c.upper);
    } else return resolved(0.0,d);
  },criteria);
}
PlannerCriteriaRangeResult resolvePlannerOutgoingCriteria(
    std::optional<double> duration,const PlannerOutgoingCriteria& criteria) noexcept {
  if (criteria.valueless_by_exception()) return failure(PlannerCriteriaResolution::invalidInput);
  const bool payloadValid=std::visit([](const auto& c) noexcept {
    using T=std::decay_t<decltype(c)>;
    if constexpr(std::is_same_v<T,PlannerOutgoingLateInSong>) return true;
    else return valid(c.time);
  },criteria);
  if (!payloadValid || (duration && !valid(*duration))) return failure(PlannerCriteriaResolution::invalidInput);
  if (!duration) return failure(PlannerCriteriaResolution::durationUnavailable);
  return std::visit([d=*duration](const auto& c) noexcept -> PlannerCriteriaRangeResult {
    using T=std::decay_t<decltype(c)>;
    if constexpr(std::is_same_v<T,PlannerOutgoingEarlyAfter>) {
      if(c.time > d) return failure(PlannerCriteriaResolution::invalidInput);
      return resolved(c.time,d);
    } else {
      // Do not import the early-after duration comparison into untraced late logic.
      return failure(PlannerCriteriaResolution::latePlacementUnresolved);
    }
  },criteria);
}
} // namespace lmg::automix
