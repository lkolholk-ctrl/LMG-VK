#include "lmg/automix/live_pcm_executor.h"
#include "lmg/automix/processed_track_stream.h"
#include "lmg/automix/continuous_schedule.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <map>
#include <optional>
#include <set>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
void need(bool ok,const char* why){if(!ok)throw std::invalid_argument(why);}
double number(std::int64_t bits){double d;std::memcpy(&d,&bits,8);need(std::isfinite(d),"Nonfinite compiled plan");return d;}
std::int64_t frame(double seconds,unsigned fs){need(seconds>=0&&seconds<=86400,"Time outside live host domain");return secondsToFrame(seconds,fs);}
bool knownCurve(std::int64_t c){return c==0||c==1||c==2||c==64||c==65||c==66||c==128||c==129;}
struct SidePlan {
 double cue=0,end=0,start=0,rate0=1,rate1=1;
 std::vector<ContinuousAutomation> automation;
};
struct Program {SidePlan sides[2];double duration=0;};
Program decode(const std::vector<std::int64_t>& p){
 need(p.size()>=40&&p.size()<=1898&&p[0]==0x4c4d47535031LL&&p[1]==1&&p[2]==std::int64_t(p.size()),"Wrong compiled-plan protocol");
 need((p[3]==8||p[3]==9||p[3]==12)&&p[4]>=0&&p[4]<=2,"Unsupported compiled style");
 need(p[36]==0&&p[37]==1&&p[38]==0&&p[39]==0,"Plan is not an observation source schedule");
 for(unsigned k=5;k<=8;++k)need(p[k]>=0&&p[k]<4096,"Invalid event index");
 need(p[5]<p[6]&&p[7]<p[8]&&p[9]>0&&p[10]>0&&p[9]<=4096&&p[10]<=4096,"Invalid plan counts");
 for(unsigned k=11;k<=32;++k)(void)number(p[k]);
 need(number(p[11])>0&&number(p[16])>0&&number(p[17])>0,"Invalid score/geometry");
 Program q;q.duration=number(p[23]);
 need(number(p[22])==0&&q.duration>0&&q.duration<=300,"Unsupported transition duration");
 need(p[25]==p[22]&&p[26]==p[23]&&p[28]==p[23]&&number(p[27])>=0&&number(p[27])<q.duration,"Invalid playback ranges");
 need(number(p[24])>=number(p[27])&&number(p[24])<=q.duration,"Invalid reference time");
 need(p[29]==p[12]&&p[30]==p[14]&&p[31]==p[23]&&number(p[32])>0,"Inconsistent cues");
 need(p[33]>0&&p[33]<=32&&p[34]>0&&p[34]<=32&&p[35]>=4&&p[35]<=256,"Invalid automation counts");
 std::size_t at=40,total=0;
 for(unsigned side=0;side<2;++side){
  auto& s=q.sides[side];s.cue=number(p[12+side*2]);s.end=number(p[13+side*2]);s.start=number(p[25+side*2]);
  s.rate0=number(p[18+side*2]);s.rate1=number(p[19+side*2]);
  need(s.cue>=0&&s.end>s.cue&&s.end<=86400&&s.rate0>=.03125&&s.rate0<=32&&s.rate1>=.03125&&s.rate1<=32,"Invalid side geometry");
  bool gain=false,rate=false;
  for(std::int64_t n=0;n<p[33+side];++n){
   need(at+2<=p.size(),"Truncated automation");auto index=p[at++],count=p[at++];
   need(index>=0&&index<29&&count>=2&&count<=4&&at+std::size_t(count)*3<=p.size(),"Invalid automation record");
   ContinuousAutomation a{allEffectParameters()[std::size_t(index)],{}};
   double previous=s.cue;
   for(std::int64_t i=0;i<count;++i){double value=number(p[at++]),time=number(p[at++]);auto curve=p[at++];
    need(time>=previous&&time>=s.cue&&time<=s.end&&knownCurve(curve),"Invalid automation points");
    a.points.push_back({value,time,static_cast<std::uint8_t>(curve)});previous=time;++total;
   }
   (void)ContinuousAutomationValues(a);
   if(a.parameter.id=="ts_rate"){
    need(count==2&&a.points[0].value==s.rate0&&a.points[1].value==s.rate1&&a.points[0].curve==128&&a.points[1].curve==128,"Rate endpoint mismatch");rate=true;
   }
   if(a.parameter.id=="out_gain")gain=true;
   s.automation.push_back(std::move(a));
  }
  need(gain&&rate,"Missing output gain/rate schedule");
 }
 need(at==p.size()&&total==std::size_t(p[35]),"Trailing or missing plan words");
 return q;
}
struct SampledLane{std::string id;ContinuousAutomationValues values;};
std::vector<GraphEvent> events(const SidePlan& s,unsigned fs,unsigned cadence,std::int64_t cue){
 std::vector<SampledLane> lanes;std::set<std::int64_t> times{0};
 const auto horizon=frame(s.end-s.cue,fs);
 need(horizon<=std::int64_t(fs)*300,"Graph horizon limit");
 for(std::int64_t i=cadence;i<=horizon;i+=cadence){need(times.size()<65536,"Graph sampling limit");times.insert(i);}
 for(const auto& a:s.automation){
  if(a.parameter.id=="out_gain"||a.parameter.id=="ts_rate")continue;
  need(a.parameter.id.size()==4,"Non-graph parameter");
  lanes.push_back({a.parameter.id,ContinuousAutomationValues(a)});
  for(const auto& p:a.points){const double f=(p.songTime-s.cue)*fs;need(f>=0&&std::isfinite(f),"Invalid event frame");times.insert(static_cast<std::int64_t>(std::ceil(f)));}
 }
 std::map<std::string,double> previous;std::vector<GraphParameterWrite>writes;
 for(auto f:times){
  std::map<std::string,double> now;for(const auto& lane:lanes)now[lane.id]=lane.values.valueAt(s.cue+double(f)/fs);
  for(const auto& item:now)if(!previous.count(item.first)||previous[item.first]!=item.second){
   need(writes.size()<262144,"Graph write limit");writes.push_back({cue+f,item.first,item.second});previous[item.first]=item.second;
  }
 }
 return prepareGraphEvents(fs,EffectSettings{},writes);
}
struct Mapping {
 const PlaybackTimeMap* map=nullptr;double cue=0,origin=0,start=0,fs=0;
 mutable double previousOut=0,previousIn=0;mutable bool failed=false;
 static double source(const void* ctx,double output) noexcept{
  auto& m=*static_cast<const Mapping*>(ctx);
  try{
   need(std::isfinite(output)&&output>=m.previousOut,"Nonmonotonic DSP map");
   double v=m.origin+(m.map->fromTransition(m.start+output/m.fs).song-m.cue)*m.fs;
   // Same finite host stabilization as accepted offline Mapping::source. Only
   // remove sub-microframe arithmetic noise at an exact sample, not tempo drift.
   const double near=std::round(v);if(std::abs(v-near)<1e-6)v=near;
   need(std::isfinite(v)&&v>=0&&v<=9007199254740991.0,"Invalid source map");
   if(output>m.previousOut){const double d=output-m.previousOut,x=v-m.previousIn;need(x>0&&x>=d/32-1e-6&&x<=d*32+1e-6,"Unsafe mapped hop");}
   m.previousOut=output;m.previousIn=v;return v;
  }catch(...){m.failed=true;const double d=std::max(1.,output-m.previousOut);m.previousOut=output;m.previousIn+=d;return m.previousIn;}
 }
};
}
struct LivePcmExecutor::Impl {
 struct Side {
  SidePlan plan;PlaybackTimeMap clock;Mapping mapping;std::optional<ContinuousAutomationValues>gain;
  std::unique_ptr<ProcessedTrackStream>stream;std::vector<float>cache;
  std::int64_t cue=0,expected=0,outputCursor=0,sourceEnd=0,outputEnd=std::numeric_limits<std::int64_t>::max();
  unsigned offset=0,count=0,zeros=0;bool eos=false;
  Side(SidePlan p,LivePcmOptions o):plan(std::move(p)),clock(schedulePlaybackTimeMap(plan.start,plan.cue,std::nullopt,plan.automation)){
   cue=frame(plan.cue,o.sampleRate);expected=cue;mapping={&clock,plan.cue,double(cue),plan.start,double(o.sampleRate),0,double(cue),false};
   for(const auto& a:plan.automation)if(a.parameter.id=="out_gain"&&!gain)gain.emplace(a);
   stream=std::make_unique<ProcessedTrackStream>(o.sampleRate,o.channels,o.maximumFrames,cue,0,EffectSettings{},events(plan,o.sampleRate,o.effectStepFrames,cue),true,TimePitchControls{1,1,8,false,false},TimePitchTimeMapView{&mapping,Mapping::source});
   cache.resize(std::size_t(o.maximumFrames)*o.channels);
  }
 };
 LivePcmOptions opt;Program program;std::unique_ptr<Side>sides[2];LivePcmStats report;
 std::int64_t transitionEnd=0,incomingStart=0,cursor=0;unsigned maxPadding;
 Impl(std::vector<std::int64_t>p,LivePcmOptions o):opt(o),program(decode(p)){
  need(o.sampleRate>=8000&&o.sampleRate<=192000&&o.channels>=1&&o.channels<=2&&o.maximumFrames>=1&&o.maximumFrames<=4096&&o.effectStepFrames>=1&&o.effectStepFrames<=4096&&o.generation>0&&o.revision>=0,"Invalid live configuration");
  transitionEnd=frame(program.duration,o.sampleRate);incomingStart=frame(program.sides[1].start,o.sampleRate);
  need(transitionEnd>incomingStart,"Empty incoming playback interval");
  maxPadding=timePitchGeometry(o.sampleRate,o.maximumFrames).fftSize*64u;
  sides[0]=std::make_unique<Side>(std::move(program.sides[0]),o);sides[1]=std::make_unique<Side>(std::move(program.sides[1]),o);
 }
 void good(){if(report.failed)throw std::logic_error("Failed PCM executor requires reset");}
 void checkMap(){for(const auto&s:sides)if(s->mapping.failed){report.failed=true;throw std::runtime_error("PCM mapping failed");}}
 unsigned fill(unsigned which,unsigned wanted){
  auto& s=*sides[which];if(s.count-s.offset>=wanted)return s.count-s.offset;
  // Retain a partially ready side across partner starvation. Compact only our
  // private cache, never the caller's codec buffer or unaccepted input.
  if(s.offset){const auto left=s.count-s.offset;std::memmove(s.cache.data(),s.cache.data()+std::size_t(s.offset)*opt.channels,std::size_t(left)*opt.channels*sizeof(float));s.count=left;s.offset=0;}
  unsigned budget=8;
  while(s.count<wanted&&budget--){
   const auto n=s.stream->dequeue(s.cache.data()+std::size_t(s.count)*opt.channels,wanted-s.count,double(s.outputCursor));checkMap();
   s.count+=n;s.outputCursor+=n;report.dequeued[which]+=n;
   if(s.count>=wanted)break;
   if(!s.eos)break; // Missing input is NOT EOF or permission to synthesize silence.
   if(s.zeros>=maxPadding)throw std::runtime_error("DSP lookahead budget exhausted");
   const auto sent=s.stream->enqueue(nullptr,std::min(opt.maximumFrames,maxPadding-s.zeros));checkMap();
   s.zeros+=sent;report.zeroPadding[which]+=sent;
   if(!n&&!sent)break;
  }
  return s.count-s.offset;
 }
};
LivePcmExecutor::LivePcmExecutor(const std::vector<std::int64_t>&p,LivePcmOptions o):impl_(std::make_unique<Impl>(p,o)){}
LivePcmExecutor::~LivePcmExecutor()=default;
unsigned LivePcmExecutor::push(unsigned side,const float* p,unsigned frames,std::int64_t first){
 auto&i=*impl_;i.good();need(side<2&&frames<=i.opt.maximumFrames&&(frames==0||p),"Invalid PCM input");auto&s=*i.sides[side];
 need(!s.eos&&first==s.expected,"Noncontiguous or post-EOF source");
 need(s.expected<=9007199254740991LL-frames,"Source counter overflow");
 for(std::size_t k=0;k<std::size_t(frames)*i.opt.channels;++k)need(std::isfinite(p[k]),"Nonfinite PCM");
 try{const auto n=s.stream->enqueue(p,frames);i.checkMap();s.expected+=n;i.report.accepted[side]+=n;return n;}catch(...){i.report.failed=true;throw;}
}
void LivePcmExecutor::endInput(unsigned side,std::int64_t end){
 auto&i=*impl_;i.good();need(side<2,"Invalid side");auto&s=*i.sides[side];
 need(end==s.expected,"EOF must follow accepted source frames");
 if(s.eos){need(end==s.sourceEnd,"Conflicting EOF");return;}
 const double seconds=s.plan.cue+double(end-s.cue)/i.opt.sampleRate;
 need(seconds+1.0/i.opt.sampleRate>=s.plan.end,"Source ended before planned region");
 s.eos=true;s.sourceEnd=end;s.outputEnd=frame(s.clock.fromSong(seconds).transition,i.opt.sampleRate);
 i.report.eos[side]=true;
}
unsigned LivePcmExecutor::pull(float*out,unsigned requested){
 auto&i=*impl_;i.good();need(requested<=i.opt.maximumFrames&&(requested==0||out),"Invalid PCM output");if(!requested||i.report.finished)return 0;
 try{
  if(i.sides[1]->eos&&i.cursor>=i.sides[1]->outputEnd){i.report.finished=true;return 0;}
  auto n=requested;
  // Do not cross any activation/deactivation boundary in one mix operation.
  for(auto edge:{i.incomingStart,i.transitionEnd,i.sides[1]->outputEnd})if(edge>i.cursor)n=std::min<std::uint64_t>(n,edge-i.cursor);
  const bool active[2]{i.cursor<i.transitionEnd,i.cursor>=i.incomingStart};
  unsigned available=n;
  for(unsigned side=0;side<2;++side)if(active[side])available=std::min(available,i.fill(side,n));
  if(!available){++i.report.underrunPolls;return 0;}
  for(unsigned f=0;f<available;++f){
   float gains[2]{};
   for(unsigned side=0;side<2;++side)if(active[side]){
    auto&s=*i.sides[side];const double time=double(i.cursor+f)/i.opt.sampleRate;
    const double value=s.gain->valueAt(s.clock.fromTransition(time).song);
    need(std::isfinite(value)&&std::abs(value)<=std::numeric_limits<float>::max(),"Nonfinite output gain");gains[side]=static_cast<float>(value);
   }
   for(unsigned c=0;c<i.opt.channels;++c){float values[2]{};
    for(unsigned side=0;side<2;++side)if(active[side]){auto&s=*i.sides[side];values[side]=s.cache[(std::size_t(s.offset)+f)*i.opt.channels+c]*gains[side];}
    const float value=values[0]+values[1];need(std::isfinite(value),"Nonfinite mixed PCM");out[std::size_t(f)*i.opt.channels+c]=value;
   }
  }
  for(unsigned side=0;side<2;++side)if(active[side]){i.sides[side]->offset+=available;i.report.consumed[side]+=available;}
  i.cursor+=available;i.report.produced+=available;
  if(i.sides[1]->eos&&i.cursor>=i.sides[1]->outputEnd)i.report.finished=true;
  return available;
 }catch(...){i.report.failed=true;throw;}
}
void LivePcmExecutor::encode(const float*p,unsigned frames,unsigned width,void*dst,std::size_t bytes){
 auto&i=*impl_;i.good();need(frames<=i.opt.maximumFrames&&(width==2||width==4)&&bytes>=std::size_t(frames)*i.opt.channels*width&&(!frames||(p&&dst)),"Invalid output encoding");
 const auto samples=std::size_t(frames)*i.opt.channels;for(std::size_t k=0;k<samples;++k)need(std::isfinite(p[k]),"Nonfinite encoder input");
 auto*out=static_cast<std::uint8_t*>(dst);
 for(std::size_t k=0;k<samples;++k){
  if(width==4){std::uint32_t bits;std::memcpy(&bits,p+k,4);for(unsigned j=0;j<4;++j)out[k*4+j]=static_cast<std::uint8_t>(bits>>(j*8));}
  else {const double scaled=double(p[k])*32768.;auto v=std::round(scaled);if(v>32767){v=32767;++i.report.saturatedSamples;}else if(v< -32768){v=-32768;++i.report.saturatedSamples;}const auto bits=static_cast<std::uint16_t>(static_cast<std::int16_t>(v));out[k*2]=static_cast<std::uint8_t>(bits);out[k*2+1]=static_cast<std::uint8_t>(bits>>8);}
 }
}
double LivePcmExecutor::sourceSecondsForOutput(unsigned side,double outFrame)const{const auto&i=*impl_;need(side<2&&std::isfinite(outFrame),"Invalid clock query");return i.sides[side]->clock.fromTransition(outFrame/i.opt.sampleRate).song;}
std::int64_t LivePcmExecutor::cueFrame(unsigned side)const{need(side<2,"Invalid side");return impl_->sides[side]->cue;}
std::int64_t LivePcmExecutor::transitionFrames()const{return impl_->transitionEnd;}
LivePcmStats LivePcmExecutor::stats()const{return impl_->report;}
} // namespace lmg::automix
