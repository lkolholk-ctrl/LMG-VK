#include "lmg/automix/processed_track_stream.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if(!(x)) throw std::runtime_error(#x); } while(false)
namespace {
constexpr unsigned capacity=128, length=8192;
std::vector<GraphEvent> events(double fs) {
  EffectSettings a,b; a.inputGain=.25; b.inputGain=.8;
  return {{37,PreparedGraph(fs,a)},{2051,PreparedGraph(fs,b)}};
}
std::vector<float> reference(const std::vector<float>& pcm,unsigned channels,double rate,unsigned chunk) {
  ScheduledTrackGraph graph(PreparedGraph(8000),channels,capacity,0,events(8000));
  std::vector<float> processed(pcm.size());
  // Apple's gain kernel has block-dependent float accumulation. Match source
  // graph slices; vary downstream pacing independently to test the transport.
  for(unsigned at=0;at<length;at+=chunk)
    CHECK(graph.process(pcm.data()+at*channels,processed.data()+at*channels,std::min(chunk,length-at)));
  TimePitchStream pitch(8000,channels,capacity,true,{rate,1,8,true,true});
  std::vector<float> l(length),r(length),ol(capacity),orr(capacity),result;
  for(unsigned i=0;i<length;++i){l[i]=processed[i*channels];if(channels==2)r[i]=processed[i*2+1];}
  unsigned sent=0,received=0;
  for(unsigned iteration=0;iteration<10000;++iteration) {
    const float* in[]{l.data()+sent,r.data()+sent};
    const auto accepted=pitch.enqueue(in,std::min(capacity,length-sent),sent);sent+=accepted;
    float* out[]{ol.data(),orr.data()};const auto n=pitch.dequeue(out,capacity,received);received+=n;
    for(unsigned i=0;i<n;++i){result.push_back(ol[i]);if(channels==2)result.push_back(orr[i]);}
    if(sent==length&&!n)return result;
    CHECK(accepted||n);
  }
  throw std::runtime_error("reference did not drain");
}
void compare(unsigned channels,double rate,unsigned chunk) {
  std::vector<float> pcm(length*channels);
  for(unsigned i=0;i<length;++i)for(unsigned c=0;c<channels;++c)
    pcm[i*channels+c]=std::sin(i*.073f+c)*.3f;
  const auto expected=reference(pcm,channels,rate,chunk);
  ProcessedTrackStream stream(8000,channels,capacity,0,0,{},events(8000),true,{rate,1,8,true,true});
  std::vector<float> output(capacity*channels),actual;
  unsigned sent=0,received=0;bool pressure=false,finished=false;
  for(unsigned iteration=0;iteration<10000;++iteration) {
    // Fill without pulling, forcing TimePitch backpressure and partial staged
    // consumption. Retried input must not run the DSPGraph twice.
    while(sent<length) {
      auto accepted=stream.enqueue(pcm.data()+sent*channels,std::min(chunk,length-sent));
      if(!accepted){pressure=true;break;}
      sent+=accepted;CHECK(stream.inputPosition()==sent);
    }
    const auto n=stream.dequeue(output.data(),chunk,received);received+=n;
    actual.insert(actual.end(),output.begin(),output.begin()+n*channels);
    if(sent==length&&!stream.pendingFrames()&&!n){finished=true;break;}
    CHECK(n||stream.pendingFrames());
  }
  CHECK(finished&&pressure);CHECK(expected.size()==actual.size());
  for(std::size_t i=0;i<actual.size();++i) if(expected[i]!=actual[i]) {
    std::cerr<<"channels="<<channels<<" rate="<<rate<<" chunk="<<chunk<<" sample="<<i
             <<" expected="<<std::hexfloat<<expected[i]<<" actual="<<actual[i]<<'\n';
    throw std::runtime_error("composed stream differs from reference");
  }
  const auto before=stream.inputPosition();
  bool rejected=false;float bad=std::numeric_limits<float>::quiet_NaN();
  try{stream.enqueue(&bad,1);}catch(const std::invalid_argument&){rejected=true;}
  CHECK(rejected&&before==stream.inputPosition());
  CHECK(stream.enqueue(nullptr,0)==0);CHECK(stream.dequeue(nullptr,0,received)==0);
}
}
int main(){try{
  for(unsigned channels:{1u,2u})for(double rate:{.75,1.,1.25})for(unsigned chunk:{64u,127u})
    compare(channels,rate,chunk);
  std::cout<<"DSPGraph -> TimePitch: source events, backpressure, mono/stereo and rates passed\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
