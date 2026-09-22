#include "lmg/automix/time_pitch_state.h"
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
namespace {
template<class T>T read(std::ifstream& f){T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);if(!f)throw std::runtime_error("truncated fixture");return v;}
template<class T>void check(std::ifstream& f,T v){const auto expected=read<T>(f);if(std::memcmp(&expected,&v,sizeof v))throw std::runtime_error("state mismatch");}
void bit(std::ifstream& f,bool v){check(f,static_cast<std::uint8_t>(v));}
void snapshot(std::ifstream& f,const TimePitchStreamState& s){
 check(f,s.totalInput);check(f,s.totalOutput);bit(f,s.inputAnchorValid);check(f,s.inputAnchorTime);check(f,s.inputAnchorFrame);check(f,s.inputAnchorOther);
 bit(f,s.outputAnchorValid);check(f,s.outputAnchorTime);check(f,s.outputAnchorFrame);check(f,s.outputAnchorOther);check(f,s.historyIndex);
 check(f,s.nextInputTime);check(f,s.nextOutputTime);check(f,s.inputTime);check(f,s.outputTime);bit(f,s.inputTimePending);
 check(f,s.sourcePullTime);check(f,s.processingInputTime);check(f,s.outputPullTime);
 check(f,s.inputWrite);check(f,s.inputRead);check(f,s.inputPadding);check(f,s.outputWrite);check(f,s.outputRead);check(f,s.outputClear);check(f,s.outputPadding);check(f,s.deliveredFrames);
 bit(f,s.spectralActive);bit(f,s.phaseInitialized);bit(f,s.mappingReset);
 for(const auto& h:s.history){bit(f,h.active);check(f,h.sourceTime);check(f,h.inputFrame);check(f,h.outputFrame);}
}
}
int main(){try{
 std::ifstream f(LMG_AUTOMIX_FIXTURES "/time_pitch_state.bin",std::ios::binary);
 const auto count=read<std::uint32_t>(f),steps=read<std::uint32_t>(f);
 for(unsigned c=0;c<count;++c){
  const auto n=read<std::uint32_t>(f);const auto scheduled=read<std::uint8_t>(f);
  const auto input=read<double>(f),output=read<double>(f);
  TimePitchStreamState s;s.processingInputTime=71.125;
  timePitchResetStream(s,n,scheduled,input,output);snapshot(f,s);
  for(unsigned i=0;i<steps;++i){
   const auto ih=read<double>(f),oh=read<double>(f);s.nextInputTime=read<double>(f);s.nextOutputTime=read<double>(f);
   s.sourcePullTime=read<double>(f);s.inputWrite=read<std::int64_t>(f);
   timePitchCommitHop(s,ih,oh);snapshot(f,s);
  }
 }
 for(unsigned size:{512u,2048u,8192u}){
  std::vector<float> ring(size),frame(size/2);for(unsigned i=0;i<size;++i)ring[i]=float(i);
  for(std::int64_t cursor:{-17ll,0ll,1ll,255ll,511ll,100000ll}){
   timePitchCopyInputFrame(ring.data(),size,cursor,frame.data(),size/2);
   for(unsigned i=0;i<size/2;++i)if(frame[i]!=ring[(std::uint64_t(cursor)+i)&(size-1)])throw std::runtime_error("copy wrap");
  }
 }
 std::cout<<count<<" reset + "<<count*steps<<" history/cursor original ARM cases passed\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
