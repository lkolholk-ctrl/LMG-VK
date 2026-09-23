#include "lmg/automix/planner_produced_observation.h"
#include <algorithm>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
using namespace lmg::automix::region_algebra;
namespace {
int passed = 0;
void check(bool v) { if (!v) throw std::runtime_error("producer assertion failed"); }
void near(double a, double b) { check(std::abs(a-b) < 1e-11); }
template<class F> void test(const char* label,F fn) {
  try { fn(); ++passed; } catch (...) { std::cerr << "FAILED: " << label << '\n'; throw; }
}
template<class F> void rejects(F fn) {
  bool caught=false; try { fn(); } catch (const std::exception&) { caught=true; } check(caught);
}
SongStructure grid(int bars=32, double bpm=120, int meter=4) {
  std::vector<FlexEvent> events;
  for (int i=0;i<=bars*meter;++i) {
    const auto kind=i%(4*meter)==0?FlexTimeScale::extraLong:i%(2*meter)==0?FlexTimeScale::longTime:
                    i%meter==0?FlexTimeScale::medium:FlexTimeScale::shortTime;
    events.push_back({i*(60.0/bpm),kind,0});
  }
  return songStructureFromFlexEvents(events);
}
PlannerSongPreparation prep(SongStructure s,double duration=128) {
  PlannerSongPreparation p; p.status=PlannerPreparationStatus::prepared;
  p.durationMs=static_cast<std::int64_t>(duration*1000);p.maps.structure=std::move(s);
  p.maps.vocals.emplace();return p;
}
void region(const std::optional<StructureRegion>& r,std::size_t a,std::size_t z) {
  check(r && r->startEvent==a && r->endEvent==z);
}
void emptyResource(const PlannerSeedProduction& r) {
  check(r.status==PlannerSeedProductionStatus::resourceLimit && r.seeds.empty() && !r.completeForResolvedInputs &&
        r.workUnits==0 && r.pairAttempts==0 && r.outgoingEnds==0 && r.incomingEnds==0 &&
        r.outgoingStable==0 && r.incomingStable==0 && r.outgoingNonSilent==0 && r.scaleMisses==0 && r.truncationMisses==0);
}
CloudSongAnalysis cloud(const char* id,double bpm=120,int bars=60) {
  CloudSongAnalysis s;s.id=id;s.durationInMillis=120000;s.supportsSmartTransitions=true;
  CloudAudioAnalysis a;a.key=CloudComposite<CloudKey>{CloudKey{"C","minor"},CloudKey{"A","major"},CloudKey{"B","major"}};
  a.melodicness=CloudComposite<double>{.8,.1,.2};a.vocalActivity.emplace();
  CloudBpm wrong;wrong.values.main=13;a.bpm=wrong;
  a.beats=CloudBeats{std::vector<double>{7,13,19},std::vector<double>{101}};
  s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"aa",a};
  CloudVideoEvents v;v.timeInSeconds.emplace();v.score.emplace();
  for(int i=0;i<=bars*4;++i){v.timeInSeconds->push_back(i*(60.0/bpm));v.score->push_back(i%16==0?800:i%8==0?600:i%4==0?400:200);}
  CloudFlexAnalysis f;f.videoEvents=v;s.flex=CloudAnalysisResource<CloudFlexAnalysis>{"fa",f};return s;
}
}
int main(){try {
  test("native catalog request order, not storage/ID sort",[]{auto r=resolvePlannerStyleRequests({12,8,9},{{9,8},{12,16},{8,8}});
    check(r.completeForRequestedIds && r.styles.size()==3 && r.styles[0].id==12 && r.styles[1].id==8 && r.maximumBars==16);});
  test("duplicate requests preserved",[]{auto r=resolvePlannerStyleRequests({9,8,9},{{8,8},{9,8}});check(r.styles.size()==3&&r.styles[2].id==9);});
  test("missing requested style clears prefix",[]{auto r=resolvePlannerStyleRequests({8,999},{{8,8}});check(r.status==PlannerStyleResolutionStatus::missingStyle&&r.styles.empty()&&!r.completeForRequestedIds);});
  test("missing suffix defaults four",[]{auto r=resolvePlannerStyleRequests({8},{{8,std::nullopt}});check(r.maximumBars==4&&!r.styles[0].suffixBars);});
  test("explicit zero not defaulted",[]{check(resolvePlannerStyleRequests({8},{{8,0}}).maximumBars==0);});
  test("explicit two not raised to four",[]{check(resolvePlannerStyleRequests({8},{{8,2}}).maximumBars==2);});
  test("empty request has source max default four",[]{auto r=resolvePlannerStyleRequests({},{});check(r.maximumBars==4&&r.styles.empty()&&r.completeForRequestedIds);});
  test("unrequested catalog item does not increase max",[]{check(resolvePlannerStyleRequests({8},{{8,8},{12,100}}).maximumBars==8);});
  test("ambiguous catalog rejected",[]{rejects([]{resolvePlannerStyleRequests({8},{{8,8},{8,16}});});});
  test("negative suffix rejected",[]{rejects([]{resolvePlannerStyleRequests({8},{{8,-1}});});});
  test("catalog/request caps explicit",[]{std::vector<std::int64_t> ids(15,8);check(resolvePlannerStyleRequests(ids,{{8,8}}).status==PlannerStyleResolutionStatus::resourceLimit);
    std::vector<PlannerCandidateStyle> styles(15,{8,8});check(resolvePlannerStyleRequests({},styles).status==PlannerStyleResolutionStatus::resourceLimit);});
  test("duration envelope short one second",[]{auto r=plannerMusicKitDurationEnvelope(1);check(r.preferredOffset==1&&r.maximumOutgoingDuration==1&&r.maximumIncomingDuration==1);});
  test("duration just below sixty",[]{auto r=plannerMusicKitDurationEnvelope(std::nextafter(60.,0.));check(r.preferredOffset==2&&r.maximumIncomingDuration==2);});
  test("duration exactly sixty changes branch",[]{auto r=plannerMusicKitDurationEnvelope(60);check(r.preferredOffset==15&&r.maximumOutgoingDuration==60&&r.maximumIncomingDuration==15);});
  test("duration one twenty",[]{auto r=plannerMusicKitDurationEnvelope(120);check(r.preferredOffset==45&&r.maximumOutgoingDuration==60&&r.maximumIncomingDuration==45);});
  test("duration long incoming capped sixty",[]{auto r=plannerMusicKitDurationEnvelope(300);check(r.preferredOffset==135&&r.maximumIncomingDuration==60);});
  test("bad duration envelope rejected",[]{for(double d:{0.,-1.,std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()})rejects([&]{plannerMusicKitDurationEnvelope(d);});});
  test("scalar main absent removes present edges",[]{check(!normalizePlannerMusicKitScalarComposite(CloudComposite<double>{std::nullopt,.1,.2}));});
  test("scalar main outside range removes map not clamped",[]{check(!normalizePlannerMusicKitScalarComposite(CloudComposite<double>{1.1,.1,.2}));check(!normalizePlannerMusicKitScalarComposite(CloudComposite<double>{-.1,.1,.2}));});
  test("scalar zero one retained, invalid edges absent",[]{auto r=normalizePlannerMusicKitScalarComposite(CloudComposite<double>{0.,-1.,2.});check(r&&r->main==0&&!r->beginning&&!r->ending);
    auto one=normalizePlannerMusicKitScalarComposite(CloudComposite<double>{1.,0.,1.});check(one&&one->main==1&&one->beginning==0&&one->ending==1);});
  test("nonfinite source scalar rejected",[]{rejects([]{normalizePlannerMusicKitScalarComposite(CloudComposite<double>{NAN,0.,0.});});});
  test("MusicKit consumer chooses main not directional edges",[]{auto s=cloud("a");auto t=plannerMusicKitMainTonalComponents(s);check(t.resolved&&t.tonality&&t.tonality->tonic==4&&t.tonality->mode==1&&t.melodicness==.8);});
  test("missing key main not replaced by ending",[]{auto s=cloud("a");s.audio->attributes->key->main.reset();auto t=plannerMusicKitMainTonalComponents(s);check(t.resolved&&!t.tonality&&t.melodicness==.8);});
  test("unresolved audio is resolved absence of main",[]{CloudSongAnalysis s;s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"link",std::nullopt};auto t=plannerMusicKitMainTonalComponents(s);check(t.resolved&&!t.tonality&&!t.melodicness);});
  test("out-of-range melodic main cannot trigger >1 fallback",[]{auto s=cloud("a");s.audio->attributes->melodicness->main=1.1;check(!plannerMusicKitMainTonalComponents(s).melodicness);});
  test("outgoing phase and inclusive time",[]{auto ends=plannerOutgoingSeedEnds(grid(),24);check(ends==std::vector<std::size_t>({48,64,80,96,112,128}));});
  test("outgoing time nextafter excludes equality",[]{auto ends=plannerOutgoingSeedEnds(grid(),std::nextafter(24.,INFINITY));check(ends.front()==64&&ends.size()==5);});
  test("phrase start itself is never an outgoing end",[]{auto ends=plannerOutgoingSeedEnds(grid(),0);check(ends.front()==16);});
  test("no sections does not fall back to stable map",[]{auto s=grid();s.sectionBoundaryEvents.clear();check(plannerOutgoingSeedEnds(s,0).empty());});
  test("phase from first divisible adjacent section pair",[]{auto s=grid();s.sectionBoundaryEvents={4,12,28};for(auto i:s.sectionBoundaryEvents)s.events[i].kind=StructureEventKind::sectionBoundary;
    auto ends=plannerOutgoingSeedEnds(s,0);check(ends.front()==28&&ends[1]==44);});
  test("phase fallback first section when no divisible pair",[]{auto s=grid();s.sectionBoundaryEvents={20,28,40};for(auto i:s.sectionBoundaryEvents)s.events[i].kind=StructureEventKind::sectionBoundary;
    check(plannerOutgoingSeedEnds(s,0).front()==20);});
  test("missing phase downbeat not nearest substituted",[]{auto s=grid();s.sectionBoundaryEvents={20,36};for(auto i:s.sectionBoundaryEvents)s.events[i].kind=StructureEventKind::sectionBoundary;
    s.downbeatEvents.erase(s.downbeatEvents.begin()+1);
    s.bars.erase(std::remove_if(s.bars.begin(),s.bars.end(),[](auto r){return r.startEvent==4||r.endEvent==4;}),s.bars.end());
    check(plannerOutgoingSeedEnds(s,0).empty());});
  test("incoming only first short item removed",[]{auto ends=plannerIncomingSeedEnds(grid(),32,16);check(ends==std::vector<std::size_t>({16,32,48,64}));});
  test("incoming equality max bound is retained",[]{check(plannerIncomingSeedEnds(grid(),24,8).back()==48);});
  test("incoming below max excludes last",[]{check(plannerIncomingSeedEnds(grid(),std::nextafter(24.,0.),8).back()==32);});
  test("incoming zero maximum bars preserves first zero endpoint",[]{check(plannerIncomingSeedEnds(grid(),8,0)==std::vector<std::size_t>({0,16}));});
  test("incoming first equal bar budget is retained",[]{auto s=grid();s.sectionBoundaryEvents={32,48,64};check(plannerIncomingSeedEnds(s,32,8).front()==32);});
  test("incoming request order never sorted",[]{auto s=grid();s.sectionBoundaryEvents={64,32,48};check(plannerIncomingSeedEnds(s,32,8)==std::vector<std::size_t>({64,32,48}));});
  test("stable suffix clips start and preserves end",[]{auto s=grid();s.beatStabilityMap={{{16,64},4,120}};region(plannerStableSeedSuffix(s,{0,48}),16,48);});
  test("stable first matching entry with closed end",[]{auto s=grid();s.beatStabilityMap={{{0,32},4,120},{{16,64},4,120}};region(plannerStableSeedSuffix(s,{0,32}),0,32);});
  test("stable zero-width first match does not retry later match",[]{auto s=grid();s.beatStabilityMap={{{32,64},4,120},{{0,32},4,120}};check(!plannerStableSeedSuffix(s,{0,32}));});
  test("stable missing end coverage gives none",[]{auto s=grid();s.beatStabilityMap={{{0,16},4,120}};check(!plannerStableSeedSuffix(s,{0,32}));});
  test("per-beat 0.030 jitter accepted",[]{auto s=grid();s.events[1].songTime+=.030;region(plannerStableSeedSuffix(s,{0,32}),0,32);});
  test("per-beat 0.032 jitter rejected, distinct from bar threshold",[]{auto s=grid();s.events[1].songTime+=.032;check(!plannerStableSeedSuffix(s,{0,32}));});
  test("stable zero duration removed",[]{auto s=grid();s.events[4].songTime=s.events[0].songTime;s.bars.erase(s.bars.begin()+1);check(!plannerStableSeedSuffix(s,{0,4}));});
  test("mean individual tempos not global/quantized tempo",[]{auto s=grid();for(std::size_t i=0;i<s.events.size();++i)s.events[i].songTime=i<=4?double(i):4.+double(i-4)*2.;
    auto t=plannerSeedMeanBarTempo(s,{0,8});check(t.has_value());near(*t,45.);});
  test("tempo region needs fully contained bars",[]{auto s=grid();s.bars.clear();check(!plannerSeedMeanBarTempo(s,{0,32}));});
  test("source unquantized tempo survives cached map distractor",[]{auto s=grid(32,123.2);s.beatStabilityMap[0].averageTempoBpm=80.;near(*plannerSeedMeanBarTempo(s,{0,32}),123.2);});
  test("zero-length tempo bar rejected",[]{auto s=grid();s.bars[0]={0,0};rejects([&]{plannerSeedMeanBarTempo(s,{0,32});});});
  test("absent loudness passes",[]{check(plannerOutgoingSeedNonSilent(nullptr,{0,10}));});
  test("present empty loudness fails",[]{PlannerLoudnessMap m;check(!plannerOutgoingSeedNonSilent(&m,{0,10}));});
  test("closed loudness interval includes both endpoints",[]{PlannerLoudnessMap m{{-20,0},{-20,1}};check(plannerOutgoingSeedNonSilent(&m,{0,1}));});
  test("unavailable regression still checks mean",[]{PlannerLoudnessMap m{{-20,0}};check(!plannerLoudnessLinearSlope(m)&&plannerOutgoingSeedNonSilent(&m,{0,1}));});
  test("mean minus thirty exactly fails",[]{PlannerLoudnessMap m{{-30,0}};check(!plannerOutgoingSeedNonSilent(&m,{0,1}));m[0].value=std::nextafter(-30.,0.);check(plannerOutgoingSeedNonSilent(&m,{0,1}));});
  test("unit positive and negative slopes rejected",[]{PlannerLoudnessMap a{{-20,0},{-19,1}},b{{-20,0},{-21,1}};check(!plannerOutgoingSeedNonSilent(&a,{0,1})&&!plannerOutgoingSeedNonSilent(&b,{0,1}));});
  test("half-unit slope passes",[]{PlannerLoudnessMap m{{-20,0},{-19.5,1}};near(*plannerLoudnessLinearSlope(m),.5);check(plannerOutgoingSeedNonSilent(&m,{0,1}));});
  test("equal-time regression absent, mean still usable",[]{PlannerLoudnessMap m{{-20,1},{-18,1}};check(!plannerLoudnessLinearSlope(m)&&plannerOutgoingSeedNonSilent(&m,{1,1}));});
  test("no samples in window does not fall back",[]{PlannerLoudnessMap m{{-20,10}};check(!plannerOutgoingSeedNonSilent(&m,{0,1}));});
  test("nonfinite loudness and reversed windows rejected",[]{PlannerLoudnessMap m{{NAN,0}};rejects([&]{plannerLoudnessLinearSlope(m);});rejects([]{plannerOutgoingSeedNonSilent(nullptr,{1,0});});});
  test("full automatic ordered seed discovery",[]{auto a=prep(grid()),z=prep(grid());auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{24,24});
    check(r.status==PlannerSeedProductionStatus::produced&&r.completeForResolvedInputs&&r.seeds.size()==18&&r.pairAttempts==18);
    check(r.outgoingEnds==6&&r.incomingEnds==3&&r.outgoingNonSilent==6);
    check(r.seeds[0].outgoing.startEvent==16&&r.seeds[0].outgoing.endEvent==48&&r.seeds[0].incoming.startEvent==0&&r.seeds[0].incoming.endEvent==16);
    check(r.seeds[1].incoming.endEvent==32&&r.seeds[2].incoming.startEvent==16&&r.seeds[3].outgoing.endEvent==64&&r.seeds.back().outgoing.endEvent==128);
    check(r.seeds[0].incomingUnit==PlannerRegionUnit::beats&&!r.canExecute);});
  test("producer max suffix uses all requested descriptors",[]{auto a=prep(grid()),z=prep(grid());auto r=producePlannerCandidateSeeds(a,z,{{8,8},{12,16}},{32,32});
    check(r.seeds.front().outgoing.startEvent==0);});
  test("producer absent style count really four",[]{auto a=prep(grid()),z=prep(grid());auto r=producePlannerCandidateSeeds(a,z,{{8,std::nullopt}},{24,24});check(r.seeds[0].outgoing.startEvent==32);});
  test("producer zero count gives no stable seed",[]{auto a=prep(grid()),z=prep(grid());auto r=producePlannerCandidateSeeds(a,z,{{8,0}},{24,24});check(r.status==PlannerSeedProductionStatus::noSeeds&&r.seeds.empty()&&r.completeForResolvedInputs);});
  test("half scale doubles incoming then truncates",[]{auto a=prep(grid()),z=prep(grid(64,240));auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{56,16});
    check(r.seeds.size()==8);auto s=r.seeds[3];check(s.incomingScale==TempoBinaryScale::half&&s.incoming.startEvent==0&&s.incoming.endEvent==64);});
  test("double scale halves incoming beats",[]{auto a=prep(grid()),z=prep(grid(32,60));auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{56,48});
    check(r.seeds.size()==6&&r.seeds[1].incomingScale==TempoBinaryScale::two&&r.seeds[1].incoming.startEvent==16&&r.seeds[1].incoming.endEvent==32);});
  test("scale search uses bar means not stability-map quantization",[]{auto a=prep(grid()),z=prep(grid(64,240));z.maps.structure->beatStabilityMap[0].averageTempoBpm=120;
    auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{56,16});check(r.seeds[3].incomingScale==TempoBinaryScale::half);});
  test("present silent outgoing map removes all outgoing seeds",[]{auto a=prep(grid()),z=prep(grid());a.maps.loudness=PlannerLoudnessMap{};auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{24,24});
    check(r.outgoingStable==6&&r.outgoingNonSilent==0&&r.seeds.empty()&&r.completeForResolvedInputs);});
  test("only outgoing loudness affects endpoint discovery",[]{auto a=prep(grid()),z=prep(grid());z.maps.loudness=PlannerLoudnessMap{};check(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24}).seeds.size()==18);});
  test("preview absence cannot affect producer",[]{auto a=prep(grid()),z=prep(grid());a.inventory.emplace();z.inventory.emplace();check(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24}).seeds.size()==18);});
  test("unknown playback duration does not use catalog",[]{auto a=prep(grid()),z=prep(grid());a.durationMs.reset();auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{24,24});check(r.status==PlannerSeedProductionStatus::unresolvedPlaybackDuration&&!r.completeForResolvedInputs);});
  test("invalid preparation and missing structure stay distinct",[]{auto a=prep(grid()),z=prep(grid());a.status=PlannerPreparationStatus::invalid;check(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24}).status==PlannerSeedProductionStatus::invalidPreparation);
    a.status=PlannerPreparationStatus::prepared;a.maps.structure.reset();check(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24}).status==PlannerSeedProductionStatus::insufficientStructure);});
  test("zero work budget clears output",[]{auto a=prep(grid()),z=prep(grid());emptyResource(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24},0));});
  test("oversized producer work budget rejected",[]{auto a=prep(grid()),z=prep(grid());emptyResource(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24},kPlannerProducerWorkBudget+1));});
  test("exhaustion after useful work publishes no prefix",[]{auto a=prep(grid()),z=prep(grid());auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{24,24});check(r.workUnits>1);emptyResource(producePlannerCandidateSeeds(a,z,{{8,8}},{24,24},r.workUnits-1));});
  test("Cartesian product limit not early winner",[]{auto a=prep(grid(128),256),z=prep(grid(128),256);emptyResource(producePlannerCandidateSeeds(a,z,{{8,8}},{0,100}));});
  test("invalid event references rejected",[]{auto s=grid();s.downbeatEvents[0]=9999;rejects([&]{plannerOutgoingSeedEnds(s,0);});});
  test("negative ordinal and bad event kind rejected",[]{auto s=grid();s.events[0].beatIndex=-1;rejects([&]{plannerIncomingSeedEnds(s,20,8);});s=grid();s.events[0].kind=static_cast<StructureEventKind>(4);rejects([&]{plannerOutgoingSeedEnds(s,0);});});
  test("out-of-track produced seed not silently clipped",[]{auto a=prep(grid(),50),z=prep(grid());rejects([&]{producePlannerCandidateSeeds(a,z,{{8,8}},{24,24});});});
  test("bad discovery bound rejected",[]{auto a=prep(grid()),z=prep(grid());rejects([&]{producePlannerCandidateSeeds(a,z,{{8,8}},{-1,24});});rejects([&]{producePlannerCandidateSeeds(a,z,{{8,8}},{24,129});});});
  test("three-beat meter uses actual event indices",[]{auto a=prep(grid(32,120,3)),z=prep(grid(32,120,3));auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{18,18});
    check(r.seeds.size()==18&&r.seeds.front().outgoing.startEvent==12&&r.seeds.front().outgoing.endEvent==36);});
  test("input maps immutable",[]{auto a=prep(grid()),z=prep(grid());auto original=a.maps.structure->events;auto r=producePlannerCandidateSeeds(a,z,{{8,8}},{24,24});check(!r.seeds.empty());
    for(std::size_t i=0;i<original.size();++i)check(original[i].songTime==a.maps.structure->events[i].songTime&&original[i].kind==a.maps.structure->events[i].kind);});
  std::cout<<"Input producers: "<<passed<<"/"<<passed<<" groups PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
