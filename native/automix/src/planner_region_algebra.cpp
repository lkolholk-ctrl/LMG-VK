#include "lmg/automix/planner_region_algebra.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix::region_algebra {
namespace {
void nonnegative(std::int64_t value) {
  if (value < 0) throw std::invalid_argument("Negative planner ordinal/count");
}
void finite(double value) {
  if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite planner region time");
}
void validateScale(TempoBinaryScale scale) {
  if (scale != TempoBinaryScale::half && scale != TempoBinaryScale::one &&
      scale != TempoBinaryScale::two) throw std::invalid_argument("Invalid region scale");
}
void validateUnit(PlannerRegionUnit unit) {
  if (unit != PlannerRegionUnit::beats && unit != PlannerRegionUnit::bars)
    throw std::invalid_argument("Invalid region unit");
}
const std::vector<std::size_t>& collection(const SongStructure& s, PlannerRegionUnit unit) {
  validateUnit(unit);
  return unit == PlannerRegionUnit::beats ? s.beatEvents : s.downbeatEvents;
}
std::int64_t ordinal(const StructureEvent& event, PlannerRegionUnit unit) {
  validateUnit(unit);
  if (unit == PlannerRegionUnit::bars && !event.downbeatIndex)
    throw std::invalid_argument("Missing downbeat index");
  const auto index = unit == PlannerRegionUnit::beats ? event.beatIndex : *event.downbeatIndex;
  nonnegative(index);
  return index;
}
void validateCollection(const SongStructure& s, PlannerRegionUnit unit) {
  const auto& indices = collection(s, unit);
  if (s.events.size() > kPlannerRegionAlgebraMaxEvents ||
      indices.size() > kPlannerRegionAlgebraMaxEvents)
    throw std::length_error("Planner region work limit");
  for (auto index : indices) {
    if (index >= s.events.size()) throw std::invalid_argument("Dangling structure event");
    const auto kind = static_cast<unsigned>(s.events[index].kind);
    if (kind > 3 || (unit == PlannerRegionUnit::bars && kind < 1))
      throw std::invalid_argument("Wrong event kind for region collection");
    // Do not sort/deduplicate or impose synthetic spacing on the source list.
    (void)ordinal(s.events[index], unit);
    finite(s.events[index].songTime);
  }
}
bool contains(const std::vector<std::size_t>& list, std::size_t index) {
  return std::find(list.begin(), list.end(), index) != list.end();
}
void validate(const SongStructure& s, StructureRegion range, PlannerRegionUnit unit) {
  validateCollection(s, unit);
  if (range.startEvent >= s.events.size() || range.endEvent >= s.events.size())
    throw std::invalid_argument("Dangling region endpoint");
  const auto& indices = collection(s, unit);
  if (!contains(indices, range.startEvent) || !contains(indices, range.endEvent))
    throw std::invalid_argument("Endpoint outside region unit collection");
  const auto& start = s.events[range.startEvent];
  const auto& end = s.events[range.endEvent];
  if (ordinal(start, unit) > ordinal(end, unit) || start.songTime > end.songTime)
    throw std::invalid_argument("Reversed region");
}
void validate(const PlannerRegionRef& ref) { validate(ref.owner(), ref.events(), ref.unit()); }
std::optional<PlannerRegionRef> suffixAt(const PlannerRegionRef& ref, std::int64_t first) {
  validate(ref);
  const auto& s = ref.owner();
  for (auto index : collection(s, ref.unit())) {
    if (ordinal(s.events[index], ref.unit()) == first) {
      // First matching entry, even when another equal ordinal appears later.
      return PlannerRegionRef(s, {index, ref.events().endEvent}, ref.unit());
    }
  }
  return std::nullopt;
}
void timeRange(PlannerSongTimeRange range) {
  finite(range.start); finite(range.end);
  if (range.end < range.start) throw std::invalid_argument("Reversed song-time range");
}
}

