#include "lmg/automix/graph.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <new>
#include <stdexcept>
#include <vector>
namespace { bool watching = false; std::size_t allocations = 0; }
void* operator new(std::size_t n) {
  if (watching) ++allocations;
  if (void* p = std::malloc(n ? n : 1)) return p;
  throw std::bad_alloc();
}
void* operator new[](std::size_t n) { return ::operator new(n); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)

void bypassRouting() {
  EffectSettings p;
  p.inputGain = .5; p.sendGain = .25; p.dryGain = .75; p.wetGain = .5;
  PreparedGraph prepared(48000, p);
  TrackGraph graph(prepared, 2, 256), alias(prepared, 2, 256);
  std::vector<float> x(512), y(512);
  for (unsigned i = 0; i < x.size(); ++i) x[i] = (int((i * 37) % 251) - 125) / 256.0f;
  CHECK(graph.process(x.data(), y.data(), 256));
  for (unsigned i = 0; i < x.size(); ++i) CHECK(y[i] == x[i] * .4375f);
  CHECK(alias.process(x.data(), x.data(), 256)); CHECK(x == y);
  CHECK(graph.process(nullptr, y.data(), 256));
  CHECK(std::all_of(y.begin(), y.end(), [](float v) { return v == 0; }));
}

void stereoTankAndIsolation() {
  EffectSettings p; p.bypass = false; p.dryGain = 0; p.wetGain = 1;
  p.reverbWetPercent = 100; p.reverbMinSeconds = .0001; p.reverbMaxSeconds = .003;
  PreparedGraph prepared(48000, p);
  TrackGraph active(prepared, 2, 256), independent(prepared, 2, 256), alias(prepared, 2, 256);
  std::vector<float> input(512), output(512), zero(512), inPlace(512);
  input[0] = inPlace[0] = 1;
  watching = true;
  const bool processed = active.process(input.data(), output.data(), 256);
  watching = false;
  CHECK(processed && allocations == 0);
  CHECK(alias.process(inPlace.data(), inPlace.data(), 256)); CHECK(inPlace == output);
  double rightEnergy = 0;
  for (unsigned i = 1; i < output.size(); i += 2) rightEnergy += std::abs(output[i]);
  CHECK(rightEnergy > .01); // stereo tank mixes mono send into both wet channels
  CHECK(independent.process(nullptr, zero.data(), 256));
  CHECK(std::all_of(zero.begin(), zero.end(), [](float v) { return v == 0; }));
  CHECK(active.process(nullptr, output.data(), 256));
  CHECK(std::any_of(output.begin(), output.end(), [](float v) { return v != 0; }));
  // Removing reverb's wet contribution restores independent per-channel delays.
  p.reverbWetPercent = 0;
  TrackGraph separate(PreparedGraph(48000, p), 2, 256);
  CHECK(separate.process(input.data(), output.data(), 256));
  for (unsigned i = 1; i < output.size(); i += 2) CHECK(output[i] == 0);
}

void updatesAndRejection() {
  EffectSettings settings;
  PreparedGraph initial(48000, settings);
  TrackGraph graph(initial, 2, 1024), reference(initial, 2, 1024);
  std::vector<float> x(2048, 1), y(2048, 7), z(2048);
  CHECK(!graph.process(x.data(), y.data(), 1025));
  CHECK(!graph.process(x.data(), nullptr, 1));
  CHECK(y.front() == 7 && y.back() == 7);
  CHECK(graph.process(x.data(), y.data(), 1));
  CHECK(reference.process(x.data(), z.data(), 1)); CHECK(y[0] == z[0]);
  settings.inputGain = 0;
  PreparedGraph muted(48000, settings);
  watching = true;
  const bool applied = graph.apply(muted);
  const bool rendered = graph.process(x.data(), y.data(), 1024);
  watching = false;
  CHECK(applied && rendered && allocations == 0);
  CHECK(y[0] == 1 && y[2046] == 0);
  for (unsigned i = 0; i < 1024; ++i) CHECK(y[2*i] == y[2*i + 1]);
  settings.reverbSeed = 2;
  CHECK(!graph.apply(PreparedGraph(48000, settings))); // atomic rejection
  CHECK(graph.process(x.data(), y.data(), 32));
  for (unsigned i = 0; i < 64; ++i) CHECK(y[i] == 0);
  CHECK(graph.process(nullptr, nullptr, 0));
  bool rejected = false;
  try { TrackGraph invalid(initial, 3, 256); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}

void bypassResetsChannelEffects() {
  for (bool wetPath : {false, true}) {
    EffectSettings s; s.bypass = false;
    s.lowpassHz = 500; s.wetLowpassHz = 800;
    s.dryGain = wetPath ? 0 : 1; s.wetGain = wetPath ? 1 : 0;
    s.delayWetPercent = 100; s.delaySeconds = .001; s.delayFeedbackPercent = 90;
    const PreparedGraph active(48000, s);
    TrackGraph toggled(active, 1, 256), running(active, 1, 256), fresh(active, 1, 256);
    std::array<float, 256> input{}, a{}, b{}, expected{};
    input[0] = 1;
    CHECK(toggled.process(input.data(), a.data(), input.size()));
    CHECK(running.process(input.data(), b.data(), input.size()));
    s.bypass = true;
    const PreparedGraph bypass(48000, s);
    watching = true;
    const bool changed = toggled.apply(bypass) && toggled.apply(active);
    watching = false;
    CHECK(changed && allocations == 0);
    input.fill(0);
    CHECK(toggled.process(input.data(), a.data(), input.size()));
    CHECK(fresh.process(input.data(), expected.data(), input.size()));
    CHECK(a == expected);
    CHECK(running.apply(active)); // equal bypass write must NOT reset
    CHECK(running.process(input.data(), b.data(), input.size()));
    CHECK(std::any_of(b.begin(), b.end(), [](float x) { return x != 0; }));
  }
}

int main() {
  try {
    bypassRouting(); stereoTankAndIsolation(); updatesAndRejection(); bypassResetsChannelEffects();
    std::cout << "Graph routing, independent track state, stereo tails and realtime updates passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
