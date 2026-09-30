#include "lmg/automix/time_pitch_stream.h"
#include "lmg/automix/live_pcm_executor.h"
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <new>
#include <stdexcept>
#include <vector>

// Counts actual C++ allocations in the linked production kernels. Preparation,
// logging and test fixture construction are intentionally outside the measured span.
static thread_local bool measuring = false;
static std::atomic<std::size_t> allocations{0};
void* operator new(std::size_t n) {
  if(measuring) ++allocations;
  if(void* p=std::malloc(n?n:1)) return p;
  throw std::bad_alloc();
}
void* operator new[](std::size_t n) { return ::operator new(n); }
void operator delete(void* p) noexcept {std::free(p);}
void operator delete[](void* p) noexcept {std::free(p);}
void operator delete(void* p,std::size_t) noexcept {std::free(p);}
void operator delete[](void* p,std::size_t) noexcept {std::free(p);}
void* operator new(std::size_t n,std::align_val_t a) {
  if(measuring)++allocations;
  void* p=nullptr;
  if(posix_memalign(&p,static_cast<std::size_t>(a),n?n:1)==0)return p;
  throw std::bad_alloc();
}
void operator delete(void* p,std::align_val_t) noexcept {std::free(p);}
void operator delete(void* p,std::size_t,std::align_val_t) noexcept {std::free(p);}
using namespace lmg::automix;
#define CHECK(x) do { if(!(x)){measuring=false;throw std::runtime_error("ASSERT " #x);} } while(false)
struct Mapping {
  double origin, first, second, breakpoint;
  static double get(const void* ptr,double output) noexcept {
    const auto& m=*static_cast<const Mapping*>(ptr);
    return m.origin + std::min(output,m.breakpoint)*m.first + std::max(0.0,output-m.breakpoint)*m.second;
  }
};
void clocks() {
  double worst=0;
  unsigned cases=0;
  for(double origin : {0.,10000000.})for(double first : {.9995,1.0005,.750125,1.250125}) {
    Mapping map{origin,first,2.-first,48000.};
    TimePitchStream stream(48000,1,1024,true,{1,1,8,false,false});
    stream.setTimeMap({&map,Mapping::get});
    std::vector<float> input(1024,.125f),output(1024);
    const float* p=input.data();float* q=output.data();
    for(unsigned pass=0;pass<2;++pass) {
      stream.reset(origin,0);
      std::int64_t sent=0,got=0;
      allocations=0;measuring=true;
      for(unsigned block=0;block<1000;++block) {
        const unsigned n=block%2?1024:509;
        sent+=stream.enqueue(&p,n,origin+double(sent));
        got+=stream.dequeue(&q,n,double(got));
        const auto& state=stream.state();
        const double error=(state.inputTime-origin)-double(state.inputRead);
        CHECK(error>=-1e-7 && error<1.000001);
        worst=std::max(worst,error);
        CHECK(state.totalInput==sent && state.totalOutput==got);
      }
      measuring=false;
      CHECK(allocations==0); CHECK(got>400000);
      ++cases;
    }
  }
  std::cout<<"Mapped cursor: "<<cases<<" origin/rate/reset cases, worst residual="<<worst
           <<" samples; 0 processing allocations\n";
}
std::int64_t bits(double x){std::int64_t w;std::memcpy(&w,&x,8);return w;}
std::vector<std::int64_t> plan() {
  std::vector<std::int64_t> p(40,0);
  p[0]=0x4c4d47535031LL;p[1]=1;p[3]=9;p[4]=1;p[5]=10;p[6]=20;p[8]=10;p[9]=8;p[10]=8;
  const double values[]{15,5,6,0,1,1,1,1,1,1,1,0,1,.5,0,1,0,1,5,0,1,1};
  for(unsigned i=0;i<22;++i)p[11+i]=bits(values[i]);
  p[33]=3;p[34]=3;p[35]=12;p[37]=1;
  for(unsigned side=0;side<2;++side)for(unsigned id:{26u,27u,28u}) {
    const double begin=side?0:5,end=side?1:6,v=id==27?.5:1.;
    p.insert(p.end(),{id,2,bits(v),bits(begin),128,bits(v),bits(end),128});
  }
  p[2]=static_cast<std::int64_t>(p.size());return p;
}
void pumpAndEncoding() {
  LivePcmExecutor engine(plan(),{48000,2,256,256,1,1});
  std::vector<float> a(48000*2,.1f),b(96000*2,.2f),out(512);
  std::vector<unsigned char> bytes(2048);
  unsigned sent[2]{}; bool eof[2]{};std::int64_t produced=0;
  allocations=0;measuring=true;
  for(unsigned tick=0;tick<10000 && !engine.stats().finished;++tick) {
    for(unsigned side=0;side<2;++side) {
      const unsigned frames=side?96000:48000;
      if(sent[side]<frames){const auto& source=side?b:a;
        sent[side]+=engine.push(side,source.data()+sent[side]*2,std::min(251u,frames-sent[side]),engine.cueFrame(side)+sent[side]);
      } else if(!eof[side]){engine.endInput(side,engine.cueFrame(side)+sent[side]);eof[side]=true;}
    }
    const auto n=engine.pull(out.data(),127);
    engine.encode(out.data(),n,4,bytes.data(),bytes.size()); produced+=n;
  }
  measuring=false;
  CHECK(engine.stats().finished);CHECK(produced==96000);CHECK(allocations==0);
  LivePcmExecutor converter(plan(),{8000,1,256,128,2,1});
  std::vector<float> values(256); std::vector<unsigned char> encoded(1024);
  for(int base=-32768;base<=32512;base+=256) {
    for(int i=0;i<256;++i)values[i]=float(base+i)/32768.f;
    converter.encode(values.data(),256,2,encoded.data(),encoded.size());
    for(int i=0;i<256;++i) {
      const auto actual=std::uint16_t(encoded[2*i])|(std::uint16_t(encoded[2*i+1])<<8);
      CHECK(actual==std::uint16_t(base+i));
    }
  }
  const float special[]{-0.f,0.f,-2.f,2.f,-1.f,1.f,0x1p-149f};
  converter.encode(special,7,4,encoded.data(),encoded.size());
  CHECK(std::memcmp(special,encoded.data(),sizeof(special))==0);
  CHECK(converter.stats().saturatedSamples==0);
  std::cout<<"Real DSP push/pull/encode: 0 processing allocations, 96000 frames; "
           <<"65536 exact PCM16 roundtrips; float signed-zero/subnormal/headroom preserved\n";
}
void warmHandoff() {
  unsigned cases=0;double worst=0;
  for(unsigned fs:{8000u,44100u,48000u})for(unsigned channels:{1u,2u})for(bool tone:{false,true}) {
    auto program=plan();
    // Unity gain, bypass graph: isolate state continuity rather than manufacture
    // a fade to hide a cold STFT. Incoming source is silent in this fixture.
    for(std::size_t pos=40;pos<program.size();) {
      const auto id=program[pos++],count=program[pos++];
      for(std::int64_t j=0;j<count;++j){if(id==27)program[pos]=bits(1.);pos+=3;}
    }
    LivePcmExecutor engine(program,{fs,channels,256,256,1,1,true});
    const auto pre=engine.outgoingPrerollFrames();
    CHECK(pre==timePitchGeometry(fs,256).halfFftSize);
    const auto begin=engine.cueFrame(0)-pre;
    std::vector<float>a(256*channels),b(256*channels,0),out(256*channels);
    auto signal=[&](std::int64_t frame,unsigned c) {
      return static_cast<float>((c==0?1.:-.7)*(tone?.1*std::sin(double(frame)*.037):.1));
    };
    std::int64_t sentA=0,sentB=0,got=0;
    allocations=0;measuring=true;
    for(unsigned tick=0;tick<200 && got<4096;++tick) {
      for(unsigned f=0;f<251;++f)for(unsigned c=0;c<channels;++c)
        a[f*channels+c]=signal(begin+sentA+f,c);
      sentA+=engine.push(0,a.data(),251,begin+sentA);
      sentB+=engine.push(1,b.data(),251,sentB);
      const auto n=engine.pull(out.data(),127);
      for(unsigned f=0;f<n;++f)for(unsigned c=0;c<channels;++c) {
        const auto error=std::abs(double(out[f*channels+c])-signal(engine.cueFrame(0)+got+f,c));
        worst=std::max(worst,error);CHECK(error<2e-6);
      }
      got+=n;
    }
    measuring=false;CHECK(allocations==0);CHECK(got>=4096);++cases;
  }
  std::cout<<"Warm handoff: "<<cases<<" sample-rate/channel/DC/tone cases, max error="<<worst
           <<"; actual source history, no replayed output, 0 processing allocations\n";
}
int main(int argc,char**argv) {
  try { if(argc==2 && std::strcmp(argv[1],"clock")==0)clocks();
    else if(argc==2 && std::strcmp(argv[1],"warm")==0)warmHandoff();else pumpAndEncoding(); }
  catch(const std::exception&e){measuring=false;std::cerr<<e.what()<<'\n';return 1;}
}
