#include "lmg/automix/time_pitch_synthesis.h"
#include "lmg/automix/time_pitch_setup.h"
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.good());return v; }
template<class T> std::vector<T> array(std::ifstream& f,unsigned n) { std::vector<T> v(n);f.read(reinterpret_cast<char*>(v.data()),sizeof(T)*n);CHECK(f.good());return v; }
std::ifstream fixture(const char* name) { return std::ifstream(std::string(LMG_AUTOMIX_FIXTURES)+name,std::ios::binary); }
int main() {
 // The equal-hop branch reconstructs a constant signal at steady state. This
 // catches missing/duplicate windowing and incorrect source normalization.
 for(unsigned smoothness : {4u,8u,16u}) {
  constexpr unsigned n=512,ringSize=2048;
  const auto window=timePitchAnalysisWindow(n);
  std::vector<float> input(n,.25f),previous(n),ring(ringSize),wrapped(ringSize);
  for(unsigned hop=0;hop<2*smoothness;++hop)
    timePitchIdentityWindow(input.data(),previous.data(),window.data(),n,
      static_cast<float>(smoothness),ring.data(),ringSize,hop*(n/smoothness));
  for(unsigned i=n;i<n+n/smoothness;++i) CHECK(std::fabs(ring[i]-.25f)<1e-6f);
  const auto saved=previous;
  timePitchIdentityWindow(input.data(),previous.data(),window.data(),n,
    static_cast<float>(smoothness),wrapped.data(),ringSize,ringSize-17);
  CHECK(previous==saved);
  for(float value:input) CHECK(value==.25f);
  std::vector<float> plain(ringSize);
  timePitchIdentityWindow(input.data(),previous.data(),window.data(),n,
    static_cast<float>(smoothness),plain.data(),ringSize,0);
  for(unsigned i=0;i<ringSize;++i)
    CHECK(std::fabs(plain[i]-wrapped[(i+ringSize-17)%ringSize])<1e-7f);
 }
 auto file=fixture("/time_pitch_synthesis.bin");const auto count=read<std::uint32_t>(file);CHECK(count==24);
 for(unsigned c=0;c<count;++c) {
  const auto n=read<std::uint32_t>(file),update=read<std::uint32_t>(file);const auto hop=read<double>(file);const auto inverse=read<float>(file);
  std::vector<float> arrays[7];for(auto&v:arrays)v=array<float>(file,n);
  const auto dc=read<float>(file),nyquist=read<float>(file);
  std::vector<float> expected[5];for(auto&v:expected)v=array<float>(file,n);
  const auto edges=timePitchSynthesize(arrays[0].data(),arrays[1].data(),arrays[2].data(),arrays[3].data(),arrays[4].data(),arrays[5].data(),arrays[6].data(),n,hop,inverse,update);
  CHECK(std::memcmp(&edges.dc,&dc,4)==0 && std::memcmp(&edges.nyquist,&nyquist,4)==0);
  const unsigned indices[]={0,1,4,5,6};
  for(unsigned i=0;i<5;++i)for(unsigned j=0;j<n;++j)if(std::memcmp(&arrays[indices[i]][j],&expected[i][j],4)) {
   std::cerr<<"Synthesis "<<c<<" array "<<i<<" bin "<<j<<" "<<std::hexfloat<<arrays[indices[i]][j]<<" vs "<<expected[i][j]<<'\n';throw std::runtime_error("synthesis mismatch");
  }
 }
 CHECK(file.peek()==std::char_traits<char>::eof());
 auto map=fixture("/time_pitch_mapping.bin");const auto maps=read<std::uint32_t>(map);CHECK(maps==20);
 for(unsigned c=0;c<maps;++c) {const auto n=read<std::uint32_t>(map);const auto pitch=read<float>(map);const auto expectedActive=read<std::uint32_t>(map);const auto expected=array<std::uint32_t>(map,n);std::vector<std::uint32_t> actual(n);CHECK(timePitchPrepareBinMapping(actual.data(),n,pitch)==expectedActive);CHECK(actual==expected);}
 CHECK(map.peek()==std::char_traits<char>::eof());
 auto remap=fixture("/time_pitch_remap.bin");const auto remaps=read<std::uint32_t>(remap);CHECK(remaps==20);
 for(unsigned c=0;c<remaps;++c) {
  const auto n=read<std::uint32_t>(remap);const auto r=array<float>(remap,n),m=array<float>(remap,n);const auto indices=array<std::uint32_t>(remap,n);const auto expectedR=array<float>(remap,n),expectedM=array<float>(remap,n);std::vector<float> outR(n),outM(n);
  timePitchRemapSpectrum(r.data(),m.data(),outR.data(),outM.data(),indices.data(),n,{.125f,-.25f});CHECK(std::memcmp(outR.data(),expectedR.data(),n*4)==0);CHECK(std::memcmp(outM.data(),expectedM.data(),n*4)==0);
 }
 CHECK(remap.peek()==std::char_traits<char>::eof());
 auto ola=fixture("/time_pitch_overlap_add.bin");const auto olas=read<std::uint32_t>(ola);CHECK(olas==144);
 for(unsigned c=0;c<olas;++c) {
  const auto n=read<std::uint32_t>(ola),ringSize=read<std::uint32_t>(ola),alignment=read<std::uint32_t>(ola);const auto cursor=read<std::uint64_t>(ola);
  const auto frame=array<float>(ola,n),window=array<float>(ola,n),initial=array<float>(ola,ringSize),expected=array<float>(ola,ringSize);
  std::vector<float> storage(ringSize+7);const auto address=reinterpret_cast<std::uintptr_t>(storage.data());float* ring=storage.data()+((16-(address&15))&15)/4+alignment;std::memcpy(ring,initial.data(),ringSize*4);
  timePitchOverlapAdd(ring,ringSize,cursor,frame.data(),window.data(),n);
  for(unsigned i=0;i<ringSize;++i)if(std::memcmp(ring+i,&expected[i],4)) {std::cerr<<"OLA "<<c<<" index "<<i<<" "<<std::hexfloat<<ring[i]<<" vs "<<expected[i]<<'\n';throw std::runtime_error("OLA mismatch");}
 }
 CHECK(ola.peek()==std::char_traits<char>::eof());
 std::cout<<"TimePitch synthesis/mapping/remap/OLA: 24/20/20/144 original ARM cases match\n";
}
