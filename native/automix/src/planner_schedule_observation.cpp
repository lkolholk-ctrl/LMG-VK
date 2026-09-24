#include "lmg/automix/planner_schedule_observation.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <stdexcept>

namespace lmg::automix {
namespace {
void need(bool ok) { if(!ok) throw std::logic_error("Invalid compiled schedule transport"); }
std::int64_t bits(double v) {
  need(std::isfinite(v)); std::int64_t w; std::memcpy(&w,&v,sizeof(w)); return w;
}
std::int64_t count(std::size_t n,std::size_t maximum) {
  need(n<=maximum);return static_cast<std::int64_t>(n);
}
std::int64_t descriptorIndex(const EffectParameterDescriptor& p) {
  const auto& catalog=allEffectParameters();
  for(std::size_t i=0;i<catalog.size();++i) {
    const auto& c=catalog[i];
    if(c.id==p.id && c.styleParameterId==p.styleParameterId && c.minimum==p.minimum &&
       c.maximum==p.maximum && c.defaultValue==p.defaultValue)return static_cast<std::int64_t>(i);
  }
  throw std::logic_error("Unknown complete schedule descriptor");
}
std::vector<std::int64_t> sourceEnvelope(const PlannerSourceContextRequest& q,
    const PlannerSourceScopeResolution& scope,const std::vector<std::int64_t>& selection) {
  const auto& p=plannerDefaultBeatMatchedStyleIds();
  std::vector<std::int64_t> w{kPlannerSourceResponseMagic,1,12,q.generation,q.revision,
    static_cast<std::int64_t>(scope.status),0,0,3,p[0],p[1],p[2]};
  if(scope.status==PlannerSourceContextStatus::resolved) {
    need(selection.size()==kPlannerBindingResponseWords);
    w[6]=count(scope.resolvedRequest.size(),kPlannerBindingMaxRequest);w[7]=48;
    w.insert(w.end(),scope.resolvedRequest.begin(),scope.resolvedRequest.end());
    w.insert(w.end(),selection.begin(),selection.end());w[2]=count(w.size(),kPlannerSourceMaxResponse);
  } else need(scope.resolvedRequest.empty()&&selection.empty());
  return w;
}
std::vector<std::int64_t> envelope(const PlannerSourceContextRequest& q,PlannerScheduleStatus status,
    const std::vector<std::int64_t>& source,const std::vector<std::int64_t>& plan={}) {
  need(source.size()>=12&&source.size()<=kPlannerSourceMaxResponse);
  need((status==PlannerScheduleStatus::compiled)==!plan.empty());
  std::vector<std::int64_t> w{kPlannerScheduleEnvelopeMagic,1,0,q.generation,q.revision,
    static_cast<std::int64_t>(status),count(source.size(),140),count(plan.size(),kPlannerScheduleMaxWords),0,0};
  w.insert(w.end(),source.begin(),source.end());w.insert(w.end(),plan.begin(),plan.end());
  w[2]=count(w.size(),kPlannerScheduleMaxWords);return w;
}
}
std::vector<std::int64_t> encodePlannerTransitionSchedule(const PlannerTransitionSchedule& s) {
  const auto& a=s.outgoing();const auto& b=s.incoming();
  std::vector<std::int64_t> w(kPlannerSchedulePlanHeader,0);
  w[0]=kPlannerSchedulePlanMagic;w[1]=1;w[3]=s.styleId();w[4]=static_cast<std::int64_t>(s.incomingScale());
  w[5]=count(a.events.startEvent,4095);w[6]=count(a.events.endEvent,4095);
  w[7]=count(b.events.startEvent,4095);w[8]=count(b.events.endEvent,4095);
  w[9]=a.beatCount;w[10]=b.beatCount;w[11]=bits(s.score());
  const double values[]{a.sourceRegion.begin,a.sourceRegion.end,b.sourceRegion.begin,b.sourceRegion.end,
    s.scaledBeatRatio(),s.effectiveIncomingDuration(),a.rates.start,a.rates.end,b.rates.start,b.rates.end,
    s.transitionRange().begin,s.transitionRange().end,s.referenceTransitionTime(),
    a.playbackTransitionRange.begin,a.playbackTransitionRange.end,
    b.playbackTransitionRange.begin,b.playbackTransitionRange.end,
    a.startPlaybackSongTime,b.startPlaybackSongTime,a.stretchedRegionDuration,b.stretchedRegionDuration};
  for(std::size_t i=0;i<21;++i)w[12+i]=bits(values[i]);
  w[33]=count(a.automations.size(),32);w[34]=count(b.automations.size(),32);
  w[36]=0;w[37]=1; // Non-executable; unquantized song-time coordinates.
  std::size_t points=0;
  for(const auto* side:{&a,&b}) for(const auto& automation:side->automations) {
    w.push_back(descriptorIndex(automation.parameter));w.push_back(count(automation.points.size(),4));
    for(const auto& p:automation.points) {
      need(p.curve && *p.curve<=0xfb);w.push_back(bits(p.value));w.push_back(bits(p.songTime));w.push_back(*p.curve);++points;
    }
  }
  w[35]=count(points,256);w[2]=count(w.size(),kPlannerScheduleMaxWords-150);return w;
}
std::vector<std::int64_t> observePlannerScheduledSource(const std::vector<std::int64_t>& input,
    const CloudSongAnalysis& out,const CloudSongAnalysis& in,const std::vector<TransitionStyle>& catalog) {
  const auto q=decodePlannerSourceContext(input);
  const auto scope=resolvePlannerSourceScope(q,out,in,catalog);
  if(scope.status!=PlannerSourceContextStatus::resolved)
    return envelope(q,PlannerScheduleStatus::sourceRejected,sourceEnvelope(q,scope,{}));
  const auto resolved=decodePlannerSelectionBinding(scope.resolvedRequest);
  // One seed search/score selection. Both the old report and new compiler use
  // this exact result; no Kotlin candidate or manually supplied windows/rates.
  const auto produced=observeProducedMusicKitCandidates(out,in,resolved.outgoingDurationMs,
      resolved.incomingDurationMs,resolved.requestedIds,resolved.catalog,resolved.context,
      resolved.eligibility,resolved.workBudget);
  const auto source=sourceEnvelope(q,scope,encodePlannerSelectionBindingResult(scope.resolvedRequest,produced));
  if(!produced.completeForResolvedScope || !produced.selection || !produced.selection->winner)
    return envelope(q,PlannerScheduleStatus::selectionUnavailable,source);
  const auto& c=*produced.selection->winner;
  const auto style=std::find_if(catalog.begin(),catalog.end(),[&](const auto& s){return s.id==c.styleId;});
  if(style==catalog.end() || (c.styleId!=8&&c.styleId!=9&&c.styleId!=12))
    return envelope(q,PlannerScheduleStatus::unsupportedStyle,source);
  try {
    // The old composition intentionally does not retain map ownership. Rebuild
    // bounded deterministic maps from the SAME immutable native cloud objects,
    // then verify winner event/time identity. No second candidate search occurs.
    const auto a=preparePlannerSongObservation(out,q.outgoingDurationMs);
    const auto b=preparePlannerSongObservation(in,q.incomingDurationMs);
    if(!a.maps.structure || !b.maps.structure)
      return envelope(q,PlannerScheduleStatus::invalidSchedule,source);
    const auto schedule=compilePlannerTransitionSchedule(c,*a.maps.structure,*b.maps.structure,*style);
    return envelope(q,PlannerScheduleStatus::compiled,source,encodePlannerTransitionSchedule(schedule));
  } catch(const std::invalid_argument&) {
    return envelope(q,PlannerScheduleStatus::invalidSchedule,source);
  }
}
} // namespace lmg::automix
