#include "lmg/automix/effects.h"
#include "lmg/automix/transition.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <new>
#include <stdexcept>
#include <vector>

namespace { bool watching = false; std::size_t allocations = 0; }
void* operator new(std::size_t size) {
  if (watching) ++allocations;
  if (void* p = std::malloc(size ? size : 1)) return p;
  throw std::bad_alloc();
}
void* operator new[](std::size_t n) { return ::operator new(n); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error("line " + std::to_string(__LINE__) + ": " #x); } while (false)
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
double energy(const std::vector<float>& x, std::size_t start = 0) {
  double sum = 0;
  for (std::size_t i = start; i < x.size(); ++i) {
    CHECK(std::isfinite(x[i])); sum += double(x[i]) * x[i];
  }
  return sum;
}
void bypassAndValidation() {
  EffectSettings p;
  p.inputGain = .5; p.dryGain = .5; p.sendGain = .5; p.wetGain = 1;
  TrackEffects graph(48000, 2, p, .5);
  float x[] = {1, -1, .5, -.5};
  CHECK(graph.process(x, x, 2));
  CHECK(x[0] == .25f && x[1] == -.25f && x[2] == .125f);
  CHECK(graph.process(nullptr, nullptr, 0));
  CHECK(!graph.process(x, nullptr, 1));
  CHECK(!graph.process(x, x, std::numeric_limits<std::size_t>::max()));
  CHECK(x[0] == .25f);
  rejects([&] { TrackEffects g(0, 2, p, 1); });
  rejects([&] { TrackEffects g(48000, 0, p, 1); });
  rejects([&] { TrackEffects g(48000, 2, p, -1); });
  p.delayFeedbackPercent = 100;
  rejects([&] { TrackEffects g(48000, 2, p, 1); });
  p.delayFeedbackPercent = 50; p.centerHz = std::numeric_limits<double>::quiet_NaN();
  rejects([&] { TrackEffects g(48000, 2, p, 1); });
  p.centerHz = 2500; p.reverbMinSeconds = .1; p.reverbMaxSeconds = .01;
  rejects([&] { TrackEffects g(48000, 2, p, 1); });
}
double toneEnergy(EffectSettings p, double hz) {
  TrackEffects graph(48000, 1, p, 1);
  std::vector<float> x(12000);
  for (std::size_t i = 0; i < x.size(); ++i) x[i] = .1 * std::sin(2 * 3.141592653589793 * hz * i / 48000);
  CHECK(graph.process(x.data(), x.data(), x.size()));
  return energy(x, 6000);
}
void filters() {
  EffectSettings p; p.bypass = false;
  const auto baseline = toneEnergy(p, 1000);
  p.centerHz = 1000; p.centerGainDb = 6;
  CHECK(std::abs(toneEnergy(p, 1000) / baseline - std::pow(10., .6)) < .002);
  p.centerGainDb = 0; p.lowpassHz = 500;
  CHECK(toneEnergy(p, 5000) < toneEnergy(p, 100) * .001);
  p.lowpassHz = 22000; p.highpassHz = 2000;
  CHECK(toneEnergy(p, 100) < toneEnergy(p, 8000) * .001);
}
void delayAndFeedback(std::uint32_t rate) {
  EffectSettings p; p.bypass = false; p.dryGain = 0; p.wetGain = 1;
  p.delayWetPercent = 100; p.delaySeconds = .01; p.delayFeedbackPercent = 0;
  const std::size_t d = rate / 100;
  std::vector<float> x(d * 5), a(x.size()), b(x.size()), negative(x.size()); x[0] = 1;
  TrackEffects plain(rate, 1, p, 1);
  CHECK(plain.process(x.data(), a.data(), x.size()));
  CHECK(std::all_of(a.begin(), a.begin() + d, [](float v) { return v == 0; }));
  CHECK(a[d] > 0 && energy(a) > .001);
  p.delayFeedbackPercent = 50; TrackEffects feedback(rate, 1, p, 1);
  CHECK(feedback.process(x.data(), b.data(), x.size()));
  p.delayFeedbackPercent = -50; TrackEffects reverse(rate, 1, p, 1);
  CHECK(reverse.process(x.data(), negative.data(), x.size()));
  CHECK(std::equal(a.begin(), a.begin() + 2 * d, b.begin()));
  CHECK(b[2 * d] > a[2 * d]);
  CHECK(negative[2 * d] < a[2 * d]);
  plain.reset(); CHECK(plain.process(nullptr, a.data(), a.size())); CHECK(energy(a) == 0);
}
void reverbAndIsolation(std::uint32_t rate) {
  EffectSettings p; p.bypass = false; p.dryGain = 0; p.wetGain = 1;
  p.reverbWetPercent = 100; p.reverbLowDecaySeconds = .2; p.reverbHighDecaySeconds = .1;
  TrackEffects whole(rate, 2, p, 1), split(rate, 2, p, 1), other(rate, 2, p, 1);
  std::vector<float> x(rate * 2), a(x.size()), b(x.size()), silence(x.size()); x[0] = 1;
  CHECK(whole.process(x.data(), a.data(), rate));
  for (std::size_t i = 0; i < rate;) {
    const auto n = std::min<std::size_t>(1 + i % 257, rate - i);
    CHECK(split.process(x.data() + 2 * i, b.data() + 2 * i, n)); i += n;
  }
  CHECK(a == b);
  for (std::size_t i = 1; i < a.size(); i += 2) CHECK(a[i] == 0);
  CHECK(energy(a, rate / 5) > 1e-10);
  CHECK(energy(a, rate) < energy(a) * .001);
  CHECK(other.process(nullptr, silence.data(), rate)); CHECK(energy(silence) == 0);
  whole.reset(); CHECK(whole.process(nullptr, b.data(), rate)); CHECK(energy(b) == 0);
  whole.reset(); CHECK(whole.process(x.data(), b.data(), rate)); CHECK(a == b);
  // Actual callback path including active effects and tail draining, then reset.
  watching = true;
  const bool ok = whole.process(nullptr, b.data(), rate);
  whole.reset();
  watching = false;
  CHECK(ok && allocations == 0);
}
void automationTimeline() {
  EffectSettings p;
  TrackEffects gain(48000, 1, p, AutomationLane(0, {{0, 4, 0, 1, 0x80}}),
                    {{&EffectSettings::inputGain, AutomationLane(.5)}});
  float x[] = {1, 1, 1, 1, 1}, out[5] = {};
  CHECK(gain.process(x, out, 5, 0));
  for (int i = 0; i < 5; ++i) CHECK(out[i] == i * .125f);
  CHECK(!gain.process(x, out, 1, 0)); CHECK(out[0] == 0);
  CHECK(!gain.reset(-1));
  CHECK(gain.reset(2)); CHECK(gain.process(x, out, 1, 2)); CHECK(out[0] == .25f);
  CHECK(gain.reset(std::numeric_limits<Frame>::max()));
  CHECK(!gain.process(x, out, 1)); CHECK(out[0] == .25f);
  rejects([&] { TrackEffects g(48000, 1, p, AutomationLane(1),
    {{&EffectSettings::reverbMinSeconds, AutomationLane(.01)}}); });
  rejects([&] { TrackEffects g(48000, 1, p, AutomationLane(1),
    {{nullptr, AutomationLane(1)}}); });
  rejects([&] { TrackEffects g(48000, 1, p, AutomationLane(1),
    {{&EffectSettings::wetGain, AutomationLane(0)}, {&EffectSettings::wetGain, AutomationLane(1)}}); });
  rejects([&] { TrackEffects g(48000, 1, p, AutomationLane(1),
    {{&EffectSettings::delaySeconds, AutomationLane(1, {{0, 2, 1, 3, 0x80}})}}); });
}
void automatedEffects(std::uint32_t rate) {
  EffectSettings p; p.bypass = false; p.wetGain = .5;
  const Frame end = 4096;
  std::vector<EffectAutomation> lanes = {
    {&EffectSettings::lowpassHz, AutomationLane(22000, {{0, end, 22000, 200, 0x81}})},
    {&EffectSettings::centerGainDb, AutomationLane(0, {{0, end, 0, -12, 0x80}})},
    {&EffectSettings::delaySeconds, AutomationLane(.001, {{0, end, .001, .005, 0x80}})},
    {&EffectSettings::delayWetPercent, AutomationLane(50)},
    {&EffectSettings::delayLowpassHz, AutomationLane(500, {{0, end, 500, 5000, 0x81}})},
    {&EffectSettings::reverbWetPercent, AutomationLane(70)},
    {&EffectSettings::reverbLowDecaySeconds, AutomationLane(.1, {{0, end, .1, 1, 0x80}})},
    {&EffectSettings::reverbHighDecaySeconds, AutomationLane(.05, {{0, end, .05, .5, 0x80}})},
    {&EffectSettings::reverbGainDb, AutomationLane(0, {{0, end, 0, -6, 0x80}})}};
  TrackEffects a(rate, 2, p, AutomationLane(1), lanes), b(rate, 2, p, AutomationLane(1), lanes);
  std::vector<float> input(end * 2), whole(input.size()), split(input.size());
  for (Frame i = 0; i < end; ++i) input[i * 2] = .1 * std::sin(i * .3);
  watching = true;
  const bool ok = a.process(input.data(), whole.data(), end);
  watching = false;
  CHECK(ok && allocations == 0);
  for (Frame i = 0; i < end;) {
    const auto n = std::min<Frame>(1 + i % 133, end - i);
    CHECK(b.process(input.data() + i * 2, split.data() + i * 2, n, i)); i += n;
  }
  CHECK(whole == split); CHECK(energy(whole) > .001);
  for (std::size_t i = 1; i < whole.size(); i += 2) CHECK(whole[i] == 0);
  CHECK(a.reset()); CHECK(a.process(input.data(), split.data(), end)); CHECK(whole == split);
  TrackEffects fresh(rate, 2, p, AutomationLane(1), lanes);
  CHECK(a.reset(1234)); CHECK(fresh.reset(1234));
  CHECK(a.process(input.data(), whole.data(), end));
  CHECK(fresh.process(input.data(), split.data(), end)); CHECK(whole == split);
}
void fractionalDelay() {
  EffectSettings p; p.bypass = false; p.dryGain = 0; p.wetGain = 1;
  p.delayWetPercent = 100; p.delayFeedbackPercent = 0; p.delaySeconds = 10. / 48000;
  TrackEffects ten(48000, 1, p, 1);
  p.delaySeconds = 11. / 48000; TrackEffects eleven(48000, 1, p, 1);
  TrackEffects fractional(48000, 1, p, AutomationLane(1),
    {{&EffectSettings::delaySeconds, AutomationLane(10.5 / 48000)}});
  std::vector<float> input(200), a(200), b(200), c(200); input[0] = 1;
  CHECK(ten.process(input.data(), a.data(), input.size()));
  CHECK(eleven.process(input.data(), b.data(), input.size()));
  CHECK(fractional.process(input.data(), c.data(), input.size()));
  for (std::size_t i = 0; i < c.size(); ++i) CHECK(std::abs(c[i] - (a[i] + b[i]) * .5) < 1e-7);
}
DeckSchedule deck(double gain) {
  return {EffectSettings{}, {{"out_gain", AutomationLane(gain)}}};
}
void transitionValidation() {
  CHECK(styleCurveByte("ease-out-4") == 0x42);
  CHECK(styleCurveByte("logarithmic") == 0x81);
  rejects([] { styleCurveByte("unknown"); });
  rejects([] { TransitionRenderer r(48000, 2, DeckSchedule{}, deck(1)); });
  for (const auto& id : {"unknown", "ts_rate", "bypa", "fx_reverb_randomize_reflections"}) {
    auto d = deck(1); d.parameters.push_back({id, AutomationLane(1.5)});
    rejects([&] { TransitionRenderer r(48000, 2, d, deck(1)); });
  }
  auto d = deck(1); d.parameters.push_back({"out_gain", AutomationLane(0)});
  rejects([&] { TransitionRenderer r(48000, 2, d, deck(1)); });
  d = deck(1); d.parameters.push_back({"fx_reverb_min_delay_time", AutomationLane(.01, {{0, 10, .01, .02, 0x80}})});
  rejects([&] { TransitionRenderer r(48000, 2, d, deck(1)); });
  rejects([] { TransitionRenderer r(48000, 2, deck(1), deck(1), 0); });
}
void transitionExecution() {
  constexpr std::size_t n = 2048;
  auto out = deck(1), in = deck(0);
  out.parameters[0].lane = AutomationLane(1, {{0, n, 1, 0, styleCurveByte("linear")}});
  in.parameters[0].lane = AutomationLane(0, {{0, n, 0, 1, styleCurveByte("linear")}});
  out.parameters.push_back({"ts_rate", AutomationLane(1)});
  TransitionRenderer whole(48000, 2, out, in, 37), split(48000, 2, out, in, 256);
  std::vector<float> a(n * 2, .25), b(n * 2, -.5), expected(n * 2), actual(n * 2);
  CHECK(whole.render(a.data(), b.data(), expected.data(), n, 0));
  for (std::size_t i = 0; i < n; ++i)
    CHECK(expected[i * 2] == static_cast<float>(.25 - .75 * i / n));
  watching = true;
  bool ok = true;
  for (std::size_t i = 0; i < n;) {
    const auto block = std::min<std::size_t>(1 + i % 79, n - i);
    ok = split.render(a.data() + 2 * i, b.data() + 2 * i, actual.data() + 2 * i, block, i) && ok;
    i += block;
  }
  watching = false;
  CHECK(ok && allocations == 0 && expected == actual);
  CHECK(!whole.render(a.data(), b.data(), actual.data(), 1, 0)); CHECK(actual == expected);
  CHECK(whole.reset()); CHECK(whole.render(a.data(), b.data(), a.data(), n, 0)); CHECK(a == expected);
  CHECK(!whole.reset(-1));
  CHECK(whole.reset(std::numeric_limits<Frame>::max()));
  CHECK(!whole.render(nullptr, nullptr, actual.data(), 1, std::numeric_limits<Frame>::max()));
  CHECK(actual == expected);
}
void transitionEffectMapping() {
  auto out = deck(.5), in = deck(0);
  out.parameters.push_back({"bypa", AutomationLane(0)});
  auto sweep = AutomationLane(20000, {{0, 1024, 20000, 1000, 0x81}});
  out.parameters.push_back({"lp_cutoff_freq", sweep});
  out.parameters.push_back({"fx_reverb_min_delay_time", AutomationLane(.06)});
  out.parameters.push_back({"fx_reverb_max_delay_time", AutomationLane(.08)});
  TransitionRenderer renderer(44100, 1, out, in, 17);
  EffectSettings p; p.bypass = false; p.reverbMinSeconds = .06; p.reverbMaxSeconds = .08;
  TrackEffects reference(44100, 1, p, AutomationLane(.5), {{&EffectSettings::lowpassHz, sweep}});
  std::vector<float> input(2048), a(2048), b(2048); input[0] = 1;
  CHECK(renderer.render(input.data(), nullptr, a.data(), input.size(), 0));
  CHECK(reference.process(input.data(), b.data(), input.size())); CHECK(a == b);
  CHECK(renderer.reset(500)); CHECK(reference.reset(500));
  CHECK(renderer.render(input.data(), nullptr, a.data(), input.size(), 500));
  CHECK(reference.process(input.data(), b.data(), input.size())); CHECK(a == b);
}
int main() {
  try {
    bypassAndValidation(); filters(); automationTimeline(); fractionalDelay();
    transitionValidation(); transitionExecution(); transitionEffectMapping();
    for (auto rate : {44100u, 48000u}) { delayAndFeedback(rate); reverbAndIsolation(rate); automatedEffects(rate); }
    std::cout << "13 effect/transition test groups passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
