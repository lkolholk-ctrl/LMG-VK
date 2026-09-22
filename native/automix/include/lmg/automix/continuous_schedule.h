#pragma once
#include "lmg/automix/playback_time.h"
#include <cstdint>
#include <array>
#include <string>
#include <string_view>
#include <vector>
namespace lmg::automix {
struct EffectParameterDescriptor {
  std::string id;
  double minimum, maximum, defaultValue;
  std::string styleParameterId; // source Codable field styleParameterID at +0x28
};
// All 29 descriptors recovered from original initializers. No range/default
// normalization: the source LP default really exceeds its declared maximum.
const EffectParameterDescriptor& effectParameter(std::string_view id);
const std::array<EffectParameterDescriptor, 29>& allEffectParameters();
const EffectParameterDescriptor& styleEffectParameter(std::string_view styleParameterId);
struct ContinuousPoint {
  double value, songTime;
  std::optional<std::uint8_t> curve;
};
struct ContinuousAutomation {
  EffectParameterDescriptor parameter;
  std::vector<ContinuousPoint> points;
};
struct ContinuousRamp {
  double startValue, endValue, startSongTime, endSongTime;
  std::uint8_t curve;
};
// Adjacent points, in supplied order. The curve belongs to the first point.
// Equal times are retained; reversed times/missing nonterminal curves fail.
// Curve tags 0xfc..0xff are reserved in the source optional point representation.
std::vector<ContinuousRamp> continuousRamps(const std::vector<ContinuousPoint>& points);
// Preparation-time immutable evaluator, preserving source point order and
// reverse inclusive ramp lookup. Times stay in seconds; no PCM quantization.
class ContinuousAutomationValues {
 public:
  explicit ContinuousAutomationValues(const ContinuousAutomation& automation);
  double valueAt(double songTime) const;
 private:
  double default_, firstValue_ = 0, lastValue_ = 0, firstTime_ = 0;
  bool hasPoints_;
  std::vector<ContinuousRamp> ramps_;
};
EffectParameterDescriptor playbackRateParameter();
EffectParameterDescriptor bypassParameter();
// Source builder prepends a bypass gate only for a nonempty parent interval
// and only if no complete bypass descriptor is already present.
std::vector<ContinuousAutomation> withStyleBypass(
    std::vector<ContinuousAutomation> automations, double begin, double end);
// First full descriptor match, then first adjacent-point ramp. Empty/single
// point match means no ramp; a later duplicate automation is not substituted.
std::optional<RateRamp> selectedPlaybackRateRamp(const std::vector<ContinuousAutomation>& automations);
PlaybackTimeMap schedulePlaybackTimeMap(double playbackTransitionStart, double startPlaybackSongTime,
    std::optional<TimeStretchingState> previous, const std::vector<ContinuousAutomation>& automations);
}
