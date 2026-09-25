# Player opening performance

> 2026-09-21: performance investigation is paused at the owner's request. The checks below are historical, not an active task. Resume only on a new request; see [pause notes](LYRICS-PERFORMANCE-PAUSED.md).

FullPlayer now receives the expansion State instead of a per-frame Float.
AppRoot and FullPlayer read continuous progress in graphics-layer or layout
callbacks; composition observes only visibility and gesture thresholds. The
existing entrance timing, drag thresholds, and final player layout are retained.
Favorite/download Room flows and online artwork requests are remembered instead
of being recreated while the transition advances.

QueueSheet reads its progress in graphics layers. Its cover is measured once and
scaled into the thumbnail, with radius compensation for the final rounded corners.
The title keeps its final font size throughout the transition to avoid per-frame
text measurement. Queue/autoplay pairs are rebuilt only when the queue or section
boundary changes, rather than once per animation frame.

Lyrics loading can run during the entrance, but the measured lyric body is admitted
after the entrance settles. Once admitted it remains mounted during exit/reversal.
The lyric renderer, clock, wave, stretch, scrolling, and provider priority are
unchanged. Background bitmap surfaces and blur buffers are allocated and released
on the existing background-render executor, including cancellation cleanup.

AirPlaySheet receives the current cover URL, audio URI and album ID from FullPlayer,
using the same AlbumArtImage resolver and local-art fallback. It registers audio
device observers only while the sheet is mounted.

Validation: debug assembly and the existing JVM suite. No emulator or device frame
trace was used; actual jank reduction still needs the user's device check. Check
opening/closing/reversing FullPlayer, LyricsScreen and QueueSheet, queue scrolling
and reordering, portrait/landscape, and AirPlay artwork for online/local tracks.
