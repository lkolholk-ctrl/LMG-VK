#pragma once

#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace lmg::automix {

// Raw cloud values, not normalized MusicKit/planner analysis. Missing and JSON
// null optional fields both mean unavailable; explicit empty arrays stay present.
template<class T> struct CloudComposite {
  std::optional<T> main, beginning, ending;
};
struct CloudBpm {
  CloudComposite<double> values;
  std::optional<double> percentDeviation;
};
struct CloudKey { std::optional<std::string> tonic, mode; };
struct CloudStatistics { std::optional<double> value, range, peak; };
struct CloudTimeRangeMs { std::optional<double> startInMilliseconds, endInMilliseconds; };
struct CloudBeats {
  std::optional<std::vector<double>> beatsInMilliseconds, barsInMilliseconds;
};
struct CloudSampledValues {
  std::optional<std::vector<double>> value;
  std::optional<double> samplingFrequency;
};
struct CloudVocalActivity {
  CloudTimeRangeMs time;
  std::optional<std::string> strength, kind;
};
struct CloudFades { std::optional<CloudTimeRangeMs> fadeIn, fadeOut; };
struct CloudAudioAnalysis {
  std::optional<CloudBpm> bpm;
  std::optional<CloudBeats> beats;
  std::optional<CloudComposite<CloudKey>> key;
  std::optional<CloudComposite<double>> acousticness, danceability, melodicness, energy, valence;
  std::optional<CloudComposite<CloudStatistics>> loudness;
  std::optional<CloudSampledValues> loudnessCurve;
  std::optional<std::vector<CloudVocalActivity>> vocalActivity;
  std::optional<CloudFades> fades;
  std::optional<std::vector<CloudTimeRangeMs>> phrases;
};
struct CloudPivotPoint {
  std::optional<double> timeInSeconds, fadeToBlack;
  std::optional<std::vector<double>> gainTimeInSeconds, gainValue;
  std::optional<std::vector<std::string>> tags;
};
struct CloudVideoEvents {
  std::optional<std::vector<double>> score, timeInSeconds;
};
struct CloudFlexAnalysis {
  std::optional<std::vector<CloudPivotPoint>> entryPoints, exitPoints;
  std::optional<CloudVideoEvents> videoEvents;
  std::optional<CloudSampledValues> arousal, valence, visualTempo;
};
template<class T> struct CloudAnalysisResource {
  std::string id;
  // Linkage without embedded/included attributes remains explicitly unresolved.
  std::optional<T> attributes;
};
struct CloudSongAnalysis {
  std::string id;
  std::optional<double> durationInMillis;
  std::optional<bool> supportsSmartTransitions;
  std::optional<std::string> isrc;
  std::optional<std::vector<std::string>> genreNames;
  std::optional<CloudAnalysisResource<CloudAudioAnalysis>> audio;
  std::optional<CloudAnalysisResource<CloudFlexAnalysis>> flex;
};

// Selects exactly the requested songs resource; never substitutes the first song.
// Resolves analysis relationship data inline or by matching included type+id.
// Unrelated catalog fields are tolerated. Invalid known types, duplicate JSON
// keys/resource identities, nonfinite numbers, >4 MiB or depth>32 are rejected.
// No units, enums, frequency, beat-grid or planner eligibility are inferred.
CloudSongAnalysis decodeMediaApiSongAnalysis(std::string_view json,
                                           std::string_view requestedSongId);

} // namespace lmg::automix
