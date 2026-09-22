#include "lmg/automix/media_time.h"
#include <cmath>
#include <limits>
#include <stdexcept>

namespace lmg::automix {
MediaTime automationMediaTime(double seconds) {
  if (!std::isfinite(seconds)) throw std::invalid_argument("Nonfinite automation time");
  MediaTime result{0, 1000000000, 1, 0};
  if (seconds == 0) return result;
  constexpr double limit = 0x1p63;
  double magnitude = std::abs(seconds * static_cast<double>(result.timescale));
  while (magnitude > limit && result.timescale > 1) {
    result.timescale >>= 1;
    magnitude = std::abs(seconds * static_cast<double>(result.timescale));
  }
  if (magnitude > limit) throw std::overflow_error("Unrepresentable automation time");
  // Source explicitly saturates magnitude == double(2^63) to INT64_MAX before
  // applying the sign. Casting that double directly to int64 would be undefined.
  result.value = magnitude == limit ? std::numeric_limits<std::int64_t>::max()
                                   : static_cast<std::int64_t>(magnitude);
  if (seconds < 0) result.value = -result.value;
  const double error = static_cast<double>(result.value) / static_cast<double>(result.timescale) - seconds;
  if (error != 0) result.flags = 3;
  return result;
}

int compareMediaTimes(MediaTime a, MediaTime b) {
  if (a.timescale <= 0 || b.timescale <= 0 || (a.flags & ~2u) != 1 || (b.flags & ~2u) != 1)
    throw std::invalid_argument("Non-numeric automation time");
  if (a.epoch != b.epoch) return a.epoch < b.epoch ? -1 : 1;
  const auto wholeA = a.value / a.timescale, wholeB = b.value / b.timescale;
  if (wholeA != wholeB) return wholeA < wholeB ? -1 : 1;
  // Each remainder has magnitude < INT32_MAX, so these products fit int64.
  const auto remainderA = (a.value % a.timescale) * b.timescale;
  const auto remainderB = (b.value % b.timescale) * a.timescale;
  return remainderA < remainderB ? -1 : remainderA > remainderB ? 1 : 0;
}
MediaTime convertMediaTimeScaleNearest(MediaTime time,std::int32_t scale) {
  if(time.timescale<=0||scale<=0||(time.flags&~2u)!=1)
    throw std::invalid_argument("Non-numeric time scale conversion");
  if(time.timescale==scale)return time;
  constexpr auto maximum=std::numeric_limits<std::int64_t>::max();
  const auto whole=time.value/time.timescale;
  const auto part=(time.value%time.timescale)*static_cast<std::int64_t>(scale);
  if(whole>maximum/scale||whole<(-maximum)/scale)
    throw std::overflow_error("Time scale conversion is infinite");
  const auto main=whole*scale,extra=part/time.timescale;
  if((extra>0&&main>maximum-extra)||(extra<0&&main<-maximum-extra))
    throw std::overflow_error("Time scale conversion is infinite");
  auto value=main+extra;
  // CoreMedia's signed multiply/divide helper reserves both magnitude-limit
  // results as infinity before applying rounding.
  if(value>=maximum||value<=-maximum)
    throw std::overflow_error("Time scale conversion is infinite");
  const auto remainder=part%time.timescale;
  auto flags=time.flags;
  if(remainder){
    flags|=2;
    if(std::abs(remainder)*2>=time.timescale)value+=remainder>0?1:-1;
  }
  return {value,scale,flags,time.epoch};
}
}  // namespace lmg::automix
