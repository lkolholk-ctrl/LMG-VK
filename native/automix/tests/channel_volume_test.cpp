#include "lmg/automix/channel_volume.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do{if(!(x))throw std::runtime_error(#x);}while(false)
template<class T>T read(std::istream& input){T x;input.read(reinterpret_cast<char*>(&x),sizeof(x));CHECK(input.good());return x;}
template<class T>T at(const std::array<char,40>& bytes,unsigned offset){T x;std::memcpy(&x,bytes.data()+offset,sizeof(x));return x;}
ChannelVolumeSlot slot(std::istream& input,float* seconds=nullptr){
    std::array<char,40> b;input.read(b.data(),b.size());CHECK(input.good());
    if(seconds)*seconds=at<float>(b,8);
    return {bool(b[0]),bool(b[1]),bool(b[2]),at<float>(b,4),at<std::int64_t>(b,16),
       at<std::uint32_t>(b,24),at<float>(b,28),at<float>(b,32),at<std::uint32_t>(b,36)};
}
std::array<ChannelVolumeSlot,11> slots(std::istream& input){
  std::array<ChannelVolumeSlot,11> result;
  for(auto& s:result)s=slot(input);
  return result;
}
bool bits(float a,float b){return std::memcmp(&a,&b,sizeof(float))==0;}
void same(const ChannelVolumeSlot& a,const ChannelVolumeSlot& b){
  CHECK(a.active==b.active&&a.started==b.started&&a.capturePending==b.capturePending);
  CHECK(bits(a.current,b.current)&&bits(a.startVolume,b.startVolume)&&bits(a.endVolume,b.endVolume));
  CHECK(a.startFrame==b.startFrame&&a.duration==b.duration&&a.curve==b.curve);
}
int main(){try{
  std::ifstream input(LMG_AUTOMIX_FIXTURES "/channel_volume.bin",std::ios::binary);
  CHECK(input.good());const auto count=read<std::uint32_t>(input);CHECK(count==576);
  for(unsigned i=0;i<count;++i){
    const auto base=read<float>(input);const auto frame=read<std::int64_t>(input);
    const bool exclude=read<std::uint32_t>(input)!=0;auto state=slots(input);
    const auto all=read<float>(input),selected=read<float>(input);
    const bool active=read<std::uint32_t>(input)!=0;const auto expected=slots(input);
    const auto actual=combineChannelVolumes(base,state,frame,exclude);
    if(!bits(actual.all,all)||!bits(actual.selected,selected)) {
      std::cerr<<"case "<<i<<" all "<<std::hexfloat<<actual.all<<" != "<<all<<'\n';
      throw std::runtime_error("original volume mismatch");
    }
    CHECK(actual.rampActive==active);
    for(unsigned j=0;j<11;++j)same(state[j],expected[j]);
  }
  CHECK(input.peek()==std::char_traits<char>::eof());
  std::ifstream targets(LMG_AUTOMIX_FIXTURES "/channel_volume_targets.bin",std::ios::binary);
  CHECK(targets.good());CHECK(read<std::uint32_t>(targets)==256);
  for(unsigned i=0;i<256;++i){
    const auto fs=read<double>(targets);const auto value=read<float>(targets);
    const auto frame=read<std::int64_t>(targets);const auto offset=read<std::int32_t>(targets);
    float seconds;auto state=slot(targets,&seconds);const auto expected=slot(targets);
    scheduleChannelVolumeTarget(state,fs,seconds,value,frame,offset);same(state,expected);
  }
  CHECK(targets.peek()==std::char_traits<char>::eof());
  std::cout<<"576 original volume evaluations and 256 target-event updates matched bit-for-bit\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
