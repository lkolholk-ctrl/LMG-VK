#pragma once
#include "lmg/automix/planner_transition_schedule.h"
#include "lmg/automix/planner_source_context.h"

namespace lmg::automix {
inline constexpr std::int64_t kPlannerScheduleEnvelopeMagic = 0x4c4d47534531;
inline constexpr std::int64_t kPlannerSchedulePlanMagic = 0x4c4d47535031;
inline constexpr std::size_t kPlannerScheduleEnvelopeHeader = 10;
inline constexpr std::size_t kPlannerSchedulePlanHeader = 40;
inline constexpr std::size_t kPlannerScheduleMaxWords = 2048;
enum class PlannerScheduleStatus : std::uint8_t {
  compiled = 0, sourceRejected = 1, selectionUnavailable = 2,
  unsupportedStyle = 3, invalidSchedule = 4
};
// Bounded wire; includes the complete source-selection envelope unchanged, plus
// a complete continuous plan or NO plan. All output descriptors use the fixed
// existing allEffectParameters() order; values remain unquantized IEEE doubles.
std::vector<std::int64_t> encodePlannerTransitionSchedule(const PlannerTransitionSchedule&);
std::vector<std::int64_t> observePlannerScheduledSource(
    const std::vector<std::int64_t>& sourceRequest, const CloudSongAnalysis& outgoing,
    const CloudSongAnalysis& incoming, const std::vector<TransitionStyle>& catalog);
std::vector<std::int64_t> observePlannerScheduledSourceJson(
    const std::vector<std::int64_t>& sourceRequest,
    std::string_view outgoing, std::string_view outgoingId,
    std::string_view incoming, std::string_view incomingId, std::string_view catalog);
} // namespace lmg::automix
