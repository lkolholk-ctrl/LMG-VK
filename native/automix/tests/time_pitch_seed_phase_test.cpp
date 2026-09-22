#include "lmg/automix/time_pitch_seed_phase.h"
#include <vector>
#include <fstream>
#include <iostream>
#include <cstring>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do {if(!(x)) throw std::runtime_error(#x);}while(false)
template<class T> T read(std::ifstream& f){T t{};f.read(reinterpret_cast<char*>(&t),sizeof t);CHECK(f.good());return t;}
std::vector<float> plane(std::ifstream& f,unsigned n){std::vector<float> v(n);f.read(reinterpret_cast<char*>(v.data()),n*4);CHECK(f.good());return v;}
int main(){
 std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/time_pitch_seed_phase.bin",std::ios::binary);const auto count=read<unsigned>(f);CHECK(count==12);
 for(unsigned c=0;c<count;++c){const auto n=read<unsigned>(f);auto r=plane(f,n),i=plane(f,n),expected=plane(f,n);std::vector<float> actual(n);timePitchSeedPhaseRadians(r.data(),i.data(),actual.data(),n);
  for(unsigned j=0;j<n;++j)if(std::memcmp(&actual[j],&expected[j],4)){std::cerr<<"case "<<c<<" index "<<j<<" inputs "<<r[j]<<","<<i[j]<<" result "<<std::hexfloat<<actual[j]<<" expected "<<expected[j]<<'\n';return 1;}}
 CHECK(f.peek()==std::char_traits<char>::eof());std::cout<<"Phase reseeding: 12 original vector cases match bit-for-bit\n";
}
