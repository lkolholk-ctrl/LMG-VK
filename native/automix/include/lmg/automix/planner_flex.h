#pragma once
#include "lmg/automix/media_analysis.h"
#include <cstdint>

namespace lmg::automix {
enum class FlexTimeScale : std::uint8_t { shortTime = 0, medium = 1, longTime = 2, extraLong = 3 };
struct FlexEvent {
  double timeInSeconds;
  FlexTimeScale timeScale;
  double amplitude;
};

// MusicKitInternal Event.init?(time:score:). Unsupported scores return no event.
// Time is copied unchanged, including its sign/bit representation.
std::optional<FlexEvent> flexEventFromScore(double timeInSeconds, std::int64_t score);

// Raw CloudEvents -> internal Flex events. No planner beat-grid/scoring claims.
// Missing either array means unavailable. Original traversal uses the time array
// count: shorter scores are invalid; excess scores are ignored. Preserves input
// order/duplicate times. Consumed scores must represent exact signed64 integers;
// no guessed floating-point truncation. Nonfinite times are an LMG host rejection.
std::optional<std::vector<FlexEvent>> normalizeCloudFlexEvents(const CloudVideoEvents& raw);
} // namespace lmg::automix
