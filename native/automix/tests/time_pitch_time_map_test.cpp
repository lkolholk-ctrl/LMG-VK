#include "lmg/automix/time_pitch_time_map.h"
#include <fstream>
#include <cstring>
#include <iostream>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do{if(!(x))throw std::runtime_error(#x);}while(false)
template<class T> T read(std::ifstream& f){T x{};f.read(reinterpret_cast<char*>(&x),sizeof x);CHECK(f.good());return x;}
int main(){
 std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/time_pitch_time_map.bin",std::ios::binary);const auto count=read<unsigned>(f);CHECK(count==96);
 for(unsigned c=0;c<count;++c){double input[5],expected[7],times[5],values[5];for(auto&x:input)x=read<double>(f);for(auto&x:expected)x=read<double>(f);for(auto&x:times)x=read<double>(f);for(auto&x:values)x=read<double>(f);
  const auto segment=timePitchMapSegment(input[0],input[1],input[2],input[3],input[4]);const double actual[]{segment.startRate,segment.endRate,segment.startSource,segment.endSource,segment.startOutput,segment.endOutput,segment.slope};CHECK(std::memcmp(actual,expected,sizeof expected)==0);
  for(unsigned j=0;j<5;++j){const auto result=timePitchMapSourceFrame(segment,times[j]);CHECK(std::memcmp(&result,&values[j],8)==0);}}
 CHECK(f.peek()==std::char_traits<char>::eof());
 TimePitchTimeMap empty({});CHECK(empty.sourceFrame(37)==37);
 const auto first=timePitchMapSegment(1,1,0,64,0),second=timePitchMapSegment(2,2,100,228,64);
 TimePitchTimeMap map({first,second});CHECK(map.sourceFrame(63)==63);CHECK(map.sourceFrame(64)==100);CHECK(map.sourceFrame(128)==228);CHECK(map.sourceFrame(129)==230);
 const auto view=map.view();CHECK(view.sourceFrame(view.context,80)==132);
 std::cout<<"96 original map constructors / 480 evaluations match bit-for-bit; node boundaries passed\n";
}
