#include "lmg/automix/live_pcm_executor.h"
#include "lmg/automix/continuous_schedule.h"
#include "lmg/automix/processed_track_stream.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
static int assertions=0;
#define CHECK(x) do{++assertions;if(!(x))throw std::runtime_error("ASSERT: " #x);}while(false)
std::int64_t bits(double d){std::int64_t w;std::memcpy(&w,&d,8);return w;}
// Synthetic compiled observations: not claimed to be captured Apple decisions.
std::vector<std::int64_t> plan(double rate=1,double gain=.5,bool filter=false){
 const double a0=5,a1=6,b0=0,b1=rate;
 std::vector<ContinuousAutomation> aa{{effectParameter("ts_rate"),{{1,a0,128},{1,a1,128}}},
  {effectParameter("out_gain"),{{gain,a0,128},{gain,a1,128}}}, {effectParameter("bypa"),{{1,a0,128},{1,a1,128}}}};
 std::vector<ContinuousAutomation> ba{{effectParameter("ts_rate"),{{rate,b0,128},{rate,b1,128}}},
  {effectParameter("out_gain"),{{gain,b0,128},{gain,b1,128}}}, {effectParameter("bypa"),{{1,b0,128},{1,b1,128}}}};
 if(filter)for(auto* list:{&aa,&ba}){auto start=list->front().points.front().songTime,end=list->front().points.back().songTime;
  list->back().points[0].value=0;list->back().points[1].value=0;
  list->push_back({effectParameter("LP1f"),{{1000,start,128},{1000,end,128}}});}
 std::vector<std::int64_t>p(40,0);p[0]=0x4c4d47535031LL;p[1]=1;p[3]=9;p[4]=1;p[5]=10;p[6]=20;p[7]=0;p[8]=10;p[9]=8;p[10]=8;
 const double d[]{15,a0,a1,b0,b1,1,b1,1,1,rate,rate,0,1,.5,0,1,0,1,a0,b0,1,1};
 for(unsigned k=0;k<22;++k){p[11+k]=bits(d[k]);}
 p[33]=aa.size();p[34]=ba.size();p[35]=2*(aa.size()+ba.size());p[37]=1;
 for(const auto*list:{&aa,&ba})for(const auto&a:*list){const auto&cat=allEffectParameters();auto it=std::find_if(cat.begin(),cat.end(),[&](const auto&e){return e.id==a.parameter.id;});p.push_back(it-cat.begin());p.push_back(a.points.size());for(const auto&v:a.points){p.push_back(bits(v.value));p.push_back(bits(v.songTime));p.push_back(*v.curve);}}
 p[2]=p.size();return p;
}
struct Run{std::vector<float>pcm;LivePcmStats stats;};
Run run(unsigned chunk,bool starve,unsigned channels=2,double rate=1,unsigned fs=8000,bool filter=false){
 LivePcmExecutor engine(plan(rate,.5,filter),{fs,channels,256,128,1,0});
 const unsigned n0=fs,n1=static_cast<unsigned>(2*fs*rate);
 std::vector<float>a(n0*channels,.1f),b(n1*channels,.2f),out(256*channels);
 if(rate!=1)for(unsigned k=unsigned(fs*1.8);k<n1;++k)for(unsigned c=0;c<channels;++c)b[k*channels+c]=.4f;
 if(filter){for(unsigned k=0;k<n0;++k)for(unsigned c=0;c<channels;++c)a[k*channels+c]=float(.2*std::sin(2*3.141592653589793*3000*k/fs));
  for(unsigned k=0;k<n1;++k)for(unsigned c=0;c<channels;++c)b[k*channels+c]=float(.2*std::sin(2*3.141592653589793*3000*k/fs));}
 unsigned sent[2]{},stalls=0;bool ended[2]{};Run r;
 for(unsigned tick=0;tick<200000&&!engine.stats().finished;++tick){
  bool progress=false;
  for(unsigned side=0;side<2;++side){if(starve&&side==1&&tick<40)continue;auto&in=side?b:a;auto frames=side?n1:n0;
   if(sent[side]<frames){const auto n=engine.push(side,in.data()+std::size_t(sent[side])*channels,std::min(chunk,frames-sent[side]),engine.cueFrame(side)+sent[side]);sent[side]+=n;progress|=n>0;}
   else if(!ended[side]){engine.endInput(side,engine.cueFrame(side)+sent[side]);ended[side]=true;progress=true;}
  }
  auto n=engine.pull(out.data(),chunk);progress|=n>0;r.pcm.insert(r.pcm.end(),out.begin(),out.begin()+std::size_t(n)*channels);
  if(!progress){++stalls;CHECK(stalls<1000);}else stalls=0;
 }
 r.stats=engine.stats();CHECK(r.stats.finished);CHECK(!r.stats.failed);CHECK(sent[0]==n0&&sent[1]==n1);CHECK(r.stats.accepted[0]==n0&&r.stats.accepted[1]==n1);
 CHECK(r.stats.produced==2*fs);CHECK(r.pcm.size()==2*fs*channels);for(float v:r.pcm)CHECK(std::isfinite(v));return r;
}
int main(){try{
 // ABI of the local pinned declaration copies is checked against actual CI DWARF.
 std::cout<<"ProcessedTrackStream bytes="<<sizeof(ProcessedTrackStream)<<" TimePitchStream="<<sizeof(TimePitchStream)<<'\n';
 auto a=run(128,false),b=run(31,true);CHECK(a.pcm.size()==b.pcm.size());
 double peak=0;for(std::size_t i=0;i<a.pcm.size();++i)peak=std::max(peak,std::abs(double(a.pcm[i])-b.pcm[i]));CHECK(peak<2e-5);
 for(unsigned i=2500;i<6500;++i)CHECK(std::abs(a.pcm[i*2]-.15f)<2e-5f);
 for(unsigned i=10000;i<13000;++i)CHECK(std::abs(a.pcm[i*2]-.1f)<2e-5f);
 CHECK(b.stats.underrunPolls>0);
 auto mono=run(127,true,1);CHECK(mono.stats.produced==a.stats.produced);
 auto rate=run(89,false,2,1.25);CHECK(rate.stats.finished);
 double stepped=0;for(unsigned f=13000;f<14000;++f)stepped+=rate.pcm[f*2];CHECK(stepped/1000>.17);
 for(unsigned fs:{44100u,48000u}){auto first=run(127,false,2,1,fs,true),second=run(63,true,2,1,fs,true);
  CHECK(first.pcm.size()==second.pcm.size());double difference=0,energy=0;
  for(std::size_t k=0;k<first.pcm.size();++k)difference=std::max(difference,std::abs(double(first.pcm[k])-second.pcm[k]));
  CHECK(difference<2e-5);for(unsigned k=fs/4;k<fs/2;++k)energy+=first.pcm[k*2]*first.pcm[k*2];
  CHECK(std::sqrt(energy/(fs/4))<.04); // independently expected attenuation above 1kHz LP corner
 }
 LivePcmExecutor e(plan(),{8000,1,256,128,3,2});
 const float values[]{-2,-1,-.5f,0,.5f,1,2};std::uint8_t encoded[28]{};e.encode(values,7,2,encoded,sizeof(encoded));
 CHECK(encoded[0]==0&&encoded[1]==128&&encoded[8]==0&&encoded[9]==64&&encoded[10]==255&&encoded[11]==127);CHECK(e.stats().saturatedSamples==3);
 e.encode(values,7,4,encoded,sizeof(encoded));CHECK(std::memcmp(values,encoded,sizeof(encoded))==0);
 unsigned rejects=0;for(int k=0;k<8;++k){auto p=plan();if(k==0)p[0]=0;if(k==1)p[1]=99;if(k==2)p[36]=1;if(k==3)p[2]--;if(k==4)p[18]=bits(0);if(k==5)p[33]=33;if(k==6)p.back()=77;if(k==7)p[12]=bits(std::numeric_limits<double>::quiet_NaN());try{LivePcmExecutor bad(p,{8000,1,256,128,1,0});}catch(const std::invalid_argument&){++rejects;}}
 CHECK(rejects==8);
 bool rejected=false;try{float z=0;e.push(0,&z,1,0);}catch(const std::invalid_argument&){rejected=true;}CHECK(rejected);
 std::cout<<"LIVE_PCM_DSP_TESTS_PASSED assertions="<<assertions<<" partitionError="<<peak<<"\n";
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
