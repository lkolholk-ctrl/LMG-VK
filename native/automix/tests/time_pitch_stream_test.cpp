#include "lmg/automix/time_pitch_stream.h"
#include "lmg/automix/time_pitch_time_map.h"
#include <cmath>
#include <cstring>
#include <iostream>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do{if(!(x))throw std::runtime_error(#x);}while(false)
struct TestMap {
 bool changing;
 static double source(const void* context,double output) noexcept {
  const auto& map=*static_cast<const TestMap*>(context);
  if(!map.changing)return output;
  // Synthetic prepared mapping exercising fractional hops and a slope change;
  // this test callback is not an implementation of Apple's schedule builder.
  return output<4096 ? output*.75 : 3072+(output-4096)*1.125;
 }
};
std::vector<float> run(unsigned chunk,double rate,bool scheduled,int mapping=0) {
 TimePitchStream stream(8000,2,128,scheduled,{rate,1,8,true,true});
 TestMap map{mapping==2};
 const TimePitchTimeMap recoveredMap({timePitchMapSegment(.75,1.25,0,12000,0)});
 if(mapping==3)stream.setTimeMap(recoveredMap.view());
 else if(mapping)stream.setTimeMap({&map,&TestMap::source});
 std::vector<float> left(8192),right(8192),a(128),b(128),result;
 for(unsigned i=0;i<left.size();++i){left[i]=std::sin(i*.1f)*.2f;right[i]=left[i]*.5f;}
 unsigned sent=0;double time=0;
 for(unsigned iteration=0;iteration<10000;++iteration){
  unsigned accepted=0;
  if(sent<left.size()) {const float* in[]{left.data()+sent,right.data()+sent};accepted=stream.enqueue(in,std::min<unsigned>(chunk,left.size()-sent),sent);sent+=accepted;}
  float* out[]{a.data(),b.data()};const auto n=stream.dequeue(out,chunk,time);time+=n;
  for(unsigned i=0;i<n;++i){CHECK(std::isfinite(a[i])&&std::isfinite(b[i]));result.push_back(a[i]);result.push_back(b[i]);}
  if(sent==left.size() && n==0)break;
  CHECK(accepted||n);
 }
 CHECK(sent==left.size());CHECK(result.size()>4000);
 CHECK(stream.state().totalInput==left.size());CHECK(stream.state().totalOutput==static_cast<long>(result.size()/2));
 return result;
}
int main(){try{
 const auto plain=run(64,1,false),mappedUnity=run(64,1,false,1);
 CHECK(plain==mappedUnity);
 const auto mappedA=run(64,1,true,2),mappedB=run(127,1,true,2);
 CHECK(mappedA.size()==mappedB.size());
 CHECK(std::memcmp(mappedA.data(),mappedB.data(),mappedA.size()*sizeof(float))==0);
 const auto rampA=run(64,1,true,3),rampB=run(127,1,true,3);
 CHECK(rampA.size()==rampB.size());
 CHECK(std::memcmp(rampA.data(),rampB.data(),rampA.size()*sizeof(float))==0);
 for(bool scheduled:{false,true})for(double rate:{.75,1.,1.25}){
  const auto a=run(64,rate,scheduled),b=run(127,rate,scheduled);
  CHECK(a.size()==b.size());for(unsigned j=0;j<a.size();++j)if(std::memcmp(&a[j],&b[j],4)){std::cerr<<"scheduled="<<scheduled<<" rate="<<rate<<" sample="<<j<<" a="<<std::hexfloat<<a[j]<<" b="<<b[j]<<"\n";throw std::runtime_error("block partition mismatch");}
  bool signal=false;for(float value:a)signal|=std::fabs(value)>.01f;CHECK(signal);
 }
 // Orthonormal mid/side encode+decode must retain each physical channel's level.
 TimePitchStream s(8000,2,128,false,{1,1,8,true,true});std::vector<float> l(128,.2f),r(128,-.1f),ol(128),orr(128);
 const float* in[]{l.data(),r.data()};float* out[]{ol.data(),orr.data()};unsigned total=0;
 for(unsigned k=0;k<40;++k){CHECK(s.enqueue(in,128,k*128)==128);auto n=s.dequeue(out,128,total);total+=n;if(k>12)for(unsigned j=0;j<n;++j){CHECK(std::fabs(ol[j]-.2f)<1e-6f);CHECK(std::fabs(orr[j]+.1f)<1e-6f);}}
 s.reset();CHECK(s.state().totalInput==0&&s.state().totalOutput==0);
 bool rejected=false;try{s.configure({0,1,8,true,true});}catch(const std::invalid_argument&){rejected=true;}CHECK(rejected);
 std::cout<<"Streaming TimePitch: block partition, mono/stereo clocks, rates, padding and channel levels passed\n";
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
