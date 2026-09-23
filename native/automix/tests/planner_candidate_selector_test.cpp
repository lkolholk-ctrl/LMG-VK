#include "lmg/automix/planner_candidate_selector.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <functional>
#include <iostream>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
using namespace lmg::automix::region_algebra;
namespace {
int groups = 0;
void require(bool v, const char* why) { if (!v) throw std::runtime_error(why); }
void near(double a, double z) { require(std::abs(a-z) < 1e-11, "numeric mismatch"); }
template<class F> void rejected(F f) {
  bool caught = false;
  try { f(); } catch (const std::exception&) { caught = true; }
  require(caught, "malformed host input was accepted");
}
template<class F> void test(const char* label, F f) {
  try { f(); ++groups; }
  catch (const std::exception& error) {
    std::cerr << "FAILED " << label << ": " << error.what() << '\n'; throw;
  }
}
SongStructure structure(std::size_t beats = 96, double tempo = 120.0, double start = 0.0) {
  SongStructure s;
  for (std::size_t i=0; i<=beats; ++i) {
    const bool bar = i%4 == 0;
    s.events.push_back({start + static_cast<double>(i)*(60.0/tempo),
      bar?StructureEventKind::downbeat:StructureEventKind::beat, static_cast<std::int64_t>(i),
      bar?std::optional<std::int64_t>(i/4):std::nullopt,std::nullopt,std::nullopt});
    s.beatEvents.push_back(i);
    if (bar) s.downbeatEvents.push_back(i);
  }
  s.beatStabilityMap.push_back({{0, beats-beats%4}, 4, tempo});
  return s;
}
struct Fixture {
  SongStructure out = structure(), in = structure();
  PlannerVocalMap ov, iv;
  PlannerLoudnessMap il;
  PlannerCandidateSong outgoing() const {return {&out, &ov, nullptr, PlannerTonality{4,1}, 0.7, true};}
  PlannerCandidateSong incoming() const {return {&in, &iv, nullptr, PlannerTonality{4,1}, 0.7, true};}
  PlannerCandidateBounds bounds{{0,0,1000}, {0,1000}, {0,1000}};
  std::vector<PlannerCandidateSeed> seeds{{{32,64},{0,32},PlannerRegionUnit::bars,TempoBinaryScale::one}};
  PlannerCandidateSelection run(std::vector<PlannerCandidateStyle> styles = {{8,8},{9,8},{12,8}}) const {
    return selectPlannerRegionCandidates(outgoing(), incoming(), seeds, styles, bounds);
  }
};
const PlannerScoredCandidate& winner(const PlannerCandidateSelection& r) {
  require(r.status==PlannerCandidateStatus::selected && r.winner.has_value(), "winner missing");
  require(!r.canExecute && r.completeForProvidedSeeds, "observation became executable/incomplete"); return *r.winner;
}
void none(const PlannerCandidateSelection& r) {
  require(r.status==PlannerCandidateStatus::noPositiveCandidate && !r.winner, "unexpected winner");
}
std::uint64_t bits(double value) { std::uint64_t b; std::memcpy(&b,&value,sizeof b);return b; }
std::optional<std::int64_t> number(const std::string& value) {
  if (value=="-") return std::nullopt;
  return std::stoll(value);
}
std::vector<std::uint8_t> tags(const std::string& s) {
  std::vector<std::uint8_t> r; if (s=="-") return r;
  for (char c:s) { require(c>='0' && c<='5',"bad oracle tag");r.push_back(c-'0'); } return r;
}
void reference(const char* path) {
  std::ifstream in(path);require(in.good(),"reference file missing");
  std::string line; std::size_t cases=0;
  while (std::getline(in,line)) {
    if (line.empty()||line.front()=='#') continue;
    std::istringstream stream(line); std::string op,a,z,s,expected;
    stream>>op>>a>>z>>s>>expected;require(!stream.fail(),"invalid reference row");
    if (op=="R") {
      const auto r=plannerCandidateBarRatio(number(a),number(z),static_cast<TempoBinaryScale>(std::stoi(s)));
      if (expected=="-") require(!r,"ratio present");
      else require(r && bits(*r)==std::stoull(expected,nullptr,16),"ratio reference bits");
    } else if (op=="M") {
      const auto r=plannerCandidateMatchingBars(number(a),number(z),static_cast<TempoBinaryScale>(std::stoi(s)));
      require(r==number(expected),"matched reference");
    } else if (op=="D") {
      const auto value=plannerCandidateEndDelta(std::stod(a),std::stod(z));
      require(bits(value)==std::stoull(expected,nullptr,16),"delta reference bits");
    } else if (op=="V") {
      require(plannerCandidateVocalConflict(tags(a),tags(z))==(expected=="1"),"vocal reference");
    } else throw std::runtime_error("unknown oracle operation");
    ++cases;
  }
  require(cases==3065,"reference suite unexpectedly incomplete");
  std::cout << "Candidate predicate specification oracle: "<<cases<<"/"<<cases<<" cases PASSED\n";
}
void runTests() {
  test("style 9 wins actual native scoring", [] {Fixture f;auto r=f.run();auto w=winner(r);require(w.styleId==9,"wrong style");near(w.score,15.016);require(w.seedIndex==0&&w.styleIndex==1&&r.constructed==3&&r.nonPositive==1,"counters");});
  test("suffix default four is not eight", [] {Fixture f;auto r=f.run({{8,std::nullopt},{9,std::nullopt}});auto w=winner(r);require(w.styleId==8&&w.outgoing.startEvent==48&&w.incoming.startEvent==16,"suffix mismatch");near(w.score,10.016);});
  test("explicit zero suffix remains zero", [] {Fixture f;auto r=f.run({{8,0},{9,0}});none(r);require(r.constructed==2,"zero conflated with missing");});
  test("seven/eight matching-bar boundary", [] {Fixture f;none(f.run({{9,7}}));require(winner(f.run({{9,8}})).features.scoreInputs.matchingBarCount==8,"eight fails");});
  test("style 12 needs normal failure expanded pass", [] {Fixture f;f.in=structure(96,150);auto r=f.run();auto w=winner(r);require(w.styleId==12&&w.incoming.startEvent==16&&w.incoming.endEvent==48,"shift missing");require(w.features.normalTempoTag==0xfc&&w.features.expandedTempoTag==0x81,"tempo tags");near(w.score,10.0+(32.0-19.2)*.001);});
  test("style 12 rejects normal match", [] {Fixture f;none(f.run({{12,8}}));});
  test("style 12 rejects expanded failure", [] {Fixture f;f.in=structure(96,170);none(f.run({{12,8}}));});
  test("style 12 absent shifted event", [] {Fixture f;f.in=structure(32,150);auto r=f.run({{12,8}});none(r);require(r.regionMisses==1,"shift miss not classified");});
  test("style 12 validates placement after shift", [] {Fixture f;f.in=structure(96,150);f.bounds.placement.maximumIncomingEnd=19;auto r=f.run({{12,8}});none(r);require(r.placementMisses==1,"unshifted bounds used");});
  test("half scale inverse beats and bar ratio", [] {Fixture f;f.seeds[0].incoming={0,64};f.seeds[0].incomingScale=TempoBinaryScale::half;f.in=structure(96,240);auto w=winner(f.run({{8,8},{9,8}}));require(w.styleId==9&&w.features.scoreInputs.matchingBarCount==8,"half normalization");near(w.features.scoreInputs.tieBreakDelta,16);});
  test("two scale inverse beats and bar ratio", [] {Fixture f;f.seeds[0].incoming={0,16};f.seeds[0].incomingScale=TempoBinaryScale::two;f.in=structure(96,60);auto w=winner(f.run({{8,8},{9,8}}));require(w.styleId==9&&w.features.scoreInputs.matchingBarCount==8,"double normalization");});
  test("style 12 half shift is 32 incoming beats", [] {Fixture f;f.in=structure(128,300);f.seeds[0].incoming={0,64};f.seeds[0].incomingScale=TempoBinaryScale::half;auto w=winner(f.run({{12,8}}));require(w.incoming.startEvent==32&&w.incoming.endEvent==96,"half shift wrong");});
  test("style 12 two shift is 8 incoming beats", [] {Fixture f;f.in=structure(96,75);f.seeds[0].incoming={0,16};f.seeds[0].incomingScale=TempoBinaryScale::two;auto w=winner(f.run({{12,8}}));require(w.incoming.startEvent==8&&w.incoming.endEvent==24,"double shift wrong");});
  test("tie is endpoint difference not starts or duration", [] {Fixture f;f.seeds={{{0,64},{0,32},PlannerRegionUnit::bars,TempoBinaryScale::one},{{0,96},{0,32},PlannerRegionUnit::bars,TempoBinaryScale::one}};auto w=winner(f.run({{8,8}}));require(w.seedIndex==1,"tie-break ignored");near(w.features.scoreInputs.tieBreakDelta,32);near(w.score,10.032);});
  test("first seed retained on identical score", [] {Fixture f;f.seeds.push_back(f.seeds[0]);auto w=winner(f.run({{8,8}}));require(w.seedIndex==0,"ties unstable");});
  test("first style retained on identical score", [] {Fixture f;auto w=winner(f.run({{8,8},{8,8}}));require(w.styleIndex==0,"style order lost");});
  test("seed beyond diagnostic first sixteen is evaluated", [] {Fixture f;f.seeds.resize(20,f.seeds[0]);f.seeds[19].outgoing={64,96};auto w=winner(f.run({{8,8}}));require(w.seedIndex==19,"preview truncated selection");});
  test("tonal unresolved is not a provisional style8 win", [] {Fixture f;auto a=f.outgoing();a.tonalComponentsResolved=false;auto r=selectPlannerRegionCandidates(a,f.incoming(),f.seeds,{{8,8},{9,8}},f.bounds);require(r.status==PlannerCandidateStatus::unresolvedTonalityBinding&&!r.winner,"invented tonality");});
  test("unresolved tonality does not block style8", [] {Fixture f;auto a=f.outgoing();a.tonalComponentsResolved=false;require(winner(selectPlannerRegionCandidates(a,f.incoming(),f.seeds,{{8,8}},f.bounds)).styleId==8,"unused field blocks8");});
  test("known absent keys respect native melodicness fallback", [] {Fixture f;auto a=f.outgoing(),z=f.incoming();a.tonality.reset();z.tonality.reset();a.melodicness=.1;require(winner(selectPlannerRegionCandidates(a,z,f.seeds,{{9,8}},f.bounds)).styleId==9,"native tonality fallback missing");a.melodicness=.25;none(selectPlannerRegionCandidates(a,z,f.seeds,{{9,8}},f.bounds));});
  test("absent and empty vocal maps differ", [] {Fixture f;auto a=f.outgoing();a.vocals=nullptr;none(selectPlannerRegionCandidates(a,f.incoming(),f.seeds,{{9,8}},f.bounds));require(winner(f.run({{9,8}})).styleId==9,"present empty vocals rejected");});
  test("low overlapping vocals block style9", [] {Fixture f;f.ov={{16.1,16.2,0,1}};f.iv={{.1,.2,0,1}};none(f.run({{9,8}}));require(winner(f.run({{8,8},{9,8}})).styleId==8,"relationship not computed");});
  test("veryLow does not block style9", [] {Fixture f;f.ov={{16,32,0,0}};f.iv={{0,16,2,4}};require(winner(f.run({{9,8}})).styleId==9,"very-low conflict");});
  test("closed vocal boundary included", [] {Fixture f;f.ov={{16.5,16.5,0,1}};f.iv={{0,0,0,1}};none(f.run({{9,8}}));});
  test("suffix vector alignment not prefix", [] {require(!plannerCandidateVocalConflict({4,5,5},{4,4}),"prefix compared");require(plannerCandidateVocalConflict({5,5,4},{4,4}),"suffix ignored");});
  test("all absent strengths stay nonconflicting", [] {require(!plannerCandidateVocalConflict({5,5},{4,4}),"missing means zero wrongly");require(!plannerCandidateVocalConflict({},{}),"empty conflicts");});
  test("leading vocal uses beginning through start-minus-four beat", [] {Fixture f;f.iv={{0,0.5,0,3}};auto w=winner(f.run({{8,4}}));require(w.features.leadingVocalWindow&&w.features.leadingVocalWindow->start==0,"missing leading window");near(w.features.leadingVocalWindow->end,6);near(w.score,7.516);});
  test("vocal immediately preceding start excluded from leading window", [] {Fixture f;f.iv={{7,7.5,0,4}};auto w=winner(f.run({{8,4}}));require(!w.features.scoreInputs.leadingIncomingVocalSignificant,"last seconds substituted");near(w.score,10.016);});
  test("leading closed endpoint", [] {Fixture f;f.iv={{6,6,0,4}};near(winner(f.run({{8,4}})).score,7.516);});
  test("missing earlier beat leaves leading window absent", [] {Fixture f;f.iv={{0,20,0,4}};auto w=winner(f.run({{8,8}}));require(!w.features.leadingVocalWindow&&!w.features.scoreInputs.leadingIncomingVocalSignificant,"manufactured early window");});
  test("incoming loudness ratio uses real normalized map", [] {Fixture f;f.il={{-8,8},{-16,24}};auto z=f.incoming();z.loudness=&f.il;auto w=winner(selectPlannerRegionCandidates(f.outgoing(),z,f.seeds,{{8,8}},f.bounds));require(w.features.scoreInputs.trailingIncomingLoudnessRatio.has_value(),"loudness missing");near(*w.features.scoreInputs.trailingIncomingLoudnessRatio,.5);near(w.score,5.016);});
  test("loudness ratio requires present vocal map", [] {Fixture f;f.il={{-8,8},{-16,24}};auto z=f.incoming();z.vocals=nullptr;z.loudness=&f.il;auto w=winner(selectPlannerRegionCandidates(f.outgoing(),z,f.seeds,{{8,8}},f.bounds));require(!w.features.scoreInputs.trailingIncomingLoudnessRatio,"vocal availability ignored");near(w.score,10.016);});
  test("stable-map start inclusion and end exclusion", [] {Fixture f;f.out.beatStabilityMap={{{0,32},4,170},{{32,96},4,120}};auto w=winner(f.run({{8,8}}));near(w.features.outgoingTempo->value,120);});
  test("first overlapping stable map wins", [] {Fixture f;f.out.beatStabilityMap={{{0,96},4,170},{{32,96},4,120}};none(f.run({{8,8}}));});
  test("stable-map missing tempo is not generated from BPM", [] {Fixture f;f.out.beatStabilityMap.clear();none(f.run({{8,8}}));});
  test("bar conversion requires BOTH exact downbeat endpoints", [] {Fixture f;f.seeds[0].incoming={1,33};f.seeds[0].incomingUnit=PlannerRegionUnit::beats;none(f.run({{8,8},{9,8}}));});
  test("inclusive minimum and maximum placement boundaries", [] {Fixture f;f.bounds={{16,32,16},{16,16},{0,0}};require(winner(f.run({{9,8}})).styleId==9,"closed bounds not inclusive");f.bounds.placement.minimumOutgoingStart=std::nextafter(16.,17.);none(f.run({{9,8}}));});
  test("unsupported IDs never acquire scores", [] {Fixture f;auto r=f.run({{4,8},{33,8}});none(r);require(r.unsupportedStyles==2&&!r.constructed,"unsupported style accepted");});
  test("empty seeds/styles valid no candidate", [] {Fixture f;none(f.run({}));f.seeds.clear();none(f.run());});
  test("absent structure explicit status", [] {Fixture f;auto a=f.outgoing();a.structure=nullptr;auto r=selectPlannerRegionCandidates(a,f.incoming(),f.seeds,{{8,8}},f.bounds);require(r.status==PlannerCandidateStatus::insufficientStructure&&!r.winner,"missing structure");});
  test("zero work budget never yields provisional result", [] {Fixture f;auto r=selectPlannerRegionCandidates(f.outgoing(),f.incoming(),f.seeds,{{8,8}},f.bounds,0);require(r.status==PlannerCandidateStatus::resourceLimit&&!r.winner&&!r.attempted,"partial result on budget exhaustion");});
  test("late budget exhaustion discards earlier winner", [] {Fixture f;f.seeds.resize(64,f.seeds[0]);auto r=selectPlannerRegionCandidates(f.outgoing(),f.incoming(),f.seeds,{{8,8}},f.bounds,150000);require(r.status==PlannerCandidateStatus::resourceLimit&&!r.winner&&!r.constructed,"partial winner leaked");});
  test("seed/style bounds", [] {Fixture f;f.seeds.resize(65,f.seeds[0]);require(f.run().status==PlannerCandidateStatus::resourceLimit,"seed cap");f.seeds.resize(1);require(f.run(std::vector<PlannerCandidateStyle>(15,{8,8})).status==PlannerCandidateStatus::resourceLimit,"style cap");});
  test("dangling event rejected before access", [] {Fixture f;f.in.beatEvents.push_back(99999);rejected([&]{f.run();});});
  test("dangling stable interval rejected", [] {Fixture f;f.in.beatStabilityMap[0].events.endEvent=99999;rejected([&]{f.run();});});
  test("negative and reversed ordinals rejected", [] {Fixture f;f.in.events[8].beatIndex=-1;rejected([&]{f.run();});f.in=structure();f.seeds[0].outgoing={64,32};rejected([&]{f.run();});});
  test("nonfinite time tempo bounds rejected", [] {Fixture f;f.in.events[9].songTime=std::numeric_limits<double>::quiet_NaN();rejected([&]{f.run();});f.in=structure();f.in.beatStabilityMap[0].averageTempoBpm=0;rejected([&]{f.run();});f.in=structure();f.bounds.incomingStarts.end=std::numeric_limits<double>::infinity();rejected([&]{f.run();});});
  test("unknown enums rejected", [] {Fixture f;f.seeds[0].incomingScale=static_cast<TempoBinaryScale>(3);rejected([&]{f.run();});f.seeds[0].incomingScale=TempoBinaryScale::one;f.seeds[0].incomingUnit=static_cast<PlannerRegionUnit>(3);rejected([&]{f.run();});});
  test("invalid normalized vocals rejected", [] {Fixture f;f.iv={{3,2,0,1}};rejected([&]{f.run();});f.iv={{0,1,0,5}};rejected([&]{f.run();});});
  test("negative internal suffix rejected", [] {Fixture f;rejected([&]{f.run({{8,-1}});});});
  test("scalar overflow/invalid domain rejection", [] {rejected([]{plannerCandidateBarRatio(1,std::numeric_limits<std::int64_t>::max(),TempoBinaryScale::two);});rejected([]{plannerCandidateMatchingBars(-1,0,TempoBinaryScale::one);});rejected([]{plannerCandidateEndDelta(std::numeric_limits<double>::max(),-std::numeric_limits<double>::max());});rejected([]{plannerCandidateVocalConflict({6},{0});});});
  test("missing counts distinct from zero; odd half truncates", [] {require(!plannerCandidateMatchingBars(std::nullopt,0,TempoBinaryScale::one),"missing count converted");require(plannerCandidateMatchingBars(0,0,TempoBinaryScale::one)==0,"zero lost");require(!plannerCandidateBarRatio(0,0,TempoBinaryScale::one),"zero ratio");require(plannerCandidateMatchingBars(4,9,TempoBinaryScale::half)==4,"integer truncation lost");near(*plannerCandidateBarRatio(8,9,TempoBinaryScale::half),.5);});
  test("vocal decimation uses enumeration from odd outgoing start", [] {
    Fixture f;
    auto meter3=[](SongStructure& s) {s.downbeatEvents.clear(); for(std::size_t i=0;i<s.events.size();++i) {
      auto& e=s.events[i];e.kind=i%3==0?StructureEventKind::downbeat:StructureEventKind::beat;
      e.downbeatIndex=i%3==0?std::optional<std::int64_t>(i/3):std::nullopt;
      if(i%3==0)s.downbeatEvents.push_back(i);
    } s.beatStabilityMap[0].beatsPerBar=3;};
    f.in=structure(96,60);meter3(f.out);meter3(f.in);
    f.seeds={{{27,51},{0,12},PlannerRegionUnit::bars,TempoBinaryScale::two}};
    f.ov={{13.55,13.6,0,1}};f.iv={{.1,.2,0,1}};none(f.run({{9,8}}));
    f.ov.clear();require(winner(f.run({{9,8}})).styleId==9,"meter3 fixture invalid");
  });
  test("vocal decimation uses enumeration from odd incoming start", [] {
    Fixture f;
    auto meter3=[](SongStructure& s) {s.downbeatEvents.clear(); for(std::size_t i=0;i<s.events.size();++i) {
      auto& e=s.events[i];e.kind=i%3==0?StructureEventKind::downbeat:StructureEventKind::beat;
      e.downbeatIndex=i%3==0?std::optional<std::int64_t>(i/3):std::nullopt;
      if(i%3==0)s.downbeatEvents.push_back(i);
    } s.beatStabilityMap[0].beatsPerBar=3;};
    f.in=structure(96,240);meter3(f.out);meter3(f.in);
    f.seeds={{{24,48},{3,51},PlannerRegionUnit::bars,TempoBinaryScale::half}};
    f.ov={{12.1,12.2,0,1}};f.iv={{.76,.77,0,1}};none(f.run({{9,8}}));
    f.iv.clear();require(winner(f.run({{9,8}})).styleId==9,"meter3 half fixture invalid");
  });
  test("rejection reason identifies missing tempo", [] {Fixture f;f.in.beatStabilityMap.clear();auto r=f.run({{8,8}});
    none(r);require(r.completeForProvidedSeeds&&(r.rejectionReasons&static_cast<std::uint64_t>(PlannerCandidateRejection::tempoUnavailable)),"tempo reason");});
  test("rejection reason identifies insufficient bar count", [] {Fixture f;auto r=f.run({{9,7}});none(r);
    require(r.rejectionReasons&static_cast<std::uint64_t>(PlannerCandidateRejection::fewerThanEightBars),"bar reason");});
  test("rejection reason identifies low vocal conflict", [] {Fixture f;f.ov={{16,32,0,1}};f.iv={{0,16,0,1}};auto r=f.run({{9,8}});none(r);
    require(r.rejectionReasons&static_cast<std::uint64_t>(PlannerCandidateRejection::vocalConflict),"vocal reason");});
  test("rejection reasons coexist with winner", [] {Fixture f;auto r=f.run({{33,8},{8,8},{12,8}});winner(r);
    require(r.rejectionReasons&static_cast<std::uint64_t>(PlannerCandidateRejection::unsupportedStyle),"unsupported reason");
    require(r.rejectionReasons&static_cast<std::uint64_t>(PlannerCandidateRejection::normalTempoMustFail),"normal-pass rejection");});
  test("negative score after tie is not a made-up predicate failure", [] {Fixture f;f.in=structure(96,120,20000);
    f.bounds.placement.maximumIncomingEnd=30000;f.bounds.incomingStarts.end=30000;auto r=f.run({{8,8}});none(r);
    require(r.rejectionReasons==static_cast<std::uint64_t>(PlannerCandidateRejection::finalScoreNonPositive),"final-score reason");});
  test("resource failure clears completed scope and reasons", [] {Fixture f;f.seeds.resize(64,f.seeds[0]);
    auto r=selectPlannerRegionCandidates(f.outgoing(),f.incoming(),f.seeds,{{33,8},{8,8}},f.bounds,150000);
    require(r.status==PlannerCandidateStatus::resourceLimit&&!r.completeForProvidedSeeds&&!r.rejectionReasons,"partial diagnostics leaked");});
  test("signed zero in end delta", [] {require(bits(plannerCandidateEndDelta(-0.0,0.0))==bits(-0.0),"negative zero lost");});
}
} // namespace
int main(int argc,char** argv) {
  try {
    if(argc==3&&std::string(argv[1])=="--reference") {reference(argv[2]);return 0;}
    if(argc!=1) throw std::runtime_error("Usage: candidate_tests [--reference FILE]");
    runTests();std::cout<<"Native candidate selector: "<<groups<<"/"<<groups<<" groups PASSED\n";
    return 0;
  } catch(const std::exception& e) {std::cerr<<e.what()<<'\n';return 1;}
}
