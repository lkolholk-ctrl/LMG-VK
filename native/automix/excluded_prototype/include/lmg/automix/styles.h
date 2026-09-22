#pragma once
#include "lmg/automix/transition.h"
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
// Explicit port policy: research has not established the nested time resolver.
// InstructionRelative maps ramp 0..1 inside its placement; TransitionRelative
// maps it inside the transition. Neither mode is claimed to emulate Apple.
enum class StyleTimeBasis { InstructionRelative, TransitionRelative };
struct ResolvedSideTiming {
  std::vector<FrameWindow> placements;
  std::vector<FrameWindow> ramps; // Flattened in instruction/ramp order.
};
struct ResolvedStyleTiming {
  ResolvedSideTiming outgoing, incoming;
  // Reported only: never shifts either deck or changes the supplied window.
  std::optional<Frame> offsetAnchor;
};
// Resolve metadata into a caller-selected output window, with explicit policy.
// Raw style.duration is not consulted. No beat/BPM/PCM alignment is inferred.
// Ramps must fit placement before rounding; out-of-window times are rejected,
// never clipped. Placement itself must fit the supplied transition window.
ResolvedStyleTiming resolveStyleTiming(const TransitionStyle& style,
    FrameWindow transition, std::uint32_t sampleRate, StyleTimeBasis basis);
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
