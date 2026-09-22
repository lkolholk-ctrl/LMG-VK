#include "lmg/automix/planner_analysis.h"

namespace lmg::automix {
std::optional<PlannerTonality> normalizeCloudTonality(const CloudKey& key) {
  if(!key.tonic||!key.mode)return std::nullopt;
  // Cloud getters -> MusicKit 21-case enum -> planner 12-case enum (27222693c).
  constexpr std::array<std::string_view,21> names{
    "Ab","A","A#","Bb","B","B#","Cb","C","C#","Db","D","D#",
    "Eb","E","E#","Fb","F","F#","Gb","G","G#"};
  constexpr std::array<std::uint8_t,21> values{0,1,2,2,3,4,3,4,5,5,6,7,7,8,9,8,9,10,10,11,0};
  std::uint8_t tonic=12,mode=3;
  for(std::size_t i=0;i<names.size();++i)if(*key.tonic==names[i]){tonic=values[i];break;}
  if(*key.mode=="major")mode=0;
  else if(*key.mode=="minor")mode=1;
  else if(*key.mode=="neutral")mode=2;
  if(tonic==12||mode==3)return std::nullopt;
  return PlannerTonality{tonic,mode};
}
std::optional<PlannerTonalityMap> normalizeCloudTonalityMap(const CloudComposite<CloudKey>& key) {
  if(!key.main)return std::nullopt;
  auto main=normalizeCloudTonality(*key.main);
  if(!main)return std::nullopt;
  return PlannerTonalityMap{*main,
    key.beginning?normalizeCloudTonality(*key.beginning):std::nullopt,
    key.ending?normalizeCloudTonality(*key.ending):std::nullopt};
}

PlannerAnalysisMaps preparePlannerAnalysisMaps(const CloudSongAnalysis& song,
                                               std::optional<double> songDurationSeconds) {
  PlannerAnalysisMaps result;
  if (song.audio && song.audio->attributes) {
    const auto& audio = *song.audio->attributes;
    if(audio.key)result.tonality=normalizeCloudTonalityMap(*audio.key);
    result.vocals = normalizeCloudVocalActivities(audio.vocalActivity);
    if (audio.loudnessCurve && audio.loudnessCurve->value)
      result.loudness = makePlannerLoudnessMap(*audio.loudnessCurve->value,
          audio.loudnessCurve->samplingFrequency, songDurationSeconds);
  }
  if (song.flex && song.flex->attributes && song.flex->attributes->videoEvents)
    result.flexEvents = normalizeCloudFlexEvents(*song.flex->attributes->videoEvents);
  if (result.flexEvents)
    result.structure = songStructureFromFlexEvents(*result.flexEvents);
  return result;
}
}  // namespace lmg::automix
