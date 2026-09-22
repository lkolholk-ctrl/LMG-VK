#pragma once
#include "lmg/automix/media_time.h"
#include "lmg/automix/stepped_schedule.h"
#include <map>
#include <optional>
#include <string_view>

namespace lmg::automix {
struct TimedEffectEvent {
  MediaTime time;
  // Four-character graph ID -> AU float value. Parameter order within one
  // instant is not specified by the original dictionary transport.
  std::map<std::uint32_t, float> parameters;
};
// ASCII domain used by DSPGraph. Non-four-character IDs have no graph address.
std::optional<std::uint32_t> effectParameterAddress(std::string_view id);
// Source 0x2721b5788 -> 0x2721b5368: stretched-song CMTime keys, last write wins
// for a repeated address at an equal time, then events sorted chronologically.
// out_gain is excluded by its complete descriptor; ts_rate has no FourCC.
// Starts with the original provider's 27 FourCC defaults at media time zero.
// A point at zero overrides only its own address; other defaults remain.
// This is the recovered effect transport, not PCM quantization or gain/rate DSP.
std::vector<TimedEffectEvent> compileTimedEffectEvents(
    const std::vector<SteppedAutomation>& automations);
}  // namespace lmg::automix
