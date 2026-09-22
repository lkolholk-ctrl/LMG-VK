#include "lmg/automix/time_pitch_magnitude.h"
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
namespace lmg::automix {
namespace {
#include "time_pitch_rsqrt_table.inc"
float fromBits(std::uint32_t bits) noexcept {float v;std::memcpy(&v,&bits,4);return v;}
float reciprocalSquareRootEstimate(float value) noexcept {
  if(value==0)return 0; // Source masks the estimate for zero magnitude.
  if(std::isinf(value))return 0;
  if(std::isnan(value))return value;
  std::uint32_t bits;std::memcpy(&bits,&value,4);
  int exponent=static_cast<int>((bits>>23)&255)-127;
  std::uint32_t mantissa=bits&0x7fffff;
  if(exponent==-127) {
    exponent=-126;
    while((mantissa&0x800000)==0){mantissa<<=1;--exponent;}
    mantissa&=0x7fffff;
  }
  const auto index=(exponent%2!=0?256u:0u)+(mantissa>>15);
  const int halfExponent=exponent>=0?exponent/2:(exponent-1)/2;
  return std::ldexp(fromBits(timePitchRsqrtEstimate[index]),-halfExponent);
}
float magnitude(float real,float imaginary,unsigned mode) noexcept {
  const float square=mode==1?imaginary*imaginary:real*real;
  const float power=mode==2 ? square+(imaginary*imaginary) :
                    mode==1 ? std::fma(real,real,square) : std::fma(imaginary,imaginary,square);
  float estimate=reciprocalSquareRootEstimate(power);
  float product=power*estimate;
  const float step=std::fma(-0.5f*estimate,product,1.5f);
  estimate=estimate*step;
  product=power*estimate;
  const float lastStep=std::fma(-0.5f*estimate,product,1.5f);
  return product*lastStep;
}
}
void timePitchMagnitudes(const float* real,const float* imaginary,float* output,std::size_t bins) noexcept {
  std::size_t i=0;
  if(bins>=4)while((reinterpret_cast<std::uintptr_t>(output+i)&15)!=0) {
    output[i]=magnitude(real[i],imaginary[i],false);++i;
  }
  const auto vectorEnd=i+((bins-i)/16)*16;
  for(;i<vectorEnd;++i)output[i]=magnitude(real[i],imaginary[i],0);
  const auto shortVectorEnd=i+((bins-i)/4)*4;
  const auto firstShortVectorEnd=i+4;
  for(;i<shortVectorEnd;++i)output[i]=magnitude(real[i],imaginary[i],i<firstShortVectorEnd?0:2);
  const auto firstTail=i;
  for(;i<bins;++i)output[i]=magnitude(real[i],imaginary[i],i!=firstTail);
}
}
