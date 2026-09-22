#include "lmg/automix/time_pitch_phase.h"
#include <cmath>
#include <cstdint>
#include <cstring>
namespace lmg::automix {
namespace {
float fromBits(std::uint32_t bits) noexcept { float value;std::memcpy(&value,&bits,sizeof value);return value; }
float approximatePhaseCycles(float real, float imaginary) noexcept {
  const float ar=std::fabs(real), ai=std::fabs(imaginary);
  const bool realLarger=ar>ai;
  const float maximum=realLarger?ar:ai, minimum=realLarger?ai:ar;
  if (maximum==0 && minimum==0) return 0;
  const float ratio=minimum/maximum;
  const float squared=ratio*ratio;
  const float linear=ratio*fromBits(0x3e2c8649);
  const float polynomial=std::fma(fromBits(0xbd321922),squared,linear);
  const float quadrant=realLarger?polynomial:0.25f-polynomial;
  const float angle=(real>0?0.0f:-0.5f)+quadrant;
  const float product=real*imaginary;
  return product>0?angle:-angle;
}
}
void timePitchAnalyzePhase(const float* real, const float* imaginary,
                           float* previousAnalysisCycles,const float* synthesisCycles,
                           float* correctionCycles,std::size_t bins,
                           float inverseFftSize,double inputHop,double outputHop,
                           float pitchRatio) noexcept {
  const float inputStep=inverseFftSize*static_cast<float>(inputHop);
  const float inverseInputStep=1.0f/inputStep;
  const float pitchedOutputHop=pitchRatio*static_cast<float>(outputHop);
  const float outputStep=inverseFftSize*pitchedOutputHop;
  for (std::size_t i=0;i<bins;++i) {
    const float phase=approximatePhaseCycles(real[i],imaginary[i]);
    const float index=static_cast<float>(i);
    const float expected=index*inputStep;
    const float difference=phase-previousAnalysisCycles[i];
    const float residual=difference-expected;
    const float wrapped=residual-std::floor(residual+0.5f);
    const float deviation=wrapped*inverseInputStep;
    const float base=index*outputStep;
    const float increment=std::fma(deviation,outputStep,base);
    const float propagated=synthesisCycles[i]+increment;
    correctionCycles[i]=propagated-phase;
    previousAnalysisCycles[i]=phase;
  }
}
TimePitchHop timePitchAdvanceHop(TimePitchHopState& state,unsigned fftSize,
                                float smoothness,double rate) noexcept {
  const double output=std::floor(static_cast<double>(fftSize)/static_cast<double>(smoothness)+0.5);
  const double roundedInput=std::floor(std::fma(output,rate,0.5));
  const double difference=roundedInput-state.previousRoundedInputHop;
  state.effectiveInputHop=state.effectiveInputHop+difference;
  state.previousRoundedInputHop=roundedInput;
  state.inputTime=state.inputTime+roundedInput;
  state.outputTime=state.outputTime+output;
  return {state.effectiveInputHop,output,state.inputTime,state.outputTime};
}
TimePitchHop timePitchAdvanceMappedHop(TimePitchHopState& state,unsigned fftSize,
                                      float smoothness,double mappedNextInputTime) noexcept {
  const double output=std::floor(static_cast<double>(fftSize)/static_cast<double>(smoothness)+0.5);
  state.outputTime=state.outputTime+output;
  state.effectiveInputHop=mappedNextInputTime-state.inputTime;
  state.previousRoundedInputHop=state.effectiveInputHop;
  state.inputTime=mappedNextInputTime;
  return {state.effectiveInputHop,output,state.inputTime,state.outputTime};
}
}
