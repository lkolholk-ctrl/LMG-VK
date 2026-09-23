#include "lmg/automix/planner_candidate_observation.h"
#include <cmath>
#include <iostream>
#include <stdexcept>

using namespace lmg::automix;
using namespace lmg::automix::region_algebra;
namespace {
int passed=0;
void check(bool v){if(!v)throw std::runtime_error("prepared candidate assertion");}
template<class F> void test(const char* name,F f){try{f();++passed;}catch(...){std::cerr<<"FAILED "<<name<<'\n';throw;}}
template<class F> void rejects(F f){bool failed=false;try{f();}catch(const std::exception&){failed=true;}check(failed);}
CloudSongAnalysis cloud(const char* id,double bpm=120.0,int meter=4) {
  CloudSongAnalysis s;s.id=id;s.durationInMillis=60000;s.supportsSmartTransitions=true;
  CloudFlexAnalysis f;CloudVideoEvents v;v.timeInSeconds.emplace();v.score.emplace();
  for(int i=0;i<=24*meter;++i){v.timeInSeconds->push_back(i*(60.0/bpm));
    v.score->push_back(i%(4*meter)==0?800:i%(2*meter)==0?600:i%meter==0?400:200);}
  f.videoEvents=v;s.flex=CloudAnalysisResource<CloudFlexAnalysis>{"flex",f};
  CloudAudioAnalysis a;a.key=CloudComposite<CloudKey>{CloudKey{"C","minor"},std::nullopt,std::nullopt};
  a.vocalActivity=std::vector<CloudVocalActivity>{};
  CloudBpm distractor;distractor.values.main=13;a.bpm=distractor;
  a.beats=CloudBeats{std::vector<double>{17,23,89},std::vector<double>{1234}};
  s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"audio",a};return s;
}
struct Fixture {
  PlannerSongPreparation out=preparePlannerSongObservation(cloud("a"),60000);
  PlannerSongPreparation in=preparePlannerSongObservation(cloud("b"),60000);
  std::vector<PlannerCandidateSeed> seeds{{{32,64},{0,32},PlannerRegionUnit::bars,TempoBinaryScale::one}};
  std::vector<PlannerCandidateStyle> styles{{8,8},{9,8},{12,8}};
  PlannerCandidateBounds bounds{{0,0,60},{0,60},{0,60}};
  // Fixture explicitly resolves the source-provider binding to its known main
  // component. This is NOT a production rule for main/beginning/ending choice.
  PlannerCandidateTonalBinding binding(const PlannerSongPreparation& p) const {
    return {true,p.maps.tonality?std::optional<PlannerTonality>(p.maps.tonality->main):std::nullopt,std::nullopt};
  }
  PlannerCandidateSelection run()const{return selectPreparedPlannerCandidates(out,in,binding(out),binding(in),seeds,styles,bounds);}
};
void selected(const PlannerCandidateSelection& r,int id){check(r.status==PlannerCandidateStatus::selected&&r.winner&&r.winner->styleId==id&&!r.canExecute);}
}
int main(){try{
  test("prepared native Flex -> regions -> style9 -> real scoring",[]{Fixture f;auto r=f.run();selected(r,9);
    check(std::abs(r.winner->score-15.016)<1e-12&&r.winner->features.outgoingTempo->value==120);
    check(f.out.maps.structure->events.size()==97&&f.out.maps.structure->events[33].downbeatIndex.has_value());});
  test("native cloud vocal defaults enter predicates",[]{Fixture f;auto a=cloud("a"),b=cloud("b");
    a.audio->attributes->vocalActivity=std::vector<CloudVocalActivity>{{{16100,16200},"unknown","unknown"}};
    b.audio->attributes->vocalActivity=std::vector<CloudVocalActivity>{{{100,200},"unknown","unknown"}};
    f.out=preparePlannerSongObservation(a,60000);f.in=preparePlannerSongObservation(b,60000);selected(f.run(),8);});
  test("prepared tempo150 selects shifted style12",[]{Fixture f;f.in=preparePlannerSongObservation(cloud("b",150),60000);
    auto r=f.run();selected(r,12);check(r.winner->incoming.startEvent==16&&r.winner->incoming.endEvent==48);});
  test("prepared three-beat meter is not hardcoded four",[]{Fixture f;f.out=preparePlannerSongObservation(cloud("a",120,3),60000);
    f.in=preparePlannerSongObservation(cloud("b",120,3),60000);f.seeds[0].outgoing={24,48};f.seeds[0].incoming={0,24};selected(f.run(),9);});
  test("preview can be empty without changing native selection",[]{Fixture f;f.out.inventory->stablePreview.clear();f.in.inventory->stablePreview.clear();selected(f.run(),9);});
  test("unknown duration never replaced by cloud duration",[]{Fixture f;f.in=preparePlannerSongObservation(cloud("b"),std::nullopt);
    auto r=f.run();check(r.status==PlannerCandidateStatus::unresolvedPlaybackDuration&&!r.winner);});
  test("invalid preparation cannot contribute partial maps",[]{Fixture f;f.in.status=PlannerPreparationStatus::invalid;
    auto r=f.run();check(r.status==PlannerCandidateStatus::invalidPreparation&&!r.winner);});
  test("resource failure not a partial winner",[]{Fixture f;f.out.status=PlannerPreparationStatus::resourceLimit;
    auto r=f.run();check(r.status==PlannerCandidateStatus::resourceLimit&&!r.winner);});
  test("missing structure is distinct from no positive score",[]{Fixture f;f.in.maps.structure.reset();auto r=f.run();
    check(r.status==PlannerCandidateStatus::insufficientStructure&&!r.winner);});
  test("reversed/malformed preparation rejected",[]{Fixture f;f.out.inventory->reversedTimes=1;check(f.run().status==PlannerCandidateStatus::invalidPreparation);
    f.out.inventory->reversedTimes=0;f.out.inventory->malformedRegions=1;check(f.run().status==PlannerCandidateStatus::invalidPreparation);});
  test("outside-track unused inventory is not globally fatal",[]{Fixture f;f.out=preparePlannerSongObservation(cloud("a"),40000);
    f.bounds.outgoingStarts.end=40;check(f.out.inventory->outsideTrackRegions>0);selected(f.run(),9);});
  test("seed outside track rejected without clipping",[]{Fixture f;f.out.durationMs=30000;rejects([&]{f.run();});});
  test("incoming maximum must be explicitly resolved",[]{Fixture f;f.bounds.placement.maximumIncomingEnd=61;rejects([&]{f.run();});});
  test("dangling seed rejected",[]{Fixture f;f.seeds[0].incoming.endEvent=999999;rejects([&]{f.run();});});
  test("selection stores values not references to maps",[]{PlannerCandidateSelection r;{Fixture f;r=f.run();}selected(r,9);check(r.winner->outgoingEnd==32);});
  test("resolved tonal binding remains explicit",[]{Fixture f;auto r=selectPreparedPlannerCandidates(f.out,f.in,{}, {},f.seeds,f.styles,f.bounds);
    check(r.status==PlannerCandidateStatus::unresolvedTonalityBinding&&!r.winner);});
  test("source maps remain immutable",[]{Fixture f;auto before=f.out.maps.structure->events;auto vocalSize=f.in.maps.vocals->size();selected(f.run(),9);
    check(f.out.maps.structure->events.size()==before.size()&&f.in.maps.vocals->size()==vocalSize);
    for(std::size_t i=0;i<before.size();++i)check(before[i].songTime==f.out.maps.structure->events[i].songTime&&before[i].kind==f.out.maps.structure->events[i].kind);});
  test("host work cap forwarded through preparation adapter",[]{Fixture f;auto r=selectPreparedPlannerCandidates(f.out,f.in,f.binding(f.out),f.binding(f.in),f.seeds,f.styles,f.bounds,0);
    check(r.status==PlannerCandidateStatus::resourceLimit&&!r.winner);});
  std::cout<<"Prepared maps -> candidate selection: "<<passed<<"/"<<passed<<" groups PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
