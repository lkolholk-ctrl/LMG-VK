#include "lmg/automix/time_pitch_magnitude.h"
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
int main() {
 std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/time_pitch_magnitude.bin",std::ios::binary);const auto count=read<std::uint32_t>(f);CHECK(count==504);
 for(unsigned c=0;c<count;++c) {
  const auto n=read<std::uint32_t>(f),alignment=read<std::uint32_t>(f);const auto real=array<float>(f,n),imaginary=array<float>(f,n),expected=array<float>(f,n);
  std::vector<float> storage(n+7);const auto address=reinterpret_cast<std::uintptr_t>(storage.data());float* output=storage.data()+((16-(address&15))&15)/4+alignment;
  timePitchMagnitudes(real.data(),imaginary.data(),output,n);
  for(unsigned i=0;i<n;++i)if(std::memcmp(output+i,&expected[i],4)) {std::cerr<<"Magnitude case "<<c<<" bin "<<i<<" "<<std::hexfloat<<output[i]<<" vs "<<expected[i]<<" inputs "<<real[i]<<","<<imaginary[i]<<'\n';throw std::runtime_error("magnitude mismatch");}
 }
 CHECK(f.peek()==std::char_traits<char>::eof());
 std::cout<<"TimePitch magnitudes: 504 original ARM cases match bit-for-bit\n";
}
