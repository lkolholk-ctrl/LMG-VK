#include "lmg/automix/scheduled_graph.h"
#include "lmg/automix/scheduling_policy.h"
#include <algorithm>
#include <cstdlib>
#include <iostream>
#include <limits>
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
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}

void boundaries() {
  EffectSettings s;
  PreparedGraph initial(48000, s);
  s.inputGain = .25;
  PreparedGraph quiet(48000, s);
  ScheduledTrackGraph graph(initial, 2, 1024, 100, {{100, quiet}, {101, initial}});
  std::vector<float> x(2048, 1), y(2048, 7);
  CHECK(graph.process(nullptr, nullptr, 0)); CHECK(graph.eventsConsumed() == 0);
  CHECK(!graph.process(x.data(), nullptr, 1));
  CHECK(!graph.process(x.data(), y.data(), 1025));
  CHECK(graph.position() == 100 && graph.eventsConsumed() == 0 && y[0] == 7);
  CHECK(graph.process(x.data(), y.data(), 1));
  CHECK(y[0] == .25f && y[1] == .25f); // initial write before first render
  CHECK(graph.position() == 101 && graph.eventsConsumed() == 1);
  CHECK(graph.process(nullptr, nullptr, 0)); CHECK(graph.eventsConsumed() == 1);
  CHECK(graph.process(x.data(), y.data(), 1024));
  CHECK(y[0] == .25f && y[2046] == 1 && y[2047] == 1);
  CHECK(graph.eventsConsumed() == 2 && graph.position() == 1125);

  const Frame last = std::numeric_limits<Frame>::max();
  ScheduledTrackGraph edge(initial, 1, 8, last - 1, {{last, quiet}});
  CHECK(!edge.process(x.data(), y.data(), 2)); CHECK(edge.position() == last - 1);
  CHECK(edge.process(x.data(), y.data(), 1)); CHECK(edge.position() == last);
  CHECK(!edge.process(x.data(), y.data(), 1)); CHECK(edge.eventsConsumed() == 0);
}

void matchesManualSlices() {
  EffectSettings s; s.bypass = false; s.wetGain = .6; s.dryGain = .4;
  s.reverbWetPercent = 100; s.reverbMinSeconds = .0001; s.reverbMaxSeconds = .003;
  PreparedGraph initial(48000, s);
  s.lowpassHz = 1000;
  PreparedGraph filtered(48000, s);
  std::vector<GraphEvent> events{{31, filtered}, {65, filtered, true}, {127, initial}};
  ScheduledTrackGraph scheduled(initial, 2, 256, 0, events);
  ScheduledTrackGraph alias(initial, 2, 256, 0, events);
  TrackGraph manual(initial, 2, 256);
  std::vector<float> x(512), expected(512), actual(512);
  for (unsigned i = 0; i < x.size(); ++i) x[i] = (int(i * 37 % 251) - 125) / 256.f;
  auto inPlace = x;
  CHECK(manual.process(x.data(), expected.data(), 31));
  CHECK(manual.apply(filtered));
  CHECK(manual.process(x.data()+62, expected.data()+62, 34));
  CHECK(manual.apply(filtered, true)); // repeated decay write must clear tank
  CHECK(manual.process(x.data()+130, expected.data()+130, 62));
  CHECK(manual.apply(initial));
  CHECK(manual.process(x.data()+254, expected.data()+254, 129));
  watching = true;
  const bool rendered = scheduled.process(x.data(), actual.data(), 256);
  const bool aliased = alias.process(inPlace.data(), inPlace.data(), 256);
  watching = false;
  CHECK(rendered && aliased && allocations == 0);
  CHECK(actual == expected && inPlace == expected);
  CHECK(scheduled.eventsConsumed() == 3);
  // Null input continues an existing tail through the same graph state.
  CHECK(manual.process(nullptr, expected.data(), 256));
  CHECK(scheduled.process(nullptr, actual.data(), 256)); CHECK(actual == expected);
  CHECK(std::any_of(actual.begin(), actual.end(), [](float v) { return v != 0; }));
}

void preparationValidation() {
  EffectSettings s; PreparedGraph initial(48000, s);
  rejects([&] { ScheduledTrackGraph g(initial, 1, 16, -1, {}); });
  rejects([&] { ScheduledTrackGraph g(initial, 1, 16, 10, {{9, initial}}); });
  rejects([&] { ScheduledTrackGraph g(initial, 1, 16, 0, {{2, initial}, {1, initial}}); });
  rejects([&] { ScheduledTrackGraph g(initial, 1, 16, 0, {{2, initial}, {2, initial}}); });
  rejects([&] { ScheduledTrackGraph g(initial, 1, 16, 0, {{2, PreparedGraph(44100, s)}}); });
  s.bypass = true; s.reverbSeed = 2;
  rejects([&] { ScheduledTrackGraph g(initial, 1, 16, 0, {{2, PreparedGraph(48000, s)}}); });
}

