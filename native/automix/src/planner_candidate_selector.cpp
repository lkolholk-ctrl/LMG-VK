#include "lmg/automix/planner_candidate_selector.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {
using namespace region_algebra;
void finite(double v) { if (!std::isfinite(v)) throw std::invalid_argument("Nonfinite candidate input"); }
void count(std::int64_t v) { if (v < 0) throw std::invalid_argument("Negative candidate ordinal"); }
void scale(TempoBinaryScale s) {
  if (s != TempoBinaryScale::half && s != TempoBinaryScale::one && s != TempoBinaryScale::two)
    throw std::invalid_argument("Invalid candidate scale");
}
struct BudgetExceeded {};
struct Budget {
  std::uint64_t remaining;
  void use(std::uint64_t n = 1) {
    if (n > remaining) throw BudgetExceeded{};
    remaining -= n;
  }
};
std::int64_t scaledBars(std::int64_t v, TempoBinaryScale s) {
  count(v); scale(s);
  if (s == TempoBinaryScale::half) return v / 2;
  if (s == TempoBinaryScale::one) return v;
  if (v > std::numeric_limits<std::int64_t>::max() / 2)
    throw std::overflow_error("Candidate bar-count overflow");
  return v * 2;
}
bool member(const std::vector<std::size_t>& c, std::size_t i) {
  return std::find(c.begin(), c.end(), i) != c.end();
}
void validateSong(const PlannerCandidateSong& song, Budget& b) {
  if (!song.structure) return;
  const auto& s = *song.structure;
  if (s.events.size() > kPlannerRegionAlgebraMaxEvents || s.beatEvents.size() > kPlannerRegionAlgebraMaxEvents ||
      s.downbeatEvents.size() > kPlannerRegionAlgebraMaxEvents || s.beatStabilityMap.size() > kPlannerRegionAlgebraMaxEvents)
    throw BudgetExceeded{};
  b.use(s.events.size() + s.beatEvents.size() + s.downbeatEvents.size());
  for (const auto& e : s.events) {
    finite(e.songTime); count(e.beatIndex);
    if (static_cast<unsigned>(e.kind) > 3) throw std::invalid_argument("Invalid event kind");
    if (e.downbeatIndex) count(*e.downbeatIndex);
  }
  // Do not reorder or silently normalize supplied native structures.
  for (auto i : s.beatEvents)
    if (i >= s.events.size()) throw std::invalid_argument("Dangling beat event");
  for (auto i : s.downbeatEvents)
    if (i >= s.events.size() || !s.events[i].downbeatIndex || static_cast<unsigned>(s.events[i].kind) < 1)
      throw std::invalid_argument("Invalid downbeat event");
  for (const auto& r : s.beatStabilityMap) {
    b.use(2 * s.downbeatEvents.size() + 1);
    if (r.events.startEvent >= s.events.size() || r.events.endEvent >= s.events.size() ||
        !member(s.downbeatEvents, r.events.startEvent) || !member(s.downbeatEvents, r.events.endEvent))
      throw std::invalid_argument("Invalid stable-map endpoints");
    const auto& a = s.events[r.events.startEvent]; const auto& z = s.events[r.events.endEvent];
    if (*a.downbeatIndex >= *z.downbeatIndex || a.songTime > z.songTime || r.beatsPerBar <= 0)
      throw std::invalid_argument("Invalid stable-map interval");
    finite(r.averageTempoBpm);
    if (!(r.averageTempoBpm > 0)) throw std::invalid_argument("Invalid stable-map tempo");
  }
  if (song.vocals) {
    if (song.vocals->size() > 4096) throw BudgetExceeded{};
    b.use(song.vocals->size());
    for (const auto& v : *song.vocals) {
      finite(v.start); finite(v.end);
      if (v.start > v.end || v.kindTag > 2 || v.strengthTag > 4)
        throw std::invalid_argument("Invalid normalized vocal entry");
    }
  }
  if (song.loudness) {
    if (song.loudness->size() > 16384) throw BudgetExceeded{};
    b.use(song.loudness->size());
    for (const auto& v : *song.loudness) { finite(v.value); finite(v.songTime); }
  }
  if (song.tonalComponentsResolved && song.tonality && (song.tonality->tonic > 11 || song.tonality->mode > 2))
    throw std::invalid_argument("Invalid resolved tonality");
  if (song.tonalComponentsResolved && song.melodicness) finite(*song.melodicness);
}
void validateBounds(const PlannerCandidateBounds& bounds) {
  const auto& p = bounds.placement;
  finite(p.minimumOutgoingStart); finite(p.minimumOutgoingEnd); finite(p.maximumIncomingEnd);
  for (const auto& range : {bounds.outgoingStarts, bounds.incomingStarts}) {
    finite(range.start); finite(range.end);
    if (range.end < range.start) throw std::invalid_argument("Reversed candidate start bounds");
  }
}
std::optional<std::int64_t> barCount(const PlannerRegionRef& ref, Budget& b) {
  const auto& s = ref.owner(); const auto r = ref.events();
  b.use(s.downbeatEvents.size() * 2 + 1);
  // 27222c8a0 is a dynamic endpoint cast, NOT nearest-downbeat snapping.
  if (!member(s.downbeatEvents, r.startEvent) || !member(s.downbeatEvents, r.endEvent)) return std::nullopt;
  const auto a = s.events[r.startEvent].downbeatIndex, z = s.events[r.endEvent].downbeatIndex;
  if (!a || !z) return std::nullopt;
  if (*z < *a) throw std::invalid_argument("Reversed candidate bar interval");
  return *z - *a;
}
std::optional<std::size_t> startDownbeat(const PlannerRegionRef& ref, Budget& b) {
  const auto& s = ref.owner(); const auto r = ref.events();
  b.use(s.downbeatEvents.size() * 2 + 1);
  if (member(s.downbeatEvents, r.startEvent) && member(s.downbeatEvents, r.endEvent)) return r.startEvent;
  // 272236920 -> 27221e200 scans BACKWARDS; 272236b2c tests beatIndex <= start.
  const auto first = s.events[r.startEvent].beatIndex;
  for (auto i = s.downbeatEvents.rbegin(); i != s.downbeatEvents.rend(); ++i) {
    b.use(); if (s.events[*i].beatIndex <= first) return *i;
  }
  return std::nullopt;
}
std::optional<PlannerTempo> startTempo(const PlannerRegionRef& ref, Budget& b) {
  const auto first = startDownbeat(ref, b);
  if (!first) return std::nullopt;
  const auto& s = ref.owner(); const auto ordinal = *s.events[*first].downbeatIndex;
  // 272218e64 uses [startDownbeat, endDownbeat-1], FIRST matching stable record.
  for (const auto& r : s.beatStabilityMap) {
    b.use();
    if (*s.events[r.events.startEvent].downbeatIndex <= ordinal &&
        ordinal < *s.events[r.events.endEvent].downbeatIndex)
      return PlannerTempo{r.averageTempoBpm, false};
  }
  return std::nullopt;
}
std::optional<PlannerVocalWindow> leadingWindow(const PlannerRegionRef& ref, Budget& b) {
  const auto& s = ref.owner(); const auto first = s.events[ref.events().startEvent].beatIndex;
  // 272237058 searches the beat at startOrdinal-4 and returns [0, its songTime].
  // Nonnegative normalized ordinal domain: missing earlier beat remains absent.
  if (first < 4) return std::nullopt;
  for (auto i : s.beatEvents) {
    b.use();
    if (s.events[i].beatIndex == first - 4) {
      if (s.events[i].songTime < 0) throw std::invalid_argument("Negative leading vocal end");
      return PlannerVocalWindow{0.0, s.events[i].songTime};
    }
  }
  return std::nullopt;
}
std::vector<std::uint8_t> vocalStrengths(const PlannerRegionRef& ref, const PlannerVocalMap& vocals,
                                     bool alternate, Budget& b) {
  const auto& s = ref.owner(); const auto range = ref.events();
  const auto a = s.events[range.startEvent].beatIndex, z = s.events[range.endEvent].beatIndex;
  std::vector<std::size_t> selected;
  std::size_t ordinal = 0;
  for (auto i : s.beatEvents) {
    b.use();
    if (a <= s.events[i].beatIndex && s.events[i].beatIndex <= z) {
      // 272236d10 retains even ENUMERATION indices, not even beat ordinals.
      if (!alternate || (ordinal & 1U) == 0) selected.push_back(i);
      ++ordinal;
    }
  }
  std::vector<std::uint8_t> out;
  if (selected.size() < 2) return out;
  out.reserve(selected.size() - 1);
  for (std::size_t i = 1; i < selected.size(); ++i) {
    b.use(2 * vocals.size() + 1); // charge validation + lookup in the existing helper
    out.push_back(plannerVocalStrength(vocals, {s.events[selected[i-1]].songTime,
                                              s.events[selected[i]].songTime}).value_or(5));
  }
  return out;
}
std::optional<PlannerRegionPairRef> shiftStyle12(const PlannerRegionPairRef& pair, Budget& b) {
  const auto& s = pair.incoming.owner(); const auto r = pair.incoming.events();
  const auto offset = plannerIncomingBeatBudget(16, pair.incomingScale);
  const auto a = s.events[r.startEvent].beatIndex, z = s.events[r.endEvent].beatIndex;
  if (z > std::numeric_limits<std::int64_t>::max() - offset)
    throw std::overflow_error("Candidate shifted ordinal overflow");
  std::optional<std::size_t> first, last;
  for (auto i : s.beatEvents) {
    b.use();
    if (!first && s.events[i].beatIndex == a + offset) first = i;
    if (!last && s.events[i].beatIndex == z + offset) last = i;
  }
  if (!first || !last) return std::nullopt;
  return PlannerRegionPairRef{pair.outgoing,
      PlannerRegionRef(s, {*first, *last}, PlannerRegionUnit::beats), pair.incomingScale};
}
bool placed(const PlannerRegionPairRef& p, const PlannerCandidateBounds& bounds) {
  const auto& a = p.outgoing.owner().events; const auto& z = p.incoming.owner().events;
  const auto ar = p.outgoing.events(), zr = p.incoming.events();
  PlannerStylingTimePair times{{a[ar.startEvent].songTime, a[ar.endEvent].songTime},
                               {z[zr.startEvent].songTime, z[zr.endEvent].songTime}};
  return plannerResolvedPlacementPasses(times, bounds.placement) &&
      bounds.outgoingStarts.start <= times.outgoing.start && times.outgoing.start <= bounds.outgoingStarts.end &&
      bounds.incomingStarts.start <= times.incoming.start && times.incoming.start <= bounds.incomingStarts.end;
}
PlannerCandidateFeatures features(const PlannerRegionPairRef& p, std::int64_t style,
    const PlannerCandidateSong& outgoing, const PlannerCandidateSong& incoming, Budget& b) {
  PlannerCandidateFeatures f;
  f.outgoingTempo = startTempo(p.outgoing, b); f.incomingTempo = startTempo(p.incoming, b);
  if (f.outgoingTempo && f.incomingTempo) {
    const auto normal = matchPlannerTempos(*f.outgoingTempo, *f.incomingTempo, kPlannerTempoTolerance);
    f.normalTempoTag = normal.sourceTag; f.scoreInputs.normalTempoCompatible = normal.compatible;
    // Source normal-only styles do not evaluate the expanded-tempo predicate.
    if (style == 12) {
      const auto expanded = matchPlannerTempos(*f.outgoingTempo, *f.incomingTempo, kPlannerExpandedTempoTolerance);
      f.expandedTempoTag = expanded.sourceTag; f.scoreInputs.expandedTempoCompatible = expanded.compatible;
    }
  }
  const auto oc = barCount(p.outgoing, b), ic = barCount(p.incoming, b);
  if (style == 8) f.scoreInputs.barCountRatio = plannerCandidateBarRatio(oc, ic, p.incomingScale);
  else f.scoreInputs.matchingBarCount = plannerCandidateMatchingBars(oc, ic, p.incomingScale);
  if (style == 9) {
    f.scoreInputs.tonalityCompatible = plannerTonalitiesCompatible(outgoing.tonality, incoming.tonality,
                                                                 outgoing.melodicness, incoming.melodicness);
    if (!outgoing.vocals || !incoming.vocals) f.scoreInputs.vocalRelationshipIncompatible = true;
    else {
      const auto a = vocalStrengths(p.outgoing, *outgoing.vocals, p.incomingScale == TempoBinaryScale::two, b);
      const auto z = vocalStrengths(p.incoming, *incoming.vocals, p.incomingScale == TempoBinaryScale::half, b);
      f.scoreInputs.vocalRelationshipIncompatible = plannerCandidateVocalConflict(a, z);
    }
  }
  if (incoming.vocals) {
    f.leadingVocalWindow = leadingWindow(p.incoming, b);
    b.use(2 * incoming.vocals->size() + 1);
    f.scoreInputs.leadingIncomingVocalSignificant = leadingIncomingVocalSignificant(incoming.vocals, f.leadingVocalWindow);
  }
  const auto& oe = p.outgoing.owner().events; const auto& ie = p.incoming.owner().events;
  const auto orng = p.outgoing.events(), irng = p.incoming.events();
  if (incoming.loudness) b.use(8 * incoming.loudness->size() + 1);
  f.scoreInputs.trailingIncomingLoudnessRatio = trailingIncomingLoudnessRatio(incoming.loudness, incoming.vocals != nullptr,
      {ie[irng.startEvent].songTime, ie[irng.endEvent].songTime});
  f.scoreInputs.tieBreakDelta = plannerCandidateEndDelta(oe[orng.endEvent].songTime, ie[irng.endEvent].songTime);
  return f;
}
std::uint64_t scoreRejections(std::int64_t style, const PlannerCandidateFeatures& f) {
  std::uint64_t mask = 0;
  auto add = [&](PlannerCandidateRejection r) { mask |= static_cast<std::uint64_t>(r); };
  const auto& in = f.scoreInputs;
  if (!f.outgoingTempo || !f.incomingTempo) add(PlannerCandidateRejection::tempoUnavailable);
  if (style == 12) {
    if (in.normalTempoCompatible) add(PlannerCandidateRejection::normalTempoMustFail);
    if (!in.expandedTempoCompatible) add(PlannerCandidateRejection::expandedTempoMismatch);
  } else if (!in.normalTempoCompatible) add(PlannerCandidateRejection::normalTempoMismatch);
  if (style == 8) {
    if (!in.barCountRatio) add(PlannerCandidateRejection::barRatioUnavailable);
  } else {
    if (!in.matchingBarCount) add(PlannerCandidateRejection::matchingBarsUnavailable);
    else if (*in.matchingBarCount < 8) add(PlannerCandidateRejection::fewerThanEightBars);
  }
  if (style == 9) {
    if (!in.tonalityCompatible) add(PlannerCandidateRejection::tonalityMismatch);
    if (in.vocalRelationshipIncompatible) add(PlannerCandidateRejection::vocalConflict);
  }
  if (!mask) add(PlannerCandidateRejection::finalScoreNonPositive);
  return mask;
}
} // namespace

