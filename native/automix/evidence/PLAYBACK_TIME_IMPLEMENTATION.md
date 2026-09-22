# Playback time conversion with one rate ramp

`PlaybackTimeMap` implements the calculator and surrounding wrappers recovered from
`_SonicKit_MusicKit_Packages`:

* `0x27224fb08`: song time -> {song, stretched song, transition}.
* `0x27224fc0c`: transition time -> the same three coordinates.
* `0x272250300`: integral of inverse playback rate over a song-time ramp.
* `0x2722503b0`: inverse of that integral in stretched time.

Raw disassembly is saved in `scheduling/playback_time.asm` and
`scheduling/playback_integrals.asm`. Helpers at `0x2722509cc` and `0x272250a20`
copy the captured calculator and tail-call the forward conversion. Arithmetic
order is taken from ARM instructions, not Ghidra's corrupted temporary names.
Automatic floating-point contraction is disabled for the C++ implementation.

The anchors are three doubles (song, stretched song, transition). The rate ramp
contains start/end playback rates and start/end song times. The rate changes
linearly in song time. Its stretched duration is a logarithmic integral; inverse
mapping uses an exponential. For absolute rate slope below `1e-6`, the source
uses the reciprocal of the mean endpoint rate. This branch and its strict
comparison are preserved, as are division by reciprocal in the inverse, the
constant-rate portions before/after the ramp, and addition grouping.

Imports were traced through matching-UUID subcache `.67`: `0x2743ddb00` branches
to `0x296db0b00` (`_exp`/`_expl`); `0x2743ddb20` branches to `0x296db4010`
(`_log`/`_logl`). The names were read from libsystem_m's symbol table in `.77`.
The curve byte is carried by the original structure but not consulted by these
four calculator functions; it is not used to choose an invented integration rule.

## Verification and limits

`tools/playback_time_reference.py` executes the original ARM functions in a
small instruction harness, with only PAC entry/return instructions replaced and
log/exp imports supplied by host libm. It does not boot iOS or Android. The saved
fixture has 256 cases, 768 output doubles: both conversion directions, two
nonzero/zero anchor sets, acceleration/deceleration, constant rates, tiny slopes
on both sides of the threshold, zero-length ramps, boundaries and post-ramp time.
Every output double matches the C++ port bit-for-bit with the same host libm.
Code and fixture hashes are saved in `tests/fixtures/playback_time.json`.
ASan/UBSan tests also check round trips and invalid input rejection. This does
not claim Apple libm last-bit equivalence on all platforms.

Public API domain checks are LMG host constraints: finite arguments, positive
rates when a ramp exists, ordered ramp times and ramp start at/after song anchor.
Queries before the anchor are now supported by the recovered unity-rate branch;
no-ramp construction uses the same branch at all times. Extremely large finite
inputs are not claimed to produce finite outputs; planner-level duration/rate
validation remains necessary. Constructor/anchor/ramp extraction from the
planner's records remains separate pending work.

This is a real time-coordinate mapping component, not an audio TimePitch engine.
It still needs the recovered planner's per-track anchors/ramp extraction and
continuous/stepped event sampling before it can drive the application schedule.


## Enclosing wrappers

`0x27225157c` converts transition time using the captured calculator. It branches
to unity-rate mapping if query < transition anchor OR the optional ramp tag is
above 0xfb (absent). Exact arithmetic order:

```
stretched = stretchedAnchor + (transition - transitionAnchor)
song = songAnchor + (stretched - stretchedAnchor)
```

Otherwise it calls the previously ported inverse. `0x27220f754` computes signed
stretched duration between two song times, independently dispatching each
endpoint. Before the song anchor or without a ramp it uses
`stretchedAnchor + (song - songAnchor)`; otherwise it calls the forward mapping.
The result is end minus start, including reversed intervals. `stretchedDuration`
preserves that behavior. `fromSong` now exposes the same stretched coordinate;
its transition coordinate follows the existing calculator anchor relation.

The reference harness additionally executes both complete wrappers, covering
64 cases with/without ramp, before/at/across/after anchors and reversed/empty
intervals. `playback_wrappers.bin` and its manifest preserve their results and
code hashes. All 64 match bit-for-bit, and all previous 256 active-domain cases
still pass ASan/UBSan. No-ramp is a real optional value in the port, not a fake
constant-rate ramp that would alter pre-anchor or near-constant arithmetic.

## Anchor construction and previous-transition carry

`playbackAnchor` now ports the anchor arithmetic of `0x27227965c`. Its
SongSchedule input layout is established by getters and Codable keys:

| Offset | Field evidence |
| --- | --- |
| +0x10 | playbackTransitionTimeRange.lowerBound; getter `0x272277dbc` |
| +0x30 | startPlaybackSongTime; encoder key 4, string `0x272291960` |
| +0x38/+0x40/+0x48 | optional previousPlaybackEndState; encoder key 5, string `0x2722915e0` |

`TimeStretchingState.songTime` (`0x27226fde0`) reads offset 0;
`stretchedSongTime` (`0x27226fdec`) reads offset 8. The constructor loads the
previous two doubles into x24/x26 and the optional tag into w25. Instructions
`0x272279768..0x272279784` compute:

```
songAnchor = startPlaybackSongTime
stretchedAnchor = previous present
  ? songAnchor + (previous.stretchedSong - previous.song)
  : songAnchor
transitionAnchor = playbackTransitionTimeRange.lowerBound
```

The exact subtract-then-add order is preserved. Finite input/output checks are
explicit LMG preparation-time validation. The helper returns a `PlaybackTime`
which is directly consumed by `PlaybackTimeMap`; no rate-ramp choice is invented.

Twelve additional reference cases execute the entire original constructor with
its automation lookup (`0x272278dfc`) hooked to return absence. This isolates
anchor construction while exercising positive/negative/no previous stretch and
fractional anchors. All 12 output triples match bit-for-bit. The lookup hook is
explicit in `playback_anchors.json`; these cases do not validate rate extraction.
The full playback-time test now passes 332 reference cases under ASan/UBSan.

Rate extraction remains to be ported from the other constructor branch: locate
its time-stretching automation, generate adjacent-point ramps, and take the
first ramp if present. The original ARM loads that ramp from array offset +0x20
at `0x272279730`; this must not be generalized into an arbitrary multi-ramp
integrator without further evidence.
