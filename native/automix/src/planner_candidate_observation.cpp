#include "lmg/automix/planner_candidate_observation.h"
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
namespace {
PlannerCandidateSelection status(PlannerCandidateStatus s) {
  PlannerCandidateSelection result; result.status=s; return result;
}
bool invalid(const PlannerSongPreparation& p) {
  if (static_cast<unsigned>(p.status)>3) throw std::invalid_argument("Unknown preparation status");
  // The diagnostic preparation can retain source-order reversals. Selection
  // rejects them rather than silently sorting or choosing across them.
  return p.status==PlannerPreparationStatus::invalid ||
    (p.inventory && (p.inventory->malformedRegions || p.inventory->reversedTimes));
}
PlannerCandidateSong view(const PlannerSongPreparation& p, const PlannerCandidateTonalBinding& t) {
  return {p.maps.structure?&*p.maps.structure:nullptr, p.maps.vocals?&*p.maps.vocals:nullptr,
    p.maps.loudness?&*p.maps.loudness:nullptr, t.tonality,t.melodicness,t.resolved};
}
void seedWithinTrack(const SongStructure& s, StructureRegion r, double duration) {
  if(r.startEvent>=s.events.size()||r.endEvent>=s.events.size())
    throw std::invalid_argument("Seed endpoint outside prepared structure");
  const auto a=s.events[r.startEvent].songTime,z=s.events[r.endEvent].songTime;
  if(!std::isfinite(a)||!std::isfinite(z)||a<0||z<a||z>duration)
    throw std::invalid_argument("Seed outside resolved playback track");
}
}
PlannerCandidateSelection selectPreparedPlannerCandidates(
    const PlannerSongPreparation& outgoing, const PlannerSongPreparation& incoming,
    const PlannerCandidateTonalBinding& outgoingTonal,
    const PlannerCandidateTonalBinding& incomingTonal,
    const std::vector<PlannerCandidateSeed>& seeds,
    const std::vector<PlannerCandidateStyle>& styles,
    const PlannerCandidateBounds& bounds, std::uint64_t workBudget) {
  if(invalid(outgoing)||invalid(incoming)) return status(PlannerCandidateStatus::invalidPreparation);
  if(outgoing.status==PlannerPreparationStatus::resourceLimit||incoming.status==PlannerPreparationStatus::resourceLimit ||
      seeds.size()>kPlannerCandidateMaxSeeds||styles.size()>kPlannerCandidateMaxStyles)
    return status(PlannerCandidateStatus::resourceLimit);
  if(!outgoing.durationMs||!incoming.durationMs)
    return status(PlannerCandidateStatus::unresolvedPlaybackDuration);
  for(const auto d:{*outgoing.durationMs,*incoming.durationMs})
    if(d<=0||d>9007199254740991LL)throw std::invalid_argument("Invalid resolved playback duration");
  if(!outgoing.maps.structure||!incoming.maps.structure)
    return status(PlannerCandidateStatus::insufficientStructure);
  const double outSeconds=static_cast<double>(*outgoing.durationMs)/1000.0;
  const double inSeconds=static_cast<double>(*incoming.durationMs)/1000.0;
  // No hidden clipping: the context must already express a legal incoming
  // maximum. The style12 shift is checked against this bound by the selector.
  if(!std::isfinite(bounds.placement.maximumIncomingEnd)||bounds.placement.maximumIncomingEnd>inSeconds)
    throw std::invalid_argument("Incoming constraint exceeds resolved track");
  for(const auto& seed:seeds) {
    seedWithinTrack(*outgoing.maps.structure,seed.outgoing,outSeconds);
    seedWithinTrack(*incoming.maps.structure,seed.incoming,inSeconds);
  }
  return selectPlannerRegionCandidates(view(outgoing,outgoingTonal),view(incoming,incomingTonal),
                                       seeds,styles,bounds,workBudget);
}
} // namespace lmg::automix
