#pragma once

#include "lmg/automix/planner_beats.h"
#include "lmg/automix/planner_scoring.h"
#include <cstdint>
#include <optional>

namespace lmg::automix::region_algebra {

// A unit-qualified view into ONE immutable, caller-owned SongStructure.
// Its owner must outlive the view and may not be mutated while a view is used.
// These views never cross JNI and are not owning native handles. Construct them
// only after the corresponding source beat/bar conversion has been resolved.
enum class PlannerRegionUnit : std::uint8_t { beats = 0, bars = 1 };
class PlannerRegionRef final {
 public:
  PlannerRegionRef(const SongStructure& owner, StructureRegion events,
                   PlannerRegionUnit unit);
  PlannerRegionRef(const SongStructure&&, StructureRegion, PlannerRegionUnit) = delete;
  const SongStructure& owner() const noexcept { return *owner_; }
  StructureRegion events() const noexcept { return events_; }
  PlannerRegionUnit unit() const noexcept { return unit_; }
  std::int64_t firstOrdinal() const;
  std::int64_t lastOrdinal() const;
  std::int64_t ordinalCount() const;
  double startSeconds() const;
  double endSeconds() const;

 private:
  const SongStructure* owner_;
  StructureRegion events_;
  PlannerRegionUnit unit_;
};

// LMG work bound, NOT a recovered Apple threshold. Kept independent of the
// diagnostic preview cap: an event after the first 16 entries remains eligible.
inline constexpr std::size_t kPlannerRegionAlgebraMaxEvents = 4096;

// Arithmetic at the source getter boundaries. Nonnegative signed-64 ordinals
// and counts only; invalid host inputs throw, overflow never wraps. The source
// instruction-slice fixtures exercise these independently of event projection.
std::int64_t plannerStyleSuffixStartOrdinal(std::int64_t first, std::int64_t last,
                                          std::int64_t requestedBars);
std::int64_t plannerIncomingSuffixStartOrdinal(std::int64_t last,
    std::int64_t sourceBeatCount, std::int64_t maximumBeatCount);
// 272233804: this scales a COUNT in the inverse direction to tempo scaling.
// half -> count*2, one -> count, two -> count/2 (integer truncation).
std::int64_t plannerIncomingBeatBudget(std::int64_t outgoingBeatCount,
                                     TempoBinaryScale scale);

// The normalized LMG model can project the SAME endpoints from bars to beats
// only if both events belong to its beat collection. No nearest-event lookup,
// resampling, time interpolation, or beat -> bar snapping is performed.
std::optional<PlannerRegionRef> plannerProjectRegionToBeats(const PlannerRegionRef&);

// 272234e14: suffix of a converted bar range; keep the original end event.
// 272234380 uses FOUR only when its optional bar-count field is absent. This
// argument is that resolved count, NOT an assumed units conversion from JSON.
std::optional<PlannerRegionRef> plannerOutgoingStyleSuffix(
    const PlannerRegionRef& outgoingBars, std::optional<std::int64_t> requestedBars);
// 272233888 / 2722335b4: truncate/halve the converted incoming BEAT range,
// preserving its end. Lookup retains the first matching event in source order.
std::optional<PlannerRegionRef> plannerIncomingBeatSuffix(
    const PlannerRegionRef& incoming, std::int64_t maximumBeatCount);
std::optional<PlannerRegionRef> plannerHalveIncomingRegion(const PlannerRegionRef& incoming);

struct PlannerRegionPairRef {
  PlannerRegionRef outgoing;
  PlannerRegionRef incoming;
  TempoBinaryScale incomingScale;
};
// 272231b68: retain outgoing; restrict incoming using the inverse-scaled beat
// budget. 272234b0c additionally takes an outgoing style suffix first. Neither
// function invents seed pairs, chooses tempo scale, evaluates eligibility or
// selects a style. In particular this is NOT the shifted style-12 builder.
std::optional<PlannerRegionPairRef> plannerTruncateRegionPair(const PlannerRegionPairRef&);
std::optional<PlannerRegionPairRef> plannerStyleRegionPair(
    const PlannerRegionPairRef&, std::optional<std::int64_t> requestedBars);

struct PlannerSongTimeRange { double start, end; };
struct PlannerStylingTimePair {
  PlannerSongTimeRange outgoing, incoming;
};
// 27223c3ac: bound outgoing by its song end; require >=2 seconds; preserve the
// incoming START and set its end to start + new outgoing duration. The incoming
// old end is NOT a second duration cap. This function has no playback effects.
std::optional<PlannerStylingTimePair> plannerTruncateStylingTimePair(
    const PlannerStylingTimePair&, double outgoingSongEndSeconds);

// 27223c184 after its two source getters: choose the later present start (the
// non-silent start wins equality), then compare duration <= maximumEnd-start.
// Does NOT implement the downbeat/non-silent getters, or invent a zero start.
std::optional<PlannerSongTimeRange> plannerIncomingStylingTimeRange(
    std::optional<double> firstDownbeatStart, std::optional<double> nonSilentStart,
    double requiredDuration, double maximumIncomingEnd);

// The already-resolved getter results consumed by 27222919c and 272229758.
// Deriving these from Criteria/previousPlaybackEndState is a separate task.
struct PlannerResolvedPlacementBounds {
  double minimumOutgoingStart, minimumOutgoingEnd, maximumIncomingEnd;
};
bool plannerResolvedPlacementPasses(const PlannerStylingTimePair&,
                                   const PlannerResolvedPlacementBounds&);

} // namespace lmg::automix::region_algebra
