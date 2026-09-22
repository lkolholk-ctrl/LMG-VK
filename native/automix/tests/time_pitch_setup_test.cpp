#include "lmg/automix/time_pitch_setup.h"
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.good());return v; }
std::ifstream fixture(const char* name) { return std::ifstream(std::string(LMG_AUTOMIX_FIXTURES)+name,std::ios::binary); }
template<class F> void rejects(F f) { bool yes=false;try{f();}catch(const std::invalid_argument&){yes=true;}CHECK(yes); }
int main() {
  auto geometry=fixture("/time_pitch_geometry.bin");
  auto count=read<std::uint32_t>(geometry);CHECK(count==375);
  for (unsigned i=0;i<count;++i) {
    const auto rate=read<double>(geometry);const auto frames=read<std::uint32_t>(geometry),quality=read<std::uint32_t>(geometry);
    const auto actual=timePitchGeometry(rate,frames,quality);
    const std::uint32_t values[]={actual.fftSize,actual.halfFftSize,actual.log2FftSize,actual.inputRingSize,actual.outputRingSize};
    for(auto v:values) CHECK(v==read<std::uint32_t>(geometry));
    CHECK(actual.inverseFftSize==read<float>(geometry));
  }
  CHECK(geometry.peek()==std::char_traits<char>::eof());
  auto window=fixture("/time_pitch_window.bin");count=read<std::uint32_t>(window);CHECK(count==6);
  for(unsigned i=0;i<count;++i) {
    const auto n=read<std::uint32_t>(window);const auto actual=timePitchAnalysisWindow(n);
    for(unsigned j=0;j<n;++j) { const auto expected=read<float>(window);CHECK(std::memcmp(&actual[j],&expected,sizeof(float))==0); }
  }
  CHECK(window.peek()==std::char_traits<char>::eof());
  auto latency=fixture("/time_pitch_latency.bin");count=read<std::uint32_t>(latency);CHECK(count==540);
  for(unsigned i=0;i<count;++i) {
    const auto rate=read<double>(latency);const auto n=read<std::uint32_t>(latency);const auto speed=read<float>(latency);const auto expected=read<double>(latency);
    const TimePitchGeometry g{n,n/2,0,0,0,0};
    const auto actual=timePitchLatencySeconds(g,rate,speed,false);
    CHECK(std::memcmp(&actual,&expected,sizeof(double))==0);
    CHECK(timePitchLatencySeconds(g,rate,speed,true)==0);
  }
  CHECK(latency.peek()==std::char_traits<char>::eof());
  rejects([]{timePitchGeometry(0,512);});rejects([]{timePitchGeometry(48000,0);});
  rejects([]{timePitchGeometry(48000,16385);});rejects([]{timePitchAnalysisWindow(1023);});
  rejects([]{timePitchGeometry(std::numeric_limits<double>::quiet_NaN(),512);});
  const auto g=timePitchGeometry(48000,512);
  rejects([&]{timePitchLatencySeconds(g,48000,0,false);});
  std::cout<<"TimePitch setup: 375 geometry cases, 16128 window coefficients, 540 latency cases match ARM\n";
}
