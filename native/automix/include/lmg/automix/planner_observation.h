#pragma once
#include "lmg/automix/planner_analysis.h"
#include "lmg/automix/planner_regions.h"
#include <cstdint>
#include <string_view>

namespace lmg::automix {
// This is the completed PREPARATION slice of Stage 3, not a candidate selector.
// Prepared means source maps and an inventory exist; it is NEVER executable.
enum class PlannerPreparationStatus : std::uint8_t {
  insufficient = 0, prepared = 1, invalid = 2, resourceLimit = 3
};
enum class PlannerPreparationIssue : std::uint64_t {
  durationMissing = 1ULL << 0, audioAbsent = 1ULL << 1,
  audioUnresolved = 1ULL << 2, flexAbsent = 1ULL << 3,
  flexUnresolved = 1ULL << 4, videoEventsMissing = 1ULL << 5,
  eventArraysMissing = 1ULL << 6, emptyStructure = 1ULL << 7,
  noStableRegions = 1ULL << 8, malformedRegions = 1ULL << 9,
  invalidAnalysis = 1ULL << 10, resourceLimit = 1ULL << 11,
  reversedEvents = 1ULL << 12, duplicateEvents = 1ULL << 13,
  outsideTrack = 1ULL << 14
};
struct PlannerSongPreparation {
  PlannerPreparationStatus status = PlannerPreparationStatus::insufficient;
  std::uint64_t issues = 0;
  std::uint8_t audioState = 0, flexState = 0, supportState = 0;
  std::optional<std::int64_t> durationMs;
  PlannerAnalysisMaps maps;
  std::optional<PlannerRegionInventory> inventory;
};
inline constexpr std::size_t kPlannerObservationMaxLoudness = 16384;
inline constexpr std::size_t kPlannerObservationMaxVocals = 4096;
inline constexpr std::size_t kPlannerPreparationTrackWords = 32;
inline constexpr std::size_t kPlannerPreparationRegionWords = 8;
inline constexpr std::size_t kPlannerPreparationHeaderWords = 8;
inline constexpr std::size_t kPlannerPreparationMaxWords = 328;

// Existing cloud converters and SongStructure implementation are the only
// semantic interpreters. LMG adds bounded work, inspection and transport.
PlannerSongPreparation preparePlannerSongObservation(const CloudSongAnalysis& song,
    std::optional<std::int64_t> resolvedDurationMs);
// Header: version, length, tracks=2, trackWords=32, regionWords=8,
// maxPreview=16, executable=0, selectedStyle=-1. Doubles use memcpy'd IEEE bits.
std::vector<std::int64_t> encodePlannerPreparationPair(
    const PlannerSongPreparation& outgoing, const PlannerSongPreparation& incoming);
// Stateless production entry point. Raw decoder enforces song identity, size,
// depth, known types and duplicate-key rules. Native state never outlives call.
std::vector<std::int64_t> preparePlannerPairObservationJson(
    std::string_view outgoing, std::string_view outgoingSongId,
    std::string_view incoming, std::string_view incomingSongId,
    std::optional<std::int64_t> outgoingDurationMs,
    std::optional<std::int64_t> incomingDurationMs);
} // namespace lmg::automix