void parameterBridge() {
  EffectSettings s; s.bypass = false;
  PreparedGraph initial(48000, s);
  const auto events = prepareGraphEvents(48000, s,
      {{0, "Ga1g", .1}, {0, "LP1f", 1234.56789}, {5, "RVhf", .5}, {7, "Ga3g", .75}});
  CHECK(events.size() == 3 && !events[0].reverbDecayWritten && events[1].reverbDecayWritten);
  CHECK(!events[2].reverbDecayWritten);
  ScheduledTrackGraph scheduled(initial, 1, 64, 0, events);
  s.inputGain = float(.1); s.lowpassHz = float(1234.56789);
  TrackGraph manual(PreparedGraph(48000, s), 1, 64);
  std::vector<float> x(64, 1), y(64), z(64);
  CHECK(manual.process(x.data(), z.data(), 5));
  CHECK(manual.apply(PreparedGraph(48000, s), true));
  CHECK(manual.process(x.data()+5, z.data()+5, 2));
  s.dryGain = .75;
  CHECK(manual.apply(PreparedGraph(48000, s)));
  CHECK(manual.process(x.data()+7, z.data()+7, 57));
  CHECK(scheduled.process(x.data(), y.data(), 64)); CHECK(y == z);
  for (const char* id : {"out_gain", "ts_rate", "RVmi", "RVma", "RVrr", "????"})
    rejects([&] { prepareGraphEvents(48000, s, {{0, id, 1}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{0, "Ga1g", .1}, {0, "Ga1g", .2}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{2, "Ga1g", .1}, {1, "Ga3g", .2}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{-1, "Ga1g", .1}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{0, "Ga1g", 2}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{0, "DLdt", std::numeric_limits<double>::infinity()}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{0, "LP1f", std::numeric_limits<double>::max()}}); });
  rejects([&] { prepareGraphEvents(0, s, {}); });
  rejects([&] { prepareGraphEvents(48000, s, {{0, "bypa", .5}}); });
  rejects([&] { prepareGraphEvents(48000, s, {{0, "bypa", 0}, {0, "bypa", 1}}); });
}

void bypassSchedule() {
  EffectSettings s; s.lowpassHz = 400;
  PreparedGraph initial(48000, s);
  const auto events = prepareGraphEvents(48000, s,
      {{7, "bypa", 0}, {39, "bypa", 1}, {63, "bypa", 0}, {95, "bypa", 1}});
  ScheduledTrackGraph scheduled(initial, 2, 128, 0, events);
  TrackGraph manual(initial, 2, 128);
  std::vector<float> input(256), actual(256), expected(256);
  for (unsigned i = 0; i < input.size(); ++i) input[i] = (int(i % 13) - 6) / 8.f;
  unsigned offset = 0;
  for (const auto& event : events) {
    const auto n = unsigned(event.frame) - offset;
    CHECK(manual.process(input.data() + offset * 2, expected.data() + offset * 2, n));
    CHECK(manual.apply(event.configuration));
    offset = unsigned(event.frame);
  }
  CHECK(manual.process(input.data() + offset * 2, expected.data() + offset * 2, 128 - offset));
  watching = true;
  const bool ok = scheduled.process(input.data(), actual.data(), 128);
  watching = false;
  CHECK(ok && allocations == 0 && actual == expected && scheduled.eventsConsumed() == 4);
  for (unsigned i = 0; i < 128; ++i) {
    if (i < 7 || (i >= 39 && i < 63) || i >= 95) {
      CHECK(actual[2*i] == input[2*i]); CHECK(actual[2*i+1] == input[2*i+1]);
    }
  }
}
void recoveredPolicy() {
  CHECK(SchedulingPolicy().mode() == SchedulingPolicy::Mode::Continuous);
  bool noDuration = false;
  try { SchedulingPolicy().stepDuration(); } catch (const std::logic_error&) { noDuration = true; }
  CHECK(noDuration);
  CHECK(SchedulingPolicy::stepped().mode() == SchedulingPolicy::Mode::Stepped);
  CHECK(SchedulingPolicy::stepped().stepDuration() == .2);
  for (double value : {.0001, .2, 1.0})
    CHECK(SchedulingPolicy::stepped(value).stepDuration() == value);
  for (double value : {std::nextafter(.0001, 0.0), std::nextafter(1.0, 2.0),
                      0.0, -1.0, std::numeric_limits<double>::infinity(),
                      -std::numeric_limits<double>::infinity(), std::numeric_limits<double>::quiet_NaN()})
    rejects([&] { SchedulingPolicy::stepped(value); });
}
int main() {
  try { boundaries(); matchesManualSlices(); preparationValidation(); parameterBridge(); recoveredPolicy(); bypassSchedule();
    std::cout << "Scheduled graph tests passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
