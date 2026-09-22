#include "lmg/automix/effects.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <vector>
#include <utility>

namespace lmg::automix {
namespace {
constexpr double pi = 3.14159265358979323846;
double flush(double x) noexcept { return std::abs(x) < 1e-30 ? 0 : x; }
void range(double x, double lo, double hi) {
  if (!std::isfinite(x) || x < lo || x > hi)
    throw std::invalid_argument("Effect parameter outside supported range");
}
double frequency(double hz, double rate) { return std::min(hz, rate * .49); }
struct Biquad {
  double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;
  double tick(double x) noexcept {
    const double y = b0 * x + z1;
    z1 = flush(b1 * x - a1 * y + z2);
    z2 = flush(b2 * x - a2 * y);
    return y;
  }
  void coefficients(const Biquad& f) noexcept {
    b0 = f.b0; b1 = f.b1; b2 = f.b2; a1 = f.a1; a2 = f.a2;
  }
  void reset() noexcept { z1 = z2 = 0; }
};
Biquad filter(double rate, double hz, double resonance, bool high) {
  const double w = 2 * pi * frequency(hz, rate) / rate;
  // Explicit LMG mapping: zero dB -> Butterworth Q, bounded resonance.
  const double q = std::clamp(std::pow(10., resonance / 20) / std::sqrt(2.), .1, 20.);
  const double c = std::cos(w), alpha = std::sin(w) / (2 * q), a0 = 1 + alpha;
  const double b0 = (high ? 1 + c : 1 - c) / 2;
  return {b0 / a0, (high ? -2 : 2) * b0 / a0, b0 / a0,
          -2 * c / a0, (1 - alpha) / a0};
}
Biquad peak(double rate, const EffectSettings& p) {
  if (p.centerGainDb == 0) return {};
  const double w = 2 * pi * frequency(p.centerHz, rate) / rate;
  const double a = std::pow(10., p.centerGainDb / 40);
  const double alpha = std::sin(w) * std::sinh(std::log(2.) / 2 * p.bandwidthOctaves * w / std::sin(w));
  const double a0 = 1 + alpha / a;
  return {(1 + alpha * a) / a0, -2 * std::cos(w) / a0,
          (1 - alpha * a) / a0, -2 * std::cos(w) / a0, (1 - alpha / a) / a0};
}
struct Ring {
  std::vector<double> data;
  std::size_t index = 0;
  explicit Ring(std::size_t length) : data(length, 0) {}
  double read() const noexcept { return data[index]; }
  double read(double delayFrames) const noexcept {
    const auto whole = static_cast<std::size_t>(delayFrames);
    const double fraction = delayFrames - whole;
    const auto recent = (index + data.size() - whole) % data.size();
    const auto older = (recent + data.size() - 1) % data.size();
    return data[recent] * (1 - fraction) + data[older] * fraction;
  }
  void write(double x) noexcept {
    data[index] = flush(x);
    if (++index == data.size()) index = 0;
  }
  void reset() noexcept { std::fill(data.begin(), data.end(), 0); index = 0; }
};
std::size_t samples(double seconds, double rate) {
  return std::max<std::size_t>(1, static_cast<std::size_t>(std::floor(seconds * rate + .5)));
}
struct Comb {
  Ring ring;
  double low = 0, lowFeedback, highFeedback, pole;
  Comb(std::size_t length, double rate, const EffectSettings& p)
      : ring(length), lowFeedback(std::pow(.001, length / (rate * p.reverbLowDecaySeconds))),
        highFeedback(std::pow(.001, length / (rate * p.reverbHighDecaySeconds))),
        pole(std::exp(-2 * pi * frequency(2500, rate) / rate)) {}
  double tick(double x) noexcept {
    const double y = ring.read();
    low = flush((1 - pole) * y + pole * low);
    ring.write(x + lowFeedback * low + highFeedback * (y - low));
    return y;
  }
  void reset() noexcept { ring.reset(); low = 0; }
};
struct Channel {
  Biquad center, hp, lp, wetHp, wetLp;
  Ring delay;
  double delayLow = 0, delayPole;
  std::vector<Comb> combs;
  Channel(double rate, const EffectSettings& p, std::size_t delayCapacity)
      : center(peak(rate, p)), hp(filter(rate, p.highpassHz, p.highpassResonanceDb, true)),
        lp(filter(rate, p.lowpassHz, p.lowpassResonanceDb, false)),
        wetHp(filter(rate, p.wetHighpassHz, p.wetHighpassResonanceDb, true)),
        wetLp(filter(rate, p.wetLowpassHz, p.wetLowpassResonanceDb, false)),
        delay(delayCapacity),
        delayPole(std::exp(-2 * pi * frequency(p.delayLowpassHz, rate) / rate)) {
    // Four stratified, deterministic delays. Seed is a port policy, not rvb2's RNG.
    auto seed = p.reverbSeed;
    for (int i = 0; i < 4; ++i) {
      seed = 1664525u * seed + 1013904223u;
      const double position = (i + (seed >> 8) / 16777216.) / 4;
      combs.emplace_back(samples(p.reverbMinSeconds + position *
          (p.reverbMaxSeconds - p.reverbMinSeconds), rate), rate, p);
    }
  }
  void reset() noexcept {
    for (auto* f : {&center, &hp, &lp, &wetHp, &wetLp}) f->reset();
    delay.reset(); delayLow = 0;
    for (auto& c : combs) c.reset();
  }
};
void validate(const EffectSettings& p) {
  for (double g : {p.inputGain, p.sendGain, p.dryGain, p.wetGain}) range(g, 0, 1);
  for (double f : {p.centerHz, p.highpassHz, p.lowpassHz, p.delayLowpassHz,
                   p.wetHighpassHz, p.wetLowpassHz}) range(f, 10, 22050);
  for (double r : {p.highpassResonanceDb, p.lowpassResonanceDb,
                   p.wetHighpassResonanceDb, p.wetLowpassResonanceDb}) range(r, -20, 40.01);
  range(p.bandwidthOctaves, .05, 3); range(p.centerGainDb, -18, 18);
  range(p.delaySeconds, .0001, 2.01); range(p.delayFeedbackPercent, -99.9, 99.9);
  range(p.delayWetPercent, 0, 100); range(p.reverbWetPercent, 0, 100);
  range(p.reverbGainDb, -20, 20.01);
  range(p.reverbMinSeconds, .0001, 1); range(p.reverbMaxSeconds, p.reverbMinSeconds, 1);
  range(p.reverbLowDecaySeconds, .001, 20); range(p.reverbHighDecaySeconds, .001, 20);
  range(p.reverbSeed, 1, 1000);
}
}  // namespace

struct TrackEffects::Impl {
  EffectSettings p;
  AutomationLane outputLane;
  std::vector<EffectAutomation> automation;
  double rate, outputGain, delayWet, feedback, reverbWet, reverbGain, delayFrames;
  bool variableDelay = false;
  Frame nextFrame = 0;
  std::vector<Channel> channels;
  Impl(std::uint32_t sampleRate, std::size_t count, EffectSettings settings,
       AutomationLane gain, std::vector<EffectAutomation> lanes)
      : p(settings), outputLane(std::move(gain)), automation(std::move(lanes)),
        rate(sampleRate), outputGain(outputLane.initial()), delayWet(p.delayWetPercent / 100),
        feedback(p.delayFeedbackPercent / 100), reverbWet(p.reverbWetPercent / 100),
        reverbGain(std::pow(10., p.reverbGainDb / 20)), delayFrames(0) {
    if (rate < 8000 || rate > 192000 || count == 0 || count > 8)
      throw std::invalid_argument("Unsupported effect PCM format");
    validate(p);
    auto validateLane = [](const AutomationLane& lane, auto check) {
      check(lane.initial());
      for (const auto& ramp : lane.ramps()) { check(ramp.from); check(ramp.to); }
    };
    validateLane(outputLane, [](double value) { range(value, 0, 1); });
    double maxDelay = p.delaySeconds;
    const std::array<double EffectSettings::*, 23> supported = {
      &EffectSettings::inputGain, &EffectSettings::sendGain, &EffectSettings::dryGain,
      &EffectSettings::wetGain, &EffectSettings::centerHz, &EffectSettings::bandwidthOctaves,
      &EffectSettings::centerGainDb, &EffectSettings::highpassHz, &EffectSettings::highpassResonanceDb,
      &EffectSettings::lowpassHz, &EffectSettings::lowpassResonanceDb, &EffectSettings::delaySeconds,
      &EffectSettings::delayLowpassHz, &EffectSettings::delayWetPercent, &EffectSettings::delayFeedbackPercent,
      &EffectSettings::reverbGainDb, &EffectSettings::reverbWetPercent,
      &EffectSettings::reverbLowDecaySeconds, &EffectSettings::reverbHighDecaySeconds,
      &EffectSettings::wetHighpassHz, &EffectSettings::wetHighpassResonanceDb,
      &EffectSettings::wetLowpassHz, &EffectSettings::wetLowpassResonanceDb};
    for (std::size_t i = 0; i < automation.size(); ++i) {
      const auto& binding = automation[i];
      if (std::find(supported.begin(), supported.end(), binding.parameter) == supported.end())
        throw std::invalid_argument("Unsupported automated effect parameter");
      for (std::size_t j = 0; j < i; ++j)
        if (binding.parameter == automation[j].parameter)
          throw std::invalid_argument("Duplicate effect automation");
      validateLane(binding.lane, [&](double value) {
        auto candidate = p; candidate.*binding.parameter = value; validate(candidate);
        if (binding.parameter == &EffectSettings::delaySeconds) maxDelay = std::max(maxDelay, value);
      });
      if (binding.parameter == &EffectSettings::delaySeconds) variableDelay = true;
    }
    delayFrames = variableDelay ? std::max(1., p.delaySeconds * rate) : samples(p.delaySeconds, rate);
    const auto capacity = variableDelay ? static_cast<std::size_t>(std::ceil(maxDelay * rate)) + 1
                                        : samples(p.delaySeconds, rate);
    channels.reserve(count);
    for (std::size_t i = 0; i < count; ++i) channels.emplace_back(rate, p, capacity);
  }
  void update(Frame frame) noexcept {
    const auto old = p;
    for (const auto& binding : automation) p.*binding.parameter = binding.lane.valueAt(frame);
    outputGain = outputLane.valueAt(frame);
    delayWet = p.delayWetPercent / 100; feedback = p.delayFeedbackPercent / 100;
    reverbWet = p.reverbWetPercent / 100;
    if (p.reverbGainDb != old.reverbGainDb) reverbGain = std::pow(10., p.reverbGainDb / 20);
    if (variableDelay) delayFrames = std::max(1., p.delaySeconds * rate);
    const bool center = p.centerHz != old.centerHz || p.centerGainDb != old.centerGainDb ||
                        p.bandwidthOctaves != old.bandwidthOctaves;
    const bool hp = p.highpassHz != old.highpassHz || p.highpassResonanceDb != old.highpassResonanceDb;
    const bool lp = p.lowpassHz != old.lowpassHz || p.lowpassResonanceDb != old.lowpassResonanceDb;
    const bool whp = p.wetHighpassHz != old.wetHighpassHz || p.wetHighpassResonanceDb != old.wetHighpassResonanceDb;
    const bool wlp = p.wetLowpassHz != old.wetLowpassHz || p.wetLowpassResonanceDb != old.wetLowpassResonanceDb;
    const auto centerCoeffs = center ? peak(rate, p) : Biquad{};
    const auto hpCoeffs = hp ? filter(rate, p.highpassHz, p.highpassResonanceDb, true) : Biquad{};
    const auto lpCoeffs = lp ? filter(rate, p.lowpassHz, p.lowpassResonanceDb, false) : Biquad{};
    const auto whpCoeffs = whp ? filter(rate, p.wetHighpassHz, p.wetHighpassResonanceDb, true) : Biquad{};
    const auto wlpCoeffs = wlp ? filter(rate, p.wetLowpassHz, p.wetLowpassResonanceDb, false) : Biquad{};
    const bool delayFilter = p.delayLowpassHz != old.delayLowpassHz;
    const double pole = delayFilter ? std::exp(-2 * pi * frequency(p.delayLowpassHz, rate) / rate) : 0;
    for (auto& c : channels) {
      if (center) c.center.coefficients(centerCoeffs);
      if (hp) c.hp.coefficients(hpCoeffs);
      if (lp) c.lp.coefficients(lpCoeffs);
      if (whp) c.wetHp.coefficients(whpCoeffs);
      if (wlp) c.wetLp.coefficients(wlpCoeffs);
      if (delayFilter) c.delayPole = pole;
      for (auto& comb : c.combs) {
        if (p.reverbLowDecaySeconds != old.reverbLowDecaySeconds)
          comb.lowFeedback = std::pow(.001, comb.ring.data.size() / (rate * p.reverbLowDecaySeconds));
        if (p.reverbHighDecaySeconds != old.reverbHighDecaySeconds)
          comb.highFeedback = std::pow(.001, comb.ring.data.size() / (rate * p.reverbHighDecaySeconds));
      }
    }
  }
  double tick(Channel& c, double x) noexcept {
    x *= p.inputGain;
    if (p.bypass) return x * (p.dryGain + p.sendGain * p.wetGain) * outputGain;
    x = c.lp.tick(c.hp.tick(c.center.tick(x)));
    const double send = x * p.sendGain;
    const double delayed = variableDelay ? c.delay.read(delayFrames) : c.delay.read();
    c.delayLow = flush((1 - c.delayPole) * delayed + c.delayPole * c.delayLow);
    c.delay.write(send + feedback * c.delayLow);
    const double delayOut = send * (1 - delayWet) + c.delayLow * delayWet;
    double tail = 0;
    for (auto& comb : c.combs) tail += comb.tick(delayOut);
    const double reverbOut = delayOut * (1 - reverbWet) + tail * .25 * reverbGain * reverbWet;
    const double wet = c.wetLp.tick(c.wetHp.tick(reverbOut));
    return (x * p.dryGain + wet * p.wetGain) * outputGain;
  }
};
TrackEffects::TrackEffects(std::uint32_t rate, std::size_t channels,
                           EffectSettings settings, double outputGain)
    : TrackEffects(rate, channels, settings, AutomationLane(outputGain), {}) {}
TrackEffects::TrackEffects(std::uint32_t rate, std::size_t channels, EffectSettings settings,
                           AutomationLane outputGain, std::vector<EffectAutomation> automation)
    : impl_(std::make_unique<Impl>(rate, channels, settings, std::move(outputGain), std::move(automation))) {}
TrackEffects::~TrackEffects() = default;
bool TrackEffects::process(const float* input, float* output, std::size_t frames) noexcept {
  return process(input, output, frames, impl_->nextFrame);
}
bool TrackEffects::process(const float* input, float* output, std::size_t frames, Frame firstFrame) noexcept {
  const auto count = impl_->channels.size();
  if (firstFrame < 0 || firstFrame != impl_->nextFrame) return false;
  if (frames == 0) return true;
  if (!output || frames > std::numeric_limits<std::size_t>::max() / count ||
      frames > static_cast<std::uint64_t>(std::numeric_limits<Frame>::max() - firstFrame)) return false;
  for (std::size_t frame = 0; frame < frames; ++frame) {
    impl_->update(firstFrame + static_cast<Frame>(frame));
    for (std::size_t ch = 0; ch < count; ++ch) {
      const auto offset = frame * count + ch;
      output[offset] = static_cast<float>(impl_->tick(impl_->channels[ch], input ? input[offset] : 0));
    }
  }
  impl_->nextFrame += static_cast<Frame>(frames);
  return true;
}
bool TrackEffects::reset(Frame firstFrame) noexcept {
  if (firstFrame < 0) return false;
  for (auto& channel : impl_->channels) channel.reset();
  impl_->nextFrame = firstFrame;
  return true;
}

}  // namespace lmg::automix
