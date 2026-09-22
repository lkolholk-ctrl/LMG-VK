#include "lmg/automix/playback_time.h"
#include "lmg/automix/continuous_schedule.h"
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{}; f.read(reinterpret_cast<char*>(&v),sizeof v); CHECK(f.good()); return v; }
void reference() {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/playback_time.bin",std::ios::binary);
  const auto count=read<std::uint32_t>(f); CHECK(count==256);
  for (unsigned i=0;i<count;++i) {
    const auto inverse=read<std::uint32_t>(f);
    PlaybackTime anchor{read<double>(f),read<double>(f),read<double>(f)};
    RateRamp ramp{read<double>(f),read<double>(f),read<double>(f),read<double>(f)};
    const auto query=read<double>(f);
    double expected[3]={read<double>(f),read<double>(f),read<double>(f)};
    PlaybackTimeMap map(anchor,ramp);
    const auto actual=inverse?map.fromTransition(query):map.fromSong(query);
    double values[3]={actual.song,actual.stretchedSong,actual.transition};
    for (unsigned j=0;j<3;++j) if (std::memcmp(&values[j],&expected[j],sizeof(double))) {
      std::cerr<<"Case "<<i<<" component "<<j<<" got "<<std::hexfloat<<values[j]<<" expected "<<expected[j]<<'\n';
      throw std::runtime_error("ARM time mapping mismatch");
    }
  }
  CHECK(f.peek()==std::char_traits<char>::eof());
}
template<class F> void rejects(F f) { bool yes=false;try{f();}catch(const std::invalid_argument&){yes=true;} CHECK(yes); }
void wrappers() {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/playback_wrappers.bin",std::ios::binary);
  const auto count=read<std::uint32_t>(f); CHECK(count==64);
  for (unsigned i=0;i<count;++i) {
    const auto inverse=read<std::uint32_t>(f), present=read<std::uint32_t>(f);
    PlaybackTime anchor{read<double>(f),read<double>(f),read<double>(f)};
    RateRamp ramp{read<double>(f),read<double>(f),read<double>(f),read<double>(f)};
    const double first=read<double>(f), last=read<double>(f);
    double expected[3]={read<double>(f),read<double>(f),read<double>(f)};
    PlaybackTimeMap map=present ? PlaybackTimeMap(anchor,ramp) : PlaybackTimeMap(anchor);
    double actual[3]{};
    if (inverse) {
      auto t=map.fromTransition(first); actual[0]=t.song; actual[1]=t.stretchedSong; actual[2]=t.transition;
    } else actual[0]=map.stretchedDuration(first,last);
    CHECK(std::memcmp(actual,expected,sizeof actual)==0);
  }
  CHECK(f.peek()==std::char_traits<char>::eof());
}
void domains() {
  PlaybackTimeMap map({20,30,5},{.8,1.2,22,30});
  for(double song:{-10.,19.,20.,21.,22.,25.,30.,35.}) {
    auto t=map.fromSong(song); auto back=map.fromTransition(t.transition);
    CHECK(std::abs(back.song-song)<1e-12);
  }
  CHECK(map.fromSong(19).stretchedSong==29);
  CHECK(map.fromTransition(4).song==19);
  PlaybackTimeMap plain({20,30,5}); CHECK(plain.fromSong(50).stretchedSong==60);
  CHECK(plain.fromTransition(35).song==50);
  rejects([&]{map.fromSong(std::numeric_limits<double>::quiet_NaN());});
  rejects([&]{PlaybackTimeMap m({0,0,0},{0,1,2,3});});
  rejects([&]{PlaybackTimeMap m({0,0,0},{1,1,3,2});});
  rejects([&]{PlaybackTimeMap m({4,0,0},{1,1,2,3});});
}
void anchors() {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/playback_anchors.bin",std::ios::binary);
  const auto count=read<std::uint32_t>(f); CHECK(count==12);
  for (unsigned i=0;i<count;++i) {
    const bool present=read<std::uint32_t>(f)!=0;
    const double transition=read<double>(f), song=read<double>(f);
    TimeStretchingState previous{read<double>(f),read<double>(f)};
    double expected[3]={read<double>(f),read<double>(f),read<double>(f)};
    const auto a=playbackAnchor(transition,song,present ? std::optional<TimeStretchingState>(previous) : std::nullopt);
    double actual[3]={a.song,a.stretchedSong,a.transition};
    CHECK(std::memcmp(actual,expected,sizeof actual)==0);
    const auto start=PlaybackTimeMap(a).fromTransition(transition);
    CHECK(start.song==song && start.stretchedSong==a.stretchedSong);
  }
  CHECK(f.peek()==std::char_traits<char>::eof());
  rejects([]{playbackAnchor(0,std::numeric_limits<double>::infinity());});
  rejects([]{playbackAnchor(0,0,TimeStretchingState{0,std::numeric_limits<double>::quiet_NaN()});});
  rejects([]{playbackAnchor(0,std::numeric_limits<double>::max(),TimeStretchingState{0,std::numeric_limits<double>::max()});});
}
void scheduleSelection() {
  const auto descriptor=playbackRateParameter();
  ContinuousAutomation rate{descriptor,{{.8,22,0x80},{1.2,30,0x41},{.5,40,std::nullopt}}};
  auto ramps=continuousRamps(rate.points);
  CHECK(ramps.size()==2 && ramps[0].curve==0x80 && ramps[1].curve==0x41);
  CHECK(ramps[1].startValue==1.2 && ramps[1].endValue==.5);
  CHECK(continuousRamps({{1,2,0x80},{2,2,std::nullopt}}).size()==1);
  rejects([]{continuousRamps({{1,3,0x80},{2,2,std::nullopt}});});
  rejects([]{continuousRamps({{1,2,std::nullopt},{2,3,0x80}});});
  CHECK(!selectedPlaybackRateRamp({}));
  CHECK(!selectedPlaybackRateRamp({{descriptor,{}},rate}));
  CHECK(!selectedPlaybackRateRamp({{descriptor,{{1,20,std::nullopt}}},rate}));
  for (unsigned field=0;field<5;++field) {
    auto wrong=rate;
    if(field==0) wrong.parameter.id="out_gain";
    if(field==1) wrong.parameter.minimum=0;
    if(field==2) wrong.parameter.maximum=2;
    if(field==3) wrong.parameter.defaultValue=.5;
    if(field==4) wrong.parameter.styleParameterId="different";
    wrong.points={{.5,21,0x80},{.5,23,std::nullopt}};
    auto selected=selectedPlaybackRateRamp({wrong,rate});
    CHECK(selected && selected->startRate==.8 && selected->endRate==1.2);
  }
  const auto actual=schedulePlaybackTimeMap(5,20,TimeStretchingState{10,12},{rate});
  const PlaybackTimeMap expected({20,22,5},{.8,1.2,22,30});
  for (double t:{-1.,5.,8.,20.,40.}) {
    const auto a=actual.fromTransition(t), b=expected.fromTransition(t);
    CHECK(a.song==b.song && a.stretchedSong==b.stretchedSong && a.transition==b.transition);
  }
  CHECK(schedulePlaybackTimeMap(5,20,std::nullopt,{}).fromTransition(10).song==25);
  auto bad=rate; bad.points[2].songTime=29;
  rejects([&]{selectedPlaybackRateRamp({bad});}); // whole ramp array validated before taking first
}
void continuousValues() {
  const auto p=playbackRateParameter();
  CHECK(ContinuousAutomationValues({p,{}}).valueAt(-100)==1);
  ContinuousAutomationValues single({p,{{.75,2,std::nullopt}}});
  CHECK(single.valueAt(-100)==.75 && single.valueAt(100)==.75);
  ContinuousAutomationValues linear({p,{{2,.125,0x80},{4,.625,std::nullopt}}});
  CHECK(linear.valueAt(0)==2 && linear.valueAt(.375)==3 && linear.valueAt(1)==4);
  // A run of coincident points chooses the last eligible ramp, including its
  // end-before-start boundary rule. Do not sort or collapse the input points.
  ContinuousAutomationValues steps({p,{{1,0,0x80},{2,1,0x80},{3,1,0x80},{4,1,0x80},{5,2,std::nullopt}}});
  CHECK(steps.valueAt(1)==4 && steps.valueAt(1.5)==4.5);
  ContinuousAutomationValues terminal({p,{{1,0,0x80},{2,1,0x80},{3,1,std::nullopt}}});
  CHECK(terminal.valueAt(1)==3);
  ContinuousAutomationValues logarithmic({p,{{1,0,0x81},{16,2,std::nullopt}}});
  CHECK(logarithmic.valueAt(1)==4);
  ContinuousAutomationValues squared({p,{{0,0,1},{1,2,std::nullopt}}});
  CHECK(squared.valueAt(1)==.25);
  rejects([&]{linear.valueAt(std::numeric_limits<double>::quiet_NaN());});
  rejects([&]{ContinuousAutomationValues v({p,{{0,0,0x81},{1,2,std::nullopt}}});});
}
void bypassGate() {
  const auto automations=withStyleBypass({{playbackRateParameter(),{}}},2,4);
  CHECK(automations.size()==2 && automations[0].parameter.id=="bypa");
  CHECK(automations[0].points.size()==4 && automations[1].parameter.id=="ts_rate");
  ContinuousAutomationValues gate(automations[0]);
  CHECK(gate.valueAt(1)==1 && gate.valueAt(2)==0 && gate.valueAt(3)==0 && gate.valueAt(4)==1 && gate.valueAt(5)==1);
  CHECK(withStyleBypass(automations,0,8).size()==2);
  CHECK(withStyleBypass({},2,2).empty());
  CHECK(withStyleBypass({{bypassParameter(),{}}},2,4).front().points.empty());
  auto wrong=bypassParameter();wrong.defaultValue=0;
  CHECK(withStyleBypass({{wrong,{}}},2,4).size()==2);
  rejects([]{withStyleBypass({},4,2);});
}
int main(){try{reference();wrappers();domains();anchors();scheduleSelection();continuousValues();bypassGate();std::cout<<"332 ARM playback time cases and continuous schedule tests passed\n";}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
