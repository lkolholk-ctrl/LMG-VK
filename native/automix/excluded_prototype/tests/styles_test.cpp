#include "lmg/automix/styles.h"
#include <fstream>
#include <iostream>
#include <iterator>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
const char* fixture = R"([{"id":101,"name":"LMG loader test","offset":{"relative":1,"offsetInSeconds":-0.3},"duration":8,
"instructions":{"outgoing":[{"name":"GAIN","placement":{"start":{"relative":0},"end":{"relative":1}},
"automations":[{"parameterId":"out_gain","startTime":{"default":{"relative":0}},
"endTime":{"default":{"relative":1}},"startValue":{"default":1},
"endValue":{"parameterName":"target","default":0},"interpolation":"linear"}]}],"incoming":[]}}])";
void timingTests() {
  auto style = loadTransitionStyles(fixture)[0];
  for (auto rate : {44100u, 48000u}) {
    const Frame origin = 9007199254741000LL, duration = rate * 18;
    auto& i = style.outgoing[0];
    i.placementStart = {1, -.3}; i.placementEnd = {1, {}};
    const auto t = resolveStyleTiming(style, {origin, origin + duration}, rate,
                                     StyleTimeBasis::InstructionRelative);
    CHECK(t.outgoing.ramps[0].begin == origin + duration - rate * 3 / 10);
    CHECK(t.outgoing.ramps[0].end == origin + duration);
    CHECK(t.offsetAnchor == t.outgoing.ramps[0].begin);
    const auto plan = compileStyleDeck(style.outgoing, t.outgoing.ramps, {}, {{"out_gain", 1}});
    CHECK(plan.parameters[0].lane.valueAt(origin) == 1);
    CHECK(plan.parameters[0].lane.valueAt(origin + duration) == 0);
    rejects([&] { resolveStyleTiming(style, {origin, origin + duration}, rate,
                                    StyleTimeBasis::TransitionRelative); });
    // Window size comes from caller, never raw duration metadata.
    style.duration = 999;
    CHECK(resolveStyleTiming(style, {origin, origin + duration}, rate,
          StyleTimeBasis::InstructionRelative).outgoing.ramps[0].begin == t.outgoing.ramps[0].begin);
  }
  style = loadTransitionStyles(fixture)[0]; style.offset.reset();
  auto& i = style.outgoing[0];
  i.ramps[0].start = {.125, {}}; i.ramps[0].end = {.625, {}};
  const auto half = resolveStyleTiming(style, {100, 104}, 48000, StyleTimeBasis::TransitionRelative);
  CHECK(half.outgoing.ramps[0].begin == 101 && half.outgoing.ramps[0].end == 103);
  rejects([&] { resolveStyleTiming(style, {0, 4}, 0, StyleTimeBasis::InstructionRelative); });
  rejects([&] { resolveStyleTiming(style, {5, 4}, 48000, StyleTimeBasis::InstructionRelative); });
  i.placementStart = {0, -.001};
  rejects([&] { resolveStyleTiming(style, {100, 200}, 48000, StyleTimeBasis::InstructionRelative); });
  i.placementStart = {0, {}}; i.ramps[0].end = {1, .001};
  rejects([&] { resolveStyleTiming(style, {100, 200}, 48000, StyleTimeBasis::InstructionRelative); });
  style = loadTransitionStyles(fixture)[0]; style.offset.reset();
  auto zero = resolveStyleTiming(style, {20, 20}, 48000, StyleTimeBasis::InstructionRelative);
  CHECK(zero.outgoing.ramps[0].begin == 20 && zero.outgoing.ramps[0].end == 20);
  const Frame max = std::numeric_limits<Frame>::max();
  CHECK(resolveStyleTiming(style, {max - 4, max}, 48000,
        StyleTimeBasis::InstructionRelative).outgoing.ramps[0].end == max);
  style.offset = StyleTime{1, 1};
  rejects([&] { resolveStyleTiming(style, {max - 4, max}, 48000, StyleTimeBasis::InstructionRelative); });
}
int main(int argc, char** argv) {
  try {
    timingTests();
    const auto catalog = loadTransitionStyles(fixture);
    CHECK(catalog.size() == 1 && catalog[0].id == 101 && catalog[0].duration == 8);
    CHECK(catalog[0].offset->offsetInSeconds == -.3);
    const auto& instructions = catalog[0].outgoing;
    CHECK(instructions[0].ramps[0].to.parameterName == "target");
    auto out = compileStyleDeck(instructions, {{0, 4}}, {}, {{"out_gain", 1}});
    auto in = compileStyleDeck({}, {}, {}, {{"out_gain", 0}});
    TransitionRenderer render(48000, 1, out, in);
    float input[] = {1, 1, 1, 1, 1}, result[5] = {};
    CHECK(render.render(input, nullptr, result, 5, 0));
    for (int i = 0; i < 5; ++i) CHECK(result[i] == 1 - i * .25f);
    out = compileStyleDeck(instructions, {{10, 14}}, {}, {{"out_gain", 1}}, {{"target", .5}});
    CHECK(out.parameters[0].lane.valueAt(12) == .75);
    rejects([&] { compileStyleDeck(instructions, {}, {}, {{"out_gain", 1}}); });
    rejects([&] { compileStyleDeck(instructions, {{0, 4}}, {}, {}); });
    rejects([&] { compileStyleDeck(instructions, {{4, 0}}, {}, {{"out_gain", 1}}); });
    auto missing = instructions; missing[0].ramps[0].to.fallback.reset();
    rejects([&] { compileStyleDeck(missing, {{0, 4}}, {}, {{"out_gain", 1}}); });
    auto overlap = instructions; overlap.push_back(instructions[0]);
    rejects([&] { compileStyleDeck(overlap, {{0, 4}, {2, 6}}, {}, {{"out_gain", 1}}); });
    for (auto bad : {"{", "{}", "[{\"id\":1,\"id\":2}]", "[true]", "[1e999]"})
      rejects([&] { loadTransitionStyles(bad); });
    std::string deep(40, '['); deep += std::string(40, ']');
    rejects([&] { loadTransitionStyles(deep); });
    rejects([] { loadTransitionStyles(std::string(1024 * 1024 + 1, ' ')); });
    auto unknown = std::string(fixture); unknown.replace(unknown.find("linear"), 6, "mystery");
    rejects([&] { loadTransitionStyles(unknown); });
    if (argc == 2) {
      std::ifstream stream(argv[1]); CHECK(stream.good());
      const std::string raw((std::istreambuf_iterator<char>(stream)), {});
      const auto real = loadTransitionStyles(raw);
      std::size_t count = 0, ramps = 0, mapped = 0;
      CHECK(real.size() == 14);
      for (const auto& style : real) for (const auto* side : {&style.outgoing, &style.incoming})
        for (const auto& instruction : *side) {
          ++count; ramps += instruction.ramps.size();
          for (const auto& r : instruction.ramps) mapped += r.from.parameterName.has_value() + r.to.parameterName.has_value();
        }
      CHECK(count == 55 && ramps == 74 && mapped == 2);
      for (const auto& style : real) if (style.id == 8 || style.id == 9 || style.id == 12) {
        const auto timing = resolveStyleTiming(style, {48000, 912000}, 48000,
                                               StyleTimeBasis::InstructionRelative);
        CHECK(timing.outgoing.ramps.back().begin == (style.id == 12 ? 681600 : 897600));
        CHECK(timing.outgoing.ramps.back().end == (style.id == 12 ? 696000 : 912000));
        CHECK(timing.incoming.ramps.back().begin == (style.id == 12 ? 264000 : 48000));
        CHECK(timing.incoming.ramps.back().end == (style.id == 12 ? 278400 : 62400));
      }
      std::cout << "Research catalog: 14 styles, 55 instructions, 74 automations, 2 mapped values\n";
    }
    std::cout << "Style loader/compiler tests passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
