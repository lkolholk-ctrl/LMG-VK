#include "lmg/automix/graph.h"
#include "lmg/automix/gain.h"
#include "lmg/automix/highpass.h"
#include <cmath>
#include <limits>
#include <stdexcept>
#include <vector>

namespace lmg::automix {
namespace {
float parameter(double value) {
  if (!std::isfinite(value) || std::abs(value) > std::numeric_limits<float>::max())
    throw std::invalid_argument("Nonfinite graph parameter");
  return static_cast<float>(value);
}
void gain(double value) {
  if (!std::isfinite(value) || value < 0 || value > 1)
    throw std::invalid_argument("Invalid graph gain");
}
}

PreparedGraph::PreparedGraph(double rate, EffectSettings s) : rate_(rate), settings_(s) {
  if (!std::isfinite(rate) || rate < 8000 || rate > 192000)
    throw std::invalid_argument("Unsupported graph rate");
  gain(s.inputGain); gain(s.sendGain); gain(s.dryGain); gain(s.wetGain);
  FilterParameters filter;
  filter.bands[0] = {parameter(s.centerHz), parameter(s.centerGainDb), parameter(s.bandwidthOctaves)};
  filter_ = filterCoefficients(filter, rate);
  hp1_ = highpassCoefficients(parameter(s.highpassHz), parameter(s.highpassResonanceDb), rate);
  lp1_ = lowpassCoefficients(parameter(s.lowpassHz), parameter(s.lowpassResonanceDb), rate);
  hp2_ = highpassCoefficients(parameter(s.wetHighpassHz), parameter(s.wetHighpassResonanceDb), rate);
  lp2_ = lowpassCoefficients(parameter(s.wetLowpassHz), parameter(s.wetLowpassResonanceDb), rate);
  delay_ = {parameter(s.delayWetPercent), parameter(s.delaySeconds),
            parameter(s.delayFeedbackPercent), parameter(s.delayLowpassHz)};
  reverb_ = {parameter(s.reverbWetPercent), parameter(s.reverbGainDb),
             parameter(s.reverbLowDecaySeconds), parameter(s.reverbHighDecaySeconds)};
  geometry_ = {parameter(s.reverbMinSeconds), parameter(s.reverbMaxSeconds), s.reverbSeed};
  const float seedParameter = static_cast<float>(s.reverbSeed);
  geometry_.seed = double(seedParameter) >= double(std::numeric_limits<std::uint32_t>::max())
      ? std::numeric_limits<std::uint32_t>::max() : static_cast<std::uint32_t>(seedParameter);
  // Validate exactly the public kernels' host domains before a snapshot may be
  // used by the callback. No temporary delay rings/reverb tanks are allocated.
  if (delay_.wetPercent < 0 || delay_.wetPercent > 100 ||
      reverb_.wetPercent < 0 || reverb_.wetPercent > 100 ||
      reverb_.gainDb < -96 || reverb_.gainDb > 20 ||
      reverb_.lowDecaySeconds < .001f || reverb_.lowDecaySeconds > 100 ||
      reverb_.highDecaySeconds < .001f || reverb_.highDecaySeconds > 100 ||
      geometry_.minSeconds < .0001f || geometry_.maxSeconds > 1 ||
      geometry_.maxSeconds < geometry_.minSeconds)
    throw std::invalid_argument("Invalid graph effect settings");
}

struct TrackGraph::State {
  struct Channel {
    FilterKernel filter;
    BiquadSection hp1, lp1, hp2, lp2;
    DelayKernel delay;
    std::vector<float> dry, wet;
    Channel(const PreparedGraph& p, std::size_t capacity)
        : filter(p.filter_), hp1(p.hp1_), lp1(p.lp1_), hp2(p.hp2_), lp2(p.lp2_),
          delay(p.rate_), dry(capacity), wet(capacity) {}
  };
  PreparedGraph configuration;
  unsigned channels;
  std::size_t capacity;
  std::vector<Channel> channel;
  ReverbEffect reverb;
  std::array<GainSmoother, 4> gains;
  State(const PreparedGraph& p, unsigned count, std::size_t frames)
      : configuration(p), channels(count), capacity(frames), reverb(p.rate_, p.geometry_, p.settings_.bypass),
        gains{GainSmoother(static_cast<std::uint32_t>(p.rate_), float(p.settings_.inputGain)),
              GainSmoother(static_cast<std::uint32_t>(p.rate_), float(p.settings_.sendGain)),
              GainSmoother(static_cast<std::uint32_t>(p.rate_), float(p.settings_.dryGain)),
              GainSmoother(static_cast<std::uint32_t>(p.rate_), float(p.settings_.wetGain))} {
    channel.reserve(count);
    for (unsigned i = 0; i < count; ++i) channel.emplace_back(p, frames);
  }
};

TrackGraph::TrackGraph(const PreparedGraph& p, unsigned channels, std::size_t capacity) {
  if (channels < 1 || channels > 2 || !capacity || capacity > 16384)
    throw std::invalid_argument("Unsupported graph buffer layout");
  state_ = std::make_unique<State>(p, channels, capacity);
}
TrackGraph::~TrackGraph() = default;

bool PreparedGraph::compatibleWith(const PreparedGraph& other) const noexcept {
  return rate_ == other.rate_ &&
      geometry_.minSeconds == other.geometry_.minSeconds &&
      geometry_.maxSeconds == other.geometry_.maxSeconds && geometry_.seed == other.geometry_.seed;
}

bool TrackGraph::apply(const PreparedGraph& p, bool decayWritten) noexcept {
  auto& s = *state_;
  if (!p.compatibleWith(s.configuration)) return false;
  // AUEffectBase property 21 resets initialized channel kernels only on the
  // bypassed -> active edge. Reverb has its own distinct property implementation.
  if (s.configuration.settings_.bypass && !p.settings_.bypass) {
    for (auto& c : s.channel) {
      c.filter.reset();
      c.hp1.reset(); c.lp1.reset(); c.hp2.reset(); c.lp2.reset();
      c.delay.reset();
    }
  }
  for (auto& c : s.channel) {
    c.filter.coefficients(p.filter_);
    c.hp1.coefficients(p.hp1_); c.lp1.coefficients(p.lp1_);
    c.hp2.coefficients(p.hp2_); c.lp2.coefficients(p.lp2_);
  }
  s.gains[0].setGain(float(p.settings_.inputGain));
  s.gains[1].setGain(float(p.settings_.sendGain));
  s.gains[2].setGain(float(p.settings_.dryGain));
  s.gains[3].setGain(float(p.settings_.wetGain));
  if (decayWritten) s.reverb.reset();
  s.reverb.setBypassed(p.settings_.bypass);
  s.configuration = p;
  return true;
}

bool TrackGraph::process(const float* input, float* output, std::size_t frames, GraphSilence silent) noexcept {
  auto& s = *state_;
  if (frames > s.capacity || (frames && !output)) return false;
  const auto& p = s.configuration;
  const auto inputGain = s.gains[0].beginBlock(static_cast<std::uint32_t>(frames), silent.input);
  const auto sendGain = s.gains[1].beginBlock(static_cast<std::uint32_t>(frames), silent.send);
  const auto dryGain = s.gains[2].beginBlock(static_cast<std::uint32_t>(frames), silent.dry);
  const auto wetGain = s.gains[3].beginBlock(static_cast<std::uint32_t>(frames), silent.wet);
  // Gather every input channel before touching user output, including in-place.
  for (unsigned ch = 0; ch < s.channels; ++ch) {
    auto& c = s.channel[ch];
    for (std::size_t i = 0; i < frames; ++i) c.dry[i] = input ? input[i * s.channels + ch] : 0;
    applyGainBlock(c.dry.data(), c.dry.data(), frames, inputGain);
    if (!p.settings_.bypass) {
      c.filter.process(c.dry.data(), c.dry.data(), frames);
      c.hp1.process(c.dry.data(), c.dry.data(), frames);
      c.lp1.process(c.dry.data(), c.dry.data(), frames);
    }
    applyGainBlock(c.dry.data(), c.wet.data(), frames, sendGain);
    applyGainBlock(c.dry.data(), c.dry.data(), frames, dryGain);
    if (!p.settings_.bypass) c.delay.process(c.wet.data(), c.wet.data(), frames, p.delay_);
  }
  {
    auto* left = s.channel[0].wet.data();
    auto* right = s.channels == 2 ? s.channel[1].wet.data() : nullptr;
    s.reverb.process(left, right, left, right, frames, p.reverb_);
  }
  for (unsigned ch = 0; ch < s.channels; ++ch) {
    auto& c = s.channel[ch];
    if (!p.settings_.bypass) {
      c.hp2.process(c.wet.data(), c.wet.data(), frames);
      c.lp2.process(c.wet.data(), c.wet.data(), frames);
    }
    applyGainBlock(c.wet.data(), c.wet.data(), frames, wetGain);
    // Mixer input 0 is dry, input 1 is wet; both fixed unity mix weights.
    multiplyAddGain(c.wet.data(), c.dry.data(), c.dry.data(), frames, 1);
    for (std::size_t i = 0; i < frames; ++i) output[i * s.channels + ch] = c.dry[i];
  }
  return true;
}
}  // namespace lmg::automix
