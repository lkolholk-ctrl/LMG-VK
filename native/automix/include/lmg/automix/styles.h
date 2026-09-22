#pragma once
#include "lmg/automix/transition.h"
#include "lmg/automix/continuous_schedule.h"
#include <map>
#include <optional>

namespace lmg::automix {
struct StyleTime { double relative; std::optional<double> offsetInSeconds; };
struct StyleValue { std::optional<double> fallback; std::optional<std::string> parameterName; };
struct StyleRamp {
  std::string parameterId;
  StyleTime start, end;
  StyleValue from, to;
  std::string interpolation;
};
struct StyleInstruction {
  std::string name;
  StyleTime placementStart, placementEnd;
  std::vector<StyleRamp> ramps;
};
struct TransitionStyle {
  int id;
  std::string name;
  std::optional<StyleTime> offset;
  std::optional<int> duration; // Raw metadata: no assumption of bars or seconds.
  std::vector<StyleInstruction> outgoing, incoming;
};

// Control-thread parser, no network or file IO. Preserves catalog times/values.
// Strict known schema, <=1 MiB input, bounded nesting, no duplicate JSON keys.
std::vector<TransitionStyle> loadTransitionStyles(std::string_view json);

struct FrameWindow { Frame begin, end; };
struct SecondsWindow { double begin, end; };
// Recovered FUN_272210660: clamp normalized endpoints to the parent window;
// reversed endpoints collapse to their midpoint. Apply once for placement,
// then again for each ramp within that placement. Parent selection is planner
// input; style.offset/duration are not inferred to be seconds here.
SecondsWindow resolveStyleWindow(SecondsWindow parent, StyleTime start, StyleTime end);
std::vector<SecondsWindow> resolveStyleWindows(
    const std::vector<StyleInstruction>& instructions, SecondsWindow parent);
struct StylePlaybackRates { double start, end; };
// Port of style builder for supplied planner window/rates/mapped values.
// Keeps separate automations in resource order; ts_rate uses planner values and
// linear curves; prepends the recovered bypa gate when absent and nonempty.
std::vector<ContinuousAutomation> compileContinuousStyle(
    const std::vector<StyleInstruction>& instructions, SecondsWindow parent,
    StylePlaybackRates playbackRates, const std::map<std::string,double>& mappedValues = {});
// The planner must resolve EVERY automation window explicitly, in instruction /
// ramp order. Placement/offset/duration are never silently reinterpreted here.
// Initial values are mandatory for every scheduled parameter. Mapped values use
// the supplied map, then the resource fallback, otherwise fail. No implicit 0.
DeckSchedule compileStyleDeck(
    const std::vector<StyleInstruction>& instructions,
    const std::vector<FrameWindow>& resolvedWindows,
    EffectSettings settings,
    const std::map<std::string, double>& initialValues,
    const std::map<std::string, double>& mappedValues = {});
} // namespace lmg::automix
