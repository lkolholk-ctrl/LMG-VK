#pragma once
#include "lmg/automix/continuous_schedule.h"
#include "lmg/automix/scheduling_policy.h"
#include <vector>

namespace lmg::automix {
struct SteppedPoint {
  double value;
  PlaybackTime time;
};
struct SteppedRamp {
  double startValue, endValue;
  PlaybackTime start, end;
};
struct TimeStretchingStep {
  PlaybackTime start, end;
  // Source 0x272282538: song duration / stretched duration, or zero when
  // the stretched duration is nonpositive. This is an interval-average rate.
  double playbackRate() const;
};
struct SteppedAutomation {
  EffectParameterDescriptor parameter;
  std::vector<SteppedPoint> points;
  // Derived independently from raw samples, never from compacted points.
  std::vector<SteppedRamp> ramps;
};
struct TransitionLandmarks {
  std::vector<double> outgoing, incoming;
};

// Source 0x272282c80: distinct sorted playback boundaries/reference time.
// All arguments are transition seconds, not song seconds or PCM frames.
TransitionLandmarks steppedTransitionLandmarks(double outgoingPlaybackStart,
    double outgoingPlaybackEnd, double incomingPlaybackStart,
    double incomingPlaybackEnd, double outgoingReferenceTransition);

// Source 0x272252970. Requires ordered distinct finite landmarks and positive
// step. A span shorter than step produces no points. All preparation is off
// callback. Resource/finite/progress guards are LMG host validation.
std::vector<double> steppedTransitionTimes(const std::vector<double>& landmarks,
                                         double stepSeconds);
// Source 0x272279d20: retain first point and later points whose value differs
// from the immediately preceding input point. Do not force a final endpoint.
std::vector<SteppedPoint> compactSteppedPoints(const std::vector<SteppedPoint>& points);
// Source 0x27227a05c / 0x27227b34c: preserve plateau boundaries preceding
// changes. An entirely constant sequence produces one ramp; a trailing
// plateau after a change does not produce an extra terminal ramp.
std::vector<SteppedRamp> steppedRamps(const std::vector<SteppedPoint>& rawPoints);
// Source 0x2722815e0: no steps without a selected rate ramp; otherwise map
// the shared grid and retain every adjacent interval, including unity ones.
std::vector<TimeStretchingStep> compileTimeStretchingSteps(
    const std::vector<double>& landmarks, SchedulingPolicy policy,
    const PlaybackTimeMap& timeMap);

// Source 0x2722842b0 -> 0x27227b000 -> 0x272279d20. Rate automation controls
// the time calculator and is excluded by full descriptor equality. Other
// automations retain resource order/duplicates, including out_gain and bypa.
// No frame quantization, graph parameter merging or implicit default cadence.
std::vector<SteppedAutomation> compileSteppedAutomations(
    const std::vector<ContinuousAutomation>& automations,
    const std::vector<double>& landmarks, SchedulingPolicy policy,
    const PlaybackTimeMap& timeMap);
}  // namespace lmg::automix
