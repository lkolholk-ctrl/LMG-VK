#include "lmg/automix/planner_analysis.h"
#include "lmg/automix/planner_scoring.h"
#include <iostream>
#include <stdexcept>

using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while (false)
int main() {
  try {
    const auto song = decodeMediaApiSongAnalysis(R"({"data":[{
      "id":"1","type":"songs","attributes":{"durationInMillis":999000},
      "relationships":{
        "audio-analysis":{"data":[{"type":"audio-analysis","id":"a","attributes":{
          "key":{"main":{"tonic":"B#","mode":"major"},"beginning":{"tonic":"A","mode":"minor"},"ending":{"tonic":"F#","mode":"neutral"}},
          "loudnessCurve":{"value":[-4,-8,-12,-16]},
          "vocalActivity":[{"startInMilliseconds":0,"endInMilliseconds":1000,"strength":"high"}]
        }}]},
        "flexml-analysis":{"data":[{"type":"flexml-analysis","id":"f","attributes":{
          "videoEvents":{"score":[200,400,800],"timeInSeconds":[0,0.5,1]}
        }}]}
      } }]})", "1");
    const auto maps = preparePlannerAnalysisMaps(song, 2.0);
    CHECK(maps.loudness && maps.vocals && maps.flexEvents);
    CHECK(maps.tonality && maps.tonality->main.tonic==4 && maps.tonality->main.mode==0);
    CHECK(maps.tonality->beginning && maps.tonality->beginning->tonic==1);
    CHECK(maps.tonality->ending && maps.tonality->ending->mode==2);
    CHECK(plannerTonalityRelationship(maps.tonality->main,maps.tonality->beginning)==1);
    CHECK(plannerTonalityRelationship(maps.tonality->main,normalizeCloudTonality({"C","major"}))==0);
    CHECK(!normalizeCloudTonality({"c","major"}));
    CHECK(!normalizeCloudTonality({"C","dorian"}));
    CHECK(!normalizeCloudTonality({std::nullopt,"major"}));
    auto key=*song.audio->attributes->key;
    key.main.reset();CHECK(!normalizeCloudTonalityMap(key));
    key.main=CloudKey{"C","major"};key.beginning=CloudKey{"unknown","minor"};key.ending.reset();
    const auto partial=normalizeCloudTonalityMap(key);
    CHECK(partial && !partial->beginning && !partial->ending);
    CHECK(maps.loudness->size() == 4 && (*maps.loudness)[1].songTime == .5);
    CHECK(maps.vocals->size() == 1 && (*maps.vocals)[0].kindTag == 0);
    CHECK((*maps.vocals)[0].end == 1 && (*maps.vocals)[0].strengthTag == 3);
    CHECK(maps.flexEvents->size() == 3 && (*maps.flexEvents)[2].timeScale == FlexTimeScale::extraLong);
    CHECK(maps.structure && maps.structure->events.size() == 3);
    CHECK(maps.structure->events[0].songTime == 0);
    CHECK(maps.structure->events[2].songTime == 1);
    BeatMatchedScoreInputs score;
    score.normalTempoCompatible = matchPlannerTempos({120}, {120}, kPlannerTempoTolerance).compatible;
    score.leadingIncomingVocalSignificant = leadingIncomingVocalSignificant(&*maps.vocals, PlannerVocalWindow{0, .5});
    score.trailingIncomingLoudnessRatio = trailingIncomingLoudnessRatio(&*maps.loudness, true, {0, .5});
    score.barCountRatio = 1;
    CHECK(score.leadingIncomingVocalSignificant && score.trailingIncomingLoudnessRatio == .6);
    CHECK(beatMatchedStyleScore(8, score) == 4.5);
    // Missing duration is not replaced by catalog duration; missing analysis
    // is not silently converted to an available empty map.
    CHECK(!preparePlannerAnalysisMaps(song, std::nullopt).loudness);
    auto missing = song;
    missing.audio->attributes.reset(); missing.flex->attributes.reset();
    const auto absent = preparePlannerAnalysisMaps(missing, 2);
    CHECK(!absent.loudness && !absent.vocals && !absent.flexEvents);
    CHECK(!absent.structure && !absent.tonality);
    auto empty = song;
    empty.audio->attributes->vocalActivity = std::vector<CloudVocalActivity>{};
    const auto emptyMaps = preparePlannerAnalysisMaps(empty, 2);
    CHECK(emptyMaps.vocals && emptyMaps.vocals->empty());
    CHECK(!leadingIncomingVocalSignificant(&*emptyMaps.vocals, PlannerVocalWindow{0, .5}));
    std::cout << "Raw JSON to recovered analysis maps and scoring integration passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
