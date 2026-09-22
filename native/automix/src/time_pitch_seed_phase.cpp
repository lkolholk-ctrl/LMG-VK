#include "lmg/automix/time_pitch_seed_phase.h"
#include <cmath>
#include <cstring>
#include <limits>
namespace lmg::automix {
namespace {
using U=std::uint32_t;
float value(U u) noexcept {float f;std::memcpy(&f,&u,4);return f;}
U bits(float f) noexcept {U u;std::memcpy(&u,&f,4);return u;}
U fmul(U a,U b) noexcept {return bits(value(a)*value(b));}
U fadd(U a,U b) noexcept {return bits(value(a)+value(b));}
U fmla(U d,U a,U b) noexcept {return bits(std::fma(value(a),value(b),value(d)));}
U fmls(U d,U a,U b) noexcept {return bits(std::fma(-value(a),value(b),value(d)));}
U fabs(U a) noexcept {return a&0x7fffffffu;}
U fneg(U a) noexcept {return a^0x80000000u;}
U fcmeq(U a,U b) noexcept {return value(a)==value(b)?~U{0}:0;}
U fcmlt(U a,U b) noexcept {return value(a)<value(b)?~U{0}:0;}
U fcmgt(U a,U b) noexcept {return value(a)>value(b)?~U{0}:0;}
U bitsAnd(U a,U b) noexcept {return a&b;}
U orr(U a,U b) noexcept {return a|b;}
U bsl(U mask,U yes,U no) noexcept {return (mask&yes)|(~mask&no);}
U bit(U d,U a,U mask) noexcept {return bsl(mask,a,d);}
U bif(U d,U a,U mask) noexcept {return bsl(mask,d,a);}
U frecps(U a,U b) noexcept {
  if(((a&0x7fffffffu)==0 && std::isinf(value(b))) ||
     ((b&0x7fffffffu)==0 && std::isinf(value(a)))) return bits(2.0f);
  return bits(std::fma(-value(a),value(b),2.0f));
}
U frecpe(U a) noexcept {
  static constexpr U table[]{
#include "time_pitch_reciprocal_table.inc"
  };
  const U sign=a&0x80000000u, magnitude=a&0x7fffffffu;
  if(magnitude==0) return sign|0x7f800000u;
  if(magnitude==0x7f800000u) return sign;
  if(magnitude>0x7f800000u) return a|0x00400000u;
  int exponent=0;
  const float fraction=std::frexp(value(magnitude),&exponent);
  const auto index=(bits(fraction)&0x7fffffu)>>15;
  return bits(std::ldexp(value(table[index]),-exponent))|sign;
}
U phase(U real,U imaginary) noexcept {
#include "time_pitch_seed_phase_ops.inc"
}
}
void timePitchSeedPhaseRadians(const float* real,const float* imaginary,float* radians,std::uint32_t bins) noexcept {
  for(std::uint32_t i=0;i<bins;++i) radians[i]=value(phase(bits(real[i]),bits(imaginary[i])));
}
}
