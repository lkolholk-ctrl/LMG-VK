#include "lmg/automix/planner_flex.h"
#include <cassert>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <iterator>
#include <limits>
#include <stdexcept>

using namespace lmg::automix;
namespace {
template<class T> T read(std::ifstream& file) {
  T value{}; file.read(reinterpret_cast<char*>(&value), sizeof value); assert(file); return value;
}
bool bitsEqual(double a, double b) { return std::memcmp(&a, &b, sizeof a) == 0; }
void reject(const CloudVideoEvents& raw) {
  bool threw = false;
  try { (void)normalizeCloudFlexEvents(raw); }
  catch (const std::invalid_argument&) { threw = true; }
  assert(threw);
}
}
int main(int argc, char** argv) {
  std::ifstream reference(std::string(LMG_AUTOMIX_FIXTURES) + "/planner_flex.bin", std::ios::binary);
  assert(reference);
  const auto count = read<std::uint32_t>(reference);
  for (std::uint32_t i = 0; i < count; ++i) {
    const auto score = read<std::int64_t>(reference);
    const auto inputTime = read<double>(reference);
    const auto expectedTime = read<double>(reference);
    const auto expectedTag = read<std::uint64_t>(reference);
    const auto expectedAmplitude = read<double>(reference);
    const auto actual = flexEventFromScore(inputTime, score);
    assert(bool(actual) == (expectedTag != 4));
    if (actual) {
      assert(bitsEqual(actual->timeInSeconds, expectedTime));
      assert(static_cast<std::uint64_t>(actual->timeScale) == expectedTag);
      assert(bitsEqual(actual->amplitude, expectedAmplitude));
    }
  }
  assert(!normalizeCloudFlexEvents({}));
  CloudVideoEvents partial;
  partial.timeInSeconds = std::vector<double>{};
  assert(!normalizeCloudFlexEvents(partial));
  partial.score = std::vector<double>{std::numeric_limits<double>::quiet_NaN()};
  assert(normalizeCloudFlexEvents(partial)->empty()); // unused score tail is not read
  CloudVideoEvents raw;
  raw.timeInSeconds = std::vector<double>{2.5, -0.0, 2.5, -1.0, 4.0};
  raw.score = std::vector<double>{299, 400, 701, 899, 600, 200.5};
  auto events = normalizeCloudFlexEvents(raw);
  assert(events->size() == 4);
  assert(events->at(0).timeInSeconds == 2.5 && events->at(0).amplitude == .99);
  assert(bitsEqual(events->at(1).timeInSeconds, -0.0));
  assert(events->at(2).timeInSeconds == -1.0 && events->at(2).timeScale == FlexTimeScale::extraLong);
  assert(events->at(3).timeScale == FlexTimeScale::longTime);
  raw.score->resize(4); reject(raw);
  raw.timeInSeconds = std::vector<double>{1};
  for (const auto invalid : {200.5, -0.5, 0x1p63, -0x1p64,
      std::numeric_limits<double>::infinity(), std::numeric_limits<double>::quiet_NaN()}) {
    raw.score = std::vector<double>{invalid}; reject(raw);
  }
  raw.score = std::vector<double>{-0x1p63};
  assert(normalizeCloudFlexEvents(raw)->empty());
  raw.score = std::vector<double>{200};
  raw.timeInSeconds = std::vector<double>{std::numeric_limits<double>::infinity()}; reject(raw);
  const auto decoded = decodeMediaApiSongAnalysis(R"({"data":[{"id":"s","type":"songs","relationships":{
    "flexml-analysis":{"data":[{"id":"f","type":"flexml-analysis","attributes":{
      "videoEvents":{"score":[499,300,200],"timeInSeconds":[1.25,2.5,3.75]}}}]}}}]})", "s");
  const auto decodedEvents = normalizeCloudFlexEvents(*decoded.flex->attributes->videoEvents);
  assert(decodedEvents->size() == 2 && decodedEvents->front().timeScale == FlexTimeScale::medium);
  assert(decodedEvents->back().timeInSeconds == 3.75 && decodedEvents->back().amplitude == 0);
  if (argc == 2) {
    std::ifstream file(argv[1]); assert(file);
    const std::string json{std::istreambuf_iterator<char>(file), {}};
    const auto live = decodeMediaApiSongAnalysis(json, "1488408568");
    const auto liveEvents = normalizeCloudFlexEvents(*live.flex->attributes->videoEvents);
    assert(liveEvents && !liveEvents->empty());
    assert(liveEvents->front().timeScale == FlexTimeScale::medium && liveEvents->front().amplitude == .99);
  }
  std::cout << count << " ARM Flex initializers and cloud conversion checks passed\n";
}
