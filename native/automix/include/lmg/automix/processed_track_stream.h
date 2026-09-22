#pragma once
#include "lmg/automix/scheduled_graph.h"
#include "lmg/automix/time_pitch_stream.h"

namespace lmg::automix {
// LMG PCM transport: recovered DSPGraph precedes TimePitch (AVF placement=1).
// Events and firstFrame are already resolved source PCM frames. This class
// neither resolves CMTime nor applies out_gain nor invents an EOF tail policy.
// Construct off the audio thread; one serialized rendering owner. Reconstruct
// for a seek. A supplied map's context must outlive this instance.
class ProcessedTrackStream {
 public:
  ProcessedTrackStream(double sampleRate, unsigned channels, unsigned maximumFrames,
      Frame firstFrame, double firstOutputFrame, EffectSettings initial,
      std::vector<GraphEvent> events, bool scheduled,
      const TimePitchControls& controls, TimePitchTimeMapView map = {});
  // Interleaved input. Accepted frames are owned by this object even when
  // TimePitch has not consumed them yet; retry only unaccepted input. Null input
  // explicitly supplies zero PCM. Zero frames never advances either stage.
  unsigned enqueue(const float* input, unsigned frames, GraphSilence silence = {});
  unsigned dequeue(float* output, unsigned frames, double outputFrame);
  Frame inputPosition() const noexcept { return graph_.position(); }
  unsigned pendingFrames() const noexcept { return pending_ - offset_; }
 private:
  void flushPending();
  unsigned channels_, maximumFrames_, pending_ = 0, offset_ = 0;
  Frame pendingStart_ = 0;
  ScheduledTrackGraph graph_;
  TimePitchStream pitch_;
  std::vector<float> interleaved_;
  std::array<std::vector<float>, 2> input_, output_;
};
}
