#include "lmg/automix/observation.h"
#include <nlohmann/json.hpp>
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
using Json = nlohmann::json;
namespace {
void check(bool value, const char* message) { if (!value) throw std::runtime_error(message); }
void reject(const std::string& input) {
  try { (void)describeSongAnalysis(input, "wanted"); }
  catch (const std::invalid_argument&) { return; }
  throw std::runtime_error("Invalid cloud input was accepted");
}
}
int main(int argc, char** argv) {
  try {
    const auto absent = Json::parse(describeSongAnalysis(
        R"({"data":[{"type":"songs","id":"wanted"}]})", "wanted"));
    check(absent.at("schemaVersion") == 1, "Snapshot version");
    check(absent.at("analysis") == Json{{"id", "wanted"}}, "Absent fields were synthesized");

    const auto snapshot = Json::parse(describeSongAnalysis(R"({"data":[
      {"type":"songs","id":"other"},
      {"type":"songs","id":"wanted","attributes":{
        "durationInMillis":1250.5,"supportsSmartTransitions":false,"isrc":"EXAMPLE","genreNames":["Тест 🎵"]},
       "relationships":{
         "audio-analysis":{"data":[{"type":"audio-analysis","id":"a"}]},
         "flexml-analysis":{"data":[{"type":"flexml-analysis","id":"f","attributes":{
           "entryPoints":[{"timeInSeconds":0.25,"fadeToBlack":0,"gainTimeInSeconds":[0,0.1],"gainValue":[0,1],"tags":["unknown"]}],
           "exitPoints":[],"videoEvents":{"score":[499],"timeInSeconds":[0.5]},
           "arousal":{"value":[]},"valence":{"value":[0.5]},"visualTempo":{"value":[42],"samplingFrequency":-1}
         }}]}}}],
      "included":[{"type":"audio-analysis","id":"a","attributes":{
        "bpm":{"main":120.25,"beginning":121,"percentDeviation":0},
        "beats":{"beatsInMilliseconds":[0,500.25],"barsInMilliseconds":[]},
        "key":{"main":{"tonic":"C#","mode":"future-mode"}},
        "acousticness":{"main":0},"danceability":{"ending":0.75},"melodicness":{"beginning":0.25},
        "energy":{"main":0.5},"valence":{"main":0.125},
        "loudness":{"main":{"value":-12,"range":4,"peak":0.25}},
        "loudnessCurve":{"value":[-10,-11]},
        "vocalActivity":[{"startInMilliseconds":100,"endInMilliseconds":200,"strength":"very-low","kind":"future-kind"}],
        "fades":{"fadeIn":{"startInMilliseconds":0},"fadeOut":{"endInMilliseconds":1250}},"phrases":[]
      }}]})", "wanted"));
    const auto& song = snapshot.at("analysis");
    check(song.at("durationInMillis") == 1250.5 && song.at("supportsSmartTransitions") == false, "Scalar transport");
    check(song.at("genreNames").at(0) == "Тест 🎵" && song.at("isrc") == "EXAMPLE", "UTF-8 transport");
    const auto& audio = song.at("audio").at("attributes");
    check(audio.at("bpm").at("main") == 120.25 && audio.at("bpm").at("percentDeviation") == 0, "BPM transport");
    check(!audio.at("bpm").contains("ending"), "Missing BPM became zero");
    check(audio.at("beats").at("beatsInMilliseconds").at(1) == 500.25, "Units changed");
    check(audio.at("beats").at("barsInMilliseconds").is_array() && audio.at("beats").at("barsInMilliseconds").empty(), "Empty array lost");
    check(audio.at("key").at("main").at("mode") == "future-mode", "Unknown enum coerced");
    check(audio.at("acousticness").at("main") == 0 && audio.at("danceability").at("ending") == 0.75, "Composite transport");
    check(audio.at("melodicness").at("beginning") == 0.25 && audio.at("energy").at("main") == 0.5, "Composite transport");
    check(audio.at("valence").at("main") == 0.125 && audio.at("loudness").at("main").at("peak") == 0.25, "Statistics transport");
    check(!audio.at("loudnessCurve").contains("samplingFrequency"), "Sampling frequency inferred");
    check(audio.at("vocalActivity").at(0).at("kind") == "future-kind", "Vocal enum transport");
    check(audio.at("fades").at("fadeOut").at("endInMilliseconds") == 1250 && audio.at("phrases").empty(), "Time ranges transport");
    const auto& flex = song.at("flex").at("attributes");
    check(flex.at("entryPoints").at(0).at("timeInSeconds") == 0.25, "Flex units changed");
    check(flex.at("entryPoints").at(0).at("gainValue").size() == 2 && flex.at("exitPoints").empty(), "Pivot transport");
    check(flex.at("videoEvents").at("score").at(0) == 499 && flex.at("arousal").at("value").empty(), "Flex samples transport");
    check(flex.at("visualTempo").at("samplingFrequency") == -1, "Raw unsupported value inferred");

    const auto unresolved = Json::parse(describeSongAnalysis(R"({"data":[{"type":"songs","id":"wanted",
      "relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"a"}]}}}]})", "wanted"));
    check(unresolved.at("analysis").at("audio") == Json{{"id", "a"}}, "Unresolved linkage became an empty analysis");
    reject(R"({"data":[],"data":[]})");
    reject(R"({"data":[{"type":"songs","id":"other"}]})");

    check(argc == 2, "Canonical catalog argument is mandatory");
    std::ifstream file(argv[1], std::ios::binary);
    check(static_cast<bool>(file), "Catalog missing");
    const std::string source{std::istreambuf_iterator<char>(file), {}};
    const auto catalog = Json::parse(describeTransitionStyles(source));
    check(catalog.at("schemaVersion") == 1 && catalog.at("styles").size() == 14, "Catalog snapshot");
    check(catalog.at("instructionCount") == 55 && catalog.at("automationCount") == 74, "Catalog counts");
    bool style4 = false, style33 = false;
    for (const auto& style : catalog.at("styles")) {
      if (style.at("id") == 4) style4 = style.at("name") == "Ease-in Ring-out";
      if (style.at("id") == 33) style33 = style.at("name") == "overlap + Reverb";
    }
    check(style4 && style33, "Catalog IDs were remapped");
    std::cout << "Observation snapshots: raw fields, UTF-8, absence, linkage, and canonical catalog passed\n";
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n'; return 1;
  }
}
