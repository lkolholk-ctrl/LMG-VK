#include "lmg/automix/time_pitch_fft_complex.h"
#include <cmath>
#include <cstring>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.gcount()==sizeof v);return v; }
std::vector<float> plane(std::ifstream& f,unsigned n) { std::vector<float> v(n);f.read(reinterpret_cast<char*>(v.data()),4*n);CHECK(f.gcount()==4*n);return v; }
std::uint32_t bits(float v) {std::uint32_t n;std::memcpy(&n,&v,4);return n;}
int main() {
 try {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/time_pitch_fft_complex.bin",std::ios::binary);CHECK(f.good());
  auto count=read<unsigned>(f);CHECK(count==72);
  for(unsigned c=0;c<count;++c) {
   const auto n=read<unsigned>(f),inverse=read<unsigned>(f);
   auto r=plane(f,n),i=plane(f,n);const auto er=plane(f,n),ei=plane(f,n);
   TimePitchComplexFft fft(n);fft.transform(r.data(),i.data(),inverse);
   unsigned mismatch=0;
   for(unsigned j=0;j<n;++j) {
    if(bits(r[j])!=bits(er[j]) || bits(i[j])!=bits(ei[j])) {
     if(mismatch++<3)std::cerr<<"case "<<c<<" n="<<n<<" inverse="<<inverse<<" bin="<<j<<" r="<<r[j]<<" / "<<er[j]<<" i="<<i[j]<<" / "<<ei[j]<<'\n';
    }
   }
   if(mismatch)throw std::runtime_error("FFT mismatches "+std::to_string(mismatch));
  }
  CHECK(f.peek()==std::char_traits<char>::eof());
  std::cout<<count<<" original complex FFT cases match bitwise\n";
 }catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}
