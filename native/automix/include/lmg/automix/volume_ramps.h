#pragma once
#include "lmg/automix/media_time.h"
#include "lmg/automix/stepped_schedule.h"

namespace lmg::automix {
// Endpoints are converted separately, before duration arithmetic, matching
// MusicKit's AVMutableAudioMixInputParameters preparation. Stretched-song clock.
struct TimedVolumeRamp {
  float startVolume, endVolume;
  MediaTime start, end;
};
// Source 0x2721b2a04: only the first full out_gain descriptor match is consumed,
// even if empty. Preserve its ramp order, including overlaps/duplicate times.
// For a nonempty automation, emit an initial constant ramp from zero to its first
// start if that start is nonzero. No implicit final ramp, clamping or gain
// smoothing. Preparation only; this does not implement AVFoundation rendering.
std::vector<TimedVolumeRamp> compileTimedVolumeRamps(
    const std::vector<SteppedAutomation>& automations);
}  // namespace lmg::automix
