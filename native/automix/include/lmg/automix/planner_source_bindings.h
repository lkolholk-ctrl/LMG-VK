#pragma once
#include <array>
#include <cstdint>
#include <optional>
#include <variant>

namespace lmg::automix {
// Packages UUID 83c387fd-3c68-344a-85e5-0c3c015811c1 (iOS 26 AppOS 23A341).
// Constructor 272243e0c -> static array 2884aa5e8. Requested IDs, NOT bar counts.
const std::array<std::int64_t, 3>& plannerDefaultBeatMatchedStyleIds() noexcept;
// Stable LMG bits: profile=0, catalog mapping=1, complete provider/Criteria=2.
// Only the profile binding is closed. No permission to execute audio follows.
inline constexpr std::uint64_t kPlannerRemainingDefaultBindings = (1ULL << 1) | (1ULL << 2);

struct PlannerCriteriaAfter { double time; };
struct PlannerCriteriaWithin { double lower, upper; };
struct PlannerCriteriaInSong {};
using PlannerIncomingCriteria = std::variant<PlannerCriteriaAfter, PlannerCriteriaWithin, PlannerCriteriaInSong>;
struct PlannerOutgoingEarlyAfter { double time; };
struct PlannerOutgoingLateAfter { double time; };
struct PlannerOutgoingLateInSong {};
using PlannerOutgoingCriteria = std::variant<PlannerOutgoingEarlyAfter, PlannerOutgoingLateAfter, PlannerOutgoingLateInSong>;
struct PlannerCriteriaTimeRange { double lower, upper; };
enum class PlannerCriteriaResolution : std::uint8_t {
  resolved, durationUnavailable, invalidInput, latePlacementUnresolved
};
struct PlannerCriteriaRangeResult {
  PlannerCriteriaResolution status;
  std::optional<PlannerCriteriaTimeRange> range;
};
// Start-time limits only: 272255b88 (incoming) and early branch of 272258148.
// Finite nonnegative host domain; unknown duration is not zero/catalog duration.
// Equal endpoints and signed zero survive without rounding, epsilon or clamping.
// Reversed Within rejection is an LMG domain check, not a claim that the original
// resolver performed this comparison. The input's Swift Range had its own invariants.
PlannerCriteriaRangeResult resolvePlannerIncomingCriteria(
    std::optional<double> durationSeconds, const PlannerIncomingCriteria& criteria) noexcept;
PlannerCriteriaRangeResult resolvePlannerOutgoingCriteria(
    std::optional<double> durationSeconds, const PlannerOutgoingCriteria& criteria) noexcept;
// Not a ResolvedPlannerScope factory. Late placement, provider witness getters,
// prior playback context and eligibility remain unresolved outside these helpers.
} // namespace lmg::automix
