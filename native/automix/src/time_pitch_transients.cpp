#include "lmg/automix/time_pitch_transients.h"
#include <algorithm>
#include <cmath>
namespace lmg::automix {
void timePitchApplyCoherence(const float* magnitude,float* correction,
                             std::uint32_t* mapping,std::uint32_t bins,
                             float pitch,TimePitchRegions& regions) noexcept {
  std::uint32_t maximum=0;
  for(std::uint32_t i=1;i<bins;++i)if(magnitude[maximum]<magnitude[i])maximum=i;
  regions.maximumBin=maximum;regions.count=0;
  const float threshold=static_cast<float>(static_cast<double>(magnitude[maximum])*0.001);
  float minimum=10000000000.0f;
  std::uint32_t boundary=0;
  for(std::uint32_t i=2;i<bins-2;) {
    const float value=magnitude[i];
    if(value<minimum) {minimum=value;boundary=i;++i;continue;}
    if(value>threshold && value>=magnitude[i-1] && value>=magnitude[i+1] &&
       value>magnitude[i-2] && value>magnitude[i+2]) {
      const auto n=regions.count;
      regions.peaks[n]=i;regions.starts[n]=boundary;
      if(n)regions.ends[n-1]=boundary-1;
      boundary=i+2;minimum=magnitude[i+2];++regions.count;i+=3;
    } else ++i;
  }
  if(regions.count==0) {regions.count=1;regions.peaks[0]=1;}
  regions.starts[0]=0;regions.ends[regions.count-1]=bins-1;
  for(std::uint32_t r=0;r<regions.count;++r) {
    const auto peak=regions.peaks[r];
    const auto target=static_cast<std::int32_t>(std::fma(pitch,static_cast<float>(peak),0.5f));
    const auto shift=static_cast<std::uint32_t>(target)-peak;
    const float phase=correction[peak];
    for(auto i=regions.starts[r];i<=regions.ends[r];++i) {
      const auto destination=i+shift;
      mapping[i]=destination<bins?destination:0;
      correction[i]=phase;
    }
  }
}
void timePitchPreserveTransients(const float* magnitude,const float* analysis,float* correction,
                                float* differences,std::uint32_t bins,const TimePitchRegions& regions,
                                double rate,double outputHop,TimePitchTransientState& state) noexcept {
  const auto fftSize=bins*2;
  const float inverse=1.0f/static_cast<float>(fftSize);
  const float fraction=rate<0.2?0.75f:0.36f;
  std::int32_t positive=0;
  bool active=state.active;
  float position=state.position;
  const auto required=static_cast<std::int32_t>(std::ceil(static_cast<float>(regions.count)*fraction));
  if(!(active && position<=0.65f)) {
    if(bins) {
      differences[0]=analysis[0];
      for(std::uint32_t i=1;i<bins;++i)differences[i]=analysis[i]-analysis[i-1];
    }
    const auto limit=static_cast<std::int32_t>(static_cast<float>(fftSize)*-0.3f);
    const float negativeLimit=static_cast<float>(limit),positiveLimit=static_cast<float>(-limit);
    std::int32_t negative=0;float sum=0;
    for(std::uint32_t r=0;r<regions.count;++r) {
      float energy=0,weighted=0;
      for(auto i=regions.starts[r];i<=regions.ends[r];++i) {
        const float phase=differences[i]-std::floor(differences[i]+0.5f);
        weighted=std::fma(phase,magnitude[i],weighted);
        energy=energy+magnitude[i];
      }
      const float numerator=static_cast<float>(static_cast<double>(fftSize)*static_cast<double>(-weighted));
      if(energy*negativeLimit>numerator) {++negative;sum=sum+(numerator/energy);}
      else if(energy*positiveLimit<numerator)++positive;
    }
    if(negative>required) {
      position=-(sum*inverse)/static_cast<float>(negative);
      state.position=position;state.active=true;active=true;
    } else {state.active=false;active=false;}
  }
  const auto flatten=[&]() {
    const float phase=correction[regions.maximumBin];
    for(std::uint32_t i=0;i<bins;++i)correction[i]=phase;
  };
  if(active && rate<1.0 && position<0.65f) {
    position=static_cast<float>(std::fma(state.effectiveInputHop,static_cast<double>(inverse),static_cast<double>(position)));
    state.position=position;
    if(position>0.4f && position<0.65f && static_cast<std::uint32_t>(state.frameDebt)<fftSize) {
      state.effectiveInputHop=outputHop;
      state.frameDebt=static_cast<std::int32_t>((outputHop+static_cast<double>(state.frameDebt))-state.previousInputHop);
      return;
    }
    state.effectiveInputHop=state.previousInputHop;
    if(position>0.3f && position<=0.4f && bins)flatten();
    return;
  }
  const auto negativeDebt=std::uint32_t{0}-static_cast<std::uint32_t>(state.frameDebt);
  if(rate>1.0 && fftSize>negativeDebt) {
    const bool continuing=active && static_cast<double>(state.position)<0.8 && static_cast<double>(state.position)>0.2;
    if(continuing || positive>required) {
      if(positive<required)
        state.position=static_cast<float>(std::fma(state.effectiveInputHop,static_cast<double>(inverse),static_cast<double>(state.position)));
      state.effectiveInputHop=outputHop;
      state.frameDebt=static_cast<std::int32_t>((outputHop+static_cast<double>(state.frameDebt))-state.previousInputHop);
      if(bins)flatten();
      return;
    }
  }
  if(active)state.active=false;
  const auto quarter=static_cast<std::int32_t>(state.previousInputHop*0.25);
  const auto absoluteDebt=state.frameDebt<0?-state.frameDebt:state.frameDebt;
  const auto amount=std::min(absoluteDebt,quarter);
  const auto adjustment=state.frameDebt<0?amount:state.frameDebt>0?-amount:0;
  state.effectiveInputHop=state.previousInputHop+static_cast<double>(adjustment);
  state.frameDebt+=adjustment;
}
}
