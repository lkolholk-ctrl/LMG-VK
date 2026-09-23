#include "lmg/automix/planner_observation.h"
#include <cmath>
#include <cstring>
#include <fstream>
#include <functional>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
namespace {
void check(bool condition) { if (!condition) throw std::runtime_error("check failed"); }
CloudSongAnalysis uniform(std::size_t bars = 8, int beats = 4, double beatSeconds = .5) {
  CloudSongAnalysis s; s.id = "synthetic"; s.durationInMillis = 16000;
  s.supportsSmartTransitions = true;
  CloudVideoEvents v; v.timeInSeconds.emplace(); v.score.emplace();
  for (std::size_t i = 0; i <= bars * static_cast<std::size_t>(beats); ++i) {
    v.timeInSeconds->push_back(static_cast<double>(i) * beatSeconds);
    v.score->push_back(i % (4 * beats) == 0 ? 800 : i % (2 * beats) == 0 ? 600 : i % beats == 0 ? 400 : 200);
  }
  CloudFlexAnalysis f; f.videoEvents = v;
  s.flex = CloudAnalysisResource<CloudFlexAnalysis>{"flex", f};
  CloudAudioAnalysis a;
  a.key = CloudComposite<CloudKey>{CloudKey{"C", "major"}, CloudKey{"Db", "minor"}, CloudKey{"unknown", "major"}};
  a.loudnessCurve = CloudSampledValues{std::vector<double>{-10, -20, -30, -40}, 2};
  a.vocalActivity = std::vector<CloudVocalActivity>{{{500, 1500}, "unknown", "unknown"}};
  s.audio = CloudAnalysisResource<CloudAudioAnalysis>{"audio", a};
  return s;
}
CloudVideoEvents& video(CloudSongAnalysis& s) { return *s.flex->attributes->videoEvents; }
CloudSongAnalysis manyRuns() {
  auto s = uniform(); auto& v = video(s); v.timeInSeconds->clear(); v.score->clear();
  double time = 0; v.timeInSeconds->push_back(time); v.score->push_back(400);
  for (int run = 0; run < 20; ++run) for (int bar = 0; bar < 5; ++bar) {
    time += 2 + run * .1;
    v.timeInSeconds->push_back(time); v.score->push_back(400);
  }
  return s;
}
bool has(const PlannerSongPreparation& p, PlannerPreparationIssue issue) {
  return p.issues & static_cast<std::uint64_t>(issue);
}
void big(std::ostream& out, std::uint64_t value, int bytes) {
  for (int i = bytes - 1; i >= 0; --i) out.put(static_cast<char>((value >> (8 * i)) & 255));
}
void exportSnapshots(const char* path) {
  auto s = uniform(); auto full = preparePlannerSongObservation(s, 16000);
  CloudSongAnalysis absent; absent.id = "absent";
  auto missing = preparePlannerSongObservation(absent, std::nullopt);
  auto emptySong = s; video(emptySong).score->clear(); video(emptySong).timeInSeconds->clear();
  emptySong.audio->attributes->vocalActivity = std::vector<CloudVocalActivity>{};
  emptySong.audio->attributes->loudnessCurve->value = std::vector<double>{};
  auto empty = preparePlannerSongObservation(emptySong, 16000);
  auto large = s; video(large).timeInSeconds->resize(4097); video(large).score->resize(4097, 400);
  auto limited = preparePlannerSongObservation(large, 16000);
  auto preview = preparePlannerSongObservation(manyRuns(), 1000000);
  auto negativeZero = s; video(negativeZero).timeInSeconds->front() = -0.0;
  auto zero = preparePlannerSongObservation(negativeZero, 16000);
  auto unknown = preparePlannerSongObservation(s, std::nullopt);
  std::vector<std::pair<std::string, std::vector<std::int64_t>>> cases{
    {"uniform", encodePlannerPreparationPair(full, full)},
    {"missing", encodePlannerPreparationPair(missing, missing)},
    {"empty", encodePlannerPreparationPair(empty, empty)},
    {"limit", encodePlannerPreparationPair(limited, full)},
    {"preview", encodePlannerPreparationPair(preview, full)},
    {"negative_zero", encodePlannerPreparationPair(zero, full)},
    {"unknown_duration", encodePlannerPreparationPair(unknown, full)},
  };
  std::ofstream out(path, std::ios::binary); if (!out) throw std::runtime_error("Snapshot file open failed");
  out.write("AMX3A001", 8); big(out, cases.size(), 4);
  for (const auto& c : cases) {
    big(out, c.first.size(), 4); out.write(c.first.data(), static_cast<std::streamsize>(c.first.size()));
    big(out, c.second.size(), 4);
    for (auto value : c.second) big(out, static_cast<std::uint64_t>(value), 8);
  }
  if (!out) throw std::runtime_error("Snapshot file write failed");
}
}
int main(int argc, char** argv) {
  int passed = 0;
  auto run = [&](const char* name, const std::function<void()>& f) { f(); ++passed; std::cout << "PASS " << name << '\n'; };
  try {
    run("native_maps_and_event_references", [] {
      const auto p = preparePlannerSongObservation(uniform(), 16000);
      check(p.status == PlannerPreparationStatus::prepared && p.durationMs == 16000);
      check(p.maps.tonality->main.tonic == 4 && p.maps.tonality->main.mode == 0);
      check(p.maps.tonality->beginning->tonic == 5 && !p.maps.tonality->ending);
      check(p.maps.loudness->size() == 4 && p.maps.loudness->at(1).songTime == .5);
      check(p.maps.vocals->size() == 1 && p.maps.vocals->front().start == .5);
      check(p.maps.vocals->front().strengthTag == 2 && p.maps.vocals->front().kindTag == 0);
      const auto& s = *p.maps.structure;
      check(s.events.size() == 33 && s.downbeatEvents.size() == 9 && s.bars.size() == 8);
      check(s.segments.size() == 4 && s.sections.size() == 2 && s.beatStabilityMap.size() == 1);
      const auto& r = p.inventory->stablePreview.at(0);
      check(r.events.startEvent == 0 && r.events.endEvent == 32 && r.startSeconds == 0 && r.endSeconds == 16);
      check(r.beatsPerBar == 4 && r.averageTempoBpm == 120 && r.trackBounds == 1);
    });
    run("missing_is_not_empty", [] {
      CloudSongAnalysis s; s.id = "absent"; const auto p = preparePlannerSongObservation(s, 16000);
      check(p.status == PlannerPreparationStatus::insufficient && !p.maps.flexEvents && !p.maps.structure);
      check(has(p, PlannerPreparationIssue::audioAbsent) && has(p, PlannerPreparationIssue::flexAbsent));
    });
    run("linkage_is_not_resolved", [] {
      auto s = uniform(); s.audio->attributes.reset(); s.flex->attributes.reset();
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.audioState == 1 && p.flexState == 1 && !p.maps.structure && !p.maps.vocals);
      check(has(p, PlannerPreparationIssue::flexUnresolved));
    });
    run("missing_video_events", [] {
      auto s = uniform(); s.flex->attributes->videoEvents.reset();
      check(has(preparePlannerSongObservation(s, 16000), PlannerPreparationIssue::videoEventsMissing));
    });
    run("missing_event_arrays", [] {
      auto s = uniform(); video(s).score.reset();
      const auto p = preparePlannerSongObservation(s, 16000);
      check(has(p, PlannerPreparationIssue::eventArraysMissing) && !p.maps.flexEvents);
    });
    run("empty_arrays_preserve_source_optionals", [] {
      auto s = uniform(); video(s).score->clear(); video(s).timeInSeconds->clear();
      s.audio->attributes->vocalActivity = std::vector<CloudVocalActivity>{};
      s.audio->attributes->loudnessCurve->value = std::vector<double>{};
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.maps.structure && p.maps.structure->events.empty() && p.maps.flexEvents->empty());
      check(p.maps.vocals && p.maps.vocals->empty() && !p.maps.loudness);
      check(has(p, PlannerPreparationIssue::emptyStructure));
    });
    run("cloud_bpm_and_audio_beats_are_not_grid_inputs", [] {
      auto s = uniform(); CloudBpm b; b.values.main = 13; s.audio->attributes->bpm = b;
      s.audio->attributes->beats = CloudBeats{std::vector<double>{99999}, std::vector<double>{8, 9}};
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.maps.structure->events.size() == 33 && p.inventory->stablePreview[0].averageTempoBpm == 120);
    });
    run("four_vs_five_bar_source_boundary", [] {
      const auto a = preparePlannerSongObservation(uniform(4), 16000);
      const auto b = preparePlannerSongObservation(uniform(5), 16000);
      check(a.maps.structure->beatStabilityMap.empty() && b.maps.structure->beatStabilityMap.size() == 1);
    });
    run("meter_is_not_hardcoded_to_four", [] {
      const auto p = preparePlannerSongObservation(uniform(8, 3), 16000);
      check(p.inventory->stablePreview[0].beatsPerBar == 3 && p.inventory->stablePreview[0].endSeconds == 12);
    });
    run("unknown_duration_never_uses_catalog_fallback", [] {
      const auto p = preparePlannerSongObservation(uniform(), std::nullopt);
      check(!p.durationMs && p.maps.structure && p.maps.loudness && p.inventory->boundedStableRegions == 0);
      check(p.inventory->stablePreview[0].trackBounds == 0 && p.status == PlannerPreparationStatus::insufficient);
    });
    run("implicit_loudness_frequency_uses_resolved_duration", [] {
      auto s = uniform(); s.audio->attributes->loudnessCurve->samplingFrequency.reset();
      s.audio->attributes->loudnessCurve->value = std::vector<double>(32, -10);
      check(preparePlannerSongObservation(s, 16000).maps.loudness->at(1).songTime == .5);
      check(!preparePlannerSongObservation(s, std::nullopt).maps.loudness);
    });
    run("outside_track_is_diagnostic_not_clipped", [] {
      const auto p = preparePlannerSongObservation(uniform(), 8000);
      check(has(p, PlannerPreparationIssue::outsideTrack) && p.inventory->boundedStableRegions == 0);
      check(p.inventory->stablePreview[0].endSeconds == 16 && p.inventory->stablePreview[0].trackBounds == 2);
    });
    run("duplicates_remain_in_the_structure", [] {
      auto s = uniform(); video(s).timeInSeconds->at(1) = 0;
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.maps.structure->events.size() == 33 && p.inventory->duplicateTimes == 1);
      check(p.maps.structure->events[1].songTime == 0 && has(p, PlannerPreparationIssue::duplicateEvents));
    });
    run("reversal_inside_region_is_detected", [] {
      auto s = uniform(); video(s).timeInSeconds->at(2) = .25;
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.inventory->reversedTimes == 1 && p.maps.structure->events[2].songTime == .25);
      check(p.inventory->malformedRegions > 0 && p.inventory->usableStableRegions == 0);
    });
    run("fractional_score_is_not_truncated", [] {
      auto s = uniform(); video(s).score->at(1) = 200.5;
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.status == PlannerPreparationStatus::invalid && !p.maps.structure && !p.maps.tonality);
    });
    run("short_score_array_rejected", [] {
      auto s = uniform(); video(s).score->pop_back();
      check(preparePlannerSongObservation(s, 16000).status == PlannerPreparationStatus::invalid);
    });
    run("excess_scores_are_not_consumed", [] {
      auto s = uniform(); video(s).score->resize(5000, 200.5);
      check(preparePlannerSongObservation(s, 16000).maps.structure->events.size() == 33);
    });
    run("fractional_vocal_ms_do_not_escape_as_partial_maps", [] {
      auto s = uniform(); s.audio->attributes->vocalActivity->front().time.startInMilliseconds = .5;
      const auto p = preparePlannerSongObservation(s, 16000);
      check(p.status == PlannerPreparationStatus::invalid && !p.maps.vocals && !p.maps.tonality && !p.inventory);
    });
    run("consumed_event_work_limit", [] {
      auto s = uniform(); video(s).timeInSeconds->resize(4097); video(s).score->resize(4097, 400);
      check(preparePlannerSongObservation(s, 16000).status == PlannerPreparationStatus::resourceLimit);
    });
    run("event_limit_boundary_is_accepted", [] {
      auto s = uniform(); auto& v = video(s); v.timeInSeconds->resize(4096); v.score->assign(4096, 400);
      for (std::size_t i = 0; i < 4096; ++i) v.timeInSeconds->at(i) = static_cast<double>(i) * .5;
      check(preparePlannerSongObservation(s, 3000000).maps.structure->events.size() == 4096);
    });
    run("loudness_work_limit", [] {
      auto s = uniform(); s.audio->attributes->loudnessCurve->value->resize(16385, -10);
      check(preparePlannerSongObservation(s, 16000).status == PlannerPreparationStatus::resourceLimit);
    });
    run("vocal_work_limit", [] {
      auto s = uniform(); s.audio->attributes->vocalActivity->resize(4097);
      check(preparePlannerSongObservation(s, 16000).status == PlannerPreparationStatus::resourceLimit);
    });
    run("duration_input_domain", [] {
      for (const auto d : {0LL, -1LL, 9007199254740992LL}) {
        const auto p = preparePlannerSongObservation(uniform(), d);
        check(p.status == PlannerPreparationStatus::invalid && !p.durationMs);
      }
      check(preparePlannerSongObservation(uniform(), 9007199254740991LL).status == PlannerPreparationStatus::prepared);
    });
    run("invalid_region_reference_is_guarded", [] {
      auto p = preparePlannerSongObservation(uniform(), 16000);
      p.maps.structure->bars[0].endEvent = p.maps.structure->events.size();
      bool failed = false;
      try { inspectPlannerRegions(*p.maps.structure, 16); } catch (const std::invalid_argument&) { failed = true; }
      check(failed);
    });
    run("preview_limit_is_explicit_and_order_preserving", [] {
      const auto p = preparePlannerSongObservation(manyRuns(), 1000000);
      check(p.maps.structure->beatStabilityMap.size() == 20 && p.inventory->usableStableRegions == 20);
      check(p.inventory->stablePreview.size() == 16 && p.inventory->stablePreview.back().mapIndex == 15);
      const auto r = encodePlannerPreparationPair(p, p); check(r[8 + 29] == 1 && r.size() == 328);
    });
    run("no_executable_style_in_transport", [] {
      const auto p = preparePlannerSongObservation(uniform(), 16000);
      const auto r = encodePlannerPreparationPair(p, p);
      check(r.size() == 88 && r[1] == 88 && r[6] == 0 && r[7] == -1 && r[8 + 28] == 1);
    });
    run("negative_zero_bits_preserved", [] {
      auto s = uniform(); video(s).timeInSeconds->front() = -0.0;
      const auto p = preparePlannerSongObservation(s, 16000);
      const auto r = encodePlannerPreparationPair(p, p);
      check(r[8 + 32 + 3] == std::numeric_limits<std::int64_t>::min());
    });
    run("support_false_is_not_unknown", [] {
      auto s = uniform(); s.supportsSmartTransitions = false;
      check(preparePlannerSongObservation(s, 16000).supportState == 1);
      s.supportsSmartTransitions.reset(); check(preparePlannerSongObservation(s, 16000).supportState == 0);
    });
    if (argc == 2) exportSnapshots(argv[1]);
    else if (argc != 1) throw std::runtime_error("Expected at most one snapshot output path");
    std::cout << "Planner preparation: " << passed << '/' << passed << " groups passed\n";
    return 0;
  } catch (const std::exception& e) { std::cerr << "FAIL after " << passed << " groups: " << e.what() << '\n'; return 1; }
}
