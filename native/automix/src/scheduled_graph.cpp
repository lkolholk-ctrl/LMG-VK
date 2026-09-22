#include "lmg/automix/scheduled_graph.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <string_view>
#include <utility>

namespace lmg::automix {
namespace {
struct Binding { std::string_view id; double EffectSettings::* member; };
constexpr Binding bindings[] = {
  {"Ga1g", &EffectSettings::inputGain}, {"Ga2g", &EffectSettings::sendGain},
  {"Ga3g", &EffectSettings::dryGain}, {"Ga4g", &EffectSettings::wetGain},
  {"Fcf1", &EffectSettings::centerHz}, {"Fcg1", &EffectSettings::centerGainDb},
  {"Fbw1", &EffectSettings::bandwidthOctaves},
  {"HP1f", &EffectSettings::highpassHz}, {"HP1r", &EffectSettings::highpassResonanceDb},
  {"HP2f", &EffectSettings::wetHighpassHz}, {"HP2r", &EffectSettings::wetHighpassResonanceDb},
  {"LP1f", &EffectSettings::lowpassHz}, {"LP1r", &EffectSettings::lowpassResonanceDb},
  {"LP2f", &EffectSettings::wetLowpassHz}, {"LP2r", &EffectSettings::wetLowpassResonanceDb},
  {"DLdw", &EffectSettings::delayWetPercent}, {"DLdt", &EffectSettings::delaySeconds},
  {"DLfb", &EffectSettings::delayFeedbackPercent}, {"DLlf", &EffectSettings::delayLowpassHz},
  {"RVdw", &EffectSettings::reverbWetPercent}, {"RVga", &EffectSettings::reverbGainDb},
  {"RVlf", &EffectSettings::reverbLowDecaySeconds}, {"RVhf", &EffectSettings::reverbHighDecaySeconds}
};
std::vector<GraphEvent> validate(const PreparedGraph& initial, Frame start,
                                 std::vector<GraphEvent> events) {
  if (start < 0) throw std::invalid_argument("Negative graph start frame");
  for (std::size_t i = 0; i < events.size(); ++i) {
    if (events[i].frame < start || (i && events[i].frame <= events[i-1].frame))
      throw std::invalid_argument("Graph events must be strictly ordered at or after start");
    if (!initial.compatibleWith(events[i].configuration))
      throw std::invalid_argument("Graph event requires host reconstruction");
  }
  return events;
}
}

EffectSettings initializeGraphSettings(double rate, const TimedEffectEvent& initial) {
  if (compareMediaTimes(initial.time, automationMediaTime(0)) != 0)
    throw std::invalid_argument("Graph initialization requires the time-zero event");
  EffectSettings settings;
  for (const auto& [address, value] : initial.parameters) {
    if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite initial graph value");
    std::string id(4, '\0');
    for (unsigned i = 0; i < 4; ++i) id[i] = static_cast<char>((address >> (24-i*8)) & 255);
    std::size_t index = 0;
    while (index < std::size(bindings) && bindings[index].id != id) ++index;
    if (index != std::size(bindings)) settings.*bindings[index].member = value;
    else if (id == "bypa") {
      if (value != 0 && value != 1) throw std::invalid_argument("Graph bypass must be 0 or 1");
      settings.bypass = value == 1;
    } else if (id == "RVmi") settings.reverbMinSeconds = value;
    else if (id == "RVma") settings.reverbMaxSeconds = value;
    else if (id == "RVrr") {
      // The public geometry API accepts an integer seed. Do not invent a
      // fractional-seed conversion until that setter conversion is recovered.
      if (value < 0 || double(value) > std::numeric_limits<std::uint32_t>::max() ||
          std::trunc(value) != value)
        throw std::invalid_argument("Initial reverb seed must be an unsigned integer");
      settings.reverbSeed = static_cast<std::uint32_t>(value);
    } else throw std::invalid_argument("Unsupported initial graph parameter: " + id);
  }
  const PreparedGraph validated(rate, settings);
  return settings;
}

std::vector<GraphEvent> prepareGraphEvents(double rate, EffectSettings settings,
                                          const std::vector<GraphParameterWrite>& writes) {
  const PreparedGraph initial(rate, settings); // validates even an empty schedule
  std::vector<GraphEvent> result;
  std::uint32_t seen = 0;
  bool decay = false;
  Frame frame = -1;
  for (const auto& write : writes) {
    if (write.frame < 0 || write.frame < frame)
      throw std::invalid_argument("Unordered graph parameter write");
    if (write.frame != frame) {
      if (frame >= 0) result.push_back({frame, PreparedGraph(rate, settings), decay});
      frame = write.frame; seen = 0; decay = false;
    }
    std::size_t index = 0;
    while (index < std::size(bindings) && bindings[index].id != write.parameterId) ++index;
    const bool bypass = write.parameterId == "bypa";
    if (index == std::size(bindings) && !bypass)
      throw std::invalid_argument("Unsupported live graph parameter: " + write.parameterId);
    const auto bit = std::uint32_t{1} << index;
    if (seen & bit) throw std::invalid_argument("Duplicate graph parameter at same frame");
    if (!std::isfinite(write.value) || std::abs(write.value) > std::numeric_limits<float>::max())
      throw std::invalid_argument("Nonfinite graph parameter write");
    seen |= bit;
    if (bypass) {
      // Recovered style gates are binary. Reject fractional host writes rather
      // than inventing an unverified threshold or integer conversion rule.
      if (write.value != 0 && write.value != 1)
        throw std::invalid_argument("Graph bypass must be 0 or 1");
      settings.bypass = write.value == 1;
    } else {
      settings.*bindings[index].member = static_cast<float>(write.value);
    }
    decay |= write.parameterId == "RVlf" || write.parameterId == "RVhf";
  }
  if (frame >= 0) result.push_back({frame, PreparedGraph(rate, settings), decay});
  return result;
}

ScheduledTrackGraph::ScheduledTrackGraph(const PreparedGraph& initial, unsigned channels,
    std::size_t maxFrames, Frame firstFrame, std::vector<GraphEvent> events)
    : events_(validate(initial, firstFrame, std::move(events))),
      graph_(initial, channels, maxFrames), channels_(channels), capacity_(maxFrames),
      position_(firstFrame) {}

bool ScheduledTrackGraph::process(const float* input, float* output, std::size_t frames,
                                  GraphSilence silence) noexcept {
  if (frames > capacity_ || (frames && !output) ||
      frames > static_cast<std::uint64_t>(std::numeric_limits<Frame>::max() - position_))
    return false;
  if (!frames) return true;
  const Frame end = position_ + static_cast<Frame>(frames);
  std::size_t offset = 0;
  while (position_ < end) {
    if (next_ < events_.size() && events_[next_].frame == position_) {
      const auto& event = events_[next_++];
      // Validated for the full immutable schedule before allocating graph state.
      graph_.apply(event.configuration, event.reverbDecayWritten);
    }
    const Frame until = next_ < events_.size() ? std::min(end, events_[next_].frame) : end;
    const auto count = static_cast<std::size_t>(until - position_);
    graph_.process(input ? input + offset * channels_ : nullptr,
                   output + offset * channels_, count, silence);
    position_ = until;
    offset += count;
  }
  return true;
}
}  // namespace lmg::automix
