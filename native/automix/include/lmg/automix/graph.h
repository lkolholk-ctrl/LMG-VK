#pragma once
#include "lmg/automix/effects.h"
#include "lmg/automix/filter.h"
#include "lmg/automix/delay.h"
#include "lmg/automix/reverb.h"
#include <memory>

namespace lmg::automix {

// Validated, coefficient-prepared snapshot. Construct away from audio callback.
class PreparedGraph {
 public:
  PreparedGraph(double sampleRate, EffectSettings settings = {});
  // Whether a running graph may accept this snapshot without reconstruction.
  bool compatibleWith(const PreparedGraph& other) const noexcept;
 private:
  friend class TrackGraph;
  double rate_;
  EffectSettings settings_;
  FilterCoefficients filter_;
  BiquadCoefficients hp1_, lp1_, hp2_, lp2_;
  DelayParameters delay_;
  ReverbParameters reverb_;
  ReverbGeometry geometry_;
};

// Explicit input flags for the four Gain boxes. These are host/AU buffer flags,
// not inferred from zero-valued PCM. False means ordinary unflagged audio.
struct GraphSilence {
  bool input = false, send = false, dry = false, wet = false;
};

// One graph per track: independent filter/delay state per channel and one shared
// stereo reverb tank within that track. Owns bounded planar scratch buffers.
// ts_rate and out_gain belong outside DSPGraph.dspg and are not applied here.
class TrackGraph {
 public:
  TrackGraph(const PreparedGraph& configuration, unsigned channels, std::size_t maxFrames);
  ~TrackGraph();
  TrackGraph(const TrackGraph&) = delete;
  TrackGraph& operator=(const TrackGraph&) = delete;

  // No allocation. Geometry/rate must match. Leaving bypass resets channel
  // filters and delay, while preserving the reverb tank (serialized AU writes).
  // The explicit flag preserves AU reset semantics for repeated decay writes.
  bool apply(const PreparedGraph& configuration, bool reverbDecayWritten = false) noexcept;

  // Finite interleaved mono/stereo PCM. Null input feeds zero PCM to drain tails;
  // it does not automatically set AU silence flags or freeze state. Exact input/
  // output alias is supported. Rejects oversized/null output before state changes.
  bool process(const float* input, float* output, std::size_t frames,
               GraphSilence silence = {}) noexcept;
 private:
  struct State;
  std::unique_ptr<State> state_;
};

}  // namespace lmg::automix