std::optional<double> plannerCandidateBarRatio(std::optional<std::int64_t> out,
    std::optional<std::int64_t> in, TempoBinaryScale s) {
  scale(s); if (out) count(*out); if (in) count(*in);
  if (!out || !in) return std::nullopt;
  const auto v = scaledBars(*in, s);
  if (*out <= 0 || v <= 0) return std::nullopt;
  return static_cast<double>(v) / static_cast<double>(*out);
}
std::optional<std::int64_t> plannerCandidateMatchingBars(std::optional<std::int64_t> out,
    std::optional<std::int64_t> in, TempoBinaryScale s) {
  scale(s); if (out) count(*out); if (in) count(*in);
  if (!out || !in || *out != scaledBars(*in, s)) return std::nullopt;
  return *out;
}
double plannerCandidateEndDelta(double out, double in) {
  finite(out); finite(in); const double v = out - in; finite(v); return v;
}
bool plannerCandidateVocalConflict(const std::vector<std::uint8_t>& out,
                                   const std::vector<std::uint8_t>& in) {
  if (out.size() > kPlannerRegionAlgebraMaxEvents || in.size() > kPlannerRegionAlgebraMaxEvents)
    throw std::length_error("Candidate vocal vector limit");
  for (auto v : out) if (v > 5) throw std::invalid_argument("Invalid vocal strength tag");
  for (auto v : in) if (v > 5) throw std::invalid_argument("Invalid vocal strength tag");
  const auto n = std::min(out.size(), in.size());
  std::optional<std::uint8_t> maximum;
  for (std::size_t i = 0; i < n; ++i) {
    const auto a = out[out.size() - n + i], z = in[in.size() - n + i];
    if (a == 5 || z == 5) continue;
    const auto v = std::min(a, z);
    if (!maximum || *maximum < v) maximum = v;
  }
  return maximum && *maximum != 0;
}
PlannerCandidateSelection selectPlannerRegionCandidates(const PlannerCandidateSong& outgoing,
    const PlannerCandidateSong& incoming, const std::vector<PlannerCandidateSeed>& seeds,
    const std::vector<PlannerCandidateStyle>& styles, const PlannerCandidateBounds& bounds,
    std::uint64_t workBudget) {
  PlannerCandidateSelection result;
  validateBounds(bounds);
  if (seeds.size() > kPlannerCandidateMaxSeeds || styles.size() > kPlannerCandidateMaxStyles ||
      workBudget > kPlannerCandidateWorkBudget) {
    result.status = PlannerCandidateStatus::resourceLimit; return result;
  }
  if (!outgoing.structure || !incoming.structure) {
    result.status = PlannerCandidateStatus::insufficientStructure; return result;
  }
  for (const auto& style : styles) {
    if (style.suffixBars) count(*style.suffixBars);
    if (style.id == 9 && (!outgoing.tonalComponentsResolved || !incoming.tonalComponentsResolved)) {
      // Never claim a winner while a competing requested style cannot be evaluated.
      result.status = PlannerCandidateStatus::unresolvedTonalityBinding; return result;
    }
  }
  Budget b{workBudget};
  try {
    validateSong(outgoing, b); validateSong(incoming, b);
    std::vector<double> scores;
    std::vector<PlannerScoredCandidate> candidates;
    for (std::size_t si = 0; si < seeds.size(); ++si) {
      const auto& seed = seeds[si]; scale(seed.incomingScale);
      // Charge the existing region-algebra validations conservatively. The
      // scan bound is independent of any Kotlin/diagnostic preview limit.
      b.use(16 * (outgoing.structure->events.size() + incoming.structure->events.size() +
          outgoing.structure->beatEvents.size() + incoming.structure->beatEvents.size() +
          outgoing.structure->downbeatEvents.size() + incoming.structure->downbeatEvents.size()) + 1);
      const PlannerRegionPairRef original{
          PlannerRegionRef(*outgoing.structure, seed.outgoing, PlannerRegionUnit::bars),
          PlannerRegionRef(*incoming.structure, seed.incoming, seed.incomingUnit), seed.incomingScale};
      for (std::size_t ti = 0; ti < styles.size(); ++ti) {
        ++result.attempted; const auto& style = styles[ti];
        if (style.id != 8 && style.id != 9 && style.id != 12) {
          ++result.unsupportedStyles;
          result.rejectionReasons |= static_cast<std::uint64_t>(PlannerCandidateRejection::unsupportedStyle);
          continue;
        }
        b.use(128 * (outgoing.structure->events.size() + incoming.structure->events.size() +
            outgoing.structure->beatEvents.size() + incoming.structure->beatEvents.size() +
            outgoing.structure->downbeatEvents.size() + incoming.structure->downbeatEvents.size()) + 1);
        auto pair = plannerStyleRegionPair(original, style.suffixBars);
        if (pair && style.id == 12) pair = shiftStyle12(*pair, b);
        if (!pair) {
          ++result.regionMisses;
          result.rejectionReasons |= static_cast<std::uint64_t>(PlannerCandidateRejection::regionUnavailable);
          continue;
        }
        if (!placed(*pair, bounds)) {
          ++result.placementMisses;
          result.rejectionReasons |= static_cast<std::uint64_t>(PlannerCandidateRejection::placement);
          continue;
        }
        const auto f = features(*pair, style.id, outgoing, incoming, b);
        const auto score = beatMatchedStyleScore(style.id, f.scoreInputs);
        if (!score) throw std::logic_error("Supported style lacks native scorer");
        ++result.constructed;
        if (!(*score > 0)) {
          ++result.nonPositive;
          result.rejectionReasons |= scoreRejections(style.id, f);
        }
        const auto orng = pair->outgoing.events(), irng = pair->incoming.events();
        const auto& oe = outgoing.structure->events; const auto& ie = incoming.structure->events;
        candidates.push_back({si, ti, style.id, orng, irng, seed.incomingScale,
            oe[orng.startEvent].songTime, oe[orng.endEvent].songTime,
            ie[irng.startEvent].songTime, ie[irng.endEvent].songTime, *score, f});
        scores.push_back(*score);
      }
    }
    result.completeForProvidedSeeds = true;
    if (const auto winner = bestPlannerCandidate(scores)) {
      result.winner = candidates[*winner]; result.status = PlannerCandidateStatus::selected;
    }
  } catch (const BudgetExceeded&) {
    result = {}; result.status = PlannerCandidateStatus::resourceLimit;
  }
  return result;
}
} // namespace lmg::automix
