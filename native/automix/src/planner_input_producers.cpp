#include "lmg/automix/planner_input_producers.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {
using namespace region_algebra;
struct Exhausted {};
struct Budget {
  std::uint64_t remaining;
  void use(std::uint64_t n = 1) { if (n > remaining) throw Exhausted{}; remaining -= n; }
};
void finite(double v) { if (!std::isfinite(v)) throw std::invalid_argument("Nonfinite producer input"); }
void nonnegative(std::int64_t n) { if (n < 0) throw std::invalid_argument("Negative producer ordinal/count"); }
void window(PlannerSongTimeRange r) {
  finite(r.start); finite(r.end);
  if (r.end < r.start) throw std::invalid_argument("Reversed producer time window");
}
std::int64_t db(const SongStructure& s, std::size_t i) {
  if (i >= s.events.size() || !s.events[i].downbeatIndex || static_cast<unsigned>(s.events[i].kind) < 1)
    throw std::invalid_argument("Invalid producer downbeat");
  return *s.events[i].downbeatIndex;
}
bool member(const std::vector<std::size_t>& indices, std::size_t i) {
  return std::find(indices.begin(), indices.end(), i) != indices.end();
}
void validateStructure(const SongStructure& s, Budget& b) {
  constexpr auto cap = kPlannerRegionAlgebraMaxEvents;
  for (auto n : {s.events.size(), s.beatEvents.size(), s.downbeatEvents.size(),
                s.sectionBoundaryEvents.size(), s.segmentBoundaryEvents.size(),
                s.bars.size(), s.beatStabilityMap.size()})
    if (n > cap) throw Exhausted{};
  b.use(s.events.size() + s.beatEvents.size() + s.downbeatEvents.size() +
        s.sectionBoundaryEvents.size() + s.segmentBoundaryEvents.size() + 1);
  for (const auto& e : s.events) {
    finite(e.songTime); nonnegative(e.beatIndex);
    if (static_cast<unsigned>(e.kind) > 3) throw std::invalid_argument("Unknown producer event kind");
    if (e.downbeatIndex) nonnegative(*e.downbeatIndex);
    if (e.sectionIndex) nonnegative(*e.sectionIndex);
    if (e.segmentIndex) nonnegative(*e.segmentIndex);
  }
  for (auto i : s.beatEvents) if (i >= s.events.size()) throw std::invalid_argument("Dangling producer beat");
  for (auto i : s.downbeatEvents) {
    (void)db(s, i);
    b.use(s.beatEvents.size());
    if (!member(s.beatEvents, i)) throw std::invalid_argument("Downbeat is not a beat event");
  }
  for (auto i : s.sectionBoundaryEvents) {
    b.use(s.downbeatEvents.size());
    if (i >= s.events.size() || s.events[i].kind != StructureEventKind::sectionBoundary ||
        !member(s.downbeatEvents, i)) throw std::invalid_argument("Invalid producer section event");
  }
  for (auto i : s.segmentBoundaryEvents)
    if (i >= s.events.size() || static_cast<unsigned>(s.events[i].kind) < 2)
      throw std::invalid_argument("Invalid producer segment event");
  auto region = [&](StructureRegion r) {
    b.use(2 * s.downbeatEvents.size() + 1);
    const auto a = db(s, r.startEvent), z = db(s, r.endEvent);
    if (!member(s.downbeatEvents, r.startEvent) || !member(s.downbeatEvents, r.endEvent) ||
        a > z || s.events[r.startEvent].beatIndex > s.events[r.endEvent].beatIndex ||
        s.events[r.startEvent].songTime > s.events[r.endEvent].songTime)
      throw std::invalid_argument("Invalid producer bar region");
  };
  for (auto r : s.bars) region(r);
  for (const auto& r : s.beatStabilityMap) {
    region(r.events); finite(r.averageTempoBpm);
    if (r.beatsPerBar <= 0 || r.averageTempoBpm <= 0) throw std::invalid_argument("Invalid stable region traits");
  }
  // No sorting/deduplication: collection order is part of source behavior.
}
void validateLoudness(const PlannerLoudnessMap* map, Budget& b) {
  if (!map) return;
  if (map->size() > kPlannerObservationMaxLoudness) throw Exhausted{};
  b.use(map->size());
  for (const auto& p : *map) { finite(p.value); finite(p.songTime); }
}
std::int64_t maximumBars(const std::vector<PlannerCandidateStyle>& styles) {
  std::optional<std::int64_t> largest;
  for (const auto& style : styles) {
    const auto value = style.suffixBars.value_or(4); nonnegative(value);
    if (!largest || value > *largest) largest = value;
  }
  return largest.value_or(4);
}
std::optional<std::size_t> phraseStart(const SongStructure& s, Budget& b) {
  if (s.sectionBoundaryEvents.empty()) return std::nullopt;
  auto reference = s.sectionBoundaryEvents.front();
  // 27221c9b8: first adjacent section pair with delta divisible by four;
  // otherwise the first section. This is not the first stable region.
  for (std::size_t i = 1; i < s.sectionBoundaryEvents.size(); ++i) {
    b.use();
    if ((db(s, s.sectionBoundaryEvents[i]) - db(s, s.sectionBoundaryEvents[i-1])) % 4 == 0) {
      reference = s.sectionBoundaryEvents[i-1]; break;
    }
  }
  const auto phase = db(s, reference) % 4;
  for (auto i : s.downbeatEvents) { b.use(); if (db(s, i) == phase) return i; }
  return std::nullopt;
}
std::vector<std::size_t> outgoingEnds(const SongStructure& s, double minimumEnd, Budget& b) {
  finite(minimumEnd);
  std::vector<std::size_t> result;
  const auto first = phraseStart(s, b); if (!first) return result;
  const auto ordinal = db(s, *first);
  for (auto i : s.downbeatEvents) {
    b.use();
    const auto v = db(s, i);
    // 27222fa50/272231ed8/272231f78: strict ordinal; inclusive time.
    if (v > ordinal && (v - ordinal) % 4 == 0 && s.events[i].songTime >= minimumEnd)
      result.push_back(i);
  }
  return result;
}
std::vector<std::size_t> incomingEnds(const SongStructure& s, double maximumEnd,
                                    std::int64_t bars, Budget& b) {
  finite(maximumEnd); nonnegative(bars);
  std::vector<std::size_t> result;
  for (auto i : s.sectionBoundaryEvents) {
    b.use(); if (s.events[i].songTime <= maximumEnd) result.push_back(i);
  }
  // 27222fd74: drop ONLY the first retained item when its ordinal is too small.
  // Do not filter all short regions and do not replace this with >= eight bars.
  if (!result.empty() && db(s, result.front()) < bars) result.erase(result.begin());
  return result;
}
StructureRegion initialSuffix(const SongStructure& s, std::size_t end,
                              std::int64_t bars, Budget& b) {
  const auto target = db(s, end) - bars; // nonnegative operands cannot overflow
  // 27221c740 accepts the first >= target (not exact or binary-search sorted).
  for (auto i : s.downbeatEvents) {
    b.use(); if (db(s, i) >= target) return {i, end};
  }
  return {end, end};
}
std::optional<StructureRegion> stableSuffix(const SongStructure& s, StructureRegion r, Budget& b) {
  const auto start = db(s, r.startEvent), end = db(s, r.endEvent);
  if (end < start) throw std::invalid_argument("Reversed stable-suffix ordinals");
  std::optional<StructureRegion> clipped;
  // 27221abb4 visits FORWARDS and returns the FIRST matching stable entry.
  // Coverage of the requested END is closed; start is clipped but end retained.
  for (const auto& stable : s.beatStabilityMap) {
    b.use();
    const auto a = db(s, stable.events.startEvent), z = db(s, stable.events.endEvent);
    if (a <= end && end <= z) {
      clipped = StructureRegion{start >= a ? r.startEvent : stable.events.startEvent, r.endEvent};
      break;
    }
  }
  if (!clipped) return std::nullopt;
  const auto& a = s.events[clipped->startEvent]; const auto& z = s.events[clipped->endEvent];
  if (!(z.songTime > a.songTime)) return std::nullopt;
  if (z.beatIndex < a.beatIndex) throw std::invalid_argument("Reversed stable beat interval");
  const auto beats = z.beatIndex - a.beatIndex;
  if (beats < 1) return clipped;
  const double mean = (z.songTime - a.songTime) / static_cast<double>(beats); finite(mean);
  std::optional<double> previous;
  for (auto i : s.beatEvents) {
    b.use(); const auto& e = s.events[i];
    if (a.beatIndex <= e.beatIndex && e.beatIndex <= z.beatIndex) {
      if (previous) {
        const double duration = e.songTime - *previous; finite(duration);
        // Distinct from the stable-map bar duration tolerance (~0.04).
        if (std::abs(duration - mean) > 0.031) return std::nullopt;
      }
      previous = e.songTime;
    }
  }
  return clipped;
}
std::optional<double> meanBarTempo(const SongStructure& s, StructureRegion r, Budget& b) {
  const auto first = db(s, r.startEvent), last = db(s, r.endEvent);
  if (last < first) throw std::invalid_argument("Reversed tempo bar interval");
  double sum = 0; std::size_t n = 0;
  for (auto bar : s.bars) {
    b.use(); const auto a = db(s, bar.startEvent), z = db(s, bar.endEvent);
    if (first <= a && z <= last) {
      const auto& ae = s.events[bar.startEvent]; const auto& ze = s.events[bar.endEvent];
      if (ze.beatIndex <= ae.beatIndex || !(ze.songTime > ae.songTime))
        throw std::invalid_argument("Nonpositive producer bar duration/count");
      const auto beats = ze.beatIndex - ae.beatIndex;
      // 272232c24: mean of individual unquantized bar BPM values. Not the
      // whole-window BPM, and not SongStructure's half-BPM quantized map value.
      const double beatDuration = (ze.songTime - ae.songTime) / static_cast<double>(beats);
      const double tempo = 60.0 / beatDuration; finite(tempo);
      if (!(tempo > 0)) throw std::invalid_argument("Invalid bar tempo");
      sum = sum + tempo; finite(sum); ++n;
    }
  }
  if (!n) return std::nullopt;
  const double result = sum / static_cast<double>(n); finite(result); return result;
}
std::optional<double> slope(const PlannerLoudnessMap& points) {
  if (points.empty()) return std::nullopt;
  double x = 0, xx = 0, y = 0, xy = 0;
  for (const auto& p : points) {
    finite(p.songTime); finite(p.value);
    const double x2 = p.songTime * p.songTime;
    const double xY = p.songTime * p.value;
    x = x + p.songTime; xx = xx + x2; y = y + p.value; xy = xy + xY;
  }
  finite(x); finite(xx); finite(y); finite(xy);
  const double n = static_cast<double>(points.size());
  const double nXX = n * xx, xX = x * x;
  const double denominator = nXX - xX; finite(denominator);
  if (denominator == 0) return std::nullopt;
  const double nXY = n * xy, xY = x * y;
  const double numerator = nXY - xY; finite(numerator);
  const double result = numerator / denominator; finite(result); return result;
}
bool nonSilent(const PlannerLoudnessMap* map, PlannerSongTimeRange r, Budget& b) {
  window(r); if (!map) return true;
  b.use(5 * map->size() + 1);
  PlannerLoudnessMap samples;
  for (const auto& p : *map) if (r.start <= p.songTime && p.songTime <= r.end) samples.push_back(p);
  const auto trend = slope(samples);
  // 272232790: no fallback to all candidates for an empty PRESENT loudness map.
  // An unavailable regression is not by itself a rejection: mean still applies.
  if (trend && !(std::abs(*trend) < 1.0)) return false;
  const auto mean = meanPlannerLoudness(samples, {r.start, r.end});
  return mean && *mean > -30.0;
}
std::optional<PlannerCandidateSeed> scalePair(const SongStructure& out, StructureRegion a,
    const SongStructure& in, StructureRegion z, Budget& b) {
  const auto at = meanBarTempo(out, a, b), zt = meanBarTempo(in, z, b);
  if (!at || !zt) return std::nullopt;
  // Existing matcher retains the scale even on 0xfc. No compatibility gate is
  // applied here; normal/expanded strategy predicates run later in the selector.
  const auto scale = matchPlannerTempos({*at, false}, {*zt, false}, 0.0).scale;
  auto incoming = z; auto unit = PlannerRegionUnit::bars;
  if (scale == TempoBinaryScale::half) {
    const auto end = db(in, z.endEvent), count = end - db(in, z.startEvent);
    if (count > std::numeric_limits<std::int64_t>::max()/2) throw std::overflow_error("Doubled region count overflow");
    const auto start = end - std::min(end, count * 2);
    std::optional<std::size_t> index;
    for (auto i : in.downbeatEvents) { b.use(); if (db(in, i) == start) { index = i; break; } }
    if (!index) return std::nullopt;
    auto stable = stableSuffix(in, {*index, z.endEvent}, b);
    if (!stable) return std::nullopt;
    incoming = *stable;
  } else if (scale == TempoBinaryScale::two) {
    b.use(32 * (in.events.size() + in.beatEvents.size() + in.downbeatEvents.size()) + 1);
    const auto halved = plannerHalveIncomingRegion(PlannerRegionRef(in, z, PlannerRegionUnit::bars));
    if (!halved) return std::nullopt;
    incoming = halved->events(); unit = PlannerRegionUnit::beats;
  }
  return PlannerCandidateSeed{a, incoming, unit, scale};
}
void inside(const SongStructure& s, StructureRegion r, double duration) {
  if (r.startEvent >= s.events.size() || r.endEvent >= s.events.size()) throw std::invalid_argument("Dangling produced seed");
  const auto a = s.events[r.startEvent].songTime, z = s.events[r.endEvent].songTime;
  if (a < 0 || z < a || z > duration) throw std::invalid_argument("Produced seed outside resolved track");
}
PlannerSeedProduction state(PlannerSeedProductionStatus s) { PlannerSeedProduction r; r.status = s; return r; }
std::optional<PlannerSeedProductionStatus> preparationStatus(const PlannerSongPreparation& p) {
  if (static_cast<unsigned>(p.status) > 3) throw std::invalid_argument("Unknown preparation status");
  if (p.status == PlannerPreparationStatus::resourceLimit) return PlannerSeedProductionStatus::resourceLimit;
  if (p.status == PlannerPreparationStatus::invalid ||
      (p.inventory && (p.inventory->malformedRegions || p.inventory->reversedTimes)))
    return PlannerSeedProductionStatus::invalidPreparation;
  if (!p.durationMs) return PlannerSeedProductionStatus::unresolvedPlaybackDuration;
  if (*p.durationMs <= 0 || *p.durationMs > 9007199254740991LL) throw std::invalid_argument("Invalid playback duration");
  if (!p.maps.structure) return PlannerSeedProductionStatus::insufficientStructure;
  return std::nullopt;
}
} // namespace

