#include "lmg/automix/channel_volume.h"
#include <cmath>
#include <cstring>
#include <limits>
namespace lmg::automix {
namespace {
std::int64_t signedBits(std::uint64_t bits) noexcept {
  std::int64_t result;std::memcpy(&result,&bits,sizeof(result));return result;
}
float shaped(float progress,bool rising) noexcept {
  double angle=static_cast<double>(progress);
  if(!rising)angle=angle+-1.0;
  angle=angle*.5;angle=angle*0x1.921fb54442d18p+1;
  const float x=static_cast<float>(angle);
  const float a=(x*-0x1.9f02f6p-2f)*std::fabs(x);
  const float b=std::fma(x,0x1.45f306p+0f,a);
  const float c=std::fma(b,std::fabs(b),-b);
  const float d=std::fma(c,0x1.ccccccp-3f,b);
  return rising?d:d+1.0f;
}
}
void scheduleChannelVolumeTarget(ChannelVolumeSlot& s,double sampleRate,
    float durationSeconds,float target,std::int64_t blockFrame,std::int32_t eventOffset) noexcept {
  if(durationSeconds==0){s.current=target;s.active=false;return;}
  if(s.active){s.current=s.endVolume;s.capturePending=true;}
  const double frames=sampleRate*static_cast<double>(durationSeconds);
  // ARM FCVTZU saturates instead of invoking C++ out-of-range conversion.
  s.duration=!(frames>0)?0:frames>=std::numeric_limits<std::uint32_t>::max()
      ?std::numeric_limits<std::uint32_t>::max():static_cast<std::uint32_t>(frames);
  s.active=true;s.started=false;s.endVolume=target;
  s.startFrame=signedBits(static_cast<std::uint64_t>(blockFrame)+
                         static_cast<std::uint64_t>(static_cast<std::int64_t>(eventOffset)));
}
ChannelVolumes combineChannelVolumes(float base,std::array<ChannelVolumeSlot,11>& slots,
    std::int64_t frame,bool selectedEnabled) noexcept {
  ChannelVolumes result{base,base,false};
  for(unsigned index=0;index<slots.size();++index){
    auto& s=slots[index];
    if(s.active){
      const auto end=signedBits(static_cast<std::uint64_t>(s.startFrame)+s.duration);
      if(!s.started){
        s.started=true;s.capturePending=false;s.startVolume=s.current;
        if(end>frame)result.rampActive=true;
        else {s.active=false;s.current=s.endVolume;}
      }else{
        const auto elapsed=signedBits(static_cast<std::uint64_t>(frame)-static_cast<std::uint64_t>(s.startFrame));
        const float progress=static_cast<float>(elapsed)/static_cast<float>(s.duration);
        float weight;
        if(progress<0)weight=0;
        else if(progress>1)weight=1;
        else weight=s.curve==1?shaped(progress,s.endVolume>s.startVolume):progress;
        if(end<=frame){s.active=false;s.current=s.endVolume;}
        else{
          const float delta=s.endVolume-s.startVolume;
          s.current=std::fma(delta,weight,s.startVolume);result.rampActive=true;
        }
      }
    }
    result.all=result.all*s.current;
    if(index!=10&&selectedEnabled)result.selected=result.selected*s.current;
  }
  return result;
}
}
