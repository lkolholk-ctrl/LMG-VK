#include "lmg/automix/stepped_schedule.h"
#include "lmg/automix/volume_ramps.h"
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while (false)
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
template<class T> T read(std::ifstream& file) {
  T value{};
  file.read(reinterpret_cast<char*>(&value), sizeof value);
  CHECK(file.gcount() == sizeof value);
  return value;
}
void bits(double actual, double expected) {
  CHECK(std::memcmp(&actual, &expected, sizeof(double)) == 0);
}
SteppedPoint point(std::ifstream& file) {
  SteppedPoint p;
  p.value = read<double>(file); p.time.song = read<double>(file);
  p.time.stretchedSong = read<double>(file); p.time.transition = read<double>(file);
  return p;
}
void references() {
  std::ifstream grid(std::string(LMG_AUTOMIX_FIXTURES)+"/stepped_grid.bin", std::ios::binary);
  CHECK(grid.good());
  const auto cases = read<std::uint32_t>(grid);
  CHECK(cases == 372);
  for (unsigned test = 0; test < cases; ++test) {
    const double step = read<double>(grid);
    const auto n = read<std::uint32_t>(grid), m = read<std::uint32_t>(grid);
    std::vector<double> input(n);
    for (double& value : input) value = read<double>(grid);
    const auto actual = steppedTransitionTimes(input, step);
    CHECK(actual.size() == m);
    for (double value : actual) bits(value, read<double>(grid));
  }
  CHECK(grid.peek() == std::char_traits<char>::eof());
  std::ifstream compact(std::string(LMG_AUTOMIX_FIXTURES)+"/stepped_compact.bin", std::ios::binary);
  CHECK(compact.good());
  const auto count = read<std::uint32_t>(compact);
  CHECK(count == 96);
  for (unsigned test = 0; test < count; ++test) {
    const auto n = read<std::uint32_t>(compact), m = read<std::uint32_t>(compact);
    std::vector<SteppedPoint> input(n);
    for (auto& p : input) p = point(compact);
    const auto actual = compactSteppedPoints(input);
    CHECK(actual.size() == m);
    for (const auto& p : actual) {
      const auto expected = point(compact);
      bits(p.value, expected.value); bits(p.time.song, expected.time.song);
      bits(p.time.stretchedSong, expected.time.stretchedSong);
      bits(p.time.transition, expected.time.transition);
    }
  }
  CHECK(compact.peek() == std::char_traits<char>::eof());
  std::ifstream ramps(std::string(LMG_AUTOMIX_FIXTURES)+"/stepped_ramps.bin", std::ios::binary);
  CHECK(ramps.good());
  CHECK(read<std::uint32_t>(ramps) == 96);
  for (unsigned test = 0; test < 96; ++test) {
    const auto n = read<std::uint32_t>(ramps), m = read<std::uint32_t>(ramps);
    std::vector<SteppedPoint> input(n);
    for (auto& p : input) p = point(ramps);
    const auto actual = steppedRamps(input);
    CHECK(actual.size() == m);
    for (const auto& r : actual) {
      bits(r.startValue, read<double>(ramps)); bits(r.endValue, read<double>(ramps));
      bits(r.start.song, read<double>(ramps)); bits(r.end.song, read<double>(ramps));
      bits(r.start.stretchedSong, read<double>(ramps)); bits(r.end.stretchedSong, read<double>(ramps));
      bits(r.start.transition, read<double>(ramps)); bits(r.end.transition, read<double>(ramps));
    }
  }
  CHECK(ramps.peek() == std::char_traits<char>::eof());
}

void pipeline() {
  const auto landmarks = steppedTransitionLandmarks(0, 2, 1, 3, 1.5);
  CHECK(landmarks.outgoing == std::vector<double>({0, 1, 1.5, 2}));
  CHECK(landmarks.incoming == std::vector<double>({1, 1.5, 2, 3}));
  const auto equal = steppedTransitionLandmarks(0, 1, 0, 1, 0);
  CHECK(equal.outgoing == std::vector<double>({0, 1}) && equal.incoming == equal.outgoing);
  std::vector<ContinuousAutomation> automations{
    {effectParameter("Ga1g"), {{0, 100, 0x80}, {1, 102, std::nullopt}}},
    {effectParameter("out_gain"), {}},
    {effectParameter("ts_rate"), {{2, 100, 0x80}, {2, 200, std::nullopt}}}
  };
  automations = withStyleBypass(automations, 100, 102);
  const auto map = schedulePlaybackTimeMap(0, 100, TimeStretchingState{100, 105}, automations);
  const auto result = compileSteppedAutomations(automations, {0, 1, 2}, SchedulingPolicy::stepped(.5), map);
  CHECK(result.size() == 3);
  CHECK(result[0].parameter.id == "bypa" && result[0].points.size() == 2);
  CHECK(result[0].points[0].value == 0 && result[0].points[0].time.transition == 0);
  CHECK(result[0].points[1].value == 1 && result[0].points[1].time.transition == 1);
  const auto& gain = result[1].points;
  CHECK(gain.size() == 3 && gain[0].value == 0 && gain[1].value == .5 && gain[2].value == 1);
  CHECK(gain[1].time.song == 101 && gain[1].time.stretchedSong == 105.5 && gain[1].time.transition == .5);
  CHECK(result[2].parameter.id == "out_gain" && result[2].points.size() == 1);
  CHECK(result[2].points[0].value == 0); // default, no invented terminal write
  CHECK(result[2].ramps.size() == 1);
  CHECK(result[2].ramps[0].start.transition == 0 && result[2].ramps[0].end.transition == 2);
  const auto shortSpan = compileSteppedAutomations(automations, {0, .1}, SchedulingPolicy::stepped(), map);
  CHECK(shortSpan.size() == 3);
  for (const auto& a : shortSpan) CHECK(a.points.empty());
  // Preserve resource duplicates. Filter only the complete rate descriptor.
  automations.push_back(automations[1]);
  auto alteredRate = automations[3]; alteredRate.parameter.maximum = 31;
  automations.push_back(alteredRate);
  const auto duplicates = compileSteppedAutomations(automations, {0, 1}, SchedulingPolicy::stepped(.5), map);
  CHECK(duplicates.size() == 5 && duplicates[3].parameter.id == "Ga1g");
  CHECK(duplicates[4].parameter.id == "ts_rate" && duplicates[4].points.size() == 1);
  CHECK(duplicates[4].points[0].value == 2);
}

