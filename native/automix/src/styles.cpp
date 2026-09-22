#include "lmg/automix/styles.h"
#include "nlohmann/json.hpp"
#include <cmath>
#include <limits>
#include <set>
#include <stdexcept>

namespace lmg::automix {
namespace {
using Json = nlohmann::json;
void require(bool condition, const char* message) {
  if (!condition) throw std::invalid_argument(message);
}
void keys(const Json& j, std::initializer_list<const char*> required,
          std::initializer_list<const char*> optional = {}) {
  require(j.is_object(), "Expected style object");
  std::set<std::string> allowed;
  for (auto k : required) { require(j.contains(k), "Missing style field"); allowed.insert(k); }
  for (auto k : optional) allowed.insert(k);
  for (auto it = j.begin(); it != j.end(); ++it)
    require(allowed.count(it.key()) != 0, "Unknown style field");
}
double number(const Json& j) {
  require(j.is_number(), "Expected numeric style value");
  const double value = j.get<double>();
  require(std::isfinite(value), "Nonfinite style value"); return value;
}
int integer(const Json& j) {
  require(j.is_number_integer(), "Expected integer style value");
  const double value = number(j);
  require(value >= 0 && value <= std::numeric_limits<int>::max(), "Invalid style integer");
  return j.get<int>();
}
std::string string(const Json& j) {
  require(j.is_string(), "Expected style string");
  auto s = j.get<std::string>(); require(!s.empty(), "Empty style string"); return s;
}
StyleTime time(const Json& j) {
  keys(j, {"relative"}, {"offsetInSeconds"});
  StyleTime t{number(j.at("relative")), std::nullopt};
  require(t.relative >= 0 && t.relative <= 1, "Relative style time outside [0, 1]");
  if (j.contains("offsetInSeconds")) t.offsetInSeconds = number(j.at("offsetInSeconds"));
  return t;
}
StyleTime timeSpec(const Json& j) { keys(j, {"default"}); return time(j.at("default")); }
StyleValue value(const Json& j) {
  keys(j, {}, {"default", "parameterName"});
  require(!j.empty(), "Empty style value");
  StyleValue v;
  if (j.contains("default")) v.fallback = number(j.at("default"));
  if (j.contains("parameterName")) v.parameterName = string(j.at("parameterName"));
  return v;
}
std::vector<StyleInstruction> instructions(const Json& j) {
  require(j.is_array(), "Expected instruction array");
  std::vector<StyleInstruction> result;
  for (const auto& i : j) {
    keys(i, {"name", "placement", "automations"});
    const auto name = string(i.at("name"));
    require(name == "TimeStretching" || name == "AULowpass" || name == "AUHipass" ||
            name == "AUDelay" || name == "AUReverb2" || name == "GAIN", "Unknown instruction");
    const auto& placement = i.at("placement"); keys(placement, {"start", "end"});
    StyleInstruction parsed{name, time(placement.at("start")), time(placement.at("end")), {}};
    const auto& ramps = i.at("automations"); require(ramps.is_array(), "Expected automations array");
    for (const auto& r : ramps) {
      keys(r, {"parameterId", "startTime", "endTime", "startValue", "endValue", "interpolation"});
      auto interpolation = string(r.at("interpolation"));
      styleCurveByte(interpolation);
      parsed.ramps.push_back({string(r.at("parameterId")), timeSpec(r.at("startTime")),
          timeSpec(r.at("endTime")), value(r.at("startValue")), value(r.at("endValue")), interpolation});
    }
    result.push_back(std::move(parsed));
  }
  return result;
}
double resolve(const StyleValue& value, const std::map<std::string, double>& mapped) {
  std::optional<double> result;
  if (value.parameterName) {
    const auto it = mapped.find(*value.parameterName);
    if (it != mapped.end()) result = it->second;
  }
  if (!result) result = value.fallback;
  require(result.has_value(), "Unresolved mapped style value");
  require(std::isfinite(*result), "Nonfinite mapped style value");
  return *result;
}
} // namespace

std::vector<TransitionStyle> loadTransitionStyles(std::string_view input) {
  require(input.size() <= 1024 * 1024, "Style catalog exceeds 1 MiB");
  try {
    std::vector<std::set<std::string>> objectKeys;
    auto callback = [&](int depth, Json::parse_event_t event, Json& parsed) {
      require(depth <= 32, "Style JSON nesting exceeds 32");
      if (event == Json::parse_event_t::object_start) objectKeys.emplace_back();
      if (event == Json::parse_event_t::key)
        require(objectKeys.back().insert(parsed.get<std::string>()).second, "Duplicate JSON key");
      if (event == Json::parse_event_t::object_end) objectKeys.pop_back();
      return true;
    };
    const auto json = Json::parse(input.begin(), input.end(), callback);
    require(json.is_array(), "Style catalog must be an array");
    std::set<int> ids;
    std::vector<TransitionStyle> result;
    for (const auto& s : json) {
      keys(s, {"id", "name", "instructions"}, {"offset", "duration"});
      TransitionStyle style{integer(s.at("id")), string(s.at("name")), {}, {}, {}, {}};
      require(ids.insert(style.id).second, "Duplicate style ID");
      if (s.contains("offset")) style.offset = time(s.at("offset"));
      if (s.contains("duration")) style.duration = integer(s.at("duration"));
      const auto& sides = s.at("instructions"); keys(sides, {"outgoing", "incoming"});
      style.outgoing = instructions(sides.at("outgoing"));
      style.incoming = instructions(sides.at("incoming"));
      result.push_back(std::move(style));
    }
    return result;
  } catch (const Json::exception& e) {
    throw std::invalid_argument(std::string("Invalid style JSON: ") + e.what());
  }
}

DeckSchedule compileStyleDeck(const std::vector<StyleInstruction>& instructions,
    const std::vector<FrameWindow>& windows, EffectSettings settings,
    const std::map<std::string, double>& initial, const std::map<std::string, double>& mapped) {
  std::size_t count = 0;
  for (const auto& i : instructions) count += i.ramps.size();
  require(count == windows.size(), "Resolve every automation window explicitly");
  std::map<std::string, std::vector<Ramp>> grouped;
  std::size_t index = 0;
  for (const auto& i : instructions) for (const auto& r : i.ramps) {
    const auto window = windows[index++];
    Ramp ramp{window.begin, window.end, resolve(r.from, mapped), resolve(r.to, mapped),
              styleCurveByte(r.interpolation)};
    ramp.validate(); grouped[r.parameterId].push_back(ramp);
  }
  DeckSchedule result{settings, {}};
  for (auto& item : grouped) {
    const auto it = initial.find(item.first);
    require(it != initial.end(), "Explicit initial value required for scheduled parameter");
    result.parameters.push_back({item.first, AutomationLane(it->second, std::move(item.second))});
  }
  // Preserve explicit unscheduled initial values, including out_gain for empty sides.
  for (const auto& item : initial) if (!grouped.count(item.first))
    result.parameters.push_back({item.first, AutomationLane(item.second)});
  return result;
}
} // namespace lmg::automix
