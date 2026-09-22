#pragma once
#include <cstdint>

namespace lmg::automix {
// Numeric subset of the source CMTime transport. Generated epochs are zero;
// flags are valid (1), optionally with hasBeenRounded (2).
struct MediaTime {
  std::int64_t value;
  std::int32_t timescale;
  std::uint32_t flags;
  std::int64_t epoch = 0;
};
// Original event producer requests timescale 1e9. CoreMedia truncates toward
// zero and halves the timescale if necessary to fit signed 64-bit magnitude.
// Nonfinite/unrepresentable times are rejected by the LMG preparation API.
MediaTime automationMediaTime(double stretchedSongSeconds);
// Exact numeric comparison, ignoring the rounded flag as CMTimeCompare does.
// No floating-point division and no compiler-specific 128-bit integer type.
int compareMediaTimes(MediaTime a, MediaTime b);
// Numeric CMTimeConvertScale(..., method=1), used by MediaToolbox's queue
// timestamp preparation: nearest, ties away from zero. Preserves epoch and
// rounded flag. Rejects conversions whose original result is nonnumeric.
MediaTime convertMediaTimeScaleNearest(MediaTime time, std::int32_t timescale);
}  // namespace lmg::automix