void sampledReferences() {
  std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES)+"/stepped_sampled.bin", std::ios::binary);
  CHECK(file.good());
  const auto count = read<std::uint32_t>(file);
  CHECK(count == 216);
  for (unsigned test = 0; test < count; ++test) {
    const auto present = read<std::uint32_t>(file), curve = read<std::uint32_t>(file), shape = read<std::uint32_t>(file);
    const double step = read<double>(file);
    PlaybackTime anchor;
    anchor.song = read<double>(file); anchor.stretchedSong = read<double>(file); anchor.transition = read<double>(file);
    RateRamp rate;
    rate.startRate = read<double>(file); rate.endRate = read<double>(file);
    rate.startSongTime = read<double>(file); rate.endSongTime = read<double>(file);
    const auto expectedSize = read<std::uint32_t>(file);
    const auto map = present ? PlaybackTimeMap(anchor, rate) : PlaybackTimeMap(anchor);
    ContinuousAutomation input{effectParameter("Ga1g"), {}};
    input.parameter.defaultValue = .75;
    const auto c = static_cast<std::uint8_t>(curve);
    if (shape == 1) input.points = {{1, anchor.song+1, c}, {8, anchor.song+5, c}};
    if (shape == 2) input.points = {{1, anchor.song+1, c}, {2, anchor.song+1, c},
                                   {2, anchor.song+3, c}, {1, anchor.song+3, c}};
    const auto actual = compileSteppedAutomations({input},
        {anchor.transition-1, anchor.transition+1, anchor.transition+4, anchor.transition+8},
        SchedulingPolicy::stepped(step), map);
    CHECK(actual.size() == 1 && actual[0].points.size() == expectedSize);
    for (const auto& p : actual[0].points) {
      const auto expected = point(file);
      bits(p.value, expected.value); bits(p.time.song, expected.time.song);
      bits(p.time.stretchedSong, expected.time.stretchedSong); bits(p.time.transition, expected.time.transition);
    }
  }
  CHECK(file.peek() == std::char_traits<char>::eof());
}

