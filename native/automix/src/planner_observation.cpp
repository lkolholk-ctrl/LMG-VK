#include "lmg/automix/planner_observation.h"
#include <cstring>
#include <limits>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
void issue(PlannerSongPreparation& out, PlannerPreparationIssue value) {
  out.issues |= static_cast<std::uint64_t>(value);
}
template<class T> std::uint8_t availability(const std::optional<CloudAnalysisResource<T>>& r) {
  return !r ? 0 : r->attributes ? 2 : 1;
}
void checkWork(const CloudSongAnalysis& song) {
  if (song.flex && song.flex->attributes && song.flex->attributes->videoEvents) {
    const auto& events = *song.flex->attributes->videoEvents;
    // The recovered converter ignores excess scores. Bound CONSUMED events,
    // not an unrelated tail. No sorting or trimming of the consumed arrays.
    if (events.timeInSeconds && events.timeInSeconds->size() > kPlannerObservationMaxEvents)
      throw std::length_error("Planner event limit");
  }
  if (song.audio && song.audio->attributes) {
    const auto& a = *song.audio->attributes;
    if (a.loudnessCurve && a.loudnessCurve->value &&
        a.loudnessCurve->value->size() > kPlannerObservationMaxLoudness)
      throw std::length_error("Planner loudness limit");
    if (a.vocalActivity && a.vocalActivity->size() > kPlannerObservationMaxVocals)
      throw std::length_error("Planner vocal limit");
  }
}
std::int64_t bits(double value) {
  static_assert(sizeof(double) == sizeof(std::int64_t));
  static_assert(std::numeric_limits<double>::is_iec559);
  std::int64_t out;
  std::memcpy(&out, &value, sizeof(out));
  return out;
}
std::int64_t count(std::size_t value) {
  if (value > static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))
    throw std::length_error("Planner count overflow");
  return static_cast<std::int64_t>(value);
}
}
PlannerSongPreparation preparePlannerSongObservation(const CloudSongAnalysis& song,
    std::optional<std::int64_t> durationMs) {
  PlannerSongPreparation out;
  out.audioState = availability(song.audio);
  out.flexState = availability(song.flex);
  out.supportState = !song.supportsSmartTransitions ? 0 : *song.supportsSmartTransitions ? 2 : 1;
  // Exact integer-millisecond transport domain, not a music-duration heuristic.
  if (durationMs && (*durationMs <= 0 || *durationMs > 9007199254740991LL)) {
    out.status = PlannerPreparationStatus::invalid;
    issue(out, PlannerPreparationIssue::invalidAnalysis);
    return out;
  }
  out.durationMs = durationMs;
  if (!durationMs) issue(out, PlannerPreparationIssue::durationMissing);
  if (out.audioState == 0) issue(out, PlannerPreparationIssue::audioAbsent);
  if (out.audioState == 1) issue(out, PlannerPreparationIssue::audioUnresolved);
  if (out.flexState == 0) issue(out, PlannerPreparationIssue::flexAbsent);
  if (out.flexState == 1) issue(out, PlannerPreparationIssue::flexUnresolved);
  if (out.flexState == 2) {
    const auto& flex = *song.flex->attributes;
    if (!flex.videoEvents) issue(out, PlannerPreparationIssue::videoEventsMissing);
    else if (!flex.videoEvents->timeInSeconds || !flex.videoEvents->score)
      issue(out, PlannerPreparationIssue::eventArraysMissing);
  }
  try {
    checkWork(song); // before potentially quadratic source section normalization
    std::optional<double> seconds;
    if (durationMs) seconds = static_cast<double>(*durationMs) / 1000.0;
    auto maps = preparePlannerAnalysisMaps(song, seconds);
    std::optional<PlannerRegionInventory> inventory;
    if (maps.structure) {
      inventory = inspectPlannerRegions(*maps.structure, seconds);
      if (maps.structure->events.empty()) issue(out, PlannerPreparationIssue::emptyStructure);
      if (maps.structure->beatStabilityMap.empty()) issue(out, PlannerPreparationIssue::noStableRegions);
      if (inventory->malformedRegions) issue(out, PlannerPreparationIssue::malformedRegions);
      if (inventory->outsideTrackRegions) issue(out, PlannerPreparationIssue::outsideTrack);
      if (inventory->duplicateTimes) issue(out, PlannerPreparationIssue::duplicateEvents);
      if (inventory->reversedTimes) issue(out, PlannerPreparationIssue::reversedEvents);
      if (inventory->boundedStableRegions > 0) out.status = PlannerPreparationStatus::prepared;
    }
    out.maps = std::move(maps);
    out.inventory = std::move(inventory);
  } catch (const std::length_error&) {
    out.status = PlannerPreparationStatus::resourceLimit;
    issue(out, PlannerPreparationIssue::resourceLimit);
  } catch (const std::invalid_argument&) {
    out.status = PlannerPreparationStatus::invalid;
    issue(out, PlannerPreparationIssue::invalidAnalysis);
  }
  // Allocation/internal failures are NOT converted into successful reports.
  return out;
}

