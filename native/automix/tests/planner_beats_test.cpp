#include "lmg/automix/planner_beats.h"
#include <cassert>
#include <cstring>
#include <fstream>
#include <iostream>

using namespace lmg::automix;
namespace {
template<class T> T read(std::ifstream& f) { T x{}; f.read(reinterpret_cast<char*>(&x), sizeof x); assert(f); return x; }
template<class T> T at(const unsigned char* p, unsigned offset) { T x{}; std::memcpy(&x, p + offset, sizeof x); return x; }
std::optional<std::int64_t> index(const unsigned char* raw, unsigned offset, int flag = -1) {
  if (flag >= 0 && (raw[flag] & 1)) return {};
  return at<std::int64_t>(raw, offset);
}
}
void referenceCases(const char* name, bool normalized) {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES) + "/" + name, std::ios::binary);
  assert(f);
  const auto cases = read<std::uint32_t>(f);
  std::size_t total = 0;
  for (std::uint32_t c = 0; c < cases; ++c) {
    const auto count = read<std::uint32_t>(f);
    std::vector<FlexEvent> input;
    std::vector<StructureEvent> expected;
    for (std::uint32_t i = 0; i < count; ++i) {
      const auto time = read<double>(f);
      const auto tag = read<std::uint32_t>(f);
      unsigned char raw[64]; f.read(reinterpret_cast<char*>(raw), 64); assert(f);
      input.push_back({time, static_cast<FlexTimeScale>(tag), .99});
      const auto kind = raw[56] >> 6;
      StructureEvent e{at<double>(raw, 0), static_cast<StructureEventKind>(kind), at<std::int64_t>(raw, 8), {}, {}, {}};
      e.downbeatIndex = index(raw, 16, kind == 0 ? 24 : -1);
      e.segmentIndex = kind == 0 ? index(raw, 32, 40) : index(raw, 24, kind == 1 ? 32 : -1);
      e.sectionIndex = kind == 0 ? index(raw, 48, 56) : kind == 1 ? index(raw, 40, 48) : index(raw, 32, kind == 2 ? 40 : -1);
      expected.push_back(e);
    }
    const auto actual = normalized ? songStructureFromFlexEvents(input).events : initialStructureEvents(input);
    assert(actual.size() == expected.size());
    for (std::size_t i = 0; i < actual.size(); ++i) {
      const auto& a = actual[i]; const auto& e = expected[i];
      assert(std::memcmp(&a.songTime, &e.songTime, sizeof(double)) == 0);
      assert(a.kind == e.kind && a.beatIndex == e.beatIndex && a.downbeatIndex == e.downbeatIndex);
      assert(a.segmentIndex == e.segmentIndex && a.sectionIndex == e.sectionIndex);
    }
    total += count;
  }
  std::cout << cases << " original hierarchy cases / " << total << " events passed\n";
}
void stabilityCases() {
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES) + "/beat_stability.bin", std::ios::binary);
  assert(f);
  const auto cases = read<std::uint32_t>(f);
  for (std::uint32_t c = 0; c < cases; ++c) {
    const auto bars = read<std::uint32_t>(f);
    std::vector<double> times(bars + 1);
    std::vector<std::uint32_t> beats(bars);
    for (auto& t : times) t = read<double>(f);
    for (auto& n : beats) n = read<std::uint32_t>(f);
    std::vector<FlexEvent> input;
    for (std::size_t bar = 0; bar < bars; ++bar) {
      for (std::uint32_t beat = 0; beat < beats[bar]; ++beat) {
        const double time = beat ? times[bar] + (times[bar+1] - times[bar]) * beat / beats[bar] : times[bar];
        input.push_back({time, beat ? FlexTimeScale::shortTime : FlexTimeScale::medium, 0});
      }
    }
    input.push_back({times.back(), FlexTimeScale::medium, 0});
    const auto structure = songStructureFromFlexEvents(input);
    const auto regionCount = read<std::uint32_t>(f);
    assert(structure.beatStabilityMap.size() == regionCount);
    for (const auto& region : structure.beatStabilityMap) {
      const auto start = read<double>(f), end = read<double>(f);
      const auto count = read<std::int64_t>(f);
      const auto bpm = read<double>(f);
      const auto a = structure.events[region.events.startEvent].songTime;
      const auto b = structure.events[region.events.endEvent].songTime;
      assert(std::memcmp(&start, &a, sizeof a) == 0 && std::memcmp(&end, &b, sizeof b) == 0);
      assert(count == region.beatsPerBar && std::memcmp(&bpm, &region.averageTempoBpm, sizeof bpm) == 0);
    }
  }
  std::cout << cases << " original stability-map cases passed\n";
}
int main() {
  referenceCases("initial_structure_events.bin", false);
  referenceCases("normalized_structure_events.bin", true);
  stabilityCases();
  std::vector<FlexEvent> metrical;
  for (int beat = 0; beat <= 24; ++beat)
    metrical.push_back({beat * .5, beat % 4 ? FlexTimeScale::shortTime : FlexTimeScale::medium, 0});
  const auto structure = songStructureFromFlexEvents(metrical);
  assert(structure.beatEvents.size() == 25 && structure.downbeatEvents.size() == 7);
  assert(structure.bars.size() == 6 && structure.sections.empty());
  assert(structure.beatStabilityMap.size() == 1);
  const auto stable = structure.beatStabilityMap.front();
  assert(stable.events.startEvent == 0 && stable.events.endEvent == 24);
  assert(stable.beatsPerBar == 4 && stable.averageTempoBpm == 120);
  metrical.resize(17);
  assert(songStructureFromFlexEvents(metrical).beatStabilityMap.empty());
}
