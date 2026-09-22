#include "lmg/automix/time_pitch_fft.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) { T v{}; f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.good());return v; }
std::vector<float> plane(std::ifstream& f,unsigned n) {std::vector<float> v(n);f.read(reinterpret_cast<char*>(v.data()),4*n);CHECK(f.good());return v;}
int main() {
 try {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/time_pitch_fft_real.bin",std::ios::binary);
  const auto count=read<unsigned>(f);CHECK(count==36);
  for(unsigned c=0;c<count;++c) {
   const auto n=read<unsigned>(f),inverse=read<unsigned>(f);
   auto r=plane(f,n/2),i=plane(f,n/2);const auto er=plane(f,n/2),ei=plane(f,n/2);
   const auto originalR=r,originalI=i;
   TimePitchRealFft fft(n);std::vector<float> time(n);
   if(inverse) {
    fft.inverse(r.data(),i.data(),time.data());
    CHECK(r==originalR && i==originalI);
    for(unsigned j=0;j<n/2;++j) {r[j]=time[2*j];i[j]=time[2*j+1];}
   } else {
    for(unsigned j=0;j<n/2;++j) {time[2*j]=r[j];time[2*j+1]=i[j];}
    fft.forward(time.data(),r.data(),i.data());
   }
   for(unsigned j=0;j<n/2;++j) if(std::memcmp(&r[j],&er[j],4)||std::memcmp(&i[j],&ei[j],4)) {
    std::cerr<<"case "<<c<<" N="<<n<<" inverse="<<inverse<<" bin="<<j<<" "<<std::hexfloat<<r[j]<<" / "<<er[j]<<" imag "<<i[j]<<" / "<<ei[j]<<'\n';throw std::runtime_error("real FFT mismatch");
   }
  }
  CHECK(f.peek()==std::char_traits<char>::eof());
  std::cout<<"36 complete original real FFT cases match bit-for-bit\n";
 } catch(const std::exception& e) {std::cerr<<e.what()<<'\n';return 1;}
}
