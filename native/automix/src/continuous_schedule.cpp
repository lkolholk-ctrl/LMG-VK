#include "lmg/automix/continuous_schedule.h"
#include "lmg/automix/automation.h"
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
namespace {
bool sameParameter(const EffectParameterDescriptor& a,const EffectParameterDescriptor& b) {
  return a.id==b.id && a.minimum==b.minimum && a.maximum==b.maximum &&
      a.defaultValue==b.defaultValue && a.styleParameterId==b.styleParameterId;
}
}
std::vector<ContinuousRamp> continuousRamps(const std::vector<ContinuousPoint>& points) {
  std::vector<ContinuousRamp> ramps;
  if (points.empty()) return ramps;
  ramps.reserve(points.size()-1);
  for (std::size_t i=0; i+1<points.size(); ++i) {
    const auto& first=points[i]; const auto& second=points[i+1];
    // Nonfinite rejection is a host guard; the source traps on malformed order
    // and absent start curves. No sorting, default interpolation or coalescing.
    if (!std::isfinite(first.value) || !std::isfinite(second.value) ||
        !std::isfinite(first.songTime) || !std::isfinite(second.songTime) ||
        second.songTime<first.songTime || !first.curve || *first.curve>0xfb)
      throw std::invalid_argument("Invalid continuous automation points");
    ramps.push_back({first.value,second.value,first.songTime,second.songTime,*first.curve});
  }
  return ramps;
}
EffectParameterDescriptor playbackRateParameter() { return effectParameter("ts_rate"); }
EffectParameterDescriptor bypassParameter() { return effectParameter("bypa"); }
std::vector<ContinuousAutomation> withStyleBypass(
    std::vector<ContinuousAutomation> automations,double begin,double end) {
  if (!std::isfinite(begin) || !std::isfinite(end) || end<begin)
    throw std::invalid_argument("Invalid style bypass window");
  const auto parameter=bypassParameter();
  for (const auto& automation:automations)
    if (sameParameter(automation.parameter,parameter)) return automations;
  if (begin<end) {
    ContinuousAutomation gate{parameter,{{1,begin,0x80},{0,begin,0x80},{0,end,0x80},{1,end,0x80}}};
    automations.insert(automations.begin(),std::move(gate));
  }
  return automations;
}
ContinuousAutomationValues::ContinuousAutomationValues(const ContinuousAutomation& automation)
    : default_(automation.parameter.defaultValue), hasPoints_(!automation.points.empty()),
      ramps_(continuousRamps(automation.points)) {
  if (!std::isfinite(default_)) throw std::invalid_argument("Nonfinite automation default");
  if (hasPoints_) {
    firstValue_=automation.points.front().value; lastValue_=automation.points.back().value;
    firstTime_=automation.points.front().songTime;
    if (!std::isfinite(firstValue_) || !std::isfinite(firstTime_))
      throw std::invalid_argument("Nonfinite automation point");
  }
  for (const auto& ramp:ramps_)
    if (ramp.curve==0x81 && (ramp.startValue<=0 || ramp.endValue<=0))
      throw std::invalid_argument("Nonpositive logarithmic automation value");
}
double ContinuousAutomationValues::valueAt(double time) const {
  if (!std::isfinite(time)) throw std::invalid_argument("Nonfinite automation query");
  if (!hasPoints_) return default_;
  if (time<firstTime_) return firstValue_;
  for (auto it=ramps_.rbegin();it!=ramps_.rend();++it) {
    if (time<it->startSongTime || time>it->endSongTime) continue;
    if (time>=it->endSongTime) return it->endValue;
    if (time<=it->startSongTime) return it->startValue;
    const double p=(time-it->startSongTime)/(it->endSongTime-it->startSongTime);
    return curveValue(it->startValue,it->endValue,p,it->curve);
  }
  return lastValue_;
}
std::optional<RateRamp> selectedPlaybackRateRamp(const std::vector<ContinuousAutomation>& automations) {
  const auto target=playbackRateParameter();
  for (const auto& automation:automations) {
    const auto& p=automation.parameter;
    if (!sameParameter(p,target)) continue;
    const auto ramps=continuousRamps(automation.points);
    if (ramps.empty()) return std::nullopt;
    const auto& first=ramps.front();
    return RateRamp{first.startValue,first.endValue,first.startSongTime,first.endSongTime};
  }
  return std::nullopt;
}
PlaybackTimeMap schedulePlaybackTimeMap(double transition,double song,
    std::optional<TimeStretchingState> previous,const std::vector<ContinuousAutomation>& automations) {
  const auto anchor=playbackAnchor(transition,song,previous);
  const auto ramp=selectedPlaybackRateRamp(automations);
  return ramp ? PlaybackTimeMap(anchor,*ramp) : PlaybackTimeMap(anchor);
}
}
