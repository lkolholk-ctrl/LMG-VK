#include "lmg/automix/time_pitch_phase.h"
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.good());return v; }
std::ifstream fixture(const char* name) { return std::ifstream(std::string(LMG_AUTOMIX_FIXTURES)+name,std::ios::binary); }
int main() {
  auto phase=fixture("/time_pitch_phase.bin");const auto count=read<std::uint32_t>(phase);CHECK(count==144);
  for(unsigned c=0;c<count;++c) {
    const auto n=read<std::uint32_t>(phase);const auto inverse=read<float>(phase);
    const auto input=read<double>(phase),output=read<double>(phase);const auto pitch=read<float>(phase);
    std::vector<float> arrays[7];for(auto& v:arrays)v.resize(n);
    for(unsigned i=0;i<6;++i)for(auto& v:arrays[i])v=read<float>(phase);
    timePitchAnalyzePhase(arrays[0].data(),arrays[1].data(),arrays[2].data(),arrays[3].data(),arrays[6].data(),n,inverse,input,output,pitch);
    for(unsigned i=0;i<n;++i) {
      if(std::memcmp(&arrays[2][i],&arrays[4][i],4)||std::memcmp(&arrays[6][i],&arrays[5][i],4)) {
        std::cerr<<"Phase case "<<c<<" bin "<<i<<" analysis "<<std::hexfloat<<arrays[2][i]<<" / "<<arrays[4][i]<<" correction "<<arrays[6][i]<<" / "<<arrays[5][i]<<'\n';throw std::runtime_error("phase mismatch");
      }
    }
  }
  CHECK(phase.peek()==std::char_traits<char>::eof());
  auto hop=fixture("/time_pitch_hop.bin");const auto hops=read<std::uint32_t>(hop);CHECK(hops==320);
  for(unsigned c=0;c<hops;++c) {
    const auto mapped=read<std::uint32_t>(hop),n=read<std::uint32_t>(hop);const auto smooth=read<float>(hop);const auto rate=read<double>(hop),target=read<double>(hop);
    TimePitchHopState state{read<double>(hop),read<double>(hop),read<double>(hop),read<double>(hop)};
    double expected[5];for(auto&v:expected)v=read<double>(hop);
    auto actual=mapped?timePitchAdvanceMappedHop(state,n,smooth,target):timePitchAdvanceHop(state,n,smooth,rate);
    double values[]={actual.nextInputTime,actual.nextOutputTime,state.previousRoundedInputHop,actual.inputFrames,actual.outputFrames};
    CHECK(std::memcmp(values,expected,sizeof values)==0);
  }
  CHECK(hop.peek()==std::char_traits<char>::eof());
  std::cout<<"TimePitch phase/hop: 144 original SIMD cases and 320 clock cases match ARM\n";
}
