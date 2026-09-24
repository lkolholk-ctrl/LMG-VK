#include "lmg/automix/planner_source_context.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <set>
#include <stdexcept>

namespace lmg::automix {
namespace {
using Status = PlannerSourceContextStatus;
std::int64_t bits(double v) { std::int64_t w; std::memcpy(&w,&v,sizeof(w)); return w; }
double real(std::int64_t w) { double v; std::memcpy(&v,&w,sizeof(v)); return v; }
void need(bool ok) { if(!ok) throw std::invalid_argument("Invalid source-context contract"); }
PlannerSourceScopeResolution failed(Status s) { return {s,{}}; }
bool validDuration(std::int64_t d) { return d>0 && d<=9007199254740991LL; }
bool validCloudDuration(double d) {
  return std::isfinite(d) && d>0 && d<=9007199254740991.0 && std::trunc(d)==d;
}
bool validKnowledge(PlannerSourceKnowledge v) { return static_cast<unsigned>(v)<=2; }
void encodeDuration(std::vector<std::int64_t>& w, std::size_t at,
                    std::optional<std::int64_t> value, std::int64_t flag) {
  if(value) { w[6]|=flag; w[at]=*value; }
}
PlannerSourceContextStatus preflight(const PlannerSourceContextRequest& r) {
  const auto& c=r.context;
  if(r.generation<0 || r.revision<=0 || static_cast<unsigned>(c.maximumComplexity)>3 ||
     static_cast<unsigned>(c.upperEligibility)>2 ||
     !validKnowledge(c.outgoingSpatial)||!validKnowledge(c.incomingSpatial)||
     !validKnowledge(c.outgoingPreviousState)||!validKnowledge(c.incomingPreviousState))
    return Status::invalidInput;
  // The provider's unresolved/denied upper gate cannot be overridden by music metadata.
  if(c.upperEligibility==PlannerBeatMatchedEligibility::unresolved) return Status::eligibilityUnresolved;
  if(c.upperEligibility==PlannerBeatMatchedEligibility::denied ||
     c.maximumComplexity<PlannerAlgorithm::beatMatched) return Status::ineligible;
  if(r.workBudget==0 || r.workBudget>kPlannerProducerWorkBudget) return Status::resourceLimit;
  for(auto v:{c.outgoingSpatial,c.incomingSpatial,c.outgoingPreviousState,c.incomingPreviousState})
    if(v==PlannerSourceKnowledge::unknown) return Status::sourceFactsUnknown;
  if(c.outgoingSpatial==PlannerSourceKnowledge::present || c.incomingSpatial==PlannerSourceKnowledge::present)
    return Status::spatialUnsupported;
  if(c.outgoingPreviousState==PlannerSourceKnowledge::present || c.incomingPreviousState==PlannerSourceKnowledge::present)
    return Status::previousStateUnsupported;
  if((r.outgoingDurationMs && !validDuration(*r.outgoingDurationMs)) ||
     (r.incomingDurationMs && !validDuration(*r.incomingDurationMs))) return Status::invalidInput;
  if(!r.outgoingDurationMs || !r.incomingDurationMs) return Status::playbackDurationMissing;
  return Status::resolved;
}
}

PlannerSourceContextStatus plannerSourceContextPreflight(const PlannerSourceContextRequest& r) { return preflight(r); }

std::vector<PlannerCandidateStyle> bindPlannerCatalogFields(const std::vector<TransitionStyle>& decoded) {
  need(decoded.size()<=14);
  std::set<int> ids;
  std::vector<PlannerCandidateStyle> result;
  result.reserve(decoded.size());
  for(const auto& s:decoded) {
    need(s.id>=0 && ids.insert(s.id).second && (!s.duration || *s.duration>=0));
    // Deliberately NOT value_or(4): absence and explicit zero remain distinguishable.
    result.push_back({s.id,s.duration?std::optional<std::int64_t>(*s.duration):std::nullopt});
  }
  return result;
}

PlannerMusicKitProviderBounds plannerMusicKitProviderBounds(double reference) {
  const auto envelope=plannerMusicKitDurationEnvelope(reference);
  // Same operand order as 272220344 / 272220350; getters return fields unchanged.
  return {reference-envelope.preferredOffset,
          reference-envelope.maximumOutgoingDuration,envelope.maximumIncomingDuration};
}

PlannerSourceScopeResolution resolvePlannerSourceScope(const PlannerSourceContextRequest& r,
    const CloudSongAnalysis& outgoing, const CloudSongAnalysis& incoming,
    const std::vector<TransitionStyle>& catalog) {
  if(const auto status=preflight(r); status!=Status::resolved) return failed(status);
  if(!outgoing.durationInMillis || !incoming.durationInMillis) return failed(Status::referenceDurationMissing);
  const double outRef=*outgoing.durationInMillis, inRef=*incoming.durationInMillis;
  if(!validCloudDuration(outRef)||!validCloudDuration(inRef)) return failed(Status::invalidInput);
  // Do not clamp, scale, overwrite catalog metadata or relabel actual playback time.
  if(outRef!=static_cast<double>(*r.outgoingDurationMs) || inRef!=static_cast<double>(*r.incomingDurationMs))
    return failed(Status::durationMappingRequired);
  const double outSeconds=outRef/1000.0, inSeconds=inRef/1000.0;
  const auto outgoingRange=resolvePlannerOutgoingCriteria(outSeconds,r.context.outgoingCriteria);
  const auto incomingRange=resolvePlannerIncomingCriteria(inSeconds,r.context.incomingCriteria);
  if(outgoingRange.status!=PlannerCriteriaResolution::resolved || incomingRange.status!=PlannerCriteriaResolution::resolved)
    return failed(Status::criteriaRejected);
  std::vector<PlannerCandidateStyle> records;
  try { records=bindPlannerCatalogFields(catalog); }
  catch(const std::invalid_argument&) { return failed(Status::catalogRejected); }
  const auto& profile=plannerDefaultBeatMatchedStyleIds();
  const std::vector<std::int64_t> requested(profile.begin(),profile.end());
  const auto selected=resolvePlannerStyleRequests(requested,records);
  if(selected.status!=PlannerStyleResolutionStatus::resolved) return failed(Status::catalogRejected);
  const auto outBounds=plannerMusicKitProviderBounds(outSeconds);
  const auto inBounds=plannerMusicKitProviderBounds(inSeconds);
  const auto a=*outgoingRange.range, b=*incomingRange.range;
  // 27222f54c uses outgoing getter +0x18 for END discovery; placement uses it
  // for START and +0x20 for END. Keeping those roles distinct matters.
  const std::array<double,9> geometry{
    outBounds.minimumOutgoingStart,inBounds.maximumIncomingEnd,
    outBounds.minimumOutgoingStart,outBounds.minimumOutgoingEnd,inBounds.maximumIncomingEnd,
    a.lower,a.upper,b.lower,b.upper};
  std::vector<std::int64_t> w(24+requested.size()+records.size()*3,0);
  w[0]=kPlannerBindingRequestMagic; w[1]=1; w[2]=static_cast<std::int64_t>(w.size());
  w[3]=r.generation; w[4]=r.revision; w[5]=1;
  encodeDuration(w,7,r.outgoingDurationMs,1); encodeDuration(w,8,r.incomingDurationMs,2);
  w[9]=static_cast<std::int64_t>(r.workBudget); w[10]=static_cast<std::int64_t>(r.context.upperEligibility);
  w[11]=static_cast<std::int64_t>(requested.size()); w[12]=static_cast<std::int64_t>(records.size());
  for(std::size_t i=0;i<geometry.size();++i) w[13+i]=bits(geometry[i]);
  std::size_t at=24;
  for(auto id:requested) w[at++]=id;
  for(const auto& d:records) { w[at++]=d.id; w[at++]=d.suffixBars?1:0; w[at++]=d.suffixBars.value_or(0); }
  // One authority for the existing resolved transport/domain; no parallel selector.
  (void)decodePlannerSelectionBinding(w);
  return {Status::resolved,std::move(w)};
}

PlannerSourceContextRequest decodePlannerSourceContext(const std::vector<std::int64_t>& w) {
  need(w.size()==kPlannerSourceRequestWords && w[0]==kPlannerSourceRequestMagic && w[1]==1 && w[2]==24);
  need(w[3]>=0 && w[4]>0 && w[5]>=0 && w[5]<=3 && w[9]>=0 &&
       w[9]<=static_cast<std::int64_t>(kPlannerProducerWorkBudget));
  need((w[5]&1)?validDuration(w[6]):w[6]==0);
  need((w[5]&2)?validDuration(w[7]):w[7]==0);
  need(w[8]==0 && w[21]==0 && w[22]==0 && w[23]==0);
  need(w[10]>=0 && w[10]<=2 && w[20]>=0 && w[20]<=3);
  for(std::size_t i=11;i<=14;++i) need(w[i]>=0 && w[i]<=2);
  PlannerSourceContextRequest r;
  r.generation=w[3]; r.revision=w[4]; r.workBudget=static_cast<std::uint64_t>(w[9]);
  if(w[5]&1)r.outgoingDurationMs=w[6];
  if(w[5]&2)r.incomingDurationMs=w[7];
  auto& c=r.context;
  c.upperEligibility=static_cast<PlannerBeatMatchedEligibility>(w[10]);
  c.outgoingSpatial=static_cast<PlannerSourceKnowledge>(w[11]);c.incomingSpatial=static_cast<PlannerSourceKnowledge>(w[12]);
  c.outgoingPreviousState=static_cast<PlannerSourceKnowledge>(w[13]);c.incomingPreviousState=static_cast<PlannerSourceKnowledge>(w[14]);
  c.maximumComplexity=static_cast<PlannerAlgorithm>(w[20]);
  need(w[15]>=0&&w[15]<=2&&w[17]>=0&&w[17]<=2);
  if(w[15]==0)c.outgoingCriteria=PlannerOutgoingEarlyAfter{real(w[16])};
  else if(w[15]==1)c.outgoingCriteria=PlannerOutgoingLateAfter{real(w[16])};
  else { need(w[16]==0);c.outgoingCriteria=PlannerOutgoingLateInSong{}; }
  if(w[17]==0) { need(w[19]==0);c.incomingCriteria=PlannerCriteriaAfter{real(w[18])}; }
  else if(w[17]==1)c.incomingCriteria=PlannerCriteriaWithin{real(w[18]),real(w[19])};
  else { need(w[18]==0&&w[19]==0);c.incomingCriteria=PlannerCriteriaInSong{}; }
  // Invalid finite-domain Criteria are also checked by source resolvers below.
  // Reject NaN/Inf here even when an earlier policy guard would have short-circuited.
  for(auto i:{16,18,19}) need(std::isfinite(real(w[i])) && real(w[i])>=0);
  if(w[17]==1)need(real(w[18])<=real(w[19]));
  return r;
}

std::vector<std::int64_t> observePlannerSourceContext(const std::vector<std::int64_t>& input,
    const CloudSongAnalysis& out,const CloudSongAnalysis& in,const std::vector<TransitionStyle>& catalog) {
  const auto request=decodePlannerSourceContext(input);
  auto scope=resolvePlannerSourceScope(request,out,in,catalog);
  const auto& profile=plannerDefaultBeatMatchedStyleIds();
  std::vector<std::int64_t> result{ kPlannerSourceResponseMagic,1,12,request.generation,request.revision,
    static_cast<std::int64_t>(scope.status),0,0,3,profile[0],profile[1],profile[2] };
  if(scope.status!=Status::resolved) return result;
  const auto selected=observePlannerSelectionBinding(scope.resolvedRequest,out,in);
  need(selected.size()==kPlannerBindingResponseWords);
  result[6]=static_cast<std::int64_t>(scope.resolvedRequest.size());result[7]=static_cast<std::int64_t>(selected.size());
  result.insert(result.end(),scope.resolvedRequest.begin(),scope.resolvedRequest.end());
  result.insert(result.end(),selected.begin(),selected.end());result[2]=static_cast<std::int64_t>(result.size());
  need(result.size()<=kPlannerSourceMaxResponse);
  return result;
}
} // namespace lmg::automix