std::vector<std::int64_t> encodePlannerPreparationPair(
    const PlannerSongPreparation& outgoing, const PlannerSongPreparation& incoming) {
  std::vector<std::int64_t> result{1, 0, 2, 32, 8, 16, 0, -1};
  result.reserve(kPlannerPreparationMaxWords);
  for (const auto* track : {&outgoing, &incoming}) {
    const auto base = result.size();
    result.resize(base + kPlannerPreparationTrackWords, 0);
    auto set = [&](std::size_t slot, std::int64_t value) { result[base + slot] = value; };
    set(0, static_cast<std::int64_t>(track->status)); set(1, static_cast<std::int64_t>(track->issues));
    set(2, track->audioState); set(3, track->flexState); set(4, track->supportState);
    std::int64_t presence = 0;
    if (track->durationMs) { presence |= 1; set(6, *track->durationMs); }
    const auto& m = track->maps;
    if (m.tonality) {
      presence |= 2;
      set(22, m.tonality->main.tonic); set(23, m.tonality->main.mode);
      if (m.tonality->beginning) {
        presence |= 64; set(24, m.tonality->beginning->tonic); set(25, m.tonality->beginning->mode);
      }
      if (m.tonality->ending) {
        presence |= 128; set(26, m.tonality->ending->tonic); set(27, m.tonality->ending->mode);
      }
    }
    if (m.loudness) { presence |= 4; set(7, count(m.loudness->size())); }
    if (m.vocals) { presence |= 8; set(8, count(m.vocals->size())); }
    if (m.flexEvents) { presence |= 16; set(9, count(m.flexEvents->size())); }
    if (m.structure) {
      presence |= 32; const auto& s = *m.structure;
      set(10, count(s.events.size())); set(11, count(s.downbeatEvents.size()));
      set(12, count(s.bars.size())); set(13, count(s.segments.size()));
      set(14, count(s.sections.size())); set(15, count(s.beatStabilityMap.size()));
    }
    set(5, presence);
    if (!track->inventory) continue;
    const auto& inv = *track->inventory;
    if (inv.stablePreview.size() > kPlannerObservationMaxPreview)
      throw std::length_error("Planner preview limit");
    set(16, count(inv.usableStableRegions)); set(17, count(inv.boundedStableRegions));
    set(18, count(inv.malformedRegions)); set(19, count(inv.outsideTrackRegions));
    set(20, count(inv.duplicateTimes)); set(21, count(inv.reversedTimes));
    set(28, count(inv.stablePreview.size()));
    set(29, inv.usableStableRegions > inv.stablePreview.size() ? 1 : 0);
    for (const auto& p : inv.stablePreview) {
      result.insert(result.end(), {count(p.mapIndex), count(p.events.startEvent), count(p.events.endEvent),
          bits(p.startSeconds), bits(p.endSeconds), p.beatsPerBar, bits(p.averageTempoBpm), p.trackBounds});
    }
  }
  result[1] = count(result.size());
  return result;
}
} // namespace lmg::automix
