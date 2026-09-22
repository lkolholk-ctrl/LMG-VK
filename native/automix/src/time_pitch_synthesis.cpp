#include "lmg/automix/time_pitch_synthesis.h"
#include <algorithm>
#include <cmath>
#include <cstddef>
namespace lmg::automix {
namespace {
float sineApproximation(float phase) noexcept {
  const float product=(phase*-16.0f)*std::fabs(phase);
  const float parabola=std::fma(8.0f,phase,product);
  const float correction=std::fma(std::fabs(parabola),parabola,-parabola);
  return std::fma(0.225f,correction,parabola);
}
void addSegment(float* ring,const float* frame,const float* window,std::uint32_t count) noexcept {
  std::uint32_t i=0;
  if(count>=8) {
    while((reinterpret_cast<std::uintptr_t>(ring+i)&15)!=0) {
      ring[i]=std::fma(window[i],frame[i],ring[i]);++i;
    }
    const auto end=i+((count-i)/32)*32;
    for(;i<end;++i) {const float product=window[i]*frame[i];ring[i]=ring[i]+product;}
  }
  for(;i<count;++i)ring[i]=std::fma(window[i],frame[i],ring[i]);
}
}
TimePitchPackedEdges timePitchSynthesize(float* real,float* imaginary,
    const float* analysis,const float* correction,float* synthesis,
    float* rotationReal,float* rotationImaginary,std::uint32_t bins,
    double outputHop,float inverseFftSize,bool updateRotation) noexcept {
  double normalization=outputHop*1.3333;
  normalization=normalization*static_cast<double>(inverseFftSize);
  normalization=normalization*static_cast<double>(inverseFftSize);
  const float scale=static_cast<float>(normalization);
  const TimePitchPackedEdges edges{real[0]*scale,imaginary[0]*scale};
  if(updateRotation) {
    for(std::uint32_t i=0;i<bins;++i) {
      const float propagated=analysis[i]+correction[i];
      synthesis[i]=propagated-std::floor(propagated+0.5f);
      const float phase=correction[i]-std::floor(correction[i]+0.5f);
      const float shifted=phase+0.25f;
      const float cosinePhase=shifted-std::floor(shifted+0.5f);
      rotationReal[i]=sineApproximation(cosinePhase)*scale;
      rotationImaginary[i]=sineApproximation(phase)*scale;
    }
  }
  for(std::uint32_t i=0;i<bins;++i) {
    const float r=real[i],m=imaginary[i],cr=rotationReal[i],ci=rotationImaginary[i];
    const float firstReal=r*cr,firstImaginary=r*ci;
    real[i]=std::fma(-m,ci,firstReal);
    imaginary[i]=std::fma(m,cr,firstImaginary);
  }
  real[0]=edges.dc;imaginary[0]=edges.nyquist;
  return edges;
}
std::uint32_t timePitchPrepareBinMapping(std::uint32_t* mapping,std::uint32_t bins,float pitch) noexcept {
  const auto active=pitch>1?static_cast<std::uint32_t>(std::floor(static_cast<float>(bins)/pitch)):bins;
  for(std::uint32_t i=0;i<bins;++i) {
    const auto index=static_cast<std::int32_t>(std::fma(pitch,static_cast<float>(i),0.5f));
    mapping[i]=index<static_cast<std::int32_t>(bins)?static_cast<std::uint32_t>(index):0;
  }
  return active;
}
void timePitchRemapSpectrum(const float* real,const float* imaginary,float* outReal,float* outImaginary,
    const std::uint32_t* mapping,std::uint32_t bins,TimePitchPackedEdges edges) noexcept {
  std::fill_n(outReal,bins,0.0f);std::fill_n(outImaginary,bins,0.0f);
  for(std::uint32_t i=0;i<bins;i+=4) {
    float r[4],m[4];
    for(unsigned lane=0;lane<4;++lane) {
      const auto dst=mapping[i+lane];
      r[lane]=outReal[dst]+real[i+lane];m[lane]=outImaginary[dst]+imaginary[i+lane];
    }
    // Original SIMD gathers all four old values before any scatter store.
    for(unsigned lane=0;lane<4;++lane) {const auto dst=mapping[i+lane];outReal[dst]=r[lane];outImaginary[dst]=m[lane];}
  }
  outReal[0]=edges.dc;outImaginary[0]=edges.nyquist;
}
void timePitchOverlapAdd(float* ring,std::uint32_t ringSize,std::uint64_t cursor,
    const float* frame,const float* window,std::uint32_t fftSize) noexcept {
  const auto offset=static_cast<std::uint32_t>(cursor&(ringSize-1));
  const auto first=std::min(fftSize,ringSize-offset);
  addSegment(ring+offset,frame,window,first);
  if(first<fftSize)addSegment(ring,frame+first,window+first,fftSize-first);
}
void timePitchIdentityWindow(const float* input,float* previousFrame,
    const float* window,std::uint32_t fftSize,float smoothness,
    float* outputRing,std::uint32_t ringSize,std::uint64_t cursor) noexcept {
  const float denominator=smoothness*3.0f;
  const float gain=8.0f/denominator;
  for(std::uint32_t i=0;i<fftSize;++i) {
    const float windowed=input[i]*window[i];
    previousFrame[i]=windowed*gain;
  }
  timePitchOverlapAdd(outputRing,ringSize,cursor,previousFrame,window,fftSize);
}
}
