#include "lmg/automix/planner_produced_observation.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

using namespace lmg::automix;
namespace {
int passed=0;
void check(bool value){if(!value)throw std::runtime_error("produced observation assertion");}
template<class F> void test(const char* name,F f){try{f();++passed;}catch(...){std::cerr<<"FAILED "<<name<<'\n';throw;}}
template<class F> void rejects(F f){bool bad=false;try{f();}catch(const std::invalid_argument&){bad=true;}check(bad);}
CloudSongAnalysis cloud(const char* id,double bpm=120.0,int bars=60,int meter=4){
  CloudSongAnalysis s;s.id=id;s.durationInMillis=120000;s.supportsSmartTransitions=true;
  CloudAudioAnalysis a;a.key=CloudComposite<CloudKey>{CloudKey{"C","minor"},CloudKey{"A","major"},CloudKey{"B","major"}};
  a.vocalActivity=std::vector<CloudVocalActivity>{};
  a.melodicness=CloudComposite<double>{.8,.1,.2};
  CloudBpm distractor;distractor.values.main=7;a.bpm=distractor;
  a.beats=CloudBeats{std::vector<double>{33,45},std::vector<double>{777}};
  s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"audio",a};
  CloudVideoEvents v;v.timeInSeconds.emplace();v.score.emplace();
  for(int i=0;i<=bars*meter;++i){v.timeInSeconds->push_back(i*(60.0/bpm));
    v.score->push_back(i%(4*meter)==0?800:i%(2*meter)==0?600:i%meter==0?400:200);}
  CloudFlexAnalysis f;f.videoEvents=v;s.flex=CloudAnalysisResource<CloudFlexAnalysis>{"flex",f};return s;
}
struct Fixture {
  CloudSongAnalysis out=cloud("a"),in=cloud("b");
  std::optional<std::int64_t> outMs=120000,inMs=120000;
  std::vector<std::int64_t> ids{8,9,12};
  std::vector<PlannerCandidateStyle> catalog{{8,8},{9,8},{12,16}};
  PlannerResolvedProducerContext context{{104,32},{{60,104,32},{60,120},{0,32}}};
  PlannerBeatMatchedEligibility eligibility=PlannerBeatMatchedEligibility::allowed;
  PlannerProducedObservation run(std::uint64_t budget=kPlannerProducerWorkBudget) const {
    return observeProducedMusicKitCandidates(out,in,outMs,inMs,ids,catalog,context,eligibility,budget);
  }
};
void chosen(const PlannerProducedObservation&r,int style){
  check(r.status==PlannerProducedStatus::observed&&r.completeForResolvedScope&&!r.canExecute&&r.selection&&
        r.selection->completeForProvidedSeeds&&r.selection->status==PlannerCandidateStatus::selected&&r.selection->winner&&
        r.selection->winner->styleId==style&&!r.selection->canExecute&&r.production.completeForResolvedInputs);
}
void noProvisional(const PlannerProducedObservation&r,PlannerProducedStatus status){
  check(r.status==status&&!r.completeForResolvedScope&&!r.selection&&r.production.seeds.empty()&&
        r.production.pairAttempts==0&&r.production.workUnits==0&&!r.production.completeForResolvedInputs&&!r.canExecute);
}
void near(double a,double b){check(std::abs(a-b)<1e-10);}
}
int main(){try{
  test("automatic seed production -> style9 with no caller seeds/tonal predicates",[]{Fixture f;auto r=f.run();chosen(r,9);
    check(r.production.seeds.size()==12&&r.production.pairAttempts==12);
    const auto&w=*r.selection->winner;near(w.score,15.104);near(w.outgoingStart,104);near(w.outgoingEnd,120);
    near(w.incomingStart,0);near(w.incomingEnd,16);check(w.seedIndex==9&&w.styleIndex==1);});
  test("150 BPM input -> actual16-bar style12 -> shifted candidate",[]{Fixture f;f.in=cloud("b",150,75);auto r=f.run();chosen(r,12);
    const auto&w=*r.selection->winner;near(w.score,10.088);near(w.outgoingStart,88);near(w.outgoingEnd,120);
    near(w.incomingStart,6.4);near(w.incomingEnd,32);check(r.production.pairAttempts==15);});
  test("requested internal descriptor field really controls suffix",[]{Fixture f;f.ids={9};f.catalog={{9,9}};
    auto r=f.run();chosen(r,9);near(r.selection->winner->outgoingStart,102);near(r.selection->winner->incomingStart,6);
    near(r.selection->winner->incomingEnd,24);check(r.selection->winner->features.scoreInputs.matchingBarCount==9);});
  test("missing duration is not supplied from cloud",[]{Fixture f;f.inMs.reset();noProvisional(f.run(),PlannerProducedStatus::unresolvedPlaybackDuration);});
  test("missing structure is not successful no-candidate",[]{Fixture f;f.in.flex.reset();noProvisional(f.run(),PlannerProducedStatus::insufficientStructure);});
  test("malformed cloud converter input cannot publish partial selection",[]{Fixture f;f.in.flex->attributes->videoEvents->score->pop_back();
    noProvisional(f.run(),PlannerProducedStatus::invalidPreparation);});
  test("preparation resource rejection retained",[]{Fixture f;f.out.flex->attributes->videoEvents->timeInSeconds->resize(4097);
    f.out.flex->attributes->videoEvents->score->resize(4097);noProvisional(f.run(),PlannerProducedStatus::resourceLimit);});
  test("unknown eligibility never inferred from supportsSmartTransitions",[]{Fixture f;f.eligibility=PlannerBeatMatchedEligibility::unresolved;
    noProvisional(f.run(),PlannerProducedStatus::eligibilityUnresolved);});
  test("denied eligibility skips even malformed music data",[]{Fixture f;f.eligibility=PlannerBeatMatchedEligibility::denied;
    f.in.flex->attributes->videoEvents->score->clear();noProvisional(f.run(),PlannerProducedStatus::ineligible);});
  test("invalid eligibility byte not coerced true",[]{Fixture f;f.eligibility=static_cast<PlannerBeatMatchedEligibility>(99);rejects([&]{f.run();});});
  test("missing requested style is not best of known subset",[]{Fixture f;f.ids={8,9,12,999};noProvisional(f.run(),PlannerProducedStatus::styleUnavailable);});
  test("empty requested style profile does not invent default IDs",[]{Fixture f;f.ids.clear();auto r=f.run();
    check(r.status==PlannerProducedStatus::observed&&r.completeForResolvedScope&&r.selection&&!r.selection->winner&&
      r.selection->status==PlannerCandidateStatus::noPositiveCandidate);});
  test("duplicate requested descriptor keeps earlier index",[]{Fixture f;f.ids={9,9,8};auto r=f.run();chosen(r,9);check(r.selection->winner->styleIndex==0);});
  test("native catalog storage order does not reorder requested IDs",[]{Fixture f;f.catalog={{12,16},{9,8},{8,8}};auto r=f.run();chosen(r,9);
    check(r.selection->winner->styleIndex==1);});
  test("tonal main instead of either edge",[]{Fixture f;f.in.audio->attributes->key->beginning=CloudKey{"F#","major"};
    f.in.audio->attributes->key->ending=CloudKey{"E","major"};chosen(f.run(),9);});
  test("missing main key is not replaced by matching edge",[]{Fixture f;f.in.audio->attributes->key->main.reset();
    f.in.audio->attributes->key->beginning=CloudKey{"C","minor"};chosen(f.run(),8);});
  test("low MAIN melodicness can satisfy existing tonal fallback",[]{Fixture f;f.in.audio->attributes->key.reset();
    f.in.audio->attributes->melodicness->main=.2;chosen(f.run(),9);});
  test("low edge melodicness cannot substitute MAIN",[]{Fixture f;f.in.audio->attributes->key.reset();
    f.in.audio->attributes->melodicness->beginning=0;f.in.audio->attributes->melodicness->ending=0;chosen(f.run(),8);});
  test("MusicKit out-of-domain melodicness invalidated not forwarded as >1 fallback",[]{Fixture f;f.in.audio->attributes->key.reset();
    f.in.audio->attributes->melodicness->main=1.2;chosen(f.run(),8);});
  test("missing vocal map differs from present empty",[]{Fixture f;f.in.audio->attributes->vocalActivity.reset();chosen(f.run(),8);});
  test("both present empty maps retain style9",[]{Fixture f;chosen(f.run(),9);});
  test("wrong cloud BPM/beat arrays do not generate a different structure",[]{Fixture f;f.in.audio->attributes->bpm->values.main=999;
    f.out.audio->attributes->beats->beatsInMilliseconds=std::vector<double>{-999999};auto r=f.run();chosen(r,9);near(r.selection->winner->score,15.104);});
  test("outgoing loudness filter can yield complete no-seed result",[]{Fixture f;
    f.out.audio->attributes->loudnessCurve=CloudSampledValues{std::vector<double>(241,-40),2};auto r=f.run();
    check(r.status==PlannerProducedStatus::observed&&r.completeForResolvedScope&&r.production.outgoingNonSilent==0&&
      r.production.seeds.empty()&&r.selection&&!r.selection->winner&&r.selection->completeForProvidedSeeds);});
  test("incoming loudness is not outgoing-discovery filter",[]{Fixture f;
    f.in.audio->attributes->loudnessCurve=CloudSampledValues{std::vector<double>(241,-40),2};auto r=f.run();chosen(r,9);check(r.production.seeds.size()==12);});
  test("style score zero is complete no-positive not unsupported",[]{Fixture f;f.ids={9};f.catalog={{9,7}};auto r=f.run();
    check(r.status==PlannerProducedStatus::observed&&r.completeForResolvedScope&&r.selection&&!r.selection->winner&&
      r.selection->status==PlannerCandidateStatus::noPositiveCandidate);});
  test("resolved discovery ceiling honored before selection",[]{Fixture f;f.context.discovery.maximumIncomingEnd=0;auto r=f.run();
    check(r.status==PlannerProducedStatus::observed&&r.completeForResolvedScope&&r.production.seeds.empty()&&!r.selection->winner);});
  test("later placement rejection does not masquerade as missing structure",[]{Fixture f;f.context.candidates.outgoingStarts={119,120};
    auto r=f.run();check(r.status==PlannerProducedStatus::observed&&r.completeForResolvedScope&&r.production.seeds.size()==12&&
      r.selection&&!r.selection->winner);});
  test("invalid discovery bounds rejected",[]{Fixture f;f.context.discovery.minimumOutgoingEnd=-1;rejects([&]{f.run();});});
  test("resolved bounds cannot exceed their own playback source",[]{Fixture f;f.context.discovery.maximumIncomingEnd=121;rejects([&]{f.run();});
    f.context.discovery.maximumIncomingEnd=32;f.context.candidates.placement.maximumIncomingEnd=121;rejects([&]{f.run();});});
  test("range endpoints are validated not clamped",[]{Fixture f;f.context.candidates.incomingStarts={20,10};rejects([&]{f.run();});});
  test("nonfinite context rejected",[]{Fixture f;f.context.candidates.outgoingStarts.start=std::numeric_limits<double>::quiet_NaN();rejects([&]{f.run();});});
  test("invalid actual duration not taken from catalog",[]{Fixture f;f.outMs=0;rejects([&]{f.run();});f.outMs=9007199254740992LL;rejects([&]{f.run();});});
  test("zero work budget no partial result",[]{Fixture f;noProvisional(f.run(0),PlannerProducedStatus::resourceLimit);});
  test("too-large work budget is rejected rather than relaxing hard limits",[]{Fixture f;
    noProvisional(f.run(kPlannerProducerWorkBudget+1),PlannerProducedStatus::resourceLimit);});
  test("shared work budget pays for BOTH producer and existing selector",[]{Fixture f;auto base=f.run();chosen(base,9);
    const auto a=preparePlannerSongObservation(f.out,f.outMs),z=preparePlannerSongObservation(f.in,f.inMs);
    const auto at=plannerMusicKitMainTonalComponents(f.out),zt=plannerMusicKitMainTonalComponents(f.in);
    const auto styles=resolvePlannerStyleRequests(f.ids,f.catalog).styles;
    // Independently query the previous selector's minimum accepted budget. This
    // makes failure of the composition's subtraction observable, not a test
    // which would fail for a tiny budget even if producer work were free.
    std::uint64_t low=0,high=kPlannerCandidateWorkBudget;
    while(low<high){const auto mid=low+(high-low)/2;
      const auto r=selectPreparedPlannerCandidates(a,z,at,zt,base.production.seeds,styles,f.context.candidates,mid);
      if(r.status==PlannerCandidateStatus::resourceLimit)low=mid+1;else{check(r.completeForProvidedSeeds);high=mid;}}
    const auto total=base.production.workUnits+low;
    check(low>0&&base.production.workUnits>0&&total<=kPlannerProducerWorkBudget);
    chosen(f.run(total),9);noProvisional(f.run(total-1),PlannerProducedStatus::resourceLimit);});
  test("many seeds do not cause prefix selection",[]{Fixture f;f.context.discovery={0,120};
    noProvisional(f.run(),PlannerProducedStatus::resourceLimit);});
  test("result owns values after input destruction",[]{PlannerProducedObservation r;{Fixture f;r=f.run();}chosen(r,9);
    near(r.selection->winner->outgoingEnd,120);check(r.production.seeds.size()==12);});
  test("cloud input arrays and fields are never mutated",[]{Fixture f;const auto times=*f.out.flex->attributes->videoEvents->timeInSeconds;
    const auto scores=*f.in.flex->attributes->videoEvents->score;auto r=f.run();chosen(r,9);
    check(times==*f.out.flex->attributes->videoEvents->timeInSeconds&&scores==*f.in.flex->attributes->videoEvents->score&&
      *f.out.audio->attributes->bpm->values.main==7&&*f.in.audio->attributes->key->main->tonic=="C");});
  test("deterministic repeated native observation",[]{Fixture f;const auto a=f.run(),b=f.run();chosen(a,9);chosen(b,9);
    check(a.selection->winner->score==b.selection->winner->score&&a.selection->winner->seedIndex==b.selection->winner->seedIndex&&
      a.production.workUnits==b.production.workUnits);});
  std::cout<<"Produced MusicKit inputs -> native selector: "<<passed<<"/"<<passed<<" groups PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
