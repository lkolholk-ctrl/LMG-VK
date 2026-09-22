#pragma once
#include "lmg/automix/time_pitch_stream.h"
#include <vector>
namespace lmg::automix {
struct TimePitchMapSegment {
  double startRate,endRate,startSource,endSource,startOutput,endOutput,slope;
};
// 1dcf70308: source interval and endpoint rates determine output duration.
TimePitchMapSegment timePitchMapSegment(double startRate,double endRate,
    double startSource,double endSource,double startOutput);
double timePitchMapSourceFrame(const TimePitchMapSegment&,double outputFrame) noexcept;
// Immutable prepared counterpart of the linked-node traversal at 234f45984.
// Supply source-ordered segments; no sorting, gap filling or rate substitution.
class TimePitchTimeMap {
 public:
  explicit TimePitchTimeMap(std::vector<TimePitchMapSegment> segments);
  double sourceFrame(double outputFrame) const noexcept;
  TimePitchTimeMapView view() const noexcept;
 private:
  std::vector<TimePitchMapSegment> segments_;
};
}
