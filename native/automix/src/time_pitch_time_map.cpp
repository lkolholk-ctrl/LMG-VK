#include "lmg/automix/time_pitch_time_map.h"
#include <cmath>
#include <stdexcept>
#include <utility>
namespace lmg::automix {
TimePitchMapSegment timePitchMapSegment(double a,double b,double x,double y,double t) {
  if(!std::isfinite(a)||!std::isfinite(b)||!std::isfinite(x)||!std::isfinite(y)||!std::isfinite(t)||a<=0||b<=0||y<x)
    throw std::invalid_argument("invalid TimePitch map segment");
  const double duration=(y-x)/((a+b)*.5);
  const double end=t+duration,slope=duration==0?0:(b-a)/duration;
  if(!std::isfinite(end)||!std::isfinite(slope))throw std::invalid_argument("TimePitch map overflow");
  return {a,b,x,y,t,end,slope};
}
double timePitchMapSourceFrame(const TimePitchMapSegment& s,double time) noexcept {
  if(s.endOutput<=time) {
    const double delta=time-s.endOutput;
    const double displacement=delta*s.endRate;
    return s.endSource+displacement;
  }
  const double delta=time-s.startOutput;
  const double halfSlope=s.slope*.5;
  const double rate=std::fma(halfSlope,delta,s.startRate);
  const double displacement=delta*rate;
  return s.startSource+displacement;
}
TimePitchTimeMap::TimePitchTimeMap(std::vector<TimePitchMapSegment> segments):segments_(std::move(segments)) {
  double previous=-INFINITY;
  for(const auto& s:segments_) {
    if(!std::isfinite(s.startRate)||!std::isfinite(s.endRate)||!std::isfinite(s.startSource)||
       !std::isfinite(s.endSource)||!std::isfinite(s.startOutput)||!std::isfinite(s.endOutput)||
       !std::isfinite(s.slope)||s.startRate<=0||s.endRate<=0||s.endOutput<s.startOutput||s.endOutput<previous)
      throw std::invalid_argument("invalid TimePitch map order or coefficients");
    previous=s.endOutput;
  }
}
double TimePitchTimeMap::sourceFrame(double time) const noexcept {
  if(segments_.empty())return time;
  std::size_t i=0;
  while(i+1<segments_.size() && segments_[i].endOutput<=time)++i;
  return timePitchMapSourceFrame(segments_[i],time);
}
TimePitchTimeMapView TimePitchTimeMap::view() const noexcept {
  return {this,[](const void* p,double t) noexcept {return static_cast<const TimePitchTimeMap*>(p)->sourceFrame(t);}};
}
}
