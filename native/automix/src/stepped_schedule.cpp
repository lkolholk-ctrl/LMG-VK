#include "lmg/automix/stepped_schedule.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
namespace {
// Explicit host resource ceiling, not an Apple schedule constant. A malformed
// server schedule must not grow an unbounded preparation buffer on this device.
constexpr std::size_t maximumPoints = 1000000;
void finite(double value) {
  if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite stepped schedule value");
}
std::vector<double> uniqueTimes(std::vector<double> times) {
  for (double time : times) finite(time);
  std::sort(times.begin(), times.end());
  times.erase(std::unique(times.begin(), times.end()), times.end());
  return times;
}
bool rateParameter(const EffectParameterDescriptor& p) {
  const auto& rate = effectParameter("ts_rate");
  return p.id == rate.id && p.minimum == rate.minimum && p.maximum == rate.maximum &&
      p.defaultValue == rate.defaultValue && p.styleParameterId == rate.styleParameterId;
}
}

TransitionLandmarks steppedTransitionLandmarks(double outStart, double outEnd,
    double inStart, double inEnd, double reference) {
  if (outEnd < outStart || inEnd < inStart)
    throw std::invalid_argument("Reversed playback transition range");
  return {uniqueTimes({outStart, inStart, reference, outEnd}),
          uniqueTimes({inStart, reference, outEnd, inEnd})};
}

std::vector<double> steppedTransitionTimes(const std::vector<double>& landmarks, double step) {
  finite(step);
  if (step <= 0) throw std::invalid_argument("Nonpositive schedule step");
  for (std::size_t i = 0; i < landmarks.size(); ++i) {
    finite(landmarks[i]);
    if (i && landmarks[i] <= landmarks[i-1])
      throw std::invalid_argument("Schedule landmarks must be distinct and ordered");
  }
  std::vector<double> result;
  if (landmarks.empty()) return result;
  double last = landmarks.front();
  const double end = landmarks.back();
  const double span = end - last;
  finite(span);
  if (span < step) return result;
  const double twice = step + step;
  finite(twice);
  auto append = [&](double value) {
    if (result.size() == maximumPoints) throw std::length_error("Stepped schedule too large");
    result.push_back(value);
  };
  append(last);
  for (double landmark : landmarks) {
    double gap = landmark - last;
    while (twice <= gap) {
      const double next = last + step; // repeated double add, never i*step/FMA
      if (!(next > last)) throw std::invalid_argument("Schedule step cannot advance time");
      last = next;
      append(last);
      gap = landmark - last;
    }
    const double remaining = end - landmark;
    if (remaining == 0 || (remaining >= step && gap >= step)) {
      last = landmark;
      append(last);
    }
  }
  return result;
}

std::vector<SteppedPoint> compactSteppedPoints(const std::vector<SteppedPoint>& points) {
  std::vector<SteppedPoint> result;
  for (std::size_t i = 0; i < points.size(); ++i) {
    const auto& p = points[i];
    finite(p.value); finite(p.time.song); finite(p.time.stretchedSong); finite(p.time.transition);
    if (!i || p.value != points[i-1].value) {
      if (result.size() == maximumPoints) throw std::length_error("Stepped schedule too large");
      result.push_back(p);
    }
  }
  return result;
}

std::vector<SteppedRamp> steppedRamps(const std::vector<SteppedPoint>& points) {
  if (points.size() > maximumPoints) throw std::length_error("Stepped schedule too large");
  for (const auto& p : points) {
    finite(p.value); finite(p.time.song); finite(p.time.stretchedSong); finite(p.time.transition);
  }
  if (points.empty()) return {};
  std::vector<SteppedRamp> result;
  auto last = points.front();
  auto append = [&](const SteppedPoint& next) {
    if (next.time.song < last.time.song || next.time.stretchedSong < last.time.stretchedSong ||
        next.time.transition < last.time.transition)
      throw std::invalid_argument("Reversed stepped ramp time range");
    result.push_back({last.value, next.value, last.time, next.time});
    last = next;
  };
  for (std::size_t i = 1; i < points.size(); ++i) {
    if (points[i].value == points[i-1].value) continue;
    if (last.time.transition < points[i-1].time.transition) append(points[i-1]);
    append(points[i]);
  }
  if (points.front().time.transition - last.time.transition == 0 &&
      points.front().time.transition < points.back().time.transition) append(points.back());
  return result;
}

double TimeStretchingStep::playbackRate() const {
  const double song = end.song - start.song;
  const double stretched = end.stretchedSong - start.stretchedSong;
  return stretched > 0 ? song / stretched : 0;
}

std::vector<TimeStretchingStep> compileTimeStretchingSteps(
    const std::vector<double>& landmarks, SchedulingPolicy policy, const PlaybackTimeMap& map) {
  if (policy.mode() != SchedulingPolicy::Mode::Stepped)
    throw std::invalid_argument("Stepped compiler requires an explicit stepped policy");
  if (!map.hasRateRamp()) return {};
  const auto times = steppedTransitionTimes(landmarks, policy.stepDuration());
  std::vector<TimeStretchingStep> result;
  if (times.empty()) return result;
  result.reserve(times.size()-1);
  auto last = map.fromTransition(times.front());
  finite(last.song); finite(last.stretchedSong); finite(last.transition);
  for (std::size_t i = 1; i < times.size(); ++i) {
    const auto next = map.fromTransition(times[i]);
    finite(next.song); finite(next.stretchedSong); finite(next.transition);
    if (next.song < last.song || next.stretchedSong < last.stretchedSong ||
        next.transition < last.transition)
      throw std::invalid_argument("Reversed time stretching interval");
    result.push_back({last, next});
    last = next;
  }
  return result;
}

std::vector<SteppedAutomation> compileSteppedAutomations(
    const std::vector<ContinuousAutomation>& automations,
    const std::vector<double>& landmarks, SchedulingPolicy policy, const PlaybackTimeMap& map) {
  if (policy.mode() != SchedulingPolicy::Mode::Stepped)
    throw std::invalid_argument("Stepped compiler requires an explicit stepped policy");
  const auto times = steppedTransitionTimes(landmarks, policy.stepDuration());
  std::vector<PlaybackTime> playback;
  playback.reserve(times.size());
  for (double t : times) {
    const auto p = map.fromTransition(t);
    finite(p.song); finite(p.stretchedSong); finite(p.transition);
    playback.push_back(p);
  }
  std::vector<SteppedAutomation> result;
  std::size_t total = 0;
  for (const auto& automation : automations) {
    if (rateParameter(automation.parameter)) continue;
    ContinuousAutomationValues values(automation);
    SteppedAutomation stepped{automation.parameter, {}, {}};
    std::vector<SteppedPoint> raw;
    raw.reserve(playback.size());
    for (const auto& time : playback) {
      const double value = values.valueAt(time.song);
      finite(value);
      raw.push_back({value, time});
      if (stepped.points.empty() || value != stepped.points.back().value) {
        if (total == maximumPoints) throw std::length_error("Stepped schedule too large");
        stepped.points.push_back({value, time});
        ++total;
      }
    }
    stepped.ramps = steppedRamps(raw);
    if (stepped.ramps.size() > maximumPoints - total)
      throw std::length_error("Stepped schedule too large");
    total += stepped.ramps.size();
    result.push_back(std::move(stepped));
  }
  return result;
}
}  // namespace lmg::automix
