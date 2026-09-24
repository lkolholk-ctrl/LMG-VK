#include "support/schedule_fixtures.h"
#include <cmath>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
using namespace lmg::automix;
using namespace schedule_test;
namespace {
std::size_t checks=0,groups=0;
void check(bool ok){++checks;if(!ok)throw std::runtime_error("schedule assertion "+std::to_string(checks));}
void close(double a,double b){check(std::abs(a-b)<=1e-11*std::max(1.0,std::max(std::abs(a),std::abs(b))));}
template<class F>void rejects(F f){bool raised=false;try{f();}catch(const std::invalid_argument&){raised=true;}check(raised);}
void ratesTests() {
  auto r=plannerStructuredPlaybackRates({104,120},{0,16},32,32,TempoBinaryScale::one);
  check(r.scaledBeatRatio==1&&r.effectiveIncomingDuration==16&&r.outgoing.start==1&&r.outgoing.end==1&&r.incoming.start==1&&r.incoming.end==1);++groups;
  r=plannerStructuredPlaybackRates({10,26},{0,12.8},32,32,TempoBinaryScale::one);
  close(r.outgoing.end,1.25);close(r.incoming.start,.8);++groups;
  r=plannerStructuredPlaybackRates({10,26},{4,20},32,16,TempoBinaryScale::one);
  check(r.scaledBeatRatio==.5&&r.effectiveIncomingDuration==32);close(r.outgoing.end,.5);close(r.incoming.start,1.5);++groups;
  r=plannerStructuredPlaybackRates({0,16},{0,16},32,64,TempoBinaryScale::one);
  check(r.scaledBeatRatio==2&&r.effectiveIncomingDuration==8);close(r.incoming.start,.5);++groups;
  for(auto s:{TempoBinaryScale::half,TempoBinaryScale::one,TempoBinaryScale::two}) {
    r=plannerStructuredPlaybackRates({.125,20.125},{.25,20.25},40,40,s);
    const double f=s==TempoBinaryScale::half?.5:s==TempoBinaryScale::one?1:2;
    close(r.scaledBeatRatio,f);close(r.outgoing.end,f);check(r.outgoing.start==1&&r.incoming.end==1);
  }++groups;
  const double nan=std::numeric_limits<double>::quiet_NaN(), inf=std::numeric_limits<double>::infinity();
  for(double v:{nan,inf,-inf,-1.0}) { rejects([&]{plannerStructuredPlaybackRates({v,10},{0,10},20,20,TempoBinaryScale::one);}); }
  ++groups;
  for(auto n:{-1LL,0LL,4097LL,std::numeric_limits<long long>::max()}) { rejects([&]{plannerStructuredPlaybackRates({0,10},{0,10},n,20,TempoBinaryScale::one);}); }
  ++groups;
  rejects([&]{plannerStructuredPlaybackRates({0,10},{0,10},20,20,static_cast<TempoBinaryScale>(3));});++groups;
  for(auto w:{SecondsWindow{0,0},SecondsWindow{5,4},SecondsWindow{-1,4}}) { rejects([&]{plannerStructuredPlaybackRates(w,{0,10},20,20,TempoBinaryScale::one);}); }
  ++groups;
}
void compileTests() {
  const auto st=grid();auto c=candidate();auto s=style(9);
  auto p=compilePlannerTransitionSchedule(c,st,st,s);
  check(!p.canExecute&&p.styleId()==9&&p.incomingScale()==TempoBinaryScale::one);
  check(p.transitionRange().begin==0&&p.transitionRange().end==16&&p.referenceTransitionTime()==8);++groups;
  check(p.outgoing().startPlaybackSongTime==104&&p.incoming().startPlaybackSongTime==0&&p.outgoing().beatCount==32);++groups;
  check(p.outgoing().automations.size()==4&&p.incoming().automations.size()==3);++groups;
  const auto& gate=p.outgoing().automations[0];check(gate.parameter.id=="bypa"&&gate.points.size()==4);
  check(gate.points[0].songTime==104&&gate.points[1].songTime==104&&gate.points[2].songTime==120);
  check(ContinuousAutomationValues(gate).valueAt(104)==0&&ContinuousAutomationValues(gate).valueAt(120)==1);++groups;
  const auto& rate=p.outgoing().automations[1];check(rate.points[0].value==1&&rate.points[1].value==1&&rate.points[0].curve==0x80);++groups;
  const auto& lp=p.outgoing().automations[3];check(lp.points[0].songTime==108&&lp.points[1].songTime==116);
  close(ContinuousAutomationValues(lp).valueAt(112),std::sqrt(22000.0*10));++groups;
  auto map=p.outgoing().timeMap();check(map.fromSong(104).transition==0&&map.fromSong(120).transition==16);++groups;
  auto variable=st;for(auto& e:variable.events)e.songTime*=.8;
  c.incomingEnd=12.8;p=compilePlannerTransitionSchedule(c,st,variable,s);
  close(p.outgoing().rates.end,1.25);close(p.incoming().rates.start,.8);
  const double ta=16*std::log(1.25)/.25, tb=12.8*std::log(1.25)/.2;
  close(p.outgoing().stretchedRegionDuration,ta);close(p.incoming().stretchedRegionDuration,tb);++groups;
  close(p.transitionRange().end,ta);close(p.incoming().playbackTransitionRange.begin,std::max(0.0,ta-tb));++groups;
  // Inverse consistency at multiple points. This is a property check, not an
  // independent oracle for the existing map's complete numerical semantics.
  for(auto* side:{&p.outgoing(),&p.incoming()}) {
    auto m=side->timeMap();for(int i=0;i<=20;++i) {
      const double t=side->sourceRegion.begin+(side->sourceRegion.end-side->sourceRegion.begin)*i/20;
      close(m.fromTransition(m.fromSong(t).transition).song,t);
    }
  }++groups;
  // Unequal scaled counts exercise BOTH branches of the original asymmetric placement.
  auto slow=st;for(auto& e:slow.events)e.songTime*=2;
  c=candidate();c.incoming.endEvent=16;s=style(9);
  p=compilePlannerTransitionSchedule(c,st,slow,s);
  const double slowOut=32*std::log(2.0), slowIn=32*std::log(1.5);
  close(p.transitionRange().end,slowOut);close(p.incoming().playbackTransitionRange.begin,slowOut-slowIn);
  close(p.referenceTransitionTime(),slowOut-slowIn*.5);++groups;
  auto fast=st;for(auto& e:fast.events)e.songTime*=.5;
  c=candidate();c.incoming.endEvent=64;
  p=compilePlannerTransitionSchedule(c,st,fast,s);
  const double fastOut=16*std::log(2.0), fastIn=32*std::log(2.0);
  close(p.transitionRange().end,fastOut);close(p.incoming().stretchedRegionDuration,fastIn);
  check(p.incoming().stretchedRegionDuration>p.transitionRange().end && p.incoming().playbackTransitionRange.begin==0);
  close(p.referenceTransitionTime(),fastOut*.5);++groups;
  c=candidate();s=style(9);s.outgoing.push_back(s.outgoing[1]);p=compilePlannerTransitionSchedule(c,st,st,s);
  check(p.outgoing().automations.size()==5&&p.outgoing().automations[2].parameter.id=="out_gain"&&p.outgoing().automations[4].parameter.id=="out_gain");++groups;
  s=style(9);s.outgoing.clear();s.incoming.clear();p=compilePlannerTransitionSchedule(c,st,st,s);
  check(p.outgoing().automations.size()==1&&p.outgoing().automations[0].parameter.id=="bypa");++groups;
  s=style(8);rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  s=style(9);s.offset->relative=.5;rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  s=style(9);s.outgoing[0].ramps[0].from.parameterName="unresolved";rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  s=style(9);s.outgoing[2].ramps[0].from.fallback=0;rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  s=style(9);s.outgoing.resize(33,s.outgoing[0]);rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  s=style(9);c.outgoingStart=103;rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  c=candidate();c.outgoing.endEvent=4096;rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
  c=candidate();auto bad=st;bad.events[210].songTime=100;rejects([&]{compilePlannerTransitionSchedule(c,bad,st,s);});++groups;
  c=candidate();c.score=std::numeric_limits<double>::quiet_NaN();rejects([&]{compilePlannerTransitionSchedule(c,st,st,s);});++groups;
}
void freshnessTests() {
  using F=PlannerScheduleFreshness;
  check(checkPlannerScheduleFreshness(7,1,7,1,104,103.99)==F::positionNotPastStart);
  check(checkPlannerScheduleFreshness(7,1,7,1,104,104)==F::positionNotPastStart);
  check(checkPlannerScheduleFreshness(7,1,7,1,104,std::nextafter(104.,105.))==F::startAlreadyPassed);++groups;
  check(checkPlannerScheduleFreshness(7,1,8,1,104,0)==F::staleGeneration);
  check(checkPlannerScheduleFreshness(7,1,7,2,104,0)==F::staleRevision);++groups;
  check(checkPlannerScheduleFreshness(7,1,7,1,104,std::nullopt)==F::positionUnavailable);
  check(checkPlannerScheduleFreshness(7,1,7,1,104,-1)==F::invalidPosition);++groups;
}
}
int main(int argc,char**argv){try{
 if(argc==3&&std::string(argv[1])=="--reference") {
  std::ifstream f(argv[2]);check(bool(f));std::string line;std::size_t rows=0;
  while(std::getline(f,line)){if(line.empty()||line[0]=='#')continue;std::istringstream row(line);
   double a0,a1,b0,b1,ratio,e,ao,bi;int na,nb,scale;
   check(bool(row>>a0>>a1>>b0>>b1>>na>>nb>>scale>>ratio>>e>>ao>>bi));std::string extra;check(!(row>>extra));
   auto r=plannerStructuredPlaybackRates({a0,a1},{b0,b1},na,nb,static_cast<TempoBinaryScale>(scale));
   close(r.scaledBeatRatio,ratio);close(r.effectiveIncomingDuration,e);close(r.outgoing.end,ao);close(r.incoming.start,bi);++rows;
  }
  check(rows==560);std::cout<<"Structured schedule independent reference: "<<rows<<" rows PASSED\n";return 0;
 }
 ratesTests();compileTests();freshnessTests();
 std::cout<<"Structured transition schedule: "<<groups<<" groups / "<<checks<<" assertions PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
