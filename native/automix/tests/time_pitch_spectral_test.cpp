#include "lmg/automix/time_pitch_spectral.h"
#include <cmath>
#include <cstring>
#include <iostream>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do {if(!(x)) throw std::runtime_error(#x);}while(false)
int main() {
 try {
  constexpr unsigned n=512,ringSize=2048;
  std::vector<float> left(n),right(n),outLeft(ringSize),outRight(ringSize),first;
  const float* inputs[]{left.data(),right.data()};float* outputs[]{outLeft.data(),outRight.data()};
  TimePitchSpectral processor(n,2);
  TimePitchTransientState state;state.effectiveInputHop=state.previousInputHop=48;
  // Silence stays silent even with coherence/transient preservation enabled.
  processor.process(inputs,outputs,ringSize,0,.75,64,true,true,state);
  for(float value:outLeft) CHECK(value==0 && std::isfinite(value));
  processor.reset();state={};state.effectiveInputHop=state.previousInputHop=48;
  for(unsigned i=0;i<n;++i) {left[i]=static_cast<float>(std::sin(i*.13)*.2);right[i]=left[i];}
  processor.process(inputs,outputs,ringSize,ringSize-31,.75,64,true,true,state);
  CHECK(std::memcmp(outLeft.data(),outRight.data(),ringSize*sizeof(float))==0);
  bool nonzero=false;for(float value:outLeft) {CHECK(std::isfinite(value));nonzero|=value!=0;}CHECK(nonzero);
  first=outLeft;
  processor.reset();std::fill(outLeft.begin(),outLeft.end(),0);std::fill(outRight.begin(),outRight.end(),0);
  state={};state.effectiveInputHop=state.previousInputHop=48;
  processor.process(inputs,outputs,ringSize,ringSize-31,.75,64,true,true,state);
  CHECK(std::memcmp(first.data(),outLeft.data(),ringSize*sizeof(float))==0);
  // Multiple hops retain phase state, share channel rotation, and remain finite
  // across pitch changes; no ring clearing is hidden inside the spectral call.
  processor.setPitch(.75f);
  for(unsigned hop=1;hop<12;++hop) {
    state.previousInputHop=48;
    processor.process(inputs,outputs,ringSize,hop*64,.75,64,true,true,state);
  }
  CHECK(std::memcmp(outLeft.data(),outRight.data(),ringSize*sizeof(float))==0);
  for(float value:outLeft) CHECK(std::isfinite(value));
  processor.setPitch(1);
  processor.processIdentity(inputs,outputs,ringSize,1024,8);
  processor.seedFromPreviousIdentity();
  state.effectiveInputHop=state.previousInputHop=48;
  processor.process(inputs,outputs,ringSize,1088,.75,64,true,true,state);
  CHECK(std::memcmp(outLeft.data(),outRight.data(),ringSize*sizeof(float))==0);
  for(float value:outLeft) CHECK(std::isfinite(value));
  bool rejected=false;try {processor.setPitch(0);}catch(const std::invalid_argument&){rejected=true;}CHECK(rejected);
  std::cout<<"Spectral pipeline: silence, coherent stereo, wrapped overlap, reset and repeated hops passed\n";
 } catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}
}