PlannerStyleResolution resolvePlannerStyleRequests(const std::vector<std::int64_t>& requested,
    const std::vector<PlannerCandidateStyle>& catalog) {
  PlannerStyleResolution r;
  if (requested.size() > kPlannerCandidateMaxStyles || catalog.size() > kPlannerCandidateMaxStyles) {
    r.status = PlannerStyleResolutionStatus::resourceLimit; return r;
  }
  for (std::size_t i = 0; i < catalog.size(); ++i) {
    if (catalog[i].suffixBars) nonnegative(*catalog[i].suffixBars);
    for (std::size_t j = 0; j < i; ++j)
      if (catalog[i].id == catalog[j].id) throw std::invalid_argument("Ambiguous native style catalog");
  }
  for (auto id : requested) {
    const auto item = std::find_if(catalog.begin(), catalog.end(), [=](const auto& s){ return s.id == id; });
    if (item == catalog.end()) return {}; // no partial catalog/default substitution
    r.styles.push_back(*item);
  }
  r.maximumBars = maximumBars(r.styles);
  r.status = PlannerStyleResolutionStatus::resolved; r.completeForRequestedIds = true; return r;
}
PlannerMusicKitDurationEnvelope plannerMusicKitDurationEnvelope(double duration) {
  finite(duration); if (!(duration > 0)) throw std::invalid_argument("Nonpositive duration envelope input");
  const double preferred = duration >= 60.0 ? std::max(0.0, duration - 30.0) * 0.5 : std::min(duration, 2.0);
  return {preferred, std::min(duration, 60.0), std::min(preferred, 60.0)};
}
std::optional<CloudComposite<double>> normalizePlannerMusicKitScalarComposite(
    const std::optional<CloudComposite<double>>& raw) {
  if (!raw || !raw->main) return std::nullopt;
  finite(*raw->main);
  if (*raw->main < 0.0 || *raw->main > 1.0) return std::nullopt;
  auto edge = [](std::optional<double> value) {
    if (value) { finite(*value); if (*value < 0.0 || *value > 1.0) value.reset(); }
    return value;
  };
  return CloudComposite<double>{raw->main, edge(raw->beginning), edge(raw->ending)};
}
PlannerCandidateTonalBinding plannerMusicKitMainTonalComponents(const CloudSongAnalysis& song) {
  PlannerCandidateTonalBinding result; result.resolved = true;
  if (song.audio && song.audio->attributes) {
    const auto& audio = *song.audio->attributes;
    if (audio.key) {
      const auto map = normalizeCloudTonalityMap(*audio.key);
      if (map) result.tonality = map->main;
    }
    const auto normalized = normalizePlannerMusicKitScalarComposite(audio.melodicness);
    if (normalized) result.melodicness = normalized->main;
  }
  return result;
}
std::vector<std::size_t> plannerOutgoingSeedEnds(const SongStructure& s, double minimumEnd) {
  Budget b{kPlannerProducerWorkBudget};
  try { validateStructure(s, b); return outgoingEnds(s, minimumEnd, b); }
  catch (const Exhausted&) { throw std::length_error("Producer helper work limit"); }
}
std::vector<std::size_t> plannerIncomingSeedEnds(const SongStructure& s, double maximumEnd, std::int64_t bars) {
  Budget b{kPlannerProducerWorkBudget};
  try { validateStructure(s, b); return incomingEnds(s, maximumEnd, bars, b); }
  catch (const Exhausted&) { throw std::length_error("Producer helper work limit"); }
}
std::optional<StructureRegion> plannerStableSeedSuffix(const SongStructure& s, StructureRegion r) {
  Budget b{kPlannerProducerWorkBudget};
  try { validateStructure(s, b); b.use(8 * s.downbeatEvents.size() + 1);
    (void)PlannerRegionRef(s, r, PlannerRegionUnit::bars); return stableSuffix(s, r, b); }
  catch (const Exhausted&) { throw std::length_error("Producer helper work limit"); }
}
std::optional<double> plannerSeedMeanBarTempo(const SongStructure& s, StructureRegion r) {
  Budget b{kPlannerProducerWorkBudget};
  try { validateStructure(s, b); b.use(8 * s.downbeatEvents.size() + 1);
    (void)PlannerRegionRef(s, r, PlannerRegionUnit::bars); return meanBarTempo(s, r, b); }
  catch (const Exhausted&) { throw std::length_error("Producer helper work limit"); }
}
std::optional<double> plannerLoudnessLinearSlope(const PlannerLoudnessMap& points) {
  Budget b{kPlannerProducerWorkBudget};
  try { validateLoudness(&points, b); return slope(points); }
  catch (const Exhausted&) { throw std::length_error("Producer helper work limit"); }
}
bool plannerOutgoingSeedNonSilent(const PlannerLoudnessMap* map, PlannerSongTimeRange r) {
  Budget b{kPlannerProducerWorkBudget};
  try { validateLoudness(map, b); return nonSilent(map, r, b); }
  catch (const Exhausted&) { throw std::length_error("Producer helper work limit"); }
}
PlannerSeedProduction producePlannerCandidateSeeds(const PlannerSongPreparation& outgoing,
    const PlannerSongPreparation& incoming, const std::vector<PlannerCandidateStyle>& styles,
    const PlannerSeedDiscoveryBounds& bounds, std::uint64_t workBudget) {
  if (styles.size() > kPlannerCandidateMaxStyles || workBudget > kPlannerProducerWorkBudget)
    return state(PlannerSeedProductionStatus::resourceLimit);
  if (const auto s = preparationStatus(outgoing)) return state(*s);
  if (const auto s = preparationStatus(incoming)) return state(*s);
  const auto maximum = maximumBars(styles);
  const double outDuration = static_cast<double>(*outgoing.durationMs) / 1000.0;
  const double inDuration = static_cast<double>(*incoming.durationMs) / 1000.0;
  finite(bounds.minimumOutgoingEnd); finite(bounds.maximumIncomingEnd);
  if (bounds.minimumOutgoingEnd < 0 || bounds.minimumOutgoingEnd > outDuration ||
      bounds.maximumIncomingEnd < 0 || bounds.maximumIncomingEnd > inDuration)
    throw std::invalid_argument("Discovery bounds outside resolved track");
  Budget b{workBudget};
  try {
    const auto& a = *outgoing.maps.structure; const auto& z = *incoming.maps.structure;
    validateStructure(a, b); validateStructure(z, b);
    const auto* loudness = outgoing.maps.loudness ? &*outgoing.maps.loudness : nullptr;
    validateLoudness(loudness, b);
    const auto aEnds = outgoingEnds(a, bounds.minimumOutgoingEnd, b);
    const auto zEnds = incomingEnds(z, bounds.maximumIncomingEnd, maximum, b);
    PlannerSeedProduction r; r.outgoingEnds = aEnds.size(); r.incomingEnds = zEnds.size();
    std::vector<StructureRegion> aRanges, zRanges;
    for (auto i : aEnds) {
      const auto stable = stableSuffix(a, initialSuffix(a, i, maximum, b), b);
      if (!stable) continue;
      ++r.outgoingStable;
      if (nonSilent(loudness, {a.events[stable->startEvent].songTime, a.events[stable->endEvent].songTime}, b))
        aRanges.push_back(*stable);
    }
    for (auto i : zEnds) {
      const auto stable = stableSuffix(z, initialSuffix(z, i, maximum, b), b);
      if (stable) zRanges.push_back(*stable);
    }
    r.outgoingNonSilent = aRanges.size(); r.incomingStable = zRanges.size();
    // Bound pair work BEFORE materializing a potentially large Cartesian product.
    // This is a host resource failure, not a musical rejection or early winner.
    if (!zRanges.empty() && aRanges.size() > kPlannerCandidateMaxSeeds / zRanges.size()) throw Exhausted{};
    for (auto ar : aRanges) for (auto zr : zRanges) {
      ++r.pairAttempts; b.use();
      const auto scaled = scalePair(a, ar, z, zr, b);
      if (!scaled) { ++r.scaleMisses; continue; }
      b.use(64 * (a.events.size() + z.events.size() + a.beatEvents.size() + z.beatEvents.size() +
                  a.downbeatEvents.size() + z.downbeatEvents.size()) + 1);
      const auto truncated = plannerTruncateRegionPair({
          PlannerRegionRef(a, scaled->outgoing, PlannerRegionUnit::bars),
          PlannerRegionRef(z, scaled->incoming, scaled->incomingUnit), scaled->incomingScale});
      if (!truncated) { ++r.truncationMisses; continue; }
      const auto outRange = truncated->outgoing.events(), inRange = truncated->incoming.events();
      inside(a, outRange, outDuration); inside(z, inRange, inDuration);
      r.seeds.push_back({outRange, inRange, truncated->incoming.unit(), truncated->incomingScale});
    }
    r.status = r.seeds.empty() ? PlannerSeedProductionStatus::noSeeds : PlannerSeedProductionStatus::produced;
    r.workUnits = workBudget - b.remaining; r.completeForResolvedInputs = true; return r;
  } catch (const Exhausted&) { return state(PlannerSeedProductionStatus::resourceLimit); }
}
} // namespace lmg::automix