PlannerRegionRef::PlannerRegionRef(const SongStructure& owner, StructureRegion events,
                                 PlannerRegionUnit unit)
    : owner_(&owner), events_(events), unit_(unit) { validate(*this); }
std::int64_t PlannerRegionRef::firstOrdinal() const {
  validate(*this); return ordinal(owner_->events[events_.startEvent], unit_);
}
std::int64_t PlannerRegionRef::lastOrdinal() const {
  validate(*this); return ordinal(owner_->events[events_.endEvent], unit_);
}
std::int64_t PlannerRegionRef::ordinalCount() const {
  // Validation establishes 0 <= first <= last <= INT64_MAX, so subtraction fits.
  return lastOrdinal() - firstOrdinal();
}
double PlannerRegionRef::startSeconds() const {
  validate(*this); return owner_->events[events_.startEvent].songTime;
}
double PlannerRegionRef::endSeconds() const {
  validate(*this); return owner_->events[events_.endEvent].songTime;
}

std::int64_t plannerStyleSuffixStartOrdinal(std::int64_t first, std::int64_t last,
                                          std::int64_t requestedBars) {
  nonnegative(first); nonnegative(last); nonnegative(requestedBars);
  if (first > last) throw std::invalid_argument("Reversed bar ordinals");
  // 272234ed0..272234fc0. Subtraction is safe in this nonnegative signed domain.
  const auto proposed = last - requestedBars;
  return std::max(std::int64_t{0}, std::max(first, proposed));
}
std::int64_t plannerIncomingSuffixStartOrdinal(std::int64_t last,
    std::int64_t sourceBeatCount, std::int64_t maximumBeatCount) {
  nonnegative(last); nonnegative(sourceBeatCount); nonnegative(maximumBeatCount);
  // A source count larger than the endpoint can be valid in signed source
  // domains, but the normalized host ordinal domain starts at zero.
  if (sourceBeatCount > last) throw std::invalid_argument("Beat count exceeds end ordinal");
  return last - std::min(sourceBeatCount, maximumBeatCount);
}
std::int64_t plannerIncomingBeatBudget(std::int64_t count, TempoBinaryScale scale) {
  nonnegative(count); validateScale(scale);
  switch (scale) {
    case TempoBinaryScale::half:
      if (count > std::numeric_limits<std::int64_t>::max() / 2)
        throw std::overflow_error("Incoming beat budget overflow");
      return count * 2;
    case TempoBinaryScale::one: return count;
    case TempoBinaryScale::two: return count / 2;
  }
  throw std::logic_error("Unreachable tempo scale");
}
std::optional<PlannerRegionRef> plannerProjectRegionToBeats(const PlannerRegionRef& ref) {
  validate(ref);
  const auto& s = ref.owner();
  validateCollection(s, PlannerRegionUnit::beats);
  const auto endpoints = ref.events();
  if (!contains(s.beatEvents, endpoints.startEvent) || !contains(s.beatEvents, endpoints.endEvent))
    return std::nullopt;
  return PlannerRegionRef(s, endpoints, PlannerRegionUnit::beats);
}
std::optional<PlannerRegionRef> plannerOutgoingStyleSuffix(
    const PlannerRegionRef& ref, std::optional<std::int64_t> requestedBars) {
  validate(ref);
  if (ref.unit() != PlannerRegionUnit::bars)
    throw std::invalid_argument("Outgoing style suffix requires resolved bar range");
  const auto first = plannerStyleSuffixStartOrdinal(ref.firstOrdinal(), ref.lastOrdinal(),
                                                   requestedBars.value_or(4));
  return suffixAt(ref, first);
}
std::optional<PlannerRegionRef> plannerIncomingBeatSuffix(
    const PlannerRegionRef& ref, std::int64_t maximumBeatCount) {
  nonnegative(maximumBeatCount);
  const auto beats = plannerProjectRegionToBeats(ref);
  if (!beats) return std::nullopt;
  const auto first = plannerIncomingSuffixStartOrdinal(beats->lastOrdinal(), beats->ordinalCount(),
                                                      maximumBeatCount);
  return suffixAt(*beats, first);
}
std::optional<PlannerRegionRef> plannerHalveIncomingRegion(const PlannerRegionRef& ref) {
  const auto beats = plannerProjectRegionToBeats(ref);
  if (!beats) return std::nullopt;
  return plannerIncomingBeatSuffix(*beats, beats->ordinalCount() / 2);
}
std::optional<PlannerRegionPairRef> plannerTruncateRegionPair(const PlannerRegionPairRef& pair) {
  validateScale(pair.incomingScale);
  const auto outgoingBeats = plannerProjectRegionToBeats(pair.outgoing);
  if (!outgoingBeats) return std::nullopt;
  const auto incoming = plannerIncomingBeatSuffix(pair.incoming,
      plannerIncomingBeatBudget(outgoingBeats->ordinalCount(), pair.incomingScale));
  if (!incoming) return std::nullopt;
  return PlannerRegionPairRef{pair.outgoing, *incoming, pair.incomingScale};
}
std::optional<PlannerRegionPairRef> plannerStyleRegionPair(
    const PlannerRegionPairRef& pair, std::optional<std::int64_t> requestedBars) {
  validateScale(pair.incomingScale);
  const auto outgoing = plannerOutgoingStyleSuffix(pair.outgoing, requestedBars);
  if (!outgoing) return std::nullopt;
  return plannerTruncateRegionPair({*outgoing, pair.incoming, pair.incomingScale});
}

