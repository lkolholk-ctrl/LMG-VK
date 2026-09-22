#include "lmg/automix/time_pitch_state.h"
#include <algorithm>
#include <cstring>
#include <cmath>
#include <limits>
#include <stdexcept>
namespace lmg::automix {
void timePitchResetStream(TimePitchStreamState& s,std::uint32_t n,bool scheduled,
                         double inputTime,double outputTime) noexcept {
  s.inputPadding=scheduled?n/2:n;
  s.inputWrite=s.inputPadding;s.inputRead=0;s.outputWrite=0;
  s.outputRead=scheduled?n/2:0;s.outputClear=0;s.outputPadding=s.outputRead;
  s.historyIndex=0;s.totalInput=0;s.totalOutput=0;s.inputAnchorValid=false;
  s.inputAnchorTime=-999999;s.inputAnchorFrame=-999999;s.inputAnchorOther=-999999;
  for(auto& entry:s.history)entry=TimePitchHistoryEntry{};
  s.outputAnchorValid=true;s.deliveredFrames=0;s.outputAnchorTime=0;
  s.outputAnchorFrame=0;s.outputAnchorOther=0;
  s.inputTime=inputTime;s.outputTime=outputTime;
  s.nextInputTime=inputTime;s.nextOutputTime=outputTime;
  s.inputTimePending=true;s.outputPullTime=outputTime;s.sourcePullTime=inputTime;
  s.mappingReset=true;s.spectralActive=false;s.phaseInitialized=true;
}
void timePitchCommitHop(TimePitchStreamState& s,double inputHop,double outputHop) noexcept {
  const auto relativeInput=s.inputRead-s.inputPadding;
  const double timestamp=s.inputAnchorTime+static_cast<double>(relativeInput-s.inputAnchorFrame);
  ++s.historyIndex;
  s.history[s.historyIndex&63]={true,timestamp,relativeInput,s.outputWrite-s.outputPadding};
  s.processingInputTime=s.sourcePullTime-static_cast<double>(s.inputWrite-s.inputRead);
  s.inputTime=s.nextInputTime;s.outputTime=s.nextOutputTime;
  s.inputRead=static_cast<std::int64_t>(static_cast<double>(s.inputRead)+inputHop);
  s.outputWrite=static_cast<std::int64_t>(static_cast<double>(s.outputWrite)+outputHop);
}
namespace {
std::int64_t wrapAdd(std::int64_t a,std::int64_t b) noexcept {
 const auto bits=static_cast<std::uint64_t>(a)+static_cast<std::uint64_t>(b);
 std::int64_t result;std::memcpy(&result,&bits,8);return result;
}
std::int64_t wrapSubtract(std::int64_t a,std::int64_t b) noexcept {
 const auto bits=static_cast<std::uint64_t>(a)-static_cast<std::uint64_t>(b);
 std::int64_t result;std::memcpy(&result,&bits,8);return result;
}
std::int64_t floorInteger(double value) noexcept {
 if(value>=0x1p63)return std::numeric_limits<std::int64_t>::max();
 if(value<=-0x1p63)return std::numeric_limits<std::int64_t>::min();
 return static_cast<std::int64_t>(std::floor(value));
}
}
double timePitchHistorySourceTime(const TimePitchStreamState& s,std::uint32_t n,
 double hop,const float* window,double time) {
 if(!window||n<256||n>8192||(n&(n-1))||!std::isfinite(hop)||hop<=0||
    std::ceil(n/hop)>64||!std::isfinite(time))
  throw std::invalid_argument("Invalid TimePitch history query");
 const auto count=static_cast<unsigned>(std::ceil(n/hop));
 const auto frame=wrapAdd(s.outputAnchorFrame,floorInteger((time+.5)-s.outputAnchorTime));
 double weighted=0,weight=0;
 for(unsigned i=0;i<count;++i){
  const auto& h=s.history[(s.historyIndex-i)&63];
  const auto delta=wrapSubtract(frame,h.outputFrame);
  if(!h.active||delta<0||wrapAdd(h.outputFrame,n)<=frame)continue;
  const float w=window[delta];
  weight+=static_cast<double>(w);
  const float product=w*static_cast<float>(wrapAdd(h.inputFrame,delta));
  weighted+=static_cast<double>(product);
 }
 const auto source=weight==0?0:floorInteger(weighted/weight+.5);
 return s.inputAnchorTime+static_cast<double>(wrapSubtract(source,s.inputAnchorFrame));
}
void timePitchCopyInputFrame(const float* ring,std::uint32_t ringSize,std::int64_t cursor,
                            float* frame,std::uint32_t n) noexcept {
  const auto offset=static_cast<std::uint64_t>(cursor)&(ringSize-1);
  const auto first=std::min<std::uint32_t>(n,ringSize-static_cast<std::uint32_t>(offset));
  std::memcpy(frame,ring+offset,first*sizeof(float));
  if(first<n)std::memcpy(frame+first,ring,(n-first)*sizeof(float));
}
}
