#include "lmg/automix/planner_selection_binding.h"
#include "lmg/automix/planner_source_bindings.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
namespace {
void require(bool ok) { if (!ok) throw std::invalid_argument("Invalid observation binding contract"); }
double real(std::int64_t w) {
  double v; static_assert(sizeof(v) == sizeof(w)); std::memcpy(&v, &w, sizeof(v));
  require(std::isfinite(v) && v >= 0); return v;
}
std::int64_t bits(double v) {
  if (!std::isfinite(v)) throw std::logic_error("Nonfinite observation result");
  std::int64_t w; std::memcpy(&w, &v, sizeof(w)); return w;
}
std::int64_t count(std::size_t v, std::size_t maximum) {
  if (v > maximum) throw std::logic_error("Observation result exceeds bounded domain");
  return static_cast<std::int64_t>(v);
}
std::vector<std::int64_t> empty(const PlannerSelectionBindingRequest& q) {
  std::vector<std::int64_t> w(kPlannerBindingResponseWords);
  w[0]=kPlannerBindingResponseMagic; w[1]=2; w[2]=static_cast<std::int64_t>(w.size());
  w[3]=q.generation; w[4]=q.revision; w[5]=q.explicitResolvedScope?1:0;
  // The source default profile is known; catalog mapping and full context are not.
  // This is LMG observation completeness, never a playback fallback decision.
  w[7]=q.explicitResolvedScope?0:static_cast<std::int64_t>(kPlannerRemainingDefaultBindings);
  const auto& profile=plannerDefaultBeatMatchedStyleIds();
  w[44]=static_cast<std::int64_t>(profile.size());
  std::copy(profile.begin(),profile.end(),w.begin()+45);
  w[27]=-1; w[28]=-1; w[29]=-1; w[30]=-1;
  w[41]=-1; w[42]=-1;
  return w;
}
}
PlannerSelectionBindingRequest decodePlannerSelectionBinding(const std::vector<std::int64_t>& w) {
  require(w.size()>=kPlannerBindingRequestHeader && w.size()<=kPlannerBindingMaxRequest);
  require(w[0]==kPlannerBindingRequestMagic && w[1]==1 && w[2]==static_cast<std::int64_t>(w.size()));
  require(w[3]>=0 && w[4]>=0 && (w[5]==0 || w[5]==1));
  require(w[6]>=0 && w[6]<=3 && w[9]>=0 && w[9]<=static_cast<std::int64_t>(kPlannerProducerWorkBudget));
  require(w[10]>=0 && w[10]<=2 && w[11]>=0 && w[11]<=14 && w[12]>=0 && w[12]<=14);
  require(w.size()==24+static_cast<std::size_t>(w[11])+3*static_cast<std::size_t>(w[12]));
  require(w[22]==0 && w[23]==0);
  PlannerSelectionBindingRequest q;
  q.generation=w[3]; q.revision=w[4]; q.explicitResolvedScope=w[5]==1;
  q.workBudget=static_cast<std::uint64_t>(w[9]);
  q.eligibility=static_cast<PlannerBeatMatchedEligibility>(w[10]);
  for (unsigned i=0;i<2;++i) {
    if (w[6] & (1LL<<i)) {
      require(w[7+i]>0 && w[7+i]<=9007199254740991LL);
      (i==0?q.outgoingDurationMs:q.incomingDurationMs)=w[7+i];
    } else require(w[7+i]==0);
  }
  if (!q.explicitResolvedScope) {
    require(q.revision==0 && w[10]==0 && w[11]==0 && w[12]==0);
    for (std::size_t i=13;i<22;++i) require(w[i]==0);
    return q;
  }
  require(q.revision>0);
  double d[9]; for (std::size_t i=0;i<9;++i) d[i]=real(w[13+i]);
  require(d[5]<=d[6] && d[7]<=d[8]);
  q.context={{d[0],d[1]},{{d[2],d[3],d[4]},{d[5],d[6]},{d[7],d[8]}}};
  std::size_t p=24;
  for (std::int64_t i=0;i<w[11];++i) {
    require(w[p]>=0 && w[p]<=std::numeric_limits<int>::max()); q.requestedIds.push_back(w[p++]);
  }
  for (std::int64_t i=0;i<w[12];++i) {
    const auto id=w[p++], present=w[p++], bars=w[p++];
    require(id>=0 && id<=std::numeric_limits<int>::max() && (present==0 || present==1));
    require(present==1?bars>=0:bars==0);
    require(std::none_of(q.catalog.begin(),q.catalog.end(),[id](const auto& s){return s.id==id;}));
    q.catalog.push_back({id,present==1?std::optional<std::int64_t>(bars):std::nullopt});
  }
  return q;
}
std::vector<std::int64_t> observePlannerSelectionBinding(const std::vector<std::int64_t>& request,
    const CloudSongAnalysis& outgoing, const CloudSongAnalysis& incoming) {
  const auto q=decodePlannerSelectionBinding(request);
  if (!q.explicitResolvedScope) return empty(q);
  const auto r=observeProducedMusicKitCandidates(outgoing,incoming,
      q.outgoingDurationMs,q.incomingDurationMs,q.requestedIds,q.catalog,q.context,q.eligibility,q.workBudget);
  return encodePlannerSelectionBindingResult(request,r);
}
std::vector<std::int64_t> encodePlannerSelectionBindingResult(const std::vector<std::int64_t>& request,
    const PlannerProducedObservation& r) {
  const auto q=decodePlannerSelectionBinding(request);
  require(q.explicitResolvedScope);
  auto w=empty(q);
  w[6]=1+static_cast<std::int64_t>(r.status);
  w[8]=r.completeForResolvedScope?1:0;
  if (!r.completeForResolvedScope) {
    if (r.selection || !r.production.seeds.empty()) throw std::logic_error("Provisional selection escaped");
    return w;
  }
  if (r.status!=PlannerProducedStatus::observed || !r.selection || !r.selection->completeForProvidedSeeds)
    throw std::logic_error("Incomplete composed observation");
  const auto& p=r.production; const auto& s=*r.selection;
  w[11]=count(p.seeds.size(),64);
  const std::size_t counts[]={p.outgoingEnds,p.incomingEnds,p.outgoingStable,p.incomingStable,
      p.outgoingNonSilent,p.pairAttempts,p.scaleMisses,p.truncationMisses};
  for (std::size_t i=0;i<8;++i) w[12+i]=count(counts[i],i<5?4096:64);
  const std::size_t sc[]={s.attempted,s.constructed,s.unsupportedStyles,s.regionMisses,s.placementMisses,s.nonPositive};
  for (std::size_t i=0;i<6;++i) w[20+i]=count(sc[i],64*14);
  if (s.rejectionReasons>8191 || p.workUnits>q.workBudget) throw std::logic_error("Invalid result counters");
  w[26]=static_cast<std::int64_t>(s.rejectionReasons);
  w[27]=static_cast<std::int64_t>(s.status); w[43]=static_cast<std::int64_t>(p.workUnits);
  if (s.winner) {
    if (s.status!=PlannerCandidateStatus::selected) throw std::logic_error("Inconsistent winner");
    const auto& c=*s.winner;
    w[10]=1; w[28]=c.styleId; w[29]=count(c.seedIndex,63); w[30]=count(c.styleIndex,13);
    w[31]=count(c.outgoing.startEvent,4095);w[32]=count(c.outgoing.endEvent,4095);
    w[33]=count(c.incoming.startEvent,4095);w[34]=count(c.incoming.endEvent,4095);
    w[35]=static_cast<std::int64_t>(c.incomingScale);
    w[36]=bits(c.outgoingStart);w[37]=bits(c.outgoingEnd);
    w[38]=bits(c.incomingStart);w[39]=bits(c.incomingEnd);w[40]=bits(c.score);
    w[41]=c.features.normalTempoTag;w[42]=c.features.expandedTempoTag;
  } else if (s.status!=PlannerCandidateStatus::noPositiveCandidate) throw std::logic_error("Unexpected complete result");
  return w;
}
} // namespace lmg::automix
