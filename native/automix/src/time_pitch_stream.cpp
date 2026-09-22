#include "lmg/automix/time_pitch_stream.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
namespace {
std::uint32_t channels(std::uint32_t n) {
  if(n<1||n>2) throw std::invalid_argument("TimePitch requires mono or stereo");
  return n;
}
// 234f45c58 / constant 0x3f3504f3; used on both input and output.
constexpr float stereoScale=0x1.6a09e6p-1f;
}
TimePitchStream::TimePitchStream(double fs,std::uint32_t count,std::uint32_t maxFrames,bool scheduled,
    const TimePitchControls& controls)
 : geometry_(timePitchGeometry(fs,maxFrames)),channels_(channels(count)),
 maximumFrames_(maxFrames),scheduled_(scheduled),spectral_(geometry_.fftSize,channels_) {
  for(std::uint32_t c=0;c<channels_;++c) {
    input_[c].resize(geometry_.inputRingSize);output_[c].resize(geometry_.outputRingSize);
    frames_[c].resize(geometry_.fftSize);
  }
  configure(controls);reset();
}
void TimePitchStream::reset(double inputTime,double outputTime) noexcept {
  timePitchResetStream(state_,geometry_.fftSize,scheduled_,inputTime,outputTime);
  for(std::uint32_t c=0;c<channels_;++c) {
    std::fill(input_[c].begin(),input_[c].end(),0);
    std::fill(output_[c].begin(),output_[c].end(),0);
  }
  // Reset retains the source's previous/effective hop and transient fields.
  clock_.inputTime=inputTime;clock_.outputTime=outputTime;spectral_.reset();
}
void TimePitchStream::configure(const TimePitchControls& controls) {
  const auto rate=controls.rate;
  const auto pitch=controls.pitch,smoothness=controls.smoothness;
  if(!std::isfinite(rate)||rate<.03125||rate>32||!std::isfinite(pitch)||pitch<.03125f||pitch>32||
     !std::isfinite(smoothness)||smoothness<3||smoothness>32)
    throw std::invalid_argument("invalid TimePitch parameters");
  const double hop=std::floor(geometry_.fftSize/static_cast<double>(smoothness)+.5);
  if(std::floor(std::fma(hop,rate,.5))<=0)
    throw std::invalid_argument("TimePitch configuration has zero input hop");
  if(pitch!=pitch_) spectral_.setPitch(pitch);
  rate_=rate;pitch_=pitch;smoothness_=smoothness;
  coherence_=controls.coherence;preserveTransients_=controls.preserveTransients;
}
void TimePitchStream::setTimeMap(TimePitchTimeMapView map) {
  if(!map.sourceFrame) throw std::invalid_argument("missing TimePitch mapping callback");
  timeMap_=map;
}
void TimePitchStream::clearTimeMap() noexcept {timeMap_={};}
double TimePitchStream::sourceTimeForOutput(double frame) const {
  if(!std::isfinite(frame))throw std::invalid_argument("Nonfinite TimePitch output time");
  if(timeMap_.sourceFrame)return timeMap_.sourceFrame(timeMap_.context,frame);
  return timePitchHistorySourceTime(state_,geometry_.fftSize,lastOutputHop_,spectral_.analysisWindow(),frame);
}
void TimePitchStream::pump() noexcept {
  const float* selected[]{frames_[0].data(),frames_[1].data()};
  float* rings[]{output_[0].data(),output_[1].data()};
  while(state_.inputWrite-state_.inputRead>=geometry_.fftSize &&
        state_.outputWrite-state_.outputClear+geometry_.fftSize<=geometry_.outputRingSize) {
    TimePitchHop hop;
    if(timeMap_.sourceFrame) {
      // 234f46440..6494: map the next output time before computing input hop.
      const double outputHop=std::floor(geometry_.fftSize/static_cast<double>(smoothness_)+.5);
      const double nextInput=timeMap_.sourceFrame(timeMap_.context,clock_.outputTime+outputHop);
      hop=timePitchAdvanceMappedHop(clock_,geometry_.fftSize,smoothness_,nextInput);
    } else {
      hop=timePitchAdvanceHop(clock_,geometry_.fftSize,smoothness_,rate_);
    }
    lastOutputHop_=hop.outputFrames;
    state_.nextInputTime=hop.nextInputTime;state_.nextOutputTime=hop.nextOutputTime;
    transient_.effectiveInputHop=hop.inputFrames;
    transient_.previousInputHop=clock_.previousRoundedInputHop;
    for(std::uint32_t c=0;c<channels_;++c)
      timePitchCopyInputFrame(input_[c].data(),geometry_.inputRingSize,state_.inputRead,
        frames_[c].data(),geometry_.fftSize);
    state_.spectralActive=false;
    if(pitch_==1 && clock_.previousRoundedInputHop==hop.outputFrames) {
      spectral_.processIdentity(selected,rings,geometry_.outputRingSize,state_.outputWrite,smoothness_);
    } else {
      if(!state_.phaseInitialized) spectral_.seedFromPreviousIdentity();
      spectral_.process(selected,rings,geometry_.outputRingSize,state_.outputWrite,
        rate_,hop.outputFrames,coherence_,preserveTransients_,transient_);
      state_.phaseInitialized=true;state_.spectralActive=true;
    }
    clock_.effectiveInputHop=transient_.effectiveInputHop;
    timePitchCommitHop(state_,transient_.effectiveInputHop,hop.outputFrames);
  }
}
std::uint32_t TimePitchStream::enqueue(const float* const* planes,std::uint32_t count,double timestamp) {
  if(count>maximumFrames_||!std::isfinite(timestamp)) throw std::invalid_argument("invalid TimePitch input block");
  const auto retained=std::max<std::int64_t>(0,state_.inputWrite-state_.inputRead);
  const auto available=geometry_.inputRingSize-retained;
  const auto accepted=static_cast<std::uint32_t>(std::min<std::int64_t>(count,available));
  if(!accepted)return 0;
  if(!planes)throw std::invalid_argument("missing TimePitch input planes");
  for(std::uint32_t c=0;c<channels_;++c) {
    if(!planes[c])throw std::invalid_argument("missing TimePitch input plane");
    for(std::uint32_t i=0;i<accepted;++i) if(!std::isfinite(planes[c][i]))throw std::invalid_argument("nonfinite TimePitch PCM");
  }
  for(std::uint32_t i=0;i<accepted;++i) {
    const auto dst=(static_cast<std::uint64_t>(state_.inputWrite)+i)&(geometry_.inputRingSize-1);
    if(channels_==1)input_[0][dst]=planes[0][i];
    else {
      input_[0][dst]=(planes[0][i]+planes[1][i])*stereoScale;
      input_[1][dst]=(planes[0][i]-planes[1][i])*stereoScale;
    }
  }
  state_.inputAnchorValid=true;state_.inputAnchorTime=timestamp;
  state_.inputAnchorFrame=state_.totalInput;state_.inputAnchorOther=state_.inputWrite;
  state_.totalInput+=accepted;state_.inputWrite+=accepted;
  state_.sourcePullTime=timestamp+static_cast<double>(accepted);
  pump();return accepted;
}
std::uint32_t TimePitchStream::dequeue(float* const* planes,std::uint32_t count,double timestamp) {
  if(count>maximumFrames_||!std::isfinite(timestamp))throw std::invalid_argument("invalid TimePitch output block");
  const auto available=std::max<std::int64_t>(0,state_.outputWrite-state_.outputRead);
  const auto delivered=static_cast<std::uint32_t>(std::min<std::int64_t>(count,available));
  if(!delivered)return 0;
  if(!planes)throw std::invalid_argument("missing TimePitch output planes");
  for(std::uint32_t c=0;c<channels_;++c) if(!planes[c])throw std::invalid_argument("missing TimePitch output plane");
  for(std::uint32_t i=0;i<delivered;++i) {
    const auto src=(static_cast<std::uint64_t>(state_.outputRead)+i)&(geometry_.outputRingSize-1);
    if(channels_==1)planes[0][i]=output_[0][src];
    else {
      planes[0][i]=(output_[0][src]+output_[1][src])*stereoScale;
      planes[1][i]=(output_[0][src]-output_[1][src])*stereoScale;
    }
  }
  // 234f45f04..10 clears through the new read cursor, including the initial
  // scheduled-mode padding skipped by outputRead, not just delivered samples.
  const auto clearCount=state_.outputRead+delivered-state_.outputClear;
  for(std::uint32_t c=0;c<channels_;++c) for(std::int64_t i=0;i<clearCount;++i)
    output_[c][(static_cast<std::uint64_t>(state_.outputClear)+i)&(geometry_.outputRingSize-1)]=0;
  state_.outputRead+=delivered;state_.outputClear=state_.outputRead;
  state_.outputPullTime=timestamp+delivered;state_.deliveredFrames+=delivered;
  state_.outputAnchorValid=true;state_.outputAnchorTime=timestamp;
  state_.outputAnchorFrame=state_.totalOutput;state_.outputAnchorOther=0;state_.totalOutput+=delivered;
  pump();return delivered;
}
}
