#pragma once
#include "lmg/automix/planner_candidate_selector.h"
#include "lmg/automix/styles.h"

namespace lmg::automix {
// The structured BeatMatched branch in the inspected image, not an unstructured,
// spatial, previous-state or stepped schedule. Coordinates stay in seconds.
struct PlannerStructuredRates {
  double scaledBeatRatio;
  double effectiveIncomingDuration;
  StylePlaybackRates outgoing, incoming;
};
PlannerStructuredRates plannerStructuredPlaybackRates(
    SecondsWindow outgoing, SecondsWindow incoming,
    std::int64_t outgoingBeats, std::int64_t incomingBeats, TempoBinaryScale);

struct PlannerScheduledSide {
  StructureRegion events;
  std::int64_t beatCount;
  SecondsWindow sourceRegion;
  SecondsWindow playbackTransitionRange;
  double startPlaybackSongTime;
  double stretchedRegionDuration;
  StylePlaybackRates rates;
  std::vector<ContinuousAutomation> automations;
  PlaybackTimeMap timeMap() const;
};

// All access is const; this object owns automation records and no native handles.
// A compiled schedule is NOT authority to touch an audio stream. Runtime/device
// validation, output-frame mapping and timely arming belong to later stages.
class PlannerTransitionSchedule {
 public:
  std::int64_t styleId() const noexcept { return styleId_; }
  TempoBinaryScale incomingScale() const noexcept { return scale_; }
  double score() const noexcept { return score_; }
  double scaledBeatRatio() const noexcept { return ratio_; }
  double effectiveIncomingDuration() const noexcept { return effective_; }
  SecondsWindow transitionRange() const noexcept { return transition_; }
  // Source referenceTransitionTime; NOT authority to change the active MediaItem.
  double referenceTransitionTime() const noexcept { return referenceTime_; }
  const PlannerScheduledSide& outgoing() const noexcept { return out_; }
  const PlannerScheduledSide& incoming() const noexcept { return in_; }
  static constexpr bool canExecute = false;
 private:
  friend PlannerTransitionSchedule compilePlannerTransitionSchedule(
      const PlannerScoredCandidate&, const SongStructure&, const SongStructure&,
      const TransitionStyle&);
  PlannerTransitionSchedule(std::int64_t, TempoBinaryScale, double,
      PlannerStructuredRates, SecondsWindow, double, PlannerScheduledSide, PlannerScheduledSide);
  std::int64_t styleId_;
  TempoBinaryScale scale_;
  double score_, ratio_, effective_;
  SecondsWindow transition_;
  double referenceTime_;
  PlannerScheduledSide out_, in_;
};
inline constexpr std::size_t kPlannerScheduleMaxAutomationsPerSide = 32;
// Source window/rate production + existing continuous compiler and time maps.
// The winner and structures must belong to the same deterministic preparations.
// Scope: styles 8/9/12 with zero source offset, no mapped parameter requirements.
// Invalid/unsupported input raises invalid_argument, never an approximate plan.
PlannerTransitionSchedule compilePlannerTransitionSchedule(
    const PlannerScoredCandidate&, const SongStructure& outgoing,
    const SongStructure& incoming, const TransitionStyle& selectedStyle);

enum class PlannerScheduleFreshness : std::uint8_t {
  positionNotPastStart, positionUnavailable, staleGeneration, staleRevision,
  invalidPosition, startAlreadyPassed
};
// Lifecycle guard, not an execution permit. Caller must supply the position of
// the CURRENT outgoing occurrence, not an arbitrary Player's position or clock.
PlannerScheduleFreshness checkPlannerScheduleFreshness(std::int64_t generation,
    std::int64_t revision, std::int64_t currentGeneration, std::int64_t currentRevision,
    double outgoingCueSeconds, std::optional<double> currentOutgoingPositionSeconds) noexcept;
} // namespace lmg::automix
