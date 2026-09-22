#include "lmg/automix/mixer.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <new>
#include <stdexcept>
#include <vector>

namespace {
bool watchAllocations = false;
std::size_t allocations = 0;
}
void* operator new(std::size_t size) {
  if (watchAllocations) ++allocations;
  if (auto p = std::malloc(size ? size : 1)) return p;
  throw std::bad_alloc();
}
void* operator new[](std::size_t size) { return ::operator new(size); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }

using namespace lmg::automix;
#define CHECK(condition) do { if (!(condition)) throw std::runtime_error( \
    "line " + std::to_string(__LINE__) + ": " #condition); } while (false)

namespace {
void near(double actual, double expected, double epsilon = 1e-10) {
  CHECK(std::isfinite(actual));
  CHECK(std::abs(actual - expected) <= epsilon);
}
template<class F> void rejects(F action) {
  bool rejected = false;
  try { action(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
TrackGainPlan fixed(double gain) { return TrackGainPlan(AutomationLane(gain)); }

void curveFamilies() {
  for (unsigned curve = 0; curve < 256; ++curve) {
    Ramp ramp{0, 4, 0.0, 1.0, static_cast<std::uint8_t>(curve)};
    if (curve == 0x81) continue;
    ramp.validate();
    double expected = 0.25;
    if (curve == 0) expected = 1 - std::sqrt(0.75);
    else if (curve == 1) expected = 0.0625;
    else if (curve < 0x40) expected = 0.00390625;
    else if (curve == 0x40) expected = 0.5;
    else if (curve == 0x41) expected = 0.4375;
    else if (curve < 0x80) expected = 0.68359375;
    near(ramp.valueAt(1), expected);
    near(ramp.valueAt(-1), 0);
    near(ramp.valueAt(4), 1);
  }
}

void logarithmicValues() {
  Ramp ramp{10, 14, 100, 1600, 0x81};
  ramp.validate();
  near(ramp.valueAt(10), 100);
  near(ramp.valueAt(12), 400);
  near(ramp.valueAt(14), 1600);
  Ramp down{0, 2, 1.0, 0.0625, 0x81};
  near(down.valueAt(1), 0.25);
  rejects([] { Ramp{0, 10, 0, 1, 0x81}.validate(); });
  rejects([] { Ramp{0, 10, -1, 1, 0x81}.validate(); });
}

void boundariesAndSteps() {
  AutomationLane lane(0.2, {{20, 24, 0.7, 1, 0x80}, {10, 14, 0.2, 0.7, 0x80}});
  near(lane.valueAt(0), 0.2);
  near(lane.valueAt(12), 0.45);
  near(lane.valueAt(19), 0.7);
  near(lane.valueAt(100), 1);
  near(lane.valueAt(12), 0.45);  // Seeking backwards is deterministic.
  AutomationLane step(0.0, {{5, 5, 0, 1, 0x80}, {6, 8, 1, 0, 0x80}});
  near(step.valueAt(4), 0);
  near(step.valueAt(5), 1);
  near(step.valueAt(7), 0.5);
  AutomationLane adjacent(0, {{0, 2, 0, 1, 0x80}, {2, 4, 1, 0, 0x80}});
  near(adjacent.valueAt(2), 1);
}

void invalidSchedules() {
  rejects([] { AutomationLane lane(0, {{0, 5, 0, 1, 0x80}, {4, 8, 1, 0, 0x80}}); });
  rejects([] { AutomationLane lane(0, {{5, 5, 0, 1, 0x80}, {5, 5, 1, 0, 0x80}}); });
  rejects([] { Ramp{5, 2, 0, 1, 0x80}.validate(); });
  rejects([] { Ramp{-1, 2, 0, 1, 0x80}.validate(); });
  rejects([] { AutomationLane lane(std::numeric_limits<double>::quiet_NaN()); });
  rejects([] { Ramp{0, 2, 0, std::numeric_limits<double>::infinity(), 0x80}.validate(); });
  rejects([] { auto plan = fixed(1.01); });
  rejects([] { TrackGainPlan plan(AutomationLane(0, {{0, 2, 0, -1, 0x80}})); });
}

void frameConversion() {
  CHECK(secondsToFrame(18, 48000) == 864000);
  CHECK(secondsToFrame(18, 44100) == 793800);
  CHECK(secondsToFrame(0.5, 1) == 1);
  rejects([] { secondsToFrame(-1, 48000); });
  rejects([] { secondsToFrame(1, 0); });
  rejects([] { secondsToFrame(std::numeric_limits<double>::infinity(), 48000); });
  bool overflow = false;
  try { secondsToFrame(std::ldexp(1.0, 63), 1); }
  catch (const std::overflow_error&) { overflow = true; }
  CHECK(overflow);
  const Frame start = (Frame{1} << 54);
  Ramp ramp{start, start + 4, 0, 1, 0x80};
  near(ramp.valueAt(start + 1), 0.25);
}

void independentDecksAndChannels() {
  TwoDeckMixer mixer(2, fixed(0.25), fixed(0.75));
  float a[]{1, -1, 0.5, -0.5}, b[]{0.4f, 0.8f, -0.4f, -0.8f}, output[4]{};
  CHECK(mixer.render(a, b, output, 2, 0));
  near(output[0], 0.55, 1e-7);
  near(output[1], 0.35, 1e-7);
  near(output[2], -0.175, 1e-7);
  near(output[3], -0.725, 1e-7);
}

void bypassedGraphGains() {
  TrackGainPlan plan(AutomationLane(0.5), AutomationLane(0.8), AutomationLane(0.25),
                     AutomationLane(0.3), AutomationLane(0.6));
  near(plan.gainAt(0), 0.18);
  near(fixed(1).gainAt(0), 1);
  near(fixed(0).gainAt(0), 0);
}

void silenceAliasingAndHeadroom() {
  TwoDeckMixer mixer(1, fixed(1), fixed(1));
  float a[]{0.75f, -0.75f}, b[]{0.75f, -0.75f};
  CHECK(mixer.render(a, b, a, 2, 0));
  near(a[0], 1.5); near(a[1], -1.5);  // No hidden limiter.
  CHECK(mixer.render(nullptr, b, b, 2, 0));
  near(b[0], 0.75);
  CHECK(mixer.render(nullptr, nullptr, b, 2, 0));
  near(b[0], 0); near(b[1], 0);
}

void invalidBlocks() {
  rejects([] { TwoDeckMixer mixer(0, fixed(1), fixed(0)); });
  TwoDeckMixer mixer(2, fixed(1), fixed(0));
  float output[]{42, 42};
  CHECK(!mixer.render(nullptr, nullptr, output, 1, -1));
  CHECK(!mixer.render(nullptr, nullptr, nullptr, 1, 0));
  CHECK(!mixer.render(nullptr, nullptr, output, 3, std::numeric_limits<Frame>::max() - 1));
  CHECK(!mixer.render(nullptr, nullptr, output, std::numeric_limits<std::size_t>::max(), 0));
  near(output[0], 42);
  CHECK(mixer.render(nullptr, nullptr, nullptr, 0, 0));
  CHECK(mixer.render(nullptr, nullptr, output, 1, std::numeric_limits<Frame>::max()));
}

void renderWithoutAllocation() {
  TwoDeckMixer mixer(2,
      TrackGainPlan(AutomationLane(1, {{0, 64, 1, 0, 0x00}})),
      TrackGainPlan(AutomationLane(0, {{0, 64, 0, 1, 0x40}})));
  float a[128]{}, b[128]{}, output[128]{};
  allocations = 0;
  watchAllocations = true;
  const bool ok = mixer.render(a, b, output, 64, 0);
  watchAllocations = false;
  CHECK(ok); CHECK(allocations == 0);
}

void eighteenSecondsPartitionInvariant(std::uint32_t rate) {
  const Frame duration = secondsToFrame(18, rate);
  const auto frames = static_cast<std::size_t>(duration + 1);
  std::vector<float> a(frames * 2), b(frames * 2), whole(frames * 2), chunks(frames * 2);
  for (std::size_t i = 0; i < frames; ++i) {
    a[i * 2] = 1; a[i * 2 + 1] = -1;
    b[i * 2] = -0.5; b[i * 2 + 1] = 0.5;
  }
  TwoDeckMixer mixer(2,
      TrackGainPlan(AutomationLane(1, {{0, duration, 1, 0, 0x80}})),
      TrackGainPlan(AutomationLane(0, {{0, duration, 0, 1, 0x80}})));
  CHECK(mixer.render(a.data(), b.data(), whole.data(), frames, 0));
  const std::size_t blockSizes[]{1, 127, 256, 511, 1024};
  for (std::size_t offset = 0, block = 0; offset < frames; ++block) {
    const auto count = std::min(blockSizes[block % 5], frames - offset);
    CHECK(mixer.render(a.data() + 2 * offset, b.data() + 2 * offset,
                       chunks.data() + 2 * offset, count, static_cast<Frame>(offset)));
    offset += count;
  }
  CHECK(whole == chunks);
  near(whole[0], 1); near(whole[1], -1);
  near(whole[duration], 0.25);  // Left channel at midpoint (stereo index = frame * 2).
  near(whole[frames * 2 - 2], -0.5); near(whole.back(), 0.5);
}
}  // namespace

int main() {
  int passed = 0;
  try {
    auto test = [&](auto action) { action(); ++passed; };
    test(curveFamilies);
    test(logarithmicValues);
    test(boundariesAndSteps);
    test(invalidSchedules);
    test(frameConversion);
    test(independentDecksAndChannels);
    test(bypassedGraphGains);
    test(silenceAliasingAndHeadroom);
    test(invalidBlocks);
    test(renderWithoutAllocation);
    test([] { eighteenSecondsPartitionInvariant(44100); });
    test([] { eighteenSecondsPartitionInvariant(48000); });
    std::cout << passed << " test groups passed\n";
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
}
