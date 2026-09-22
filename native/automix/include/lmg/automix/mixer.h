#pragma once

#include "lmg/automix/automation.h"

#include <cstddef>

namespace lmg::automix {

// Gain boxes from DSPGraph.dspg, with effect AUs bypassed. This is not an
// implementation of filters, delay, reverb, or time stretching.
// out_gain is kept separate because Apple's graph does not contain it.
class TrackGainPlan {
 public:
  explicit TrackGainPlan(AutomationLane output,
                        AutomationLane input = AutomationLane(1.0),
                        AutomationLane send = AutomationLane(1.0),
                        AutomationLane dry = AutomationLane(1.0),
                        AutomationLane wet = AutomationLane(0.0));
  double gainAt(Frame frame) const noexcept;

 private:
  AutomationLane output_, input_, send_, dry_, wet_;
};

// Owns two independent immutable plans. The caller supplies already aligned,
// finite, interleaved float PCM with the same sample rate and channel layout.
// Plan construction/replacement/destruction belongs outside the audio callback.
class TwoDeckMixer {
 public:
  TwoDeckMixer(std::size_t channels, TrackGainPlan outgoing, TrackGainPlan incoming);

  // Null input means silence for that deck. Output may equal an input exactly;
  // partial overlapping buffers are not supported. No clipping or normalization
  // is applied: float PCM preserves headroom for a future output stage.
  // False indicates invalid buffer/time/size; output is then left untouched.
  // Absolute frame positions allow seeks and arbitrary callback block sizes.
  bool render(const float* outgoing, const float* incoming, float* output,
              std::size_t frames, Frame firstFrame) const noexcept;

 private:
  std::size_t channels_;
  TrackGainPlan outgoing_, incoming_;
};

}  // namespace lmg::automix
