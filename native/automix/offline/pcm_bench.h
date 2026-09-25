#pragma once
#include "pcm_support.h"
#include "lmg/automix/planner_transition_schedule.h"
#include "lmg/automix/processed_track_stream.h"
#include <optional>

namespace lmg::automix::offline {
struct Policy {
  unsigned maximumFrames = 1024;
  std::vector<unsigned> inputPattern{1024}, outputPattern{1024};
  unsigned effectStepFrames = 256; // explicit SOURCE-frame sampling cadence
  bool fillAhead = false;
  // No renderer EOF primitive exists: the bench can append bounded ZERO INPUT,
  // not silently declare a drain. This is separate from output capture length.
  std::size_t maximumZeroInputFrames = 192000;
  std::size_t quietWindowFrames = 2048;
  double residualThreshold = 1e-6;
  void validate(Format format, std::size_t captureFrames) const;
};
struct TrackProgram {
  double cueSeconds;
  double transitionStartSeconds;
  PlaybackTimeMap timeMap;
  std::vector<ContinuousAutomation> automations;
  EffectSettings initial;
  bool applyOutputGain = true;
};
struct TrackReport {
  std::size_t sourceFrames = 0, acceptedSourceFrames = 0, acceptedZeroFrames = 0;
  std::size_t deliveredFrames = 0, firstDeliveryAfterInputFrames = 0;
  std::size_t enqueueCalls = 0, dequeueCalls = 0, backpressureCalls = 0, partialReads = 0;
  std::size_t pendingFramesAtCaptureEnd = 0, graphEvents = 0, fftSize = 0;
  std::size_t firstTailOutputFrame = 0, tailFramesObserved = 0;
  bool outputGainApplied = false, sourceFullyAccepted = false;
  bool finalWindowAboveThreshold = false;
  double declaredScheduledLatencySeconds = 0;
  Metrics raw, weighted, rawTail, rawFinalWindow;
};
struct TrackCapture { std::vector<float> raw, weighted; TrackReport report; };
// Cued finite PCM excerpts only. There is no Player, decoder, device or thread
// handoff here. Null source after EOF means ordinary zeros, NOT AU silence flags.
TrackCapture renderTrack(const TrackProgram&, const std::vector<float>& cuedPcm,
    Format, std::size_t captureFrames, const Policy&);
struct TransitionCapture {
  TrackCapture outgoing, incoming;
  std::vector<float> mix;
  std::size_t transitionFrames = 0, incomingStartFrame = 0;
  Metrics mixMetrics;
  static constexpr bool canExecute = false;
};
// One output timeline; two PCM lanes, not two players. Each finite excerpt starts
// at the plan's cue. Capturing a test tail NEVER extends either playback range.
TransitionCapture renderTransition(const PlannerTransitionSchedule&,
    const std::vector<float>& outgoingCuedPcm, const std::vector<float>& incomingCuedPcm,
    Format, std::size_t diagnosticTailFrames, const Policy&);
std::string trackReportJson(const TrackReport&);
std::string transitionReportJson(const TransitionCapture&, Format, const Policy&);
} // namespace lmg::automix::offline
