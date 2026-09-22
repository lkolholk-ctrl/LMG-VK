#include "lmg/automix/queue_rate_history.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <utility>
namespace lmg::automix {
namespace {
std::int64_t saturated(double v,bool nearest) noexcept {
  const double rounded=nearest?std::round(v):std::trunc(v);
  if(rounded>=0x1p63)return std::numeric_limits<std::int64_t>::max();
  if(rounded<=-0x1p63)return std::numeric_limits<std::int64_t>::min();
  return static_cast<std::int64_t>(rounded);
}
std::int64_t difference(std::int64_t a,std::int64_t b) noexcept {
  const auto bits=static_cast<std::uint64_t>(a)-static_cast<std::uint64_t>(b);
  std::int64_t value;std::memcpy(&value,&bits,sizeof(value));return value;
}
}
QueueRateHistory::QueueRateHistory(std::vector<QueueRateAnchor> anchors,std::int64_t highWater)
 : anchors_(std::move(anchors)),highWater_(highWater) {
  for(std::size_t i=0;i<anchors_.size();++i){
    const auto& a=anchors_[i];
    if(!std::isfinite(a.scaledOrigin)||!std::isfinite(a.rate)||a.rate<=0||
       (i&&(a.unscaledFrame<anchors_[i-1].unscaledFrame||a.scaledFrame<anchors_[i-1].scaledFrame)))
      throw std::invalid_argument("Invalid queue rate history");
  }
}
std::int64_t QueueRateHistory::toUnscaled(double frame) {
  if(!std::isfinite(frame))throw std::invalid_argument("Nonfinite queue time");
  std::int64_t result;
  if(anchors_.empty())result=saturated(frame,false);
  else{
    const auto key=saturated(frame,true);
    auto it=std::upper_bound(anchors_.begin(),anchors_.end(),key,
        [](auto v,const auto& a){return v<a.scaledFrame;});
    if(it!=anchors_.begin())--it;
    const double delta=frame-static_cast<double>(it->scaledFrame);
    result=saturated(std::fma(delta,it->rate,static_cast<double>(it->unscaledFrame)),true);
  }
  highWater_=std::max(highWater_,result);return result;
}
double QueueRateHistory::toScaled(std::int64_t frame,double* rate) noexcept {
  highWater_=std::max(highWater_,frame);
  if(anchors_.empty()){if(rate)*rate=1;return static_cast<double>(frame);}
  auto it=std::upper_bound(anchors_.begin(),anchors_.end(),frame,
      [](auto v,const auto& a){return v<a.unscaledFrame;});
  if(it!=anchors_.begin())--it;
  const double delta=static_cast<double>(difference(frame,it->unscaledFrame));
  const double scaled=it->scaledOrigin+delta/it->rate;
  if(rate)*rate=it->rate;
  return std::round(scaled);
}
}
