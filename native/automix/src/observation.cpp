#include "lmg/automix/observation.h"
#include "lmg/automix/media_analysis.h"
#include "lmg/automix/styles.h"
#include <nlohmann/json.hpp>
#include <utility>

namespace lmg::automix {
namespace {
using Json = nlohmann::json;
template<class T> void scalar(Json& out, const char* name, const std::optional<T>& value) {
  if (value) out[name] = *value;
}
template<class T, class Encode>
void field(Json& out, const char* name, const std::optional<T>& value, Encode encode) {
  if (value) out[name] = encode(*value);
}
template<class T, class Encode> Json list(const std::vector<T>& values, Encode encode) {
  auto out = Json::array();
  for (const auto& value : values) out.push_back(encode(value));
  return out;
}
template<class T, class Encode> Json composite(const CloudComposite<T>& value, Encode encode) {
  auto out = Json::object();
  field(out, "main", value.main, encode);
  field(out, "beginning", value.beginning, encode);
  field(out, "ending", value.ending, encode);
  return out;
}
Json scalarComposite(const CloudComposite<double>& value) {
  return composite(value, [](double v) { return Json(v); });
}
Json timeRange(const CloudTimeRangeMs& value) {
  auto out = Json::object();
  scalar(out, "startInMilliseconds", value.startInMilliseconds);
  scalar(out, "endInMilliseconds", value.endInMilliseconds);
  return out;
}
Json samples(const CloudSampledValues& value) {
  auto out = Json::object();
  scalar(out, "value", value.value);
  scalar(out, "samplingFrequency", value.samplingFrequency);
  return out;
}
Json audio(const CloudAudioAnalysis& value) {
  auto out = Json::object();
  field(out, "bpm", value.bpm, [](const CloudBpm& v) {
    auto j = scalarComposite(v.values);
    scalar(j, "percentDeviation", v.percentDeviation);
    return j;
  });
  field(out, "beats", value.beats, [](const CloudBeats& v) {
    auto j = Json::object();
    scalar(j, "beatsInMilliseconds", v.beatsInMilliseconds);
    scalar(j, "barsInMilliseconds", v.barsInMilliseconds);
    return j;
  });
  field(out, "key", value.key, [](const CloudComposite<CloudKey>& v) {
    return composite(v, [](const CloudKey& k) {
      auto j = Json::object(); scalar(j, "tonic", k.tonic); scalar(j, "mode", k.mode); return j;
    });
  });
  field(out, "acousticness", value.acousticness, scalarComposite);
  field(out, "danceability", value.danceability, scalarComposite);
  field(out, "melodicness", value.melodicness, scalarComposite);
  field(out, "energy", value.energy, scalarComposite);
  field(out, "valence", value.valence, scalarComposite);
  field(out, "loudness", value.loudness, [](const CloudComposite<CloudStatistics>& v) {
    return composite(v, [](const CloudStatistics& s) {
      auto j = Json::object();
      scalar(j, "value", s.value); scalar(j, "range", s.range); scalar(j, "peak", s.peak);
      return j;
    });
  });
  field(out, "loudnessCurve", value.loudnessCurve, samples);
  field(out, "vocalActivity", value.vocalActivity, [](const auto& values) {
    return list(values, [](const CloudVocalActivity& v) {
      auto j = timeRange(v.time); scalar(j, "strength", v.strength); scalar(j, "kind", v.kind); return j;
    });
  });
  field(out, "fades", value.fades, [](const CloudFades& v) {
    auto j = Json::object(); field(j, "fadeIn", v.fadeIn, timeRange); field(j, "fadeOut", v.fadeOut, timeRange); return j;
  });
  field(out, "phrases", value.phrases, [](const auto& values) { return list(values, timeRange); });
  return out;
}
Json pivot(const CloudPivotPoint& value) {
  auto out = Json::object();
  scalar(out, "timeInSeconds", value.timeInSeconds);
  scalar(out, "fadeToBlack", value.fadeToBlack);
  scalar(out, "gainTimeInSeconds", value.gainTimeInSeconds);
  scalar(out, "gainValue", value.gainValue);
  scalar(out, "tags", value.tags);
  return out;
}
Json flex(const CloudFlexAnalysis& value) {
  auto out = Json::object();
  field(out, "entryPoints", value.entryPoints, [](const auto& values) { return list(values, pivot); });
  field(out, "exitPoints", value.exitPoints, [](const auto& values) { return list(values, pivot); });
  field(out, "videoEvents", value.videoEvents, [](const CloudVideoEvents& v) {
    auto j = Json::object(); scalar(j, "score", v.score); scalar(j, "timeInSeconds", v.timeInSeconds); return j;
  });
  field(out, "arousal", value.arousal, samples);
  field(out, "valence", value.valence, samples);
  field(out, "visualTempo", value.visualTempo, samples);
  return out;
}
template<class T, class Encode> Json resource(const CloudAnalysisResource<T>& value, Encode encode) {
  Json out{{"id", value.id}};
  field(out, "attributes", value.attributes, encode);
  return out;
}
} // namespace

std::string describeSongAnalysis(std::string_view input, std::string_view songId) {
  const auto value = decodeMediaApiSongAnalysis(input, songId);
  Json out{{"id", value.id}};
  scalar(out, "durationInMillis", value.durationInMillis);
  scalar(out, "supportsSmartTransitions", value.supportsSmartTransitions);
  scalar(out, "isrc", value.isrc);
  scalar(out, "genreNames", value.genreNames);
  field(out, "audio", value.audio, [](const auto& v) { return resource(v, audio); });
  field(out, "flex", value.flex, [](const auto& v) { return resource(v, flex); });
  return Json{{"schemaVersion", 1}, {"analysis", std::move(out)}}.dump();
}

std::string describeTransitionStyles(std::string_view input) {
  const auto styles = loadTransitionStyles(input);
  auto descriptors = Json::array();
  std::size_t instructions = 0, automations = 0;
  for (const auto& style : styles) {
    const auto instructionCount = style.outgoing.size() + style.incoming.size();
    std::size_t automationCount = 0;
    for (const auto& i : style.outgoing) automationCount += i.ramps.size();
    for (const auto& i : style.incoming) automationCount += i.ramps.size();
    Json descriptor{{"id", style.id}, {"name", style.name},
                    {"instructionCount", instructionCount}, {"automationCount", automationCount}};
    scalar(descriptor, "duration", style.duration);
    field(descriptor, "offset", style.offset, [](const StyleTime& v) {
      Json j{{"relative", v.relative}}; scalar(j, "offsetInSeconds", v.offsetInSeconds); return j;
    });
    descriptors.push_back(std::move(descriptor));
    instructions += instructionCount;
    automations += automationCount;
  }
  return Json{{"schemaVersion", 1}, {"styles", std::move(descriptors)},
              {"instructionCount", instructions}, {"automationCount", automations}}.dump();
}
} // namespace lmg::automix
