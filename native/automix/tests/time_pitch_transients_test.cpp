#include "lmg/automix/time_pitch_transients.h"
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.good());return v; }
template<class T> std::vector<T> readArray(std::ifstream& f,unsigned n) { std::vector<T> v(n);f.read(reinterpret_cast<char*>(v.data()),sizeof(T)*n);CHECK(f.good());return v; }
std::ifstream fixture(const char* name) { return std::ifstream(std::string(LMG_AUTOMIX_FIXTURES)+name,std::ios::binary); }
int main() {
 auto file=fixture("/time_pitch_coherence.bin");const auto count=read<std::uint32_t>(file);CHECK(count==48);
 for(unsigned c=0;c<count;++c) {
  auto n=read<std::uint32_t>(file);const auto pitch=read<float>(file);const auto expectedCount=read<std::uint32_t>(file),maximum=read<std::uint32_t>(file);
  const auto magnitude=readArray<float>(file,n);auto correction=readArray<float>(file,n);const auto expected=readArray<float>(file,n);const auto expectedMap=readArray<std::uint32_t>(file,n);
  const auto expectedPeaks=readArray<std::uint32_t>(file,expectedCount),expectedStarts=readArray<std::uint32_t>(file,expectedCount),expectedEnds=readArray<std::uint32_t>(file,expectedCount);
  std::vector<std::uint32_t> map(n),peaks(n),starts(n),ends(n);TimePitchRegions regions{peaks.data(),starts.data(),ends.data()};
  timePitchApplyCoherence(magnitude.data(),correction.data(),map.data(),n,pitch,regions);
  if(regions.count!=expectedCount || regions.maximumBin!=maximum || std::memcmp(correction.data(),expected.data(),n*4) || map!=expectedMap ||
    std::memcmp(peaks.data(),expectedPeaks.data(),expectedCount*4) || std::memcmp(starts.data(),expectedStarts.data(),expectedCount*4) || std::memcmp(ends.data(),expectedEnds.data(),expectedCount*4)) {
    std::cerr<<"Coherence case "<<c<<" count "<<regions.count<<" expected "<<expectedCount<<'\n';throw std::runtime_error("coherence mismatch");
  }
 }
 CHECK(file.peek()==std::char_traits<char>::eof());
 auto trans=fixture("/time_pitch_transients.bin");const auto transCount=read<std::uint32_t>(trans);CHECK(transCount==960);
 for(unsigned c=0;c<transCount;++c) {
  const auto n=read<std::uint32_t>(trans),regionCount=read<std::uint32_t>(trans),maximum=read<std::uint32_t>(trans);
  const auto rate=read<double>(trans),output=read<double>(trans),previous=read<double>(trans),effective=read<double>(trans);
  const auto position=read<float>(trans);const auto active=read<std::uint32_t>(trans);const auto debt=read<std::int32_t>(trans);
  auto starts=readArray<std::uint32_t>(trans,regionCount),ends=readArray<std::uint32_t>(trans,regionCount);
  const auto magnitude=readArray<float>(trans,n),analysis=readArray<float>(trans,n);auto correction=readArray<float>(trans,n);
  const auto expectedPosition=read<float>(trans);const auto expectedActive=read<std::uint32_t>(trans);const auto expectedDebt=read<std::int32_t>(trans);const auto expectedHop=read<double>(trans);
  const auto expectedCorrection=readArray<float>(trans,n),expectedScratch=readArray<float>(trans,n);
  std::vector<float> scratch(n,-123.f);TimePitchRegions regions{nullptr,starts.data(),ends.data(),regionCount,maximum};
  TimePitchTransientState state{position,active!=0,debt,effective,previous};
  timePitchPreserveTransients(magnitude.data(),analysis.data(),correction.data(),scratch.data(),n,regions,rate,output,state);
  if(std::memcmp(&state.position,&expectedPosition,4)||state.active!=(expectedActive!=0)||state.frameDebt!=expectedDebt||std::memcmp(&state.effectiveInputHop,&expectedHop,8)||std::memcmp(correction.data(),expectedCorrection.data(),n*4)||std::memcmp(scratch.data(),expectedScratch.data(),n*4)) {
   std::cerr<<"Transient case "<<c<<" pos "<<state.position<<" / "<<expectedPosition<<" active "<<state.active<<" / "<<expectedActive<<" debt "<<state.frameDebt<<" / "<<expectedDebt<<" hop "<<state.effectiveInputHop<<" / "<<expectedHop<<'\n';throw std::runtime_error("transient mismatch");
  }
 }
 CHECK(trans.peek()==std::char_traits<char>::eof());
 std::cout<<"TimePitch coherence/transients: 48 / 960 original ARM cases match bit-for-bit\n";
}
