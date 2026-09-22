# Stepped time-stretching schedule

`compileTimeStretchingSteps` ports `_SonicKit_MusicKit_Packages`
`0x2722815e0`, called by outgoing/incoming song step getters `0x272281544`
and `0x2722817bc`. It uses the same caller-supplied transition landmarks and
explicit stepped policy as automation sampling.

The original first checks whether the time calculator contains a rate ramp
(optional tag at +0x38 <= 0xfb). Without a ramp it returns an empty array,
even though the ordinary playback-time calculator has a unity fallback.
With a ramp, it runs grid generator `0x272252970`, maps every grid time through
the recovered calculator, and zips adjacent PlaybackTimes with `0x272281200`.
Before the anchor it uses unity mapping. Afterward it calls `0x27224fc0c`.

All adjacent intervals remain, including equal-rate and unity-rate intervals;
there is no point-value compaction. The range zipper rejects descending song,
stretched-song or transition endpoints. The playbackRate getter `0x272282538`
subtracts song endpoints, subtracts stretched endpoints, and divides the first
duration by the second when the latter is positive, returning zero otherwise.
This rate is averaged over the interval, not the instantaneous rate sampled
at either endpoint. The C++ API preserves both endpoint triples.

`tools/stepped_schedule_reference.py` now executes the original grid, mapping,
range zipper and playback-rate getter for 72 cases: absent/unity/increasing/
decreasing ramps, two anchors, three steps and short/cross-anchor/long spans.
Swift allocation/refcounts/slices are supplied, PAC is patched and libm uses
host functions. All six endpoints and computed rates are compared bit-for-bit
in the native test. Hashes are in `tests/fixtures/stepped_schedule.json`.

This is schedule generation, not spectral PCM processing. The rate schedule
does not itself preserve pitch or feed an Android resampler. Rate-ramp choice,
landmarks and policy still come from the planner; this module does not invent
them or replace absent rate automation with fabricated unity steps.
