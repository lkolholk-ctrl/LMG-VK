#include "lmg/automix/playback_time.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
PlaybackTime playbackAnchor(double transition, double song,
                            std::optional<TimeStretchingState> previous) {
  if (!std::isfinite(transition) || !std::isfinite(song) ||
      (previous && (!std::isfinite(previous->song) || !std::isfinite(previous->stretchedSong))))
    throw std::invalid_argument("Nonfinite playback anchor context");
  const double stretched = previous ? song + (previous->stretchedSong - previous->song) : song;
  if (!std::isfinite(stretched)) throw std::invalid_argument("Playback anchor overflow");
  return {song, stretched, transition};
}
PlaybackTimeMap::PlaybackTimeMap(PlaybackTime anchor) : anchor_(anchor) {
  for (double v : {anchor.song, anchor.stretchedSong, anchor.transition})
    if (!std::isfinite(v)) throw std::invalid_argument("Nonfinite playback time anchor");
}
PlaybackTimeMap::PlaybackTimeMap(PlaybackTime anchor, RateRamp ramp) : anchor_(anchor), ramp_(ramp) {
  for (double v : {anchor.song, anchor.stretchedSong, anchor.transition, ramp.startRate,
                  ramp.endRate, ramp.startSongTime, ramp.endSongTime})
    if (!std::isfinite(v)) throw std::invalid_argument("Nonfinite playback time map");
  if (ramp.startRate <= 0 || ramp.endRate <= 0 || ramp.startSongTime < anchor.song ||
      ramp.endSongTime < ramp.startSongTime)
    throw std::invalid_argument("Unsupported playback time map domain");
}
double PlaybackTimeMap::integral(double song) const noexcept {
  const auto& r = *ramp_;
  const double elapsed = std::min(song, r.endSongTime) - r.startSongTime;
  if (elapsed <= 0) return 0;
  const double duration = r.endSongTime - r.startSongTime;
  const double slope = duration > 0 ? (r.endRate - r.startRate) / duration : 0;
  if (duration <= 0 || std::abs(slope) < 1e-6)
    return (1 / ((r.startRate + r.endRate) * .5)) * elapsed;
  const double rate = r.startRate + slope * elapsed;
  return (std::log(rate) - std::log(r.startRate)) / slope;
}
PlaybackTime PlaybackTimeMap::fromSong(double song) const {
  if (!std::isfinite(song)) throw std::invalid_argument("Nonfinite song time");
  if (!ramp_ || song < anchor_.song) {
    const double stretched = anchor_.stretchedSong + (song - anchor_.song);
    return {song, stretched, anchor_.transition + (stretched - anchor_.stretchedSong)};
  }
  const auto& r = *ramp_;
  const double before = std::min(song, r.startSongTime) - anchor_.song;
  const double after = std::max(song, r.endSongTime) - r.endSongTime;
  const double head = before > 0 ? (1 / r.startRate) * before : 0;
  const double tail = after > 0 ? (1 / r.endRate) * after : 0;
  const double stretched = anchor_.stretchedSong + ((head + integral(song)) + tail);
  return {song, stretched, anchor_.transition + (stretched - anchor_.stretchedSong)};
}
PlaybackTime PlaybackTimeMap::fromTransition(double time) const {
  if (!std::isfinite(time)) throw std::invalid_argument("Nonfinite transition time");
  const double stretched = anchor_.stretchedSong + (time - anchor_.transition);
  if (!ramp_ || time < anchor_.transition)
    return {anchor_.song + (stretched - anchor_.stretchedSong), stretched, time};
  const auto& r = *ramp_;
  const double start = fromSong(r.startSongTime).stretchedSong;
  const double end = fromSong(r.endSongTime).stretchedSong;
  const double before = std::min(stretched, start) - anchor_.stretchedSong;
  const double after = std::max(stretched, end) - end;
  const double head = before > 0 ? before / (1 / r.startRate) : 0;
  const double tail = after > 0 ? after / (1 / r.endRate) : 0;
  const double elapsed = std::min(stretched, end) - start;
  double middle = 0;
  if (elapsed > 0) {
    const double duration = r.endSongTime - r.startSongTime;
    const double slope = duration > 0 ? (r.endRate - r.startRate) / duration : 0;
    if (duration <= 0 || std::abs(slope) < 1e-6)
      middle = elapsed / (1 / ((r.startRate + r.endRate) * .5));
    else
      middle = (std::exp(std::log(r.startRate) + slope * elapsed) - r.startRate) / slope;
  }
  return {anchor_.song + ((head + middle) + tail), stretched, time};
}
double PlaybackTimeMap::stretchedDuration(double start, double end) const {
  const double first = fromSong(start).stretchedSong;
  return fromSong(end).stretchedSong - first;
}
}
