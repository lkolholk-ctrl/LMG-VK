#include "lmg/automix/media_analysis.h"
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
namespace {
const std::string minimal = R"({"data":[{"type":"songs","id":"wanted"}]})";
std::string response(const std::string& audio, const std::string& flex = "{}") {
  return R"({"data":[{"id":"wanted","type":"songs","attributes":{"durationInMillis":1250.5,"supportsSmartTransitions":false,"isrc":"EXAMPLE","genreNames":["Example"],"unrelated":{"anything":true}},"relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"analysis-a","attributes":)" + audio +
      R"(}]},"flexml-analysis":{"data":[{"type":"flexml-analysis","id":"analysis-f","attributes":)" + flex + R"(}]}}}]})";
}
void reject(const std::string& input, std::string_view id = "wanted") {
  bool thrown = false;
  try { (void)decodeMediaApiSongAnalysis(input, id); }
  catch (const std::invalid_argument&) { thrown = true; }
  assert(thrown);
}
} // namespace
int main(int argc, char** argv) {
  const auto empty = decodeMediaApiSongAnalysis(minimal, "wanted");
  assert(empty.id == "wanted" && !empty.audio && !empty.flex && !empty.durationInMillis && !empty.supportsSmartTransitions);
  const auto parsed = decodeMediaApiSongAnalysis(response(R"({
    "bpm":{"main":120.25,"beginning":121,"percentDeviation":1},
    "beats":{"beatsInMilliseconds":[0,500.25],"barsInMilliseconds":[]},
    "key":{"main":{"tonic":"C#","mode":"unmapped-future-mode"}},
    "acousticness":{"main":0.5},"danceability":{"ending":0.75},"melodicness":{"beginning":0.25},
    "loudness":{"main":{"value":-12,"peak":0.25,"range":4}},
    "loudnessCurve":{"value":[-10,-11]},
    "vocalActivity":[{"startInMilliseconds":100,"endInMilliseconds":200,"strength":"very-low"},
                     {"kind":"future-kind","strength":"future-strength"}],
    "fades":{"fadeOut":{"startInMilliseconds":1000,"endInMilliseconds":1250}},"phrases":[]
  })", R"({
    "entryPoints":[{"timeInSeconds":0.25,"gainTimeInSeconds":[0,0.1],"gainValue":[0,1],"tags":["intro","unknown"]}],
    "exitPoints":[{"timeInSeconds":1.25,"fadeToBlack":1.125}],
    "videoEvents":{"timeInSeconds":[0.25,0.5],"score":[499,200]},
    "visualTempo":{"value":[42.75],"samplingFrequency":-1},"arousal":{"value":[]}
  })"), "wanted");
  assert(parsed.durationInMillis == 1250.5 && parsed.supportsSmartTransitions == false);
  assert(parsed.isrc == "EXAMPLE" && parsed.genreNames->at(0) == "Example");
  assert(parsed.audio->id == "analysis-a" && parsed.flex->id == "analysis-f");
  const auto& a = *parsed.audio->attributes;
  assert(a.bpm->values.main == 120.25 && !a.bpm->values.ending && a.bpm->percentDeviation == 1);
  assert(a.beats->beatsInMilliseconds->at(1) == 500.25 && a.beats->barsInMilliseconds->empty());
  assert(a.key->main->mode == "unmapped-future-mode");
  assert(!a.loudnessCurve->samplingFrequency && a.loudnessCurve->value->at(1) == -11);
  assert(a.vocalActivity->at(0).strength == "very-low" && !a.vocalActivity->at(0).kind);
  assert(a.vocalActivity->at(1).kind == "future-kind");
  assert(a.fades->fadeOut->endInMilliseconds == 1250 && a.phrases->empty());
  const auto& f = *parsed.flex->attributes;
  assert(f.entryPoints->at(0).gainTimeInSeconds->at(1) == 0.1 && f.entryPoints->at(0).gainValue->at(1) == 1);
  assert(f.exitPoints->at(0).fadeToBlack == 1.125 && f.videoEvents->score->at(0) == 499);
  assert(f.visualTempo->samplingFrequency == -1 && f.arousal->value->empty());
  const auto included = decodeMediaApiSongAnalysis(R"({"data":[
    {"id":"other","type":"songs"},
    {"id":"wanted","type":"songs","relationships":{"audio-analysis":{"data":[{"id":"a","type":"audio-analysis"}]}}}],
    "included":[{"id":"a","type":"audio-analysis","attributes":{"bpm":{"main":99.5}}}]})", "wanted");
  assert(included.audio->attributes->bpm->values.main == 99.5);
  const auto unresolved = decodeMediaApiSongAnalysis(R"({"data":[{"id":"wanted","type":"songs","relationships":{
    "audio-analysis":{"data":[{"id":"a","type":"audio-analysis"}]},"flexml-analysis":{"data":[]}}}]})", "wanted");
  assert(unresolved.audio->id == "a" && !unresolved.audio->attributes && !unresolved.flex);
  const auto nullable = decodeMediaApiSongAnalysis(response(R"({"bpm":null,"vocalActivity":[]})"), "wanted");
  assert(!nullable.audio->attributes->bpm && nullable.audio->attributes->vocalActivity->empty());

  reject(minimal, "missing"); reject(minimal, ""); reject("{}"); reject("[]");
  reject(R"({"data":[],"data":[]})");
  reject(R"({"data":[{"type":"songs","id":"wanted"},{"type":"songs","id":"wanted"}]})");
  reject(R"({"data":[{"type":"songs","id":"wanted"}],"included":[{"type":"x","id":"a"},{"type":"x","id":"a"}]})");
  reject(response(R"({"bpm":{"main":1,"main":2}})"));
  reject(response(R"({"bpm":{"main":"120"}})"));
  reject(response(R"({"bpm":{"main":true}})"));
  reject(response(R"({"bpm":{"main":1e999}})"));
  reject(response(R"({"beats":{"beatsInMilliseconds":[null]}})"));
  reject(response(R"({"vocalActivity":[{"strength":0.5}]})"));
  reject(response(R"({"loudnessCurve":{"value":1}})"));
  reject(response("{}", R"({"entryPoints":[{"gainValue":1}]})"));
  reject(response("{}", R"({"videoEvents":{"score":["499"]}})"));
  reject(R"({"data":[{"id":"wanted","type":"songs","attributes":{"supportsSmartTransitions":1}}]})");
  reject(R"({"data":[{"id":"wanted","type":"songs","relationships":{"audio-analysis":{"data":[{"type":"songs","id":"wanted"}]}}}]})");
  reject(R"({"data":[{"id":"wanted","type":"songs","relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"a","attributes":{"bpm":{"main":100}}}]}}}],"included":[{"type":"audio-analysis","id":"a","attributes":{"bpm":{"main":101}}}]})");
  reject(std::string(4 * 1024 * 1024 + 1, ' '));
  reject(response(std::string(40, '[') + "0" + std::string(40, ']')));

  // Optional local capture; never copied into public test fixtures or logs.
  if (argc == 2) {
    std::ifstream file(argv[1]); assert(file);
    const std::string json{std::istreambuf_iterator<char>(file), {}};
    const auto live = decodeMediaApiSongAnalysis(json, "1488408568");
    assert(live.supportsSmartTransitions == true && live.durationInMillis == 201570);
    const auto& liveAudio = *live.audio->attributes;
    assert(liveAudio.beats->beatsInMilliseconds->size() == 574);
    assert(liveAudio.loudnessCurve->value->size() == 402 && !liveAudio.loudnessCurve->samplingFrequency);
    assert(liveAudio.vocalActivity->size() == 44 && !liveAudio.vocalActivity->front().kind);
    assert(live.flex->attributes->entryPoints->front().gainValue->size() == 2);
    assert(live.flex->attributes->videoEvents->score->size() == 570);
  }
  std::cout << "MediaAPI raw analysis decoding passed\n";
}
