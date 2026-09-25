#include "pcm_bench.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <map>
#include <set>
#include <sstream>
#include <stdexcept>

namespace lmg::automix::offline {
namespace {
void need(bool ok,const char* message){if(!ok)throw std::invalid_argument(message);}
bool same(const EffectParameterDescriptor& a,const EffectParameterDescriptor& b){
  return a.id==b.id&&a.styleParameterId==b.styleParameterId&&a.minimum==b.minimum&&
         a.maximum==b.maximum&&a.defaultValue==b.defaultValue;
}
struct Lane { std::string id; ContinuousAutomationValues values; };
struct PreparedControl {
  std::vector<GraphEvent> events;
  std::optional<ContinuousAutomationValues> gain;
};
PreparedControl prepareControl(const TrackProgram& p,Format f,Frame origin,
                               std::size_t sourceHorizon,const Policy& policy){
  need(p.automations.size()<=32,"Too many automation records");
  std::vector<Lane> lanes;PreparedControl result;std::set<std::size_t> times;
  times.insert(0);
  // The grid depends on SOURCE position, not enqueue/dequeue block sizes. This
  // is an explicit bench reference sampler, NOT Apple's AudioUnit event cadence.
  for(std::size_t i=policy.effectStepFrames;i<sourceHorizon;i+=policy.effectStepFrames){
    need(times.size()<32768,"Effect sampling budget exceeded");times.insert(i);
  }
  for(const auto& a:p.automations){
    const auto& parameter=effectParameter(a.parameter.id);
    need(same(parameter,a.parameter),"Descriptor mismatch");
    need(a.points.size()<=128,"Too many automation points");
    (void)ContinuousAutomationValues(a);
    if(same(parameter,playbackRateParameter())){
      for(const auto& point:a.points)need(point.value>=.03125&&point.value<=32,"Rate outside TimePitch domain");
      continue; // Sole rate owner is the existing native mapped-hop path.
    }
    if(same(parameter,effectParameter("out_gain"))){
      if(!result.gain)result.gain.emplace(a); // Source first-full-descriptor rule.
      continue;
    }
    need(parameter.id.size()==4,"Unsupported graph address");
    lanes.push_back({parameter.id,ContinuousAutomationValues(a)});
    for(const auto& point:a.points){
      const double relative=(point.songTime-p.cueSeconds)*f.sampleRate;
      need(std::isfinite(relative),"Nonfinite automation frame");
      if(relative>=0&&relative<static_cast<double>(sourceHorizon)){
        // Boundary applies to the first sample at/after it. No early write.
        const auto at=static_cast<std::size_t>(std::ceil(relative));
        if(at<sourceHorizon)times.insert(at);
      }
    }
  }
  need(!p.applyOutputGain||result.gain.has_value(),"Missing explicit output-gain automation");
  need(times.size()<=32768,"Effect sampling budget exceeded");
  std::map<std::string,double> previous;
  std::vector<GraphParameterWrite> writes;
  for(const auto at:times){
    const double song=p.cueSeconds+double(at)/f.sampleRate;
    std::map<std::string,double> current;
    for(const auto& lane:lanes)current[lane.id]=lane.values.valueAt(song);
    // Multiple records retain order; final write for an address at this sampled
    // instant wins. Compact equal DOUBLE values, then let prepareGraphEvents do
    // AU float narrowing. Repeated decay writes are not forced per I/O block.
    for(const auto& [id,value]:current){
      if(!previous.count(id)||previous[id]!=value){
        need(writes.size()<131072,"Graph-write budget exceeded");
        writes.push_back({origin+static_cast<Frame>(at),id,value});previous[id]=value;
      }
    }
  }
  result.events=prepareGraphEvents(f.sampleRate,p.initial,writes);
  return result;
}
struct Mapping {
  const PlaybackTimeMap* map;
  double sourceCue,sourceFrameOrigin,transitionStart,rate;
  mutable bool failed=false;
  mutable double previousOutput=0,previousSource=0;
  static double source(const void* context,double outputFrame) noexcept {
    auto& c=*static_cast<const Mapping*>(context);
    try {
      if(!std::isfinite(outputFrame)||outputFrame<c.previousOutput)throw std::invalid_argument("Mapping order");
      const auto t=c.map->fromTransition(c.transitionStart+outputFrame/c.rate);
      double frame=c.sourceFrameOrigin+(t.song-c.sourceCue)*c.rate;
      if(std::abs(frame-std::round(frame))<1e-6)frame=std::round(frame);
      if(!std::isfinite(frame)||frame<0||frame>9007199254740991.0||
         (outputFrame>c.previousOutput&&frame<=c.previousSource))throw std::invalid_argument("Mapping range");
      if(outputFrame>c.previousOutput){
        const double outputDelta=outputFrame-c.previousOutput,sourceDelta=frame-c.previousSource;
        if(sourceDelta<outputDelta/32.0||sourceDelta>outputDelta*32.0)
          throw std::invalid_argument("Mapped hop outside TimePitch rate domain");
      }
      c.previousOutput=outputFrame;c.previousSource=frame;return frame;
    }catch(...){
      // The kernel's callback is noexcept. Quarantine this call, then the owner
      // throws before publishing ANY capture. This return only prevents undefined
      // input-hop arithmetic while unwinding the current native public call.
      c.failed=true;const double delta=std::max(1.0,outputFrame-c.previousOutput);
      c.previousOutput=outputFrame;c.previousSource+=delta;return c.previousSource;
    }
  }
};
std::string metricsJson(const Metrics& m){
  return "{\"frames\":"+std::to_string(m.frames)+",\"peak\":"+jsonNumber(m.peak)+
    ",\"rms\":"+jsonNumber(m.rms)+",\"mean\":"+jsonNumber(m.mean)+",\"peakFrame\":"+
    std::to_string(m.peakFrame)+",\"samplesAboveUnity\":"+std::to_string(m.samplesAboveUnity)+"}";
}
}
void Policy::validate(Format f,std::size_t captureFrames) const {
  f.validate();checkedSamples(captureFrames,f.channels);need(captureFrames>0,"Empty output capture");
  need(maximumFrames>=1&&maximumFrames<=16384,"Invalid maximum block size");
  need(!inputPattern.empty()&&!outputPattern.empty()&&inputPattern.size()<=128&&outputPattern.size()<=128,"Invalid I/O patterns");
  for(const auto* pattern:{&inputPattern,&outputPattern})for(auto v:*pattern)need(v>0&&v<=maximumFrames,"Invalid I/O request size");
  need(effectStepFrames>=1&&effectStepFrames<=16384,"Invalid effect sampling cadence");
  checkedSamples(maximumZeroInputFrames,f.channels);need(quietWindowFrames>0,"Empty tail measurement window");
  need(std::isfinite(residualThreshold)&&residualThreshold>=0,"Invalid residual threshold");
}
TrackCapture renderTrack(const TrackProgram& p,const std::vector<float>& pcm,Format f,
                          std::size_t captureFrames,const Policy& policy){
  policy.validate(f,captureFrames);const auto inputMetrics=measure(pcm,f);
  need(std::isfinite(p.cueSeconds)&&p.cueSeconds>=0&&std::isfinite(p.transitionStartSeconds)&&p.transitionStartSeconds>=0,"Invalid track origin");
  const auto firstFrame=secondsToFrame(p.cueSeconds,f.sampleRate);
  const auto sourceFrames=inputMetrics.frames;
  const auto horizon=sourceFrames+policy.maximumZeroInputFrames;
  checkedSamples(horizon,f.channels);
  need(horizon>0&&firstFrame<=9007199254740991LL-static_cast<Frame>(horizon),"Source horizon overflow");
  const auto start=p.timeMap.fromTransition(p.transitionStartSeconds);
  const double anchorError=std::abs((start.song-p.cueSeconds)*f.sampleRate);
  need(std::isfinite(anchorError)&&anchorError<=1e-7,"Track/map cue mismatch");
  const auto end=p.timeMap.fromTransition(p.transitionStartSeconds+double(captureFrames)/f.sampleRate);
  need(std::isfinite(end.song)&&end.song>p.cueSeconds,"Nonadvancing capture map");
  auto control=prepareControl(p,f,firstFrame,horizon,policy);
  TrackCapture capture;capture.raw.resize(checkedSamples(captureFrames,f.channels));capture.weighted.resize(capture.raw.size());
  auto& report=capture.report;report.sourceFrames=sourceFrames;report.graphEvents=control.events.size();
  const auto geometry=timePitchGeometry(f.sampleRate,policy.maximumFrames);report.fftSize=geometry.fftSize;
  report.declaredScheduledLatencySeconds=timePitchLatencySeconds(geometry,f.sampleRate,1.0f,true);
  // Explicit OFFLINE probe settings. Not attributed to the Apple default policy.
  // Mapped variable-rate transient policy is deliberately excluded here: rate_ is
  // not dynamically configured by ProcessedTrackStream's current public API.
  const TimePitchControls controls{1.0,1.0f,8.0f,false,false};
  Mapping mapping{&p.timeMap,p.cueSeconds,double(firstFrame),p.transitionStartSeconds,double(f.sampleRate),false,0,double(firstFrame)};
  ProcessedTrackStream stream(f.sampleRate,f.channels,policy.maximumFrames,firstFrame,0,
                             p.initial,std::move(control.events),true,controls,{&mapping,Mapping::source});
  std::vector<float> block(checkedSamples(policy.maximumFrames,f.channels));
  std::size_t source=0,zeros=0,delivered=0,inIndex=0,outIndex=0,noProgress=0;
  auto checkMapping=[&]{if(mapping.failed)throw std::runtime_error("Native map failed; capture discarded");};
  auto receive=[&]()->unsigned {
    const auto want=static_cast<unsigned>(std::min<std::size_t>(policy.outputPattern[outIndex%policy.outputPattern.size()],captureFrames-delivered));
    if(!want)return 0;
    std::fill(block.begin(),block.end(),std::numeric_limits<float>::quiet_NaN());
    ++report.dequeueCalls;const auto got=stream.dequeue(block.data(),want,double(delivered));checkMapping();
    if(got>want)throw std::runtime_error("Kernel over-delivery");
    if(got){if(!delivered)report.firstDeliveryAfterInputFrames=source+zeros;
      if(got<want)++report.partialReads;
      for(std::size_t i=0;i<static_cast<std::size_t>(got)*f.channels;++i){
        if(!std::isfinite(block[i]))throw std::runtime_error("Nonfinite processed PCM; capture discarded");
        capture.raw[delivered*f.channels+i]=block[i];}
      delivered+=got;++outIndex;}
    return got;
  };
  auto send=[&]()->unsigned {
    const auto remaining=source<sourceFrames?sourceFrames-source:policy.maximumZeroInputFrames-zeros;
    const auto want=static_cast<unsigned>(std::min<std::size_t>(policy.inputPattern[inIndex%policy.inputPattern.size()],remaining));
    if(!want)return 0;
    const bool real=source<sourceFrames;const float* input=real?pcm.data()+source*f.channels:nullptr;
    ++report.enqueueCalls;const auto accepted=stream.enqueue(input,want,{});checkMapping();
    if(accepted>want)throw std::runtime_error("Kernel over-acceptance");
    if(!accepted)++report.backpressureCalls;
    if(real)source+=accepted;else zeros+=accepted;
    if(accepted)++inIndex; // Retry only the unaccepted suffix, never a whole accepted block.
    const auto expected=firstFrame+static_cast<Frame>(source+zeros);
    if(stream.inputPosition()!=expected)throw std::runtime_error("Source accounting mismatch");
    return accepted;
  };
  // Zero-sized calls are part of the contract, never EOS and never progression.
  if(stream.enqueue(nullptr,0,{})||stream.dequeue(block.data(),0,0)||stream.inputPosition()!=firstFrame)
    throw std::runtime_error("Zero-frame call changed state");
  while(delivered<captureFrames){
    std::size_t progress=receive();
    if(delivered==captureFrames)break;
    const unsigned attempts=policy.fillAhead?64:1;
    for(unsigned i=0;i<attempts;++i){const auto n=send();progress+=n;if(!n)break;}
    progress+=receive();
    if(progress)noProgress=0;
    else if(++noProgress==4)throw std::runtime_error("PCM drain budget exhausted or pipeline stalled; no complete capture");
  }
  report.acceptedSourceFrames=source;report.acceptedZeroFrames=zeros;report.deliveredFrames=delivered;
  report.sourceFullyAccepted=source==sourceFrames;report.pendingFramesAtCaptureEnd=stream.pendingFrames();
  report.outputGainApplied=p.applyOutputGain;
  for(std::size_t frame=0;frame<captureFrames;++frame){
    const double transition=p.transitionStartSeconds+double(frame)/f.sampleRate;
    const double song=p.timeMap.fromTransition(transition).song;
    const double value=p.applyOutputGain?control.gain->valueAt(song):1.0;
    need(std::isfinite(value)&&std::abs(value)<=std::numeric_limits<float>::max(),"Unrepresentable output gain");
    const float gain=static_cast<float>(value);
    for(unsigned c=0;c<f.channels;++c){const auto i=frame*f.channels+c;
      capture.weighted[i]=capture.raw[i]*gain; // exactly ONE bench output-gain owner
      need(std::isfinite(capture.weighted[i]),"Nonfinite gain output");}
  }
  const double tailTime=p.timeMap.fromSong(p.cueSeconds+double(sourceFrames)/f.sampleRate).transition-p.transitionStartSeconds;
  need(std::isfinite(tailTime)&&tailTime>=0,"Invalid EOF time map");
  const double tailFrames=std::ceil(tailTime*f.sampleRate);
  report.firstTailOutputFrame=tailFrames>=double(captureFrames)?captureFrames:static_cast<std::size_t>(tailFrames);
  report.tailFramesObserved=captureFrames-report.firstTailOutputFrame;
  report.raw=measure(capture.raw,f);report.weighted=measure(capture.weighted,f);
  report.rawTail=measure(capture.raw,f,report.firstTailOutputFrame,captureFrames);
  report.rawFinalWindow=measure(capture.raw,f,captureFrames-std::min(captureFrames,policy.quietWindowFrames),captureFrames);
  report.finalWindowAboveThreshold=report.rawFinalWindow.peak>policy.residualThreshold;
  return capture;
}
TransitionCapture renderTransition(const PlannerTransitionSchedule& plan,const std::vector<float>& a,
                                    const std::vector<float>& b,Format f,std::size_t tail,const Policy& policy){
  f.validate();need(plan.transitionRange().begin==0,"Unsupported nonzero transition origin");
  TransitionCapture result;result.transitionFrames=roundedFrames(plan.transitionRange().end,f.sampleRate);
  need(result.transitionFrames>0,"Subframe transition");result.incomingStartFrame=roundedFrames(plan.incoming().playbackTransitionRange.begin,f.sampleRate);
  need(result.incomingStartFrame<result.transitionFrames,"Empty incoming playback extent");
  checkedSamples(tail,f.channels);
  checkedSamples(result.transitionFrames+tail,f.channels);
  auto render=[&](const PlannerScheduledSide& side,const std::vector<float>& input,std::size_t frames){
    TrackProgram p{side.startPlaybackSongTime,side.playbackTransitionRange.begin,side.timeMap(),side.automations,{},true};
    return renderTrack(p,input,f,frames+tail,policy);
  };
  result.outgoing=render(plan.outgoing(),a,result.transitionFrames);
  result.incoming=render(plan.incoming(),b,result.transitionFrames-result.incomingStartFrame);
  result.mix.assign(checkedSamples(result.transitionFrames,f.channels),0);
  for(std::size_t frame=0;frame<result.transitionFrames;++frame)for(unsigned c=0;c<f.channels;++c){
    const auto i=frame*f.channels+c;float value=result.outgoing.weighted[i];
    if(frame>=result.incomingStartFrame)value=value+result.incoming.weighted[(frame-result.incomingStartFrame)*f.channels+c];
    need(std::isfinite(value),"Nonfinite mixed PCM");result.mix[i]=value;
  }
  result.mixMetrics=measure(result.mix,f);return result;
}
std::string trackReportJson(const TrackReport& r){
  std::ostringstream s;s<<"{\"sourceFrames\":"<<r.sourceFrames<<",\"acceptedSourceFrames\":"<<r.acceptedSourceFrames
    <<",\"acceptedZeroFrames\":"<<r.acceptedZeroFrames<<",\"deliveredFrames\":"<<r.deliveredFrames
    <<",\"firstDeliveryAfterInputFrames\":"<<r.firstDeliveryAfterInputFrames<<",\"enqueueCalls\":"<<r.enqueueCalls
    <<",\"dequeueCalls\":"<<r.dequeueCalls<<",\"backpressureCalls\":"<<r.backpressureCalls<<",\"partialReads\":"<<r.partialReads
    <<",\"pendingFramesAtCaptureEnd\":"<<r.pendingFramesAtCaptureEnd<<",\"graphEvents\":"<<r.graphEvents<<",\"fftSize\":"<<r.fftSize
    <<",\"declaredScheduledLatencySeconds\":"<<jsonNumber(r.declaredScheduledLatencySeconds)
    <<",\"outputGainApplications\":"<<(r.outputGainApplied?1:0)<<",\"sourceFullyAccepted\":"<<(r.sourceFullyAccepted?"true":"false")
    <<",\"firstTailOutputFrame\":"<<r.firstTailOutputFrame<<",\"tailFramesObserved\":"<<r.tailFramesObserved
    <<",\"tailObservation\":\""<<(!r.tailFramesObserved?"NOT_CAPTURED":r.finalWindowAboveThreshold?"RESIDUAL_AT_CAPTURE_LIMIT":"FINAL_WINDOW_QUIET_NOT_PROOF_OF_DRAIN")
    <<"\",\"raw\":"<<metricsJson(r.raw)<<",\"weighted\":"<<metricsJson(r.weighted)<<",\"rawTail\":"<<metricsJson(r.rawTail)
    <<",\"rawFinalWindow\":"<<metricsJson(r.rawFinalWindow)<<"}";return s.str();
}
std::string transitionReportJson(const TransitionCapture& r,Format f,const Policy& p){
  std::ostringstream s;s<<"{\"schemaVersion\":1,\"kind\":\"LMG_OFFLINE_PCM_BENCH\",\"canExecute\":false,\"playerObjects\":0,\"outputTimelines\":1,"
    <<"\"sampleRate\":"<<f.sampleRate<<",\"channels\":"<<f.channels<<",\"maximumFrames\":"<<p.maximumFrames
    <<",\"maximumZeroInputFrames\":"<<p.maximumZeroInputFrames<<",\"quietWindowFrames\":"<<p.quietWindowFrames<<",\"residualThreshold\":"<<jsonNumber(p.residualThreshold)
    <<",\"effectStepSourceFrames\":"<<p.effectStepFrames<<",\"scheduled\":true,\"pitch\":1,\"smoothness\":8,\"coherence\":false,\"preserveTransients\":false,"
    <<"\"tailPolicy\":\"BOUNDED_ZERO_INPUT_AND_FIXED_CAPTURE\",\"latencyPolicy\":\"NO_EXTRA_TRIM_AFTER_NATIVE_SCHEDULED_MODE\","
    <<"\"gainPolicy\":\"FIRST_FULL_CONTINUOUS_OUT_GAIN_ONCE_AFTER_TIMEPITCH\",\"automationPolicy\":\"FIXED_SOURCE_GRID_PLUS_POINT_BOUNDARIES\","
    <<"\"transitionFrames\":"<<r.transitionFrames<<",\"incomingStartFrame\":"<<r.incomingStartFrame
    <<",\"outgoing\":"<<trackReportJson(r.outgoing.report)<<",\"incoming\":"<<trackReportJson(r.incoming.report)
    <<",\"mix\":"<<metricsJson(r.mixMetrics)<<"}\n";return s.str();
}
} // namespace lmg::automix::offline
