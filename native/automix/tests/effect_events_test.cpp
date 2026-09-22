#include "lmg/automix/effect_events.h"
#include "lmg/automix/scheduled_graph.h"
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while (false)
template<class T> T read(std::ifstream& file) {
  T value{};
  file.read(reinterpret_cast<char*>(&value), sizeof value);
  CHECK(file.gcount() == sizeof value);
  return value;
}
MediaTime readTime(std::ifstream& file) {
  MediaTime t;
  t.value = read<std::int64_t>(file); t.timescale = read<std::int32_t>(file);
  t.flags = read<std::uint32_t>(file); t.epoch = read<std::int64_t>(file);
  return t;
}
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
void binaryReferences() {
  std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES)+"/media_time.bin", std::ios::binary);
  CHECK(file.good());
  const auto count = read<std::uint32_t>(file);
  CHECK(count == 570);
  for (unsigned i = 0; i < count; ++i) {
    const auto actual = automationMediaTime(read<double>(file));
    const auto expected = readTime(file);
    CHECK(actual.value == expected.value && actual.timescale == expected.timescale);
    CHECK(actual.flags == expected.flags && actual.epoch == expected.epoch);
  }
  CHECK(file.peek() == std::char_traits<char>::eof());
  std::ifstream comparison(std::string(LMG_AUTOMIX_FIXTURES)+"/media_time_compare.bin", std::ios::binary);
  CHECK(comparison.good());
  const auto n = read<std::uint32_t>(comparison);
  CHECK(n == 424);
  for (unsigned i = 0; i < n; ++i) {
    const auto a = readTime(comparison), b = readTime(comparison);
    CHECK(compareMediaTimes(a, b) == read<std::int32_t>(comparison));
  }
  CHECK(comparison.peek() == std::char_traits<char>::eof());
}
void mergeAndTimeBase() {
  const auto gain = *effectParameterAddress("Ga1g"), cutoff = *effectParameterAddress("LP1f");
  CHECK(gain == 0x47613167 && cutoff == 0x4c503166);
  const double nan = std::numeric_limits<double>::quiet_NaN();
  std::vector<SteppedAutomation> automations{
    {effectParameter("Ga1g"), {{.1, {100, 7, 0}}, {.5, {102, 9, 2}}}},
    {effectParameter("LP1f"), {{1234.56789, {99, 3, -3}}, {2000, {100, 7, 0}}}},
    {effectParameter("Ga1g"), {{.9, {500, 7+.4e-9, 200}}, {.2, {501, 9, 201}}}},
    {effectParameter("out_gain"), {{nan, {nan, nan, nan}}}},
    {effectParameter("ts_rate"), {{nan, {nan, nan, nan}}}}
  };
  const auto events = compileTimedEffectEvents(automations);
  CHECK(events.size() == 4);
  CHECK(events[0].time.value == 0 && events[0].parameters.size() == 27);
  CHECK(events[1].time.value == 3000000000 && events[2].time.value == 7000000000);
  CHECK(events[3].time.value == 9000000000);
  CHECK(events[1].parameters.size() == 1 && events[1].parameters.at(cutoff) == float(1234.56789));
  CHECK(events[2].parameters.size() == 2 && events[2].parameters.at(gain) == float(.9));
  CHECK(events[2].parameters.at(cutoff) == 2000);
  CHECK(events[3].parameters.at(gain) == float(.2));
  // First dictionary key representation is retained when an equal rounded key
  // updates the existing entry. The time comparator ignores hasBeenRounded.
  CHECK(events[2].time.flags == 1);
  CHECK(automationMediaTime(7+.4e-9).flags == 3);
  auto unknown = effectParameter("Ga1g"); unknown.id = "zzzz";
  const auto passed = compileTimedEffectEvents({{unknown, {{1, {0, 0, 0}}}}});
  CHECK(passed.size() == 1 && passed[0].parameters.count(0x7a7a7a7a) == 1);
  const auto defaults = compileTimedEffectEvents({});
  CHECK(defaults.size() == 1 && defaults[0].time.value == 0 && defaults[0].time.flags == 1);
  CHECK(defaults[0].parameters.size() == 27);
  CHECK(defaults[0].parameters.at(gain) == 1);
  CHECK(defaults[0].parameters.at(*effectParameterAddress("bypa")) == 1);
  CHECK(defaults[0].parameters.at(*effectParameterAddress("RVmi")) == float(.008));
  const auto atZero = compileTimedEffectEvents({{effectParameter("Ga1g"),
      {{.4, {0, -.4e-9, 0}}, {.6, {0, .4e-9, 0}}}}});
  CHECK(atZero.size() == 1 && atZero[0].parameters.size() == 27);
  CHECK(atZero[0].time.flags == 1 && atZero[0].parameters.at(gain) == float(.6));
  const auto negative = compileTimedEffectEvents({{effectParameter("Ga1g"), {{.3, {0, -1, 0}}}}});
  CHECK(negative.size() == 2 && negative[0].time.value == -1000000000);
  CHECK(negative[1].parameters.at(gain) == 1);
}
void scaleReferences() {
  std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES)+"/media_time_scale.bin",std::ios::binary);
  CHECK(file.good());CHECK(read<std::uint32_t>(file)==1002);
  for(unsigned i=0;i<1002;++i){
    const auto input=readTime(file);const auto scale=read<std::int32_t>(file);
    const auto expected=readTime(file);
    try{
      const auto actual=convertMediaTimeScaleNearest(input,scale);
      CHECK((expected.flags&~2u)==1);
      CHECK(actual.value==expected.value&&actual.timescale==expected.timescale);
      CHECK(actual.flags==expected.flags&&actual.epoch==expected.epoch);
    }catch(const std::overflow_error&){CHECK((expected.flags&~2u)!=1);}
  }
  CHECK(file.peek()==std::char_traits<char>::eof());
  rejects([]{convertMediaTimeScaleNearest({1,0,1,0},48000);});
  rejects([]{convertMediaTimeScaleNearest({1,1,1,0},0);});
  rejects([]{convertMediaTimeScaleNearest({1,1,5,0},48000);});
}
void styleToTransport() {
  std::vector<ContinuousAutomation> continuous{
    {effectParameter("Ga1g"), {{0, 100, 0x80}, {1, 102, std::nullopt}}},
    {effectParameter("out_gain"), {{0, 100, 0x80}, {1, 102, std::nullopt}}},
    {effectParameter("ts_rate"), {{2, 100, 0x80}, {2, 104, std::nullopt}}}
  };
  continuous = withStyleBypass(continuous, 100, 102);
  const auto map = schedulePlaybackTimeMap(0, 100, TimeStretchingState{100, 105}, continuous);
  const auto stepped = compileSteppedAutomations(continuous, {0, 1, 2}, SchedulingPolicy::stepped(.5), map);
  const auto events = compileTimedEffectEvents(stepped);
  CHECK(events.size() == 4);
  CHECK(events[0].time.value == 0 && events[0].parameters.size() == 27);
  CHECK(events[1].time.value == 105000000000 && events[2].time.value == 105500000000);
  CHECK(events[3].time.value == 106000000000);
  const auto gain = *effectParameterAddress("Ga1g"), bypass = *effectParameterAddress("bypa");
  CHECK(events[1].parameters.at(gain) == 0 && events[1].parameters.at(bypass) == 0);
  CHECK(events[2].parameters.size() == 1 && events[2].parameters.at(gain) == .5f);
  CHECK(events[3].parameters.at(gain) == 1 && events[3].parameters.at(bypass) == 1);
}
void validation() {
  CHECK(automationMediaTime(1.9e-9).value == 1);
  CHECK(automationMediaTime(-1.9e-9).value == -1);
  CHECK(automationMediaTime(-0.).value == 0 && automationMediaTime(-0.).flags == 1);
  CHECK(compareMediaTimes({1, 2, 1, 0}, {2, 4, 3, 0}) == 0);
  CHECK(!effectParameterAddress("ts_rate") && !effectParameterAddress("out_gain"));
  CHECK(!effectParameterAddress("") && !effectParameterAddress("abc"));
  rejects([] { effectParameterAddress("\xc3\xa9" "abc"); });
  rejects([] { automationMediaTime(std::numeric_limits<double>::infinity()); });
  rejects([] { automationMediaTime(std::numeric_limits<double>::quiet_NaN()); });
  rejects([] { compareMediaTimes({1, 0, 1, 0}, {0, 1, 1, 0}); });
  rejects([] { compareMediaTimes({1, 1, 5, 0}, {0, 1, 1, 0}); });
  bool overflow = false;
  try { automationMediaTime(1e30); } catch (const std::overflow_error&) { overflow = true; }
  CHECK(overflow);
  rejects([] { compileTimedEffectEvents({{effectParameter("Ga1g"),
      {{std::numeric_limits<double>::infinity(), {0, 0, 0}}}}}); });
  rejects([] { compileTimedEffectEvents({{effectParameter("Ga1g"),
      {{1, {0, std::numeric_limits<double>::quiet_NaN(), 0}}}}}); });
}
void initialization() {
  auto event = compileTimedEffectEvents({}).front();
  auto settings = initializeGraphSettings(48000, event);
  CHECK(settings.bypass && settings.inputGain == 1 && settings.wetGain == 0);
  CHECK(settings.reverbMinSeconds == double(float(.008)));
  CHECK(settings.reverbMaxSeconds == double(float(.05)) && settings.reverbSeed == 1);
  event.parameters[*effectParameterAddress("Ga1g")] = .25f;
  settings = initializeGraphSettings(48000, event);
  ScheduledTrackGraph graph(PreparedGraph(48000, settings), 1, 4, 0, {});
  float in[] = {1, .5f, -.5f, -1}, out[4]{};
  CHECK(graph.process(in, out, 4));
  for (unsigned i = 0; i < 4; ++i) CHECK(out[i] == in[i] * .25f);
  event.parameters[*effectParameterAddress("RVrr")] = .5f;
  rejects([&] { initializeGraphSettings(48000, event); });
  event.parameters[*effectParameterAddress("RVrr")] = 1;
  event.time = automationMediaTime(1);
  rejects([&] { initializeGraphSettings(48000, event); });
  event.time = automationMediaTime(0);
  event.parameters[*effectParameterAddress("zzzz")] = 1;
  rejects([&] { initializeGraphSettings(48000, event); });
}
int main() {
  try {
    binaryReferences(); scaleReferences(); mergeAndTimeBase(); styleToTransport(); validation(); initialization();
    std::cout << "570 original time conversions, 1002 scale conversions, 424 comparisons and effect transport checks passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