void validation() {
  rejects([] { steppedTransitionTimes({0, 0, 1}, .2); });
  rejects([] { steppedTransitionTimes({1, 0}, .2); });
  rejects([] { steppedTransitionTimes({0, 1}, 0); });
  rejects([] { steppedTransitionTimes({0, 1}, std::numeric_limits<double>::quiet_NaN()); });
  rejects([] { steppedTransitionTimes({0, std::numeric_limits<double>::infinity()}, .2); });
  rejects([] { steppedTransitionTimes({1e16, 1e16+100}, .2); });
  rejects([] { steppedTransitionLandmarks(2, 1, 0, 1, 0); });
  rejects([] { compactSteppedPoints({{std::numeric_limits<double>::infinity(), {0, 0, 0}}}); });
  rejects([] { compileSteppedAutomations({}, {0, 1}, SchedulingPolicy(), PlaybackTimeMap({0, 0, 0})); });
  rejects([] { continuousRamps({{0, 0, 0xff}, {1, 1, 0x80}}); });
  rejects([] { steppedRamps({{0, {1, 1, 1}}, {1, {0, 0, 0}}}); });
  rejects([] { steppedRamps({{std::numeric_limits<double>::quiet_NaN(), {0, 0, 0}}}); });
  CHECK(steppedTransitionTimes({}, .2).empty());
  CHECK(steppedTransitionTimes({0}, .2).empty());
  CHECK(steppedTransitionTimes({0, .1}, .2).empty());
  CHECK(steppedTransitionTimes({0, .5}, .5) == std::vector<double>({0, .5}));
  CHECK(compactSteppedPoints({{1, {0, 0, 0}}, {1, {2, 2, 2}}}).size() == 1);
}
void volumePipeline() {
  const std::vector<ContinuousAutomation> source{
    {effectParameter("out_gain"), {{0, 101, 0x80}, {1, 102, 0x80}}},
    {effectParameter("Ga1g"), {{0, 101, 0x80}, {1, 102, 0x80}}}
  };
  const auto stepped = compileSteppedAutomations(source, {0, 1, 2, 3},
      SchedulingPolicy::stepped(.5), PlaybackTimeMap({100, 105, 0}));
  const auto actual = compileTimedVolumeRamps(stepped);
  CHECK(actual.size() == 4);
  // Initial hold, sampled plateau, then two slope segments; no trailing hold.
  const double starts[] = {0, 105, 106, 106.5};
  const double ends[] = {105, 106, 106.5, 107};
  const float from[] = {0, 0, 0, .5f}, to[] = {0, 0, .5f, 1};
  for (std::size_t i = 0; i < actual.size(); ++i) {
    CHECK(actual[i].startVolume == from[i] && actual[i].endVolume == to[i]);
    CHECK(compareMediaTimes(actual[i].start, automationMediaTime(starts[i])) == 0);
    CHECK(compareMediaTimes(actual[i].end, automationMediaTime(ends[i])) == 0);
  }
  auto altered = stepped.front();
  altered.parameter.defaultValue = 1;
  CHECK(compileTimedVolumeRamps({altered}).empty());
  CHECK(compileTimedVolumeRamps({stepped[0], stepped[0]}).size() == 4);
  auto emptyFirst = stepped[0]; emptyFirst.ramps.clear();
  CHECK(compileTimedVolumeRamps({emptyFirst, stepped[0]}).empty());
  CHECK(compileTimedVolumeRamps({altered, stepped[0]}).size() == 4);
  SteppedAutomation zero{effectParameter("out_gain"), {},
      {{1.123456789, 2.123456789, {100, 0, 50}, {101, .0000000009, 51}}}};
  const auto shortRamp = compileTimedVolumeRamps({zero});
  CHECK(shortRamp.size() == 1 && shortRamp[0].startVolume == static_cast<float>(1.123456789));
  CHECK(shortRamp[0].endVolume == static_cast<float>(2.123456789)); // no metadata clamp
  CHECK(shortRamp[0].start.value == 0 && shortRamp[0].end.value == 0);
  CHECK(shortRamp[0].end.flags == 3); // quantization does not drop the ramp
  zero.ramps[0].endValue = std::numeric_limits<double>::infinity();
  rejects([&] { compileTimedVolumeRamps({zero}); });
  zero.ramps[0].endValue = 1;
  zero.ramps[0].end.stretchedSong = -1;
  rejects([&] { compileTimedVolumeRamps({zero}); });
}
void speedReferences() {
  std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES)+"/stepped_speed.bin", std::ios::binary);
  CHECK(file.good());
  const auto count = read<std::uint32_t>(file);
  CHECK(count == 72);
  for (unsigned test = 0; test < count; ++test) {
    const auto present = read<std::uint32_t>(file);
    PlaybackTime anchor;
    anchor.song = read<double>(file); anchor.stretchedSong = read<double>(file);
    anchor.transition = read<double>(file);
    RateRamp rate;
    rate.startRate = read<double>(file); rate.endRate = read<double>(file);
    rate.startSongTime = read<double>(file); rate.endSongTime = read<double>(file);
    const double step = read<double>(file), span = read<double>(file);
    const auto expectedSize = read<std::uint32_t>(file);
    const auto map = present ? PlaybackTimeMap(anchor, rate) : PlaybackTimeMap(anchor);
    const auto actual = compileTimeStretchingSteps(
        {anchor.transition-1, anchor.transition-1+span*.3, anchor.transition-1+span},
        SchedulingPolicy::stepped(step), map);
    CHECK(actual.size() == expectedSize);
    for (const auto& s : actual) {
      bits(s.start.song, read<double>(file)); bits(s.end.song, read<double>(file));
      bits(s.start.stretchedSong, read<double>(file)); bits(s.end.stretchedSong, read<double>(file));
      bits(s.start.transition, read<double>(file)); bits(s.end.transition, read<double>(file));
    }
    for (const auto& s : actual) bits(s.playbackRate(), read<double>(file));
  }
  CHECK(file.peek() == std::char_traits<char>::eof());
  CHECK((TimeStretchingStep{{1, 2, 3}, {1, 2, 3}}.playbackRate() == 0));
  rejects([] { compileTimeStretchingSteps({0, 1}, SchedulingPolicy(), PlaybackTimeMap({0, 0, 0})); });
}
int main() {
  try {
    references(); sampledReferences(); pipeline(); validation(); volumePipeline(); speedReferences();
    std::cout << "372 ARM grids, 96 ARM compactions, 96 ARM ramp sequences, 72 ARM speed schedules, 216 complete ARM samplers and stepped pipeline checks passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
