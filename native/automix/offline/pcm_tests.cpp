#include "pcm_fixtures.h"
#include <algorithm>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
using namespace lmg::automix;
using namespace lmg::automix::offline;
namespace {
std::size_t assertions=0;
void check(bool x,const char* message){++assertions;if(!x)throw std::runtime_error(message);}
template<class F>void rejects(F f){bool yes=false;try{f();}catch(const std::exception&){yes=true;}check(yes,"Expected an offline rejection");}
TrackProgram identity(bool gain=false){
  std::vector<ContinuousAutomation> lanes;
  if(gain)lanes.push_back({effectParameter("out_gain"),{{.5,0,0x80},{.5,60,0x80}}});
  return {0,0,PlaybackTimeMap(playbackAnchor(0,0)),lanes,{},gain};
}
Policy partitioned(Policy p){p.inputPattern={1,17,257,997};p.outputPattern={113,3,1024,31};p.fillAhead=true;return p;}
void equalCapture(const TrackCapture& a,const TrackCapture& b){
  check(a.raw.size()==b.raw.size(),"Capture length changed with blocks");
  check(compare(a.raw,b.raw).maxAbsolute<=2e-5,"Raw PCM changed with I/O partition");
  check(compare(a.weighted,b.weighted).maxAbsolute<=2e-5,"Gain PCM changed with I/O partition");
  check(a.report.graphEvents==b.report.graphEvents,"Event grid depends on I/O size");
}
void transport(){
  for(auto rate:{8000U,44100U,48000U})for(unsigned channels:{1U,2U}){
    Format f{rate,channels};Policy policy;policy.maximumZeroInputFrames=rate*2;
    const auto pcm=signal(f,rate/2,7);const auto count=rate/2+2048;
    auto a=renderTrack(identity(),pcm,f,count,policy),b=renderTrack(identity(),pcm,f,count,partitioned(policy));equalCapture(a,b);
    check(a.report.acceptedSourceFrames==rate/2&&a.report.sourceFullyAccepted,"Not all source samples accepted");
    check(a.report.deliveredFrames==count&&b.report.deliveredFrames==count,"Missing output frames");
    check(a.report.acceptedZeroFrames>0,"No explicit EOF padding accounted");
    check(a.report.declaredScheduledLatencySeconds==0,"Scheduled latency not zero");
    check(b.report.backpressureCalls>0,"Backpressure was not exercised");
    check(a.report.raw.peak>.01&&a.report.raw.samplesAboveUnity==0,"No signal or unexpected overload");
    // Unity oracle excludes only startup/EOF window transients; no shift or fitted
    // gain is used to make the comparison pass.
    const auto margin=a.report.fftSize;
    if(rate/2>2*margin)for(std::size_t frame=margin;frame<rate/2-margin;++frame)for(unsigned c=0;c<channels;++c)
      check(std::abs(a.raw[frame*channels+c]-pcm[frame*channels+c])<2e-5,"Unity interior mismatch");
  }
  Format f{8000,1};Policy p;p.maximumZeroInputFrames=16000;
  auto silence=renderTrack(identity(),{},f,4096,p);check(silence.report.raw.peak==0,"Silence generated signal");
  check(silence.report.acceptedSourceFrames==0&&silence.report.acceptedZeroFrames>0,"Silence accounting");
  auto bad=signal(f,1024);bad[19]=std::numeric_limits<float>::quiet_NaN();rejects([&]{renderTrack(identity(),bad,f,2048,p);});
  Policy noPadding=p;noPadding.maximumZeroInputFrames=0;rejects([&]{renderTrack(identity(),std::vector<float>(8,.1f),f,4096,noPadding);});
  Policy invalid=p;invalid.inputPattern={0};rejects([&]{renderTrack(identity(),{},f,100,invalid);});
  invalid=p;invalid.outputPattern={1025};rejects([&]{renderTrack(identity(),{},f,100,invalid);});
  invalid=p;invalid.effectStepFrames=0;rejects([&]{renderTrack(identity(),{},f,100,invalid);});
  auto program=identity();program.applyOutputGain=true;rejects([&]{renderTrack(program,{},f,100,p);});
  program=identity();program.cueSeconds=1;rejects([&]{renderTrack(program,{},f,100,p);});
  program=identity();program.automations={{effectParameter("RVrr"),{{2,0,0x80},{3,1,0x80}}}};
  rejects([&]{renderTrack(program,{},f,100,p);}); // no silent live geometry edits
  program=identity();program.timeMap=PlaybackTimeMap(playbackAnchor(0,0),RateRamp{33,33,0,8});
  rejects([&]{renderTrack(program,signal(f,8192),f,4096,p);});
  // Gain is post-TimePitch and applied ONCE, not once in both graph and mixer.
  const auto data=signal(f,8192);auto gained=renderTrack(identity(true),data,f,9000,p);
  for(std::size_t i=0;i<gained.raw.size();++i)check(gained.weighted[i]==gained.raw[i]*.5f,"Double/missing output gain");
}
void rateClockOracle(){
  // Independent finite-input/clock and pitch-preservation probes, not the same
  // schedule compiler evaluating itself. Use a continuous 311 Hz tone and leave
  // source EOF outside the capture. Large rate changes distinguish a disconnected
  // native time map from ordinary FFT lookahead and bounded input buffering.
  Format f{8000,1};Policy p;p.maximumZeroInputFrames=0;
  std::vector<float> tone(8*f.sampleRate);
  for(std::size_t i=0;i<tone.size();++i)tone[i]=static_cast<float>(.05*std::sin(6.283185307179586*311*double(i)/f.sampleRate));
  for(double rate:{.75,1.5}){
    const auto map=PlaybackTimeMap(playbackAnchor(0,0),RateRamp{rate,rate,0,8});
    const auto r=renderTrack(TrackProgram{0,0,map,{}, {},false},tone,f,4*f.sampleRate,p);
    const double expected=4*f.sampleRate*rate;
    const double allowance=4*r.report.fftSize+2*p.maximumFrames;
    check(std::abs(double(r.report.acceptedSourceFrames)-expected)<=allowance,"Mapped rate not reflected in accepted source clock");
    check(r.report.acceptedZeroFrames==0&&!r.report.sourceFullyAccepted,"Unexpected EOF or synthetic input in clock oracle");
    unsigned crossings=0;
    for(std::size_t i=f.sampleRate+1;i<3*f.sampleRate;++i)
      if(r.raw[i-1]<=0&&r.raw[i]>0)++crossings;
    const double frequency=double(crossings)*.5;
    check(std::abs(frequency-311)<=3,"Time stretching changed steady-tone frequency");
  }
}
void latencyTails(){
  rateClockOracle();
  for(auto rate:{8000U,44100U,48000U})for(unsigned channels:{1U,2U}){
    Format f{rate,channels};Policy p;p.maximumZeroInputFrames=rate*2;
    const auto n=rate,at=rate/2;auto r=renderTrack(identity(),impulse(f,n,at),f,n+4096,p);
    check(r.report.raw.peakFrame==at,"Unity impulse moved: extra/missing latency compensation");
    check(std::abs(r.raw[at*channels]-.125f)<2e-5,"Unity impulse amplitude mismatch");
    check(r.report.firstDeliveryAfterInputFrames>0,"No input lookahead measurement");
    if(channels==2){double leak=0;for(std::size_t i=1;i<r.raw.size();i+=2)leak=std::max(leak,std::abs(double(r.raw[i])));check(leak<2e-5,"Stereo leakage");}
  }
  for(bool reverb:{false,true}){
    Format f{48000,2};Policy p;p.maximumZeroInputFrames=48000*4;p.quietWindowFrames=1024;p.residualThreshold=1e-10;
    auto program=identity();program.initial.bypass=false;program.initial.dryGain=0;program.initial.wetGain=1;program.initial.sendGain=1;
    if(reverb){program.initial.delayWetPercent=0;program.initial.reverbWetPercent=100;program.initial.reverbLowDecaySeconds=.5;program.initial.reverbHighDecaySeconds=.2;}
    else {program.initial.delaySeconds=.025;program.initial.delayWetPercent=100;program.initial.delayFeedbackPercent=75;program.initial.reverbWetPercent=0;}
    const auto data=impulse(f,4800,3840);auto a=renderTrack(program,data,f,24000,p),b=renderTrack(program,data,f,24000,partitioned(p));equalCapture(a,b);
    check(a.report.rawTail.peak>1e-7,"Expected a real DSP tail after source EOF");
    check(a.report.tailFramesObserved>0&&a.report.acceptedZeroFrames>0,"Tail not captured/clock not advancing");
    if(!reverb)check(a.report.finalWindowAboveThreshold,"A capped echo tail was incorrectly declared quiet");
    auto longCapture=renderTrack(program,data,f,144000,p);
    check(longCapture.report.rawFinalWindow.rms<a.report.rawFinalWindow.rms,"Longer capture did not show decay");
    check(compare(a.raw,std::vector<float>(longCapture.raw.begin(),longCapture.raw.begin()+a.raw.size())).maxAbsolute<=2e-5,"Tail capture length changed prefix");
  }
}
void schedules(const std::string& catalog){
  for(bool stretch:{false,true}){
    const auto plan=fixtureSchedule(catalog,stretch);Format f{8000,2};Policy p;p.maximumZeroInputFrames=f.sampleRate*4;
    const auto outFrames=roundedFrames(plan.outgoing().sourceRegion.end-plan.outgoing().sourceRegion.begin,f.sampleRate);
    const auto inFrames=roundedFrames(plan.incoming().sourceRegion.end-plan.incoming().sourceRegion.begin,f.sampleRate);
    const auto a=signal(f,outFrames,21),b=signal(f,inFrames,97);
    const auto first=renderTransition(plan,a,b,f,1024,p),second=renderTransition(plan,a,b,f,1024,partitioned(p));
    equalCapture(first.outgoing,second.outgoing);equalCapture(first.incoming,second.incoming);
    check(compare(first.mix,second.mix).maxAbsolute<=2e-5,"Mixed transition differs by block pattern");
    check(first.transitionFrames==roundedFrames(plan.transitionRange().end,f.sampleRate),"Wrong output horizon");
    check(first.incomingStartFrame==roundedFrames(plan.incoming().playbackTransitionRange.begin,f.sampleRate),"Wrong incoming start frame");
    check(!first.canExecute&&!plan.canExecute,"Offline render activated playback");
    check(first.mixMetrics.peak>.001&&first.mixMetrics.samplesAboveUnity==0,"Empty or overloaded transition");
    for(std::size_t i=0;i<first.transitionFrames;++i)for(unsigned c=0;c<f.channels;++c){
      float expected=first.outgoing.weighted[i*f.channels+c];
      if(i>=first.incomingStartFrame)expected+=first.incoming.weighted[(i-first.incomingStartFrame)*f.channels+c];
      check(first.mix[i*f.channels+c]==expected,"Mix alignment/gain ownership mismatch");}
    auto bad=p;bad.maximumZeroInputFrames=0;rejects([&]{renderTransition(plan,a,b,f,f.sampleRate,bad);});
  }
  // Fractional origin remains an explicit frame-domain bench offset; never an
  // invented Player seek or a second clock. Test the native map with a ramp.
  Format f{8000,1};Policy p;p.maximumZeroInputFrames=32000;
  const auto map=PlaybackTimeMap(playbackAnchor(.125,.03125),RateRamp{.75,1.25,.03125,1.03125});
  TrackProgram program{.03125,.125,map,{}, {},false};const auto pcm=signal(f,12000);
  auto x=renderTrack(program,pcm,f,14000,p),y=renderTrack(program,pcm,f,14000,partitioned(p));equalCapture(x,y);
  check(x.report.raw.peak>.001,"Mapped ramp produced no signal");
}
}
int main(int argc,char** argv){try{
  check(argc>=2,"Usage: pcm-tests transport|latency-tails|schedule [catalog]");const std::string group=argv[1];
  if(group=="transport")transport();else if(group=="latency-tails")latencyTails();else if(group=="schedule"){check(argc==3,"Catalog required");schedules(readCatalog(argv[2]));}
  else throw std::invalid_argument("Unknown PCM test group");
  std::cout<<"Offline real-DSP "<<group<<": "<<assertions<<" assertions PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<"Offline real-DSP test FAILED: "<<e.what()<<'\n';return 1;}}
