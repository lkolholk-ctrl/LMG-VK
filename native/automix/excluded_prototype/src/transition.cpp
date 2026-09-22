#include "lmg/automix/transition.h"

#include <algorithm>
#include <limits>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
struct Binding { std::string_view id; double EffectSettings::* member; };
constexpr Binding bindings[] = {
  {"player_gain", &EffectSettings::inputGain},
  {"aufilter_center_bandwidth", &EffectSettings::bandwidthOctaves},
  {"aufilter_center_gain", &EffectSettings::centerGainDb},
  {"aufilter_center_freq", &EffectSettings::centerHz},
  {"hp_cutoff_freq", &EffectSettings::highpassHz},
  {"hp_reso", &EffectSettings::highpassResonanceDb},
  {"lp_cutoff_freq", &EffectSettings::lowpassHz},
  {"lp_reso", &EffectSettings::lowpassResonanceDb},
  {"send_mixer_gain", &EffectSettings::sendGain},
  {"fx_delay_delay_time", &EffectSettings::delaySeconds},
  {"fx_delay_lp_cutoff_frequency", &EffectSettings::delayLowpassHz},
  {"fx_delay_dry_wet", &EffectSettings::delayWetPercent},
  {"fx_delay_feedback", &EffectSettings::delayFeedbackPercent},
  {"fx_reverb_gain", &EffectSettings::reverbGainDb},
  {"fx_reverb_dry_wet", &EffectSettings::reverbWetPercent},
  {"fx_reverb_low_frequency_decay_time", &EffectSettings::reverbLowDecaySeconds},
  {"fx_reverb_high_frequency_decay_time", &EffectSettings::reverbHighDecaySeconds},
  {"fx_hp_cutoff_freq", &EffectSettings::wetHighpassHz},
  {"fx_hp_reso", &EffectSettings::wetHighpassResonanceDb},
  {"fx_lp_cutoff_freq", &EffectSettings::wetLowpassHz},
  {"fx_lp_reso", &EffectSettings::wetLowpassResonanceDb},
  {"fx_mixer_dry", &EffectSettings::dryGain},
  {"fx_mixer_wet", &EffectSettings::wetGain}
};
double constant(const ParameterSchedule& p) {
  const double value = p.lane.initial();
  for (const auto& r : p.lane.ramps())
    if (r.from != value || r.to != value)
      throw std::invalid_argument("Dynamic parameter not supported: " + p.parameterId);
  return value;
}
std::unique_ptr<TrackEffects> prepare(std::uint32_t rate, std::size_t channels, DeckSchedule deck) {
  std::vector<EffectAutomation> effects;
  const AutomationLane* output = nullptr;
  for (std::size_t i = 0; i < deck.parameters.size(); ++i) {
    const auto& p = deck.parameters[i];
    for (std::size_t j = 0; j < i; ++j)
      if (p.parameterId == deck.parameters[j].parameterId)
        throw std::invalid_argument("Duplicate parameter: " + p.parameterId);
    if (p.parameterId == "out_gain") { output = &p.lane; continue; }
    if (p.parameterId == "ts_rate") {
      if (constant(p) != 1) throw std::invalid_argument("Time stretching is not implemented");
      continue;
    }
    if (p.parameterId == "bypa") {
      const double value = constant(p);
      if (value != 0 && value != 1) throw std::invalid_argument("bypa must be 0 or 1");
      deck.settings.bypass = value == 1; continue;
    }
    if (p.parameterId == "fx_reverb_min_delay_time") {
      deck.settings.reverbMinSeconds = constant(p); continue;
    }
    if (p.parameterId == "fx_reverb_max_delay_time") {
      deck.settings.reverbMaxSeconds = constant(p); continue;
    }
    if (p.parameterId == "fx_reverb_randomize_reflections") {
      const double value = constant(p);
      if (value < 1 || value > 1000 || value != static_cast<std::uint32_t>(value))
        throw std::invalid_argument("Reverb seed must be an integer in [1, 1000]");
      deck.settings.reverbSeed = static_cast<std::uint32_t>(value); continue;
    }
    const auto match = std::find_if(std::begin(bindings), std::end(bindings),
                                   [&](const Binding& b) { return b.id == p.parameterId; });
    if (match == std::end(bindings)) throw std::invalid_argument("Unknown parameter: " + p.parameterId);
    effects.push_back({match->member, p.lane});
  }
  if (!output) throw std::invalid_argument("Deck requires explicit out_gain");
  return std::make_unique<TrackEffects>(rate, channels, deck.settings, *output, std::move(effects));
}
}  // namespace

std::uint8_t styleCurveByte(std::string_view name) {
  if (name == "linear") return 0x80;
  if (name == "ease-in-0.5") return 0;
  if (name == "ease-in-2") return 1;
  if (name == "ease-in-4") return 2;
  if (name == "ease-out-0.5") return 0x40;
  if (name == "ease-out-2") return 0x41;
  if (name == "ease-out-4") return 0x42;
  if (name == "logarithmic") return 0x81;
  throw std::invalid_argument("Unknown interpolation: " + std::string(name));
}

struct TransitionRenderer::Impl {
  std::size_t channels, capacity;
  Frame next = 0;
  std::unique_ptr<TrackEffects> outgoing, incoming;
  std::vector<float> a, b;
  Impl(std::uint32_t rate, std::size_t count, DeckSchedule out, DeckSchedule in, std::size_t scratch)
      : channels(count), capacity(scratch) {
    if (count == 0 || count > 8 || scratch == 0 || scratch > 8192)
      throw std::invalid_argument("Unsupported renderer buffer size");
    outgoing = prepare(rate, count, std::move(out));
    incoming = prepare(rate, count, std::move(in));
    a.resize(count * scratch); b.resize(count * scratch);
  }
};
TransitionRenderer::TransitionRenderer(std::uint32_t rate, std::size_t channels,
    DeckSchedule outgoing, DeckSchedule incoming, std::size_t scratchFrames)
    : impl_(std::make_unique<Impl>(rate, channels, std::move(outgoing), std::move(incoming), scratchFrames)) {}
TransitionRenderer::~TransitionRenderer() = default;
bool TransitionRenderer::render(const float* outgoing, const float* incoming, float* output,
                                std::size_t frames, Frame firstFrame) noexcept {
  auto& p = *impl_;
  if (firstFrame < 0 || firstFrame != p.next) return false;
  if (frames == 0) return true;
  if (!output || frames > std::numeric_limits<std::size_t>::max() / p.channels ||
      frames > static_cast<std::uint64_t>(std::numeric_limits<Frame>::max() - firstFrame)) return false;
  for (std::size_t offset = 0; offset < frames;) {
    const auto block = std::min(p.capacity, frames - offset);
    const auto sample = offset * p.channels;
    // Preconditions above guarantee both process calls succeed. Render both
    // decks to scratch before writing output, including when inputs alias it.
    p.outgoing->process(outgoing ? outgoing + sample : nullptr, p.a.data(), block);
    p.incoming->process(incoming ? incoming + sample : nullptr, p.b.data(), block);
    for (std::size_t i = 0; i < block * p.channels; ++i) output[sample + i] = p.a[i] + p.b[i];
    offset += block;
  }
  p.next += static_cast<Frame>(frames);
  return true;
}
bool TransitionRenderer::reset(Frame firstFrame) noexcept {
  if (firstFrame < 0) return false;
  impl_->outgoing->reset(firstFrame); impl_->incoming->reset(firstFrame);
  impl_->next = firstFrame;
  return true;
}

}  // namespace lmg::automix
