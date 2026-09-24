#pragma once
// Deliberately small TEST catalog, not a replacement for Apple's canonical asset.
// Full-catalog and raw-JSON JNI tests load the original resource on the server.
#include "lmg/automix/planner_schedule_observation.h"
#include <cstring>
#include <limits>

namespace schedule_test {
using namespace lmg::automix;
inline std::int64_t bits(double d){std::int64_t w;std::memcpy(&w,&d,8);return w;}
inline double real(std::int64_t w){double d;std::memcpy(&d,&w,8);return d;}
inline CloudSongAnalysis cloud(const char* id,int duration=120000,int bars=60,double bpm=120) {
  CloudSongAnalysis s;s.id=id;s.durationInMillis=duration;s.supportsSmartTransitions=true;
  CloudAudioAnalysis audio;
  CloudComposite<CloudKey> key;key.main=CloudKey{std::string("C"),std::string("minor")};audio.key=key;
  CloudComposite<double> melodic;melodic.main=.8;audio.melodicness=melodic;
  audio.vocalActivity=std::vector<CloudVocalActivity>{};
  s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"aa",audio};
  CloudVideoEvents events;events.timeInSeconds=std::vector<double>{};events.score=std::vector<double>{};
  for(int i=0;i<=bars*4;++i) {
    events.timeInSeconds->push_back(i*(60.0/bpm));
    events.score->push_back(i%16==0?800:i%8==0?600:i%4==0?400:200);
  }
  CloudFlexAnalysis flex;flex.videoEvents=events;s.flex=CloudAnalysisResource<CloudFlexAnalysis>{"ff",flex};
  return s;
}
inline StyleInstruction lane(const char* name,const char* id,double v0,double v1,
                             const char* curve="linear",double begin=0,double end=1) {
  return {name,{begin,std::nullopt},{end,std::nullopt},
    {{id,{0,std::nullopt},{1,std::nullopt},{v0,std::nullopt},{v1,std::nullopt},curve}}};
}
inline TransitionStyle style(int id) {
  TransitionStyle s;s.id=id;s.name="Synthetic schedule fixture";s.offset=StyleTime{0,std::nullopt};
  s.duration=id==12?16:8;
  // Resource ts endpoints deliberately disagree: source compiler must override.
  s.outgoing={lane("TimeStretching","ts_rate",9,11,"ease-in-4"),
      lane("GAIN","out_gain",1,0,"ease-in-0.5"),
      lane("LP","lp_cutoff_freq",22000,10,"logarithmic",.25,.75)};
  s.incoming={lane("TimeStretching","ts_rate",9,11,"ease-in-4"),
      lane("GAIN","out_gain",0,1,"ease-out-0.5")};
  return s;
}
inline std::vector<TransitionStyle> catalog(){return {style(8),style(9),style(12)};}
inline std::vector<std::int64_t> request(int duration=120000) {
  std::vector<std::int64_t> q(24);q[0]=kPlannerSourceRequestMagic;q[1]=1;q[2]=24;q[3]=71;q[4]=1;
  q[5]=3;q[6]=q[7]=duration;q[9]=16000000;q[10]=1;q[11]=q[12]=q[13]=q[14]=1;
  q[15]=q[17]=2;q[20]=3;return q;
}
inline SongStructure grid(int count=240) {
  std::vector<FlexEvent> events;for(int i=0;i<=count;++i)
    events.push_back({i*.5,i%16==0?FlexTimeScale::extraLong:i%8==0?FlexTimeScale::longTime:
      i%4==0?FlexTimeScale::medium:FlexTimeScale::shortTime,0});
  return songStructureFromFlexEvents(events);
}
inline PlannerScoredCandidate candidate(int id=9) {
  PlannerScoredCandidate c{};c.styleId=id;c.outgoing={208,240};c.incoming={0,32};
  c.incomingScale=TempoBinaryScale::one;c.outgoingStart=104;c.outgoingEnd=120;
  c.incomingStart=0;c.incomingEnd=16;c.score=15.104;return c;
}
} // namespace schedule_test
