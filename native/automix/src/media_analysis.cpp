#include "lmg/automix/media_analysis.h"
#include <nlohmann/json.hpp>
#include <cmath>
#include <map>
#include <set>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
using Json = nlohmann::json;
void require(bool okay, const char* message) {
  if (!okay) throw std::invalid_argument(message);
}
const Json& object(const Json& value) {
  require(value.is_object(), "Expected analysis JSON object"); return value;
}
double number(const Json& value) {
  require(value.is_number(), "Expected analysis number");
  const double result = value.get<double>();
  require(std::isfinite(result), "Nonfinite analysis number"); return result;
}
std::string string(const Json& value) {
  require(value.is_string(), "Expected analysis string"); return value.get<std::string>();
}
bool boolean(const Json& value) {
  require(value.is_boolean(), "Expected analysis boolean"); return value.get<bool>();
}
template<class Decode>
auto optional(const Json& obj, const char* key, Decode decode)
    -> std::optional<decltype(decode(obj))> {
  object(obj);
  const auto found = obj.find(key);
  if (found == obj.end() || found->is_null()) return std::nullopt;
  return decode(*found);
}
template<class Decode>
auto array(const Json& value, Decode decode) -> std::vector<decltype(decode(value))> {
  require(value.is_array(), "Expected analysis array");
  std::vector<decltype(decode(value))> result;
  result.reserve(value.size());
  for (const auto& entry : value) result.push_back(decode(entry));
  return result;
}
auto numbers(const Json& value) { return array(value, number); }
auto strings(const Json& value) { return array(value, string); }
template<class Decode>
auto composite(const Json& value, Decode decode) -> CloudComposite<decltype(decode(value))> {
  return {optional(value, "main", decode), optional(value, "beginning", decode), optional(value, "ending", decode)};
}
auto scalarComposite(const Json& value) { return composite(value, number); }
CloudKey key(const Json& j) { return {optional(j, "tonic", string), optional(j, "mode", string)}; }
CloudStatistics statistics(const Json& j) {
  return {optional(j, "value", number), optional(j, "range", number), optional(j, "peak", number)};
}
CloudTimeRangeMs timeRange(const Json& j) {
  return {optional(j, "startInMilliseconds", number), optional(j, "endInMilliseconds", number)};
}
CloudSampledValues samples(const Json& j) {
  return {optional(j, "value", numbers), optional(j, "samplingFrequency", number)};
}
CloudVocalActivity vocal(const Json& j) {
  return {timeRange(j), optional(j, "strength", string), optional(j, "kind", string)};
}
CloudAudioAnalysis audio(const Json& j) {
  object(j);
  CloudAudioAnalysis a;
  a.bpm = optional(j, "bpm", [](const Json& x) {
    return CloudBpm{scalarComposite(x), optional(x, "percentDeviation", number)};
  });
  a.beats = optional(j, "beats", [](const Json& x) {
    return CloudBeats{optional(x, "beatsInMilliseconds", numbers), optional(x, "barsInMilliseconds", numbers)};
  });
  a.key = optional(j, "key", [](const Json& x) { return composite(x, key); });
  a.acousticness = optional(j, "acousticness", scalarComposite);
  a.danceability = optional(j, "danceability", scalarComposite);
  a.melodicness = optional(j, "melodicness", scalarComposite);
  a.energy = optional(j, "energy", scalarComposite);
  a.valence = optional(j, "valence", scalarComposite);
  a.loudness = optional(j, "loudness", [](const Json& x) { return composite(x, statistics); });
  a.loudnessCurve = optional(j, "loudnessCurve", samples);
  a.vocalActivity = optional(j, "vocalActivity", [](const Json& x) { return array(x, vocal); });
  a.fades = optional(j, "fades", [](const Json& x) {
    return CloudFades{optional(x, "fadeIn", timeRange), optional(x, "fadeOut", timeRange)};
  });
  a.phrases = optional(j, "phrases", [](const Json& x) { return array(x, timeRange); });
  return a;
}
CloudPivotPoint pivot(const Json& j) {
  return {optional(j, "timeInSeconds", number), optional(j, "fadeToBlack", number),
          optional(j, "gainTimeInSeconds", numbers), optional(j, "gainValue", numbers), optional(j, "tags", strings)};
}
CloudFlexAnalysis flex(const Json& j) {
  object(j);
  CloudFlexAnalysis f;
  f.entryPoints = optional(j, "entryPoints", [](const Json& x) { return array(x, pivot); });
  f.exitPoints = optional(j, "exitPoints", [](const Json& x) { return array(x, pivot); });
  f.videoEvents = optional(j, "videoEvents", [](const Json& x) {
    return CloudVideoEvents{optional(x, "score", numbers), optional(x, "timeInSeconds", numbers)};
  });
  f.arousal = optional(j, "arousal", samples);
  f.valence = optional(j, "valence", samples);
  f.visualTempo = optional(j, "visualTempo", samples);
  return f;
}
using Identity = std::pair<std::string, std::string>; // type, id
Identity identity(const Json& resource) {
  object(resource);
  const auto type = string(resource.at("type")), id = string(resource.at("id"));
  require(!type.empty() && !id.empty(), "Empty MediaAPI resource identity");
  return {type, id};
}
using Resources = std::map<Identity, const Json*>;
template<class Decode>
auto relationship(const Json& song, const char* name, const Resources& included, Decode decode)
    -> std::optional<CloudAnalysisResource<decltype(decode(song))>> {
  const auto relations = song.find("relationships");
  if (relations == song.end() || relations->is_null()) return std::nullopt;
  object(*relations);
  const auto relation = relations->find(name);
  if (relation == relations->end() || relation->is_null()) return std::nullopt;
  object(*relation);
  const auto data = relation->find("data");
  if (data == relation->end() || data->is_null()) return std::nullopt;
  require(data->is_array() && data->size() <= 1, "Expected at most one analysis relationship resource");
  if (data->empty()) return std::nullopt;
  const auto& resource = data->front();
  const auto id = identity(resource);
  require(id.first == name, "Wrong analysis relationship resource type");
  auto attributes = optional(resource, "attributes", decode);
  const auto found = included.find(id);
  if (found != included.end()) {
    auto resolved = optional(*found->second, "attributes", decode);
    if (attributes && resolved)
      require(resource.at("attributes") == found->second->at("attributes"), "Conflicting embedded and included analysis");
    if (!attributes) attributes = std::move(resolved);
  }
  return CloudAnalysisResource<decltype(decode(song))>{id.second, std::move(attributes)};
}
} // namespace

