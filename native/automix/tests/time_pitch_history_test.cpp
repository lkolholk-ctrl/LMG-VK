#include "lmg/automix/time_pitch_state.h"
#include "lmg/automix/time_pitch_stream.h"
#include <fstream>
#include <cmath>
#include <iostream>
#include <cstring>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
#define CHECK(x) do{if(!(x))throw std::runtime_error(#x);}while(false)
template<class T>T read(std::ifstream& in){T n;in.read(reinterpret_cast<char*>(&n),sizeof(n));CHECK(in.good());return n;}
int main(){try{
 std::ifstream in(LMG_AUTOMIX_FIXTURES "/time_pitch_history.bin",std::ios::binary);
 CHECK(read<unsigned>(in)==256);
 for(unsigned i=0;i<256;++i){
  const auto n=read<unsigned>(in);const auto hop=read<double>(in);TimePitchStreamState s;
  s.historyIndex=read<unsigned>(in);s.inputAnchorTime=read<double>(in);s.inputAnchorFrame=read<std::int64_t>(in);
  s.outputAnchorTime=read<double>(in);s.outputAnchorFrame=read<std::int64_t>(in);const auto time=read<double>(in);
  for(auto& h:s.history){h.active=read<std::uint8_t>(in);h.inputFrame=read<std::int64_t>(in);h.outputFrame=read<std::int64_t>(in);}
  std::vector<float> window(n);for(auto& w:window)w=read<float>(in);
  const auto expected=read<double>(in),actual=timePitchHistorySourceTime(s,n,hop,window.data(),time);
  if(std::memcmp(&actual,&expected,8)){std::cerr<<"case "<<i<<'\n';throw std::runtime_error("source time mismatch");}
 }
 CHECK(in.peek()==std::char_traits<char>::eof());
 TimePitchStream stream(48000,1,256,true,{1,1,8,true,true});
 double offset=17.5;
 stream.setTimeMap({&offset,[](const void* p,double t) noexcept{return t*1.25+*static_cast<const double*>(p);}});
 CHECK(stream.sourceTimeForOutput(100)==142.5);stream.clearTimeMap();
 float input[256]{},output[256]{};const float* inputs[]{input};float* outputs[]{output};
 double delivered=0;
 for(int i=0;i<64;++i){stream.enqueue(inputs,256,i*256.0);const auto n=stream.dequeue(outputs,256,delivered);if(n){CHECK(std::isfinite(stream.sourceTimeForOutput(delivered))); CHECK(stream.sourceTimeForOutput(delivered)==delivered);delivered+=n;}}
 CHECK(delivered>0);
 std::cout<<"256 original weighted history queries match bit-for-bit; stream map/history paths passed\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
