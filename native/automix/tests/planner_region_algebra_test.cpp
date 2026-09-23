#include "lmg/automix/planner_region_algebra.h"
#include <algorithm>
#include <cmath>
#include <fstream>
#include <functional>
#include <iostream>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>
#include <type_traits>

using namespace lmg::automix;
using namespace lmg::automix::region_algebra;
namespace {
void expect(bool value, int line) {
  if (!value) throw std::runtime_error("Failed check at line " + std::to_string(line));
}
#define CHECK(value) expect(static_cast<bool>(value), __LINE__)
template<class Exception = std::invalid_argument, class Run> void throws(Run run) {
  bool caught = false;
  try { run(); } catch (const Exception&) { caught = true; }
  CHECK(caught);
}
SongStructure normalized(std::size_t bars = 16, std::size_t beatsPerBar = 4) {
  CloudVideoEvents cloud;
  cloud.timeInSeconds.emplace(); cloud.score.emplace();
  for (std::size_t i = 0; i <= bars * beatsPerBar; ++i) {
    cloud.timeInSeconds->push_back(static_cast<double>(i) * .5);
    cloud.score->push_back(i % (4 * beatsPerBar) == 0 ? 800 : i % beatsPerBar == 0 ? 400 : 200);
  }
  const auto events = normalizeCloudFlexEvents(cloud);
  CHECK(events);
  return songStructureFromFlexEvents(*events);
}
PlannerRegionRef beats(const SongStructure& s, std::size_t first, std::size_t last) {
  return PlannerRegionRef(s, {first, last}, PlannerRegionUnit::beats);
}
PlannerRegionRef bars(const SongStructure& s, std::size_t first, std::size_t last) {
  return PlannerRegionRef(s, {first, last}, PlannerRegionUnit::bars);
}
void same(const PlannerRegionRef& ref, const SongStructure& owner, std::size_t first,
          std::size_t last, PlannerRegionUnit unit) {
  CHECK(&ref.owner() == &owner);
  CHECK(ref.events().startEvent == first && ref.events().endEvent == last);
  CHECK(ref.unit() == unit);
}
constexpr std::int64_t maxInt = std::numeric_limits<std::int64_t>::max();
int reference(const char* path) {
  std::ifstream stream(path);
  if (!stream) throw std::runtime_error("Cannot open region reference fixture");
  std::string magic; std::size_t expected = 0;
  CHECK(stream >> magic >> expected);
  CHECK(magic == "LMG_REGION_ORACLE_V1" && expected == 1657);
  std::size_t count = 0, index = 0;
  std::string line;
  std::getline(stream, line);
  CHECK(line.empty());
  while (std::getline(stream, line)) {
    CHECK(!line.empty());
    std::istringstream row(line);
    std::string op, result, extra;
    std::int64_t first = 0, last = 0, n = 0, target = 0;
    unsigned scale = 0;
    CHECK(row >> index >> op >> first >> last >> n >> scale >> result >> target);
    CHECK(!(row >> extra));
    CHECK(index == count && scale <= 2 && (result == "ok" || result == "overflow"));
    bool overflow = false;
    std::int64_t actual = 0;
    try {
      if (op == "style") actual = plannerStyleSuffixStartOrdinal(first, last, n);
      else if (op == "incoming") actual = plannerIncomingSuffixStartOrdinal(last, last - first, n);
      else if (op == "budget") actual = plannerIncomingBeatBudget(n, static_cast<TempoBinaryScale>(scale));
      else if (op == "half") actual = plannerIncomingSuffixStartOrdinal(last, last - first, (last - first) / 2);
      else throw std::runtime_error("Unknown reference operation");
    } catch (const std::overflow_error&) { overflow = true; }
    if ((result == "overflow") != overflow || (!overflow && actual != target)) {
      throw std::runtime_error("Region reference mismatch at row " + std::to_string(index));
    }
    ++count;
  }
  CHECK(!stream.bad() && count == expected);
  std::cout << "Region reference: " << count << "/" << expected << " scalar text-slice cases passed\n";
  return 0;
}
}
int main(int argc, char** argv) {
  std::string current;
  int passed = 0;
  try {
    if (argc == 3 && std::string(argv[1]) == "--reference") return reference(argv[2]);
    if (argc != 1) throw std::runtime_error("Usage: region_tests [--reference fixture.tsv]");
    auto run = [&](const char* name, const std::function<void()>& fn) {
      current = name; fn(); ++passed; std::cout << "PASS " << name << '\n';
    };
    run("normalized_flex_to_real_event_views", [] {
      const auto s = normalized();
      CHECK(s.events.size() == 65 && s.bars.size() == 16 && !s.beatStabilityMap.empty());
      const auto region = bars(s, 32, 64);
      CHECK(region.firstOrdinal() == 8 && region.lastOrdinal() == 16 && region.ordinalCount() == 8);
      const auto projected = plannerProjectRegionToBeats(region);
      CHECK(projected && projected->ordinalCount() == 32);
      same(*projected, s, 32, 64, PlannerRegionUnit::beats);
    });
    run("outgoing_is_suffix_not_prefix", [] {
      const auto s = normalized();
      const auto result = plannerOutgoingStyleSuffix(bars(s, 0, 64), 4);
      CHECK(result); same(*result, s, 48, 64, PlannerRegionUnit::bars);
      CHECK(result->startSeconds() == 24 && result->endSeconds() == 32);
    });
    run("absent_bar_count_uses_source_default_four", [] {
      const auto s = normalized();
      const auto result = plannerOutgoingStyleSuffix(bars(s, 0, 64), std::nullopt);
      CHECK(result); same(*result, s, 48, 64, PlannerRegionUnit::bars);
    });
    run("explicit_zero_is_not_missing", [] {
      const auto s = normalized();
      const auto result = plannerOutgoingStyleSuffix(bars(s, 0, 64), 0);
      CHECK(result); same(*result, s, 64, 64, PlannerRegionUnit::bars);
      CHECK(result->ordinalCount() == 0);
    });
    run("suffix_does_not_extend_before_seed_start", [] {
      const auto s = normalized();
      const auto result = plannerOutgoingStyleSuffix(bars(s, 48, 64), 100);
      CHECK(result); same(*result, s, 48, 64, PlannerRegionUnit::bars);
    });
    run("incoming_truncation_preserves_end", [] {
      const auto s = normalized();
      const auto result = plannerIncomingBeatSuffix(beats(s, 0, 32), 16);
      CHECK(result); same(*result, s, 16, 32, PlannerRegionUnit::beats);
    });
    run("incoming_large_budget_keeps_original_range", [] {
      const auto s = normalized();
      const auto result = plannerIncomingBeatSuffix(beats(s, 8, 32), maxInt);
      CHECK(result); same(*result, s, 8, 32, PlannerRegionUnit::beats);
    });
    run("incoming_zero_budget_keeps_end_reference", [] {
      const auto s = normalized();
      const auto result = plannerIncomingBeatSuffix(beats(s, 8, 32), 0);
      CHECK(result); same(*result, s, 32, 32, PlannerRegionUnit::beats);
    });
    run("odd_halving_truncates_beat_count_not_time", [] {
      auto s = normalized();
      s.events[6].songTime = 3.125;
      const auto result = plannerHalveIncomingRegion(beats(s, 3, 8));
      CHECK(result); same(*result, s, 6, 8, PlannerRegionUnit::beats);
      CHECK(result->startSeconds() == 3.125);
    });
    run("one_beat_halving_can_be_empty", [] {
      const auto s = normalized();
      const auto result = plannerHalveIncomingRegion(beats(s, 3, 4));
      CHECK(result); same(*result, s, 4, 4, PlannerRegionUnit::beats);
    });
    run("tempo_scale_and_count_scale_are_inverse", [] {
      CHECK(plannerIncomingBeatBudget(17, TempoBinaryScale::half) == 34);
      CHECK(plannerIncomingBeatBudget(17, TempoBinaryScale::one) == 17);
      CHECK(plannerIncomingBeatBudget(17, TempoBinaryScale::two) == 8);
    });
    run("paired_style_suffix_unity_owns_correct_songs", [] {
      const auto outgoing = normalized(), incoming = normalized();
      const auto result = plannerStyleRegionPair(
          {bars(outgoing, 32, 64), beats(incoming, 0, 32), TempoBinaryScale::one}, 4);
      CHECK(result);
      same(result->outgoing, outgoing, 48, 64, PlannerRegionUnit::bars);
      same(result->incoming, incoming, 16, 32, PlannerRegionUnit::beats);
      CHECK(result->incomingScale == TempoBinaryScale::one);
    });
    run("paired_style_suffix_half_and_double", [] {
      const auto out = normalized(), in = normalized();
      auto result = plannerStyleRegionPair({bars(out, 32, 64), beats(in, 0, 32), TempoBinaryScale::half}, 4);
      CHECK(result); same(result->incoming, in, 0, 32, PlannerRegionUnit::beats);
      CHECK(result->incomingScale == TempoBinaryScale::half);
      result = plannerStyleRegionPair({bars(out, 32, 64), beats(in, 0, 32), TempoBinaryScale::two}, 4);
      CHECK(result); same(result->incoming, in, 24, 32, PlannerRegionUnit::beats);
      CHECK(result->incomingScale == TempoBinaryScale::two);
    });
    run("pair_truncation_does_not_rewrite_outgoing", [] {
      const auto out = normalized(), in = normalized();
      auto result = plannerTruncateRegionPair({beats(out, 2, 12), beats(in, 0, 40), TempoBinaryScale::one});
      CHECK(result); same(result->outgoing, out, 2, 12, PlannerRegionUnit::beats);
      same(result->incoming, in, 30, 40, PlannerRegionUnit::beats);
    });
    run("non_four_beat_meter_uses_event_ordinals", [] {
      const auto out = normalized(16, 3), in = normalized(16, 3);
      const auto result = plannerStyleRegionPair({bars(out, 0, 48), beats(in, 0, 24), TempoBinaryScale::two}, 1);
      CHECK(result); same(result->outgoing, out, 45, 48, PlannerRegionUnit::bars);
      same(result->incoming, in, 23, 24, PlannerRegionUnit::beats);
    });
    run("no_nearest_event_when_exact_beat_is_missing", [] {
      auto s = normalized();
      s.beatEvents.erase(std::find(s.beatEvents.begin(), s.beatEvents.end(), 16));
      CHECK(!plannerIncomingBeatSuffix(beats(s, 0, 32), 16));
    });
    run("no_nearest_downbeat_when_requested_ordinal_is_missing", [] {
      auto s = normalized();
      s.downbeatEvents.erase(std::find(s.downbeatEvents.begin(), s.downbeatEvents.end(), 48));
      CHECK(!plannerOutgoingStyleSuffix(bars(s, 0, 64), 4));
    });
    run("duplicate_ordinal_first_source_match_is_retained", [] {
      auto s = normalized();
      s.events[14].beatIndex = 16;
      const auto result = plannerIncomingBeatSuffix(beats(s, 0, 32), 16);
      CHECK(result); same(*result, s, 14, 32, PlannerRegionUnit::beats);
    });
    run("lookup_never_sorts_source_collection", [] {
      auto s = normalized();
      s.events[14].beatIndex = 16;
      std::swap(s.beatEvents[14], s.beatEvents[16]);
      const auto result = plannerIncomingBeatSuffix(beats(s, 0, 32), 16);
      CHECK(result); same(*result, s, 16, 32, PlannerRegionUnit::beats);
    });
    run("not_limited_to_diagnostic_preview", [] {
      const auto s = normalized(40);
      const auto result = plannerOutgoingStyleSuffix(bars(s, 0, 160), 4);
      CHECK(result); same(*result, s, 144, 160, PlannerRegionUnit::bars);
      CHECK(result->firstOrdinal() == 36);
    });
    run("unsupported_bar_projection_does_not_snap", [] {
      auto s = normalized();
      s.beatEvents.erase(std::find(s.beatEvents.begin(), s.beatEvents.end(), 0));
      CHECK(!plannerProjectRegionToBeats(bars(s, 0, 64)));
      throws([&] { (void)plannerOutgoingStyleSuffix(beats(s, 4, 64), 4); });
    });
    run("dangling_reference_and_invalid_unit_rejected", [] {
      auto s = normalized();
      throws([&] { (void)beats(s, 0, 65); });
      throws([&] { (void)PlannerRegionRef(s, {0, 64}, static_cast<PlannerRegionUnit>(2)); });
      s.beatEvents.push_back(999);
      throws([&] { (void)beats(s, 0, 64); });
    });
    run("missing_downbeat_and_wrong_membership_rejected", [] {
      auto s = normalized();
      throws([&] { (void)bars(s, 1, 64); });
      s.events[4].downbeatIndex.reset();
      throws([&] { (void)bars(s, 0, 64); });
    });
    run("inherited_downbeat_index_is_not_a_downbeat_event", [] {
      auto s = normalized();
      s.downbeatEvents.push_back(1);
      throws([&] { (void)bars(s, 0, 64); });
      s = normalized(); s.events[1].kind = static_cast<StructureEventKind>(4);
      throws([&] { (void)beats(s, 0, 32); });
    });
    run("negative_counts_and_invalid_scale_rejected", [] {
      const auto s = normalized();
      throws([&] { (void)plannerOutgoingStyleSuffix(bars(s, 0, 64), -1); });
      throws([&] { (void)plannerIncomingBeatSuffix(beats(s, 0, 64), -1); });
      throws([] { (void)plannerIncomingBeatBudget(-1, TempoBinaryScale::one); });
      throws([] { (void)plannerIncomingBeatBudget(1, static_cast<TempoBinaryScale>(3)); });
      throws([] { (void)plannerStyleSuffixStartOrdinal(2, 1, 1); });
      throws([] { (void)plannerIncomingSuffixStartOrdinal(1, 2, 2); });
    });
    run("signed64_budget_boundary_does_not_wrap", [] {
      CHECK(plannerIncomingBeatBudget(maxInt / 2, TempoBinaryScale::half) == maxInt - 1);
      throws<std::overflow_error>([] { (void)plannerIncomingBeatBudget(maxInt / 2 + 1, TempoBinaryScale::half); });
      CHECK(plannerIncomingBeatBudget(maxInt, TempoBinaryScale::two) == maxInt / 2);
      CHECK(plannerStyleSuffixStartOrdinal(0, maxInt, maxInt) == 0);
    });
    run("nonfinite_and_reversed_time_rejected", [] {
      auto s = normalized();
      throws([&] { (void)beats(s, 32, 0); });
      s.events[0].songTime = std::numeric_limits<double>::quiet_NaN();
      throws([&] { (void)beats(s, 0, 32); });
      s.events[0].songTime = std::numeric_limits<double>::infinity();
      throws([&] { (void)beats(s, 0, 32); });
    });
    run("mutation_is_detected_on_use_not_silently_rebound", [] {
      auto s = normalized();
      const auto ref = beats(s, 0, 32);
      s.beatEvents.clear();
      throws([&] { (void)plannerHalveIncomingRegion(ref); });
    });
    run("host_work_bound_is_explicit", [] {
      auto s = normalized();
      s.beatEvents.resize(kPlannerRegionAlgebraMaxEvents + 1, 0);
      throws<std::length_error>([&] { (void)beats(s, 0, 32); });
      s.beatEvents.clear(); s.events.resize(kPlannerRegionAlgebraMaxEvents + 1);
      throws<std::length_error>([&] { (void)beats(s, 0, 32); });
    });
    run("negative_zero_time_survives_projection", [] {
      auto s = normalized(); s.events[0].songTime = -0.0;
      const auto result = plannerProjectRegionToBeats(bars(s, 0, 64));
      CHECK(result && std::signbit(result->startSeconds()));
    });
    run("styling_pair_clips_outgoing_and_rebuilds_incoming_end", [] {
      const auto result = plannerTruncateStylingTimePair({{100, 120}, {5, 6}}, 112);
      CHECK(result && result->outgoing.start == 100 && result->outgoing.end == 112);
      CHECK(result->incoming.start == 5 && result->incoming.end == 17);
    });
    run("styling_pair_requires_two_seconds_inclusively", [] {
      CHECK(plannerTruncateStylingTimePair({{0, 2}, {0, 4}}, 2));
      CHECK(!plannerTruncateStylingTimePair({{0, 2}, {0, 4}}, std::nextafter(2., 0.)));
      CHECK(!plannerTruncateStylingTimePair({{1, 4}, {0, 4}}, 1));
      CHECK(!plannerTruncateStylingTimePair({{1, 4}, {0, 4}}, 0));
    });
    run("styling_pair_does_not_extend_outgoing_past_seed_end", [] {
      const auto result = plannerTruncateStylingTimePair({{10, 13}, {2, 50}}, 30);
      CHECK(result && result->outgoing.end == 13 && result->incoming.end == 5);
    });
    run("styling_preserves_source_signed_zero_on_equal_end", [] {
      const auto result = plannerTruncateStylingTimePair({{-3, -0.0}, {0, 1}}, +0.0);
      CHECK(result && std::signbit(result->outgoing.end));
    });
    run("styling_invalid_domains_and_floating_overflow", [] {
      const auto inf = std::numeric_limits<double>::infinity();
      throws([&] { (void)plannerTruncateStylingTimePair({{0, 3}, {0, 2}}, inf); });
      throws([] { (void)plannerTruncateStylingTimePair({{4, 3}, {0, 2}}, 10); });
      throws([] { (void)plannerTruncateStylingTimePair({{-1e308, 1e308}, {0, 2}}, 1e308); });
      throws([] { (void)plannerTruncateStylingTimePair({{0, 1e308}, {1e308, 1.5e308}}, 1e308); });
    });
    run("resolved_placement_inclusive_all_three_bounds", [] {
      const PlannerStylingTimePair pair{{10, 20}, {0, 10}};
      CHECK(plannerResolvedPlacementPasses(pair, {10, 20, 10}));
      CHECK(!plannerResolvedPlacementPasses(pair, {std::nextafter(10., 20.), 20, 10}));
      CHECK(!plannerResolvedPlacementPasses(pair, {10, std::nextafter(20., 30.), 10}));
      CHECK(!plannerResolvedPlacementPasses(pair, {10, 20, std::nextafter(10., 0.)}));
      throws([&] { (void)plannerResolvedPlacementPasses(pair, {0, 0, std::numeric_limits<double>::quiet_NaN()}); });
    });
    run("incoming_styling_missing_starts_remain_absent", [] {
      CHECK(!plannerIncomingStylingTimeRange(std::nullopt, std::nullopt, 4, 100));
      const auto a = plannerIncomingStylingTimeRange(3, std::nullopt, 4, 100);
      const auto b = plannerIncomingStylingTimeRange(std::nullopt, 3, 4, 100);
      CHECK(a && b && a->start == 3 && b->end == 7);
    });
    run("incoming_styling_later_start_wins", [] {
      auto a = plannerIncomingStylingTimeRange(3, 5, 4, 100);
      auto b = plannerIncomingStylingTimeRange(5, 3, 4, 100);
      CHECK(a && b && a->start == 5 && b->start == 5 && a->end == 9);
    });
    run("incoming_styling_equality_preserves_non_silent_bits", [] {
      const auto a = plannerIncomingStylingTimeRange(-0., +0., 2, 5);
      const auto b = plannerIncomingStylingTimeRange(+0., -0., 2, 5);
      CHECK(a && b && !std::signbit(a->start) && std::signbit(b->start));
    });
    run("incoming_styling_limit_inclusive_and_no_duration_cap", [] {
      CHECK(plannerIncomingStylingTimeRange(2, 3, 4, 7));
      CHECK(!plannerIncomingStylingTimeRange(2, 3, 4, std::nextafter(7., 0.)));
      const auto zero = plannerIncomingStylingTimeRange(3, 3, 0, 3);
      CHECK(zero && zero->start == 3 && zero->end == 3);
      CHECK(plannerIncomingStylingTimeRange(0, 0, .5, 1));
    });
    run("incoming_styling_subtract_before_compare_is_preserved", [] {
      // (start+duration <= end) would incorrectly succeed after rounding.
      CHECK(1e16 + 1 == 1e16);
      CHECK(!plannerIncomingStylingTimeRange(1e16, std::nullopt, 1, 1e16));
    });
    run("incoming_styling_invalid_fields_rejected", [] {
      throws([] { (void)plannerIncomingStylingTimeRange(0, 0, -1, 1); });
      throws([] { (void)plannerIncomingStylingTimeRange(std::numeric_limits<double>::infinity(), 0, 1, 2); });
      throws([] { (void)plannerIncomingStylingTimeRange(-1e308, std::nullopt, 1, 1e308); });
    });
    run("normal_seed_sweeps_preserve_endpoint_and_owner", [] {
      const auto out = normalized(), in = normalized();
      for (std::size_t first = 0; first <= 48; first += 4) {
        for (std::int64_t count = 0; count <= 20; ++count) {
          for (auto scale : {TempoBinaryScale::half, TempoBinaryScale::one, TempoBinaryScale::two}) {
            const auto result = plannerStyleRegionPair({bars(out, first, 64), beats(in, 0, 32), scale}, count);
            CHECK(result);
            CHECK(&result->outgoing.owner() == &out && &result->incoming.owner() == &in);
            CHECK(result->outgoing.events().endEvent == 64 && result->incoming.events().endEvent == 32);
            CHECK(result->outgoing.firstOrdinal() >= static_cast<std::int64_t>(first / 4));
            CHECK(result->incomingScale == scale);
          }
        }
      }
      CHECK(out.events.size() == 65 && in.events[32].songTime == 16);
    });
    // Deleted rvalue construction prevents a common dangling-view error at compile time.
    static_assert(!std::is_constructible_v<PlannerRegionRef, SongStructure&&, StructureRegion, PlannerRegionUnit>);
    std::cout << "Region algebra: " << passed << "/43 groups passed; no selector or PCM execution\n";
    CHECK(passed == 43);
    return 0;
  } catch (const std::exception& error) {
    std::cerr << "FAIL " << current << ": " << error.what() << '\n';
    return 1;
  }
}
