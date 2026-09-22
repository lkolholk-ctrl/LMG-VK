#pragma once
#include "lmg/automix/graph.h"
#include "lmg/automix/effect_events.h"
#include <string>
#include <vector>

namespace lmg::automix {

// Control-thread prepared write on an already resolved PCM timeline. Each entry
// is the complete resulting snapshot, not a patch against the initial settings.
// Coalesce simultaneous writes before construction; retain any decay-write flag.
struct GraphEvent {
  Frame frame;
  PreparedGraph configuration;
  bool reverbDecayWritten = false;
};

struct GraphParameterWrite {
  Frame frame;
  std::string parameterId;
  double value;
};

// Fresh graph initialization from the source's time-zero parameter event.
// Includes all 27 graph IDs, including off-callback reverb geometry. Returns
// validated settings for constructing both initial graph and later snapshots.
// This does not handle seeks, history reconstruction or live geometry changes.
EffectSettings initializeGraphSettings(double sampleRate, const TimedEffectEvent& initial);

// Control-thread bridge for 23 DSP scalar parameters plus binary bypa. Narrows
// values to AU float before preparing coefficients. Input must be time ordered;
// same-frame distinct parameters are combined, duplicates rejected. Does not
// silently discard out_gain/ts_rate or accept unimplemented host properties.
std::vector<GraphEvent> prepareGraphEvents(double sampleRate, EffectSettings initial,
                                         const std::vector<GraphParameterWrite>& writes);

// LMG host transport for explicit stepped events. Does not choose the sampling
// cadence, convert Apple song/playback time, or resolve normalized style times.
// Single audio-thread owner; rebuild off callback for a seek/new schedule.
class ScheduledTrackGraph {
 public:
  ScheduledTrackGraph(const PreparedGraph& initial, unsigned channels,
                      std::size_t maxFrames, Frame firstFrame,
                      std::vector<GraphEvent> events);
  // Half-open [position, position + frames). An event at the end waits until
  // the next nonempty call. Zero frames is a no-op, including gain/kernel state.
  // Rejects invalid buffers/overflow before consuming events or changing state.
  bool process(const float* input, float* output, std::size_t frames,
               GraphSilence silence = {}) noexcept;
  Frame position() const noexcept { return position_; }
  std::size_t eventsConsumed() const noexcept { return next_; }
 private:
  std::vector<GraphEvent> events_;
  TrackGraph graph_;
  unsigned channels_;
  std::size_t capacity_, next_ = 0;
  Frame position_;
};
}  // namespace lmg::automix