std::optional<PlannerStylingTimePair> plannerTruncateStylingTimePair(
    const PlannerStylingTimePair& pair, double songEnd) {
  timeRange(pair.outgoing); timeRange(pair.incoming); finite(songEnd);
  if (songEnd <= pair.outgoing.start) return std::nullopt;
  const double first = pair.outgoing.start;
  // Source fcsel takes the original outgoing end on equality (including -0).
  const double last = pair.outgoing.end > songEnd ? songEnd : pair.outgoing.end;
  const double duration = last - first;
  finite(duration);
  if (duration < 2.0) return std::nullopt;
  const double incomingEnd = pair.incoming.start + duration;
  finite(incomingEnd);
  if (incomingEnd < pair.incoming.start) throw std::invalid_argument("Incoming time overflow");
  return PlannerStylingTimePair{{first, last}, {pair.incoming.start, incomingEnd}};
}
std::optional<PlannerSongTimeRange> plannerIncomingStylingTimeRange(
    std::optional<double> downbeat, std::optional<double> nonSilent,
    double duration, double maximumEnd) {
  finite(duration); finite(maximumEnd);
  if (duration < 0) throw std::invalid_argument("Negative incoming styling duration");
  if (downbeat) finite(*downbeat);
  if (nonSilent) finite(*nonSilent);
  if (!downbeat && !nonSilent) return std::nullopt;
  // Source keeps nonSilent on equality, which matters for signed zero.
  const double start = !downbeat ? *nonSilent :
      (!nonSilent || *nonSilent < *downbeat ? *downbeat : *nonSilent);
  const double remaining = maximumEnd - start;
  finite(remaining);
  if (!(duration <= remaining)) return std::nullopt;
  const double end = start + duration;
  finite(end);
  if (end < start) throw std::invalid_argument("Invalid incoming styling range");
  return PlannerSongTimeRange{start, end};
}
bool plannerResolvedPlacementPasses(const PlannerStylingTimePair& pair,
                                   const PlannerResolvedPlacementBounds& bounds) {
  timeRange(pair.outgoing); timeRange(pair.incoming);
  finite(bounds.minimumOutgoingStart); finite(bounds.minimumOutgoingEnd); finite(bounds.maximumIncomingEnd);
  return bounds.minimumOutgoingStart <= pair.outgoing.start &&
         bounds.minimumOutgoingEnd <= pair.outgoing.end &&
         pair.incoming.end <= bounds.maximumIncomingEnd;
}
} // namespace lmg::automix::region_algebra
