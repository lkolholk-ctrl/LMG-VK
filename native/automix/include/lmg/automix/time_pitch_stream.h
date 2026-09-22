#pragma once
#include "lmg/automix/time_pitch_setup.h"
#include "lmg/automix/time_pitch_state.h"
#include "lmg/automix/time_pitch_phase.h"
#include "lmg/automix/time_pitch_spectral.h"
namespace lmg::automix {
struct TimePitchControls {
  double rate;
  float pitch, smoothness;
  bool coherence, preserveTransients;
};
// Prepared host mapping evaluated by the original mapped-hop branch. Units
// are PCM frames on both axes. Context outlives attachment and the callback
// must be allocation-free/noexcept and return finite, advancing source times.
struct TimePitchTimeMapView {
  const void* context;
  double (*sourceFrame)(const void*,double outputFrame) noexcept;
};
// Bounded planar PCM owner for recovered TimePitch kernels. Each public call
// processes at most maximumFrames input/output samples; enqueue can accept less
// under backpressure. Stereo uses the source orthonormal mid/side transform.
// No implicit EOF padding/trimming: the host must supply the source tail policy.
class TimePitchStream {
 public:
  TimePitchStream(double sampleRate,std::uint32_t channels,
                  std::uint32_t maximumFrames,bool scheduledOrOffline,
                  const TimePitchControls& controls);
  void reset(double inputTime=0,double outputTime=0) noexcept;
  // Control-thread only, serialized with rendering. Values validate before any
  // state changes. Policy values are explicit host inputs, not guessed defaults.
  void configure(const TimePitchControls& controls);
  void setTimeMap(TimePitchTimeMapView map);
  void clearTimeMap() noexcept;
  std::uint32_t enqueue(const float* const* input,std::uint32_t frames,double sourceTime);
  std::uint32_t dequeue(float* const* output,std::uint32_t frames,double outputTime);
  double sourceTimeForOutput(double outputFrame) const;
  const TimePitchStreamState& state() const noexcept {return state_;}
  const TimePitchGeometry& geometry() const noexcept {return geometry_;}
 private:
  void pump() noexcept;
  TimePitchGeometry geometry_;
  std::uint32_t channels_,maximumFrames_;
  bool scheduled_;
  bool coherence_=false,preserveTransients_=false;
  double rate_=1,lastOutputHop_=0;
  float pitch_=1,smoothness_=8;
  TimePitchStreamState state_;
  TimePitchHopState clock_;
  TimePitchTransientState transient_;
  TimePitchTimeMapView timeMap_{};
  TimePitchSpectral spectral_;
  std::array<std::vector<float>,2> input_,output_,frames_;
};
}
