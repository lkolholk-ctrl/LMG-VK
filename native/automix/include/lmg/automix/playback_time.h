#pragma once
#include <optional>
namespace lmg::automix {
struct PlaybackTime { double song, stretchedSong, transition; };
struct RateRamp { double startRate, endRate, startSongTime, endSongTime; };
struct TimeStretchingState { double song, stretchedSong; };

// From ContinuousSchedule.SongSchedule's playbackTransitionTimeRange.lowerBound,
// startPlaybackSongTime and optional previousPlaybackEndState. Rate-ramp
// selection is separate; the caller must supply the recovered selected ramp.
PlaybackTime playbackAnchor(double playbackTransitionStart, double startPlaybackSongTime,
                            std::optional<TimeStretchingState> previous = std::nullopt);
// Recovered single-ramp calculator. Inputs are seconds, not PCM frames.
// Before the anchor, or without a rate ramp, recovered wrappers use unity rate.
class PlaybackTimeMap {
 public:
  PlaybackTimeMap(PlaybackTime anchor, RateRamp ramp);
  explicit PlaybackTimeMap(PlaybackTime anchor);
  PlaybackTime fromSong(double seconds) const;
  PlaybackTime fromTransition(double seconds) const;
  // Signed stretched interval, matching the source's endpoint subtraction.
  double stretchedDuration(double songStart, double songEnd) const;
  bool hasRateRamp() const noexcept { return ramp_.has_value(); }
 private:
  PlaybackTime anchor_;
  std::optional<RateRamp> ramp_;
  double integral(double song) const noexcept;
};
}
