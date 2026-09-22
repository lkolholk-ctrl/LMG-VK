#include "lmg/automix/styles.h"
#include "lmg/automix/stepped_schedule.h"
#include "lmg/automix/effect_events.h"
#include "lmg/automix/volume_ramps.h"
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
int main(int argc, char** argv) {
  try {
    CHECK(loadTransitionStyles("[]").empty());
    for (auto bad : {"{", "{}", "[{\"id\":1,\"id\":2}]", "[true]", "[1e999]"})
      rejects([&] { loadTransitionStyles(bad); });
    const std::string deep = std::string(40, '[') + std::string(40, ']');
    rejects([&] { loadTransitionStyles(deep); });
    rejects([] { loadTransitionStyles(std::string(1024 * 1024 + 1, ' ')); });
    const auto fixture = loadTransitionStyles(R"([{"id":101,"name":"schema fixture",
      "offset":{"relative":1,"offsetInSeconds":-0.3},"duration":8,
      "instructions":{"outgoing":[{"name":"GAIN",
      "placement":{"start":{"relative":0},"end":{"relative":1}},
      "automations":[{"parameterId":"out_gain","startTime":{"default":{"relative":0}},
      "endTime":{"default":{"relative":1}},"startValue":{"default":1},
      "endValue":{"parameterName":"target","default":0},"interpolation":"linear"}]}],"incoming":[]}}])");
    CHECK(fixture[0].duration == 8 && fixture[0].offset->offsetInSeconds == -.3);
    const auto& commands = fixture[0].outgoing;
    const auto continuous=compileContinuousStyle(commands,{10,14},{.9,1},{{"target",.5}});
    CHECK(continuous.size()==2 && continuous[0].parameter.id=="bypa");
    CHECK(continuous[1].parameter.id=="out_gain");
    CHECK(ContinuousAutomationValues(continuous[1]).valueAt(12)==.75);
    CHECK(ContinuousAutomationValues(continuous[0]).valueAt(10)==0);
    CHECK(ContinuousAutomationValues(continuous[0]).valueAt(14)==1);
    CHECK(effectParameter("LP1f").defaultValue>effectParameter("LP1f").maximum);
    CHECK(effectParameter("Ga1g").styleParameterId=="player_gain");
    CHECK(styleEffectParameter("fx_reverb_high_frequency_decay_time").id=="RVhf");
    rejects([]{styleEffectParameter("RVhf");});
    rejects([]{effectParameter("unknown");});
    auto stretch=commands;
    stretch[0].ramps[0].parameterId="ts_rate";
    stretch[0].ramps[0].interpolation="ease-in-4";
    stretch[0].ramps[0].from={std::nullopt,"missing"};
    const auto rate=compileContinuousStyle(stretch,{10,14},{.8,1.2});
    CHECK(rate[1].points[0].value==.8 && rate[1].points[1].value==1.2);
    CHECK(rate[1].points[0].curve==0x80 && rate[1].points[1].curve==0x80);
    CHECK(selectedPlaybackRateRamp(rate)->startSongTime==10);
    auto missing=commands;missing[0].ramps[0].from={std::nullopt,"missing"};
    rejects([&]{compileContinuousStyle(missing,{0,4},{1,1});});
    auto duplicate=commands;duplicate.push_back(commands[0]);
    CHECK(compileContinuousStyle(duplicate,{0,4},{1,1}).size()==3);
    CHECK(compileContinuousStyle({}, {0,4},{1,1}).size()==1);
    CHECK(compileContinuousStyle({}, {2,2},{1,1}).empty());
    auto plan = compileStyleDeck(commands, {{10, 14}}, {}, {{"out_gain", 1}}, {{"target", .5}});
    CHECK(plan.parameters[0].lane.valueAt(12) == .75);
    rejects([&] { compileStyleDeck(commands, {}, {}, {{"out_gain", 1}}); });
    rejects([&] { compileStyleDeck(commands, {{0, 4}}, {}, {}); });
    if (argc == 2) {
      std::ifstream stream(argv[1]); CHECK(stream.good());
      const std::string raw((std::istreambuf_iterator<char>(stream)), {});
      const auto real = loadTransitionStyles(raw);
      std::size_t count = 0, ramps = 0, mapped = 0;
      CHECK(real.size() == 14);
      std::size_t compiled=0;
      for (const auto& style:real) for (const auto* side:{&style.outgoing,&style.incoming}) {
        const auto output=compileContinuousStyle(*side,{100,108},{.95,1.05},{{"beat_length",.5}});
        CHECK(!output.empty() && output[0].parameter.id=="bypa");
        CHECK(ContinuousAutomationValues(output[0]).valueAt(100)==0);
        CHECK(ContinuousAutomationValues(output[0]).valueAt(108)==1);
        const auto map=schedulePlaybackTimeMap(0,100,std::nullopt,output);
        const auto stepped=compileSteppedAutomations(output,
            {0,map.fromSong(104).transition,map.fromSong(108).transition},
            SchedulingPolicy::stepped(.2),map);
        CHECK(!stepped.empty() && stepped[0].parameter.id=="bypa");
        for (const auto& automation:stepped) {
          CHECK(automation.parameter.id!="ts_rate" && !automation.points.empty());
          for (std::size_t i=1;i<automation.points.size();++i) {
            CHECK(automation.points[i].time.transition>automation.points[i-1].time.transition);
            CHECK(automation.points[i].value!=automation.points[i-1].value);
          }
        }
        const auto events=compileTimedEffectEvents(stepped);
        const auto volumeRamps=compileTimedVolumeRamps(stepped);
        for (const auto& ramp:volumeRamps)
          CHECK(compareMediaTimes(ramp.start,ramp.end)<=0);
        CHECK(!events.empty());
        for (std::size_t i=0;i<events.size();++i) {
          CHECK(!events[i].parameters.empty());
          if (i) CHECK(compareMediaTimes(events[i-1].time,events[i].time)<0);
        }
        compiled+=output.size()-1;
        for (std::size_t i=1;i<output.size();++i) {
          CHECK(output[i].points.size()==2);
          CHECK(output[i].points[0].songTime>=100 && output[i].points[1].songTime<=108);
          CHECK(output[i].points[0].songTime<=output[i].points[1].songTime);
        }
      }
      CHECK(compiled==74);
      for (const auto& style : real) for (const auto* side : {&style.outgoing, &style.incoming})
        for (const auto& instruction : *side) {
          ++count; ramps += instruction.ramps.size();
          for (const auto& r : instruction.ramps)
            mapped += r.from.parameterName.has_value() + r.to.parameterName.has_value();
        }
      CHECK(count == 55 && ramps == 74 && mapped == 2);
      std::cout << "Research catalog verified: 14/55/74; mapped values 2\n";
    }
    std::cout << "Schema and resolved-schedule tests passed; no DSP equivalence claim\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