CloudSongAnalysis decodeMediaApiSongAnalysis(std::string_view input, std::string_view songId) {
  require(!songId.empty(), "Requested song ID is empty");
  require(input.size() <= 4 * 1024 * 1024, "MediaAPI JSON exceeds 4 MiB");
  try {
    std::vector<std::set<std::string>> keys;
    auto callback = [&](int depth, Json::parse_event_t event, Json& parsed) {
      require(depth <= 32, "MediaAPI JSON nesting exceeds 32");
      if (event == Json::parse_event_t::object_start) keys.emplace_back();
      if (event == Json::parse_event_t::key)
        require(keys.back().insert(parsed.get<std::string>()).second, "Duplicate MediaAPI JSON key");
      if (event == Json::parse_event_t::object_end) keys.pop_back();
      if (event == Json::parse_event_t::value && parsed.is_number()) number(parsed);
      return true;
    };
    const auto root = Json::parse(input.begin(), input.end(), callback);
    object(root);
    const auto& data = root.at("data");
    require(data.is_array(), "MediaAPI data must be an array");
    Resources included;
    if (root.contains("included")) {
      require(root.at("included").is_array(), "MediaAPI included must be an array");
      for (const auto& entry : root.at("included"))
        require(included.emplace(identity(entry), &entry).second, "Duplicate included resource identity");
    }
    const Json* selected = nullptr;
    std::set<Identity> seen;
    for (const auto& resource : data) {
      const auto id = identity(resource);
      require(seen.insert(id).second, "Duplicate data resource identity");
      if (id.first == "songs" && id.second == songId) selected = &resource;
    }
    require(selected != nullptr, "Requested song is missing from MediaAPI data");
    CloudSongAnalysis result;
    result.id = std::string(songId);
    if (selected->contains("attributes") && !selected->at("attributes").is_null()) {
      const auto& attributes = selected->at("attributes");
      result.durationInMillis = optional(attributes, "durationInMillis", number);
      result.supportsSmartTransitions = optional(attributes, "supportsSmartTransitions", boolean);
      result.isrc = optional(attributes, "isrc", string);
      result.genreNames = optional(attributes, "genreNames", strings);
    }
    result.audio = relationship(*selected, "audio-analysis", included, audio);
    result.flex = relationship(*selected, "flexml-analysis", included, flex);
    return result;
  } catch (const Json::exception& error) {
    throw std::invalid_argument(std::string("Invalid MediaAPI analysis JSON: ") + error.what());
  }
}
} // namespace lmg::automix
