# Explicit stepped graph event transport

`ScheduledTrackGraph` is an LMG host layer around the connected DSP graph. A
control thread supplies immutable, coefficient-prepared `GraphEvent` snapshots
on an already resolved, nonnegative PCM frame timeline. Audio processing splits
a caller block at events, applies each snapshot before that frame, and preserves
kernel/gain state between slices. Callback processing allocates no memory.

`prepareGraphEvents` binds 23 DSP scalar IDs and binary `bypa` from `DSPGraph.dspg` to the
corresponding effect settings. The source resource is the same SHA256 cited in
GRAPH_IMPLEMENTATION.md. Values narrow from double to float before preparing
coefficients. The producer at `0x2721b5788` independently shows a double-to-float
conversion (`fcvt s0,d8` at `0x2721b5e90`); its time conversion is NOT implemented
by this host API. IDs `RVlf` and `RVhf` explicitly mark decay writes, including
repeated equal values, to preserve the already recovered reverb reset behavior.

The following are explicit LMG API policies, not claims about Apple scheduling:

* Times are supplied as frames; no song-time, playback-time, normalized-style or
  nanosecond-to-frame conversion is guessed here.
* Scalar writes must be time ordered. Distinct simultaneous writes merge into a
  complete snapshot. Duplicate IDs at one frame are rejected as ambiguous.
* Direct snapshots are strictly increasing and cumulative. All rate and
  reverb geometry compatibility is validated before graph allocation/rendering.
* Events use half-open buffer intervals. An event exactly at the buffer end is
  consumed at the start of the next nonempty call. Empty calls do nothing.
* The cursor advances monotonically; a seek needs reconstruction off callback.
* `out_gain`/`ts_rate` require external processors. Geometry needs additional
  host lifecycle behavior. Passing any of these IDs to this bridge raises an
  error; they are never silently discarded or simulated.
* `bypa` accepts exactly 0 or 1, matching recovered style gates. Fractional values
  are rejected rather than assigning an unverified conversion threshold. Exiting
  bypass resets the channel effects, while preserving the reverb tank.
* Caller-provided silence flags apply to every slice in that call. There is no
  invented AU silence propagation. Only exact input/output alias is supported.

This module supplies delivery of explicit frame events. Continuous-to-stepped
conversion is now implemented separately in STEPPED_SCHEDULE_IMPLEMENTATION.md,
preserving seconds and the explicit planner policy. Timed effect grouping and
duplicate-lane collision rules are in EFFECT_EVENTS_IMPLEMENTATION.md. Final
host frame conversion and full render slice behavior remain unresolved.
Block splitting affects the recovered gain and biquad arithmetic;
therefore arbitrary callback partition invariance and full Apple runtime parity
are not claimed.

## Verification

ASan/UBSan tests compare scheduled output bit-for-bit against explicit manual
TrackGraph slices at identical boundaries, including stereo state, in-place
input, parameter retention, repeated decay writes, and null-input tails. Tests
also cover start/end events, initial gain writes, smoothing, zero-length calls,
invalid buffer atomic rejection, frame overflow, unsupported snapshot changes,
unknown/external parameter IDs, duplicate/unordered writes, float overflow and
no heap allocations during active scheduled rendering. A separate source check
compared the supported ID set with the original graph: all 23 match.

This is a tested native host component; application AutoMix integration,
TimePitch, planner/server matching and remaining AU lifecycle behavior are still
required before a complete product can be claimed.

## Recovered scheduling policy (follow-up)

The extracted `_SonicKit_MusicKit_Packages` binary now resolves two previously
uncertain configuration details. Evidence is in `scheduling/policy.asm`,
`scheduling/step_defaults.asm`, and `scheduling/constants.json`; regenerate the
constant extraction with `tools/scheduling_constants.py EXTRACTED_MACH_O`.

* `SteppedSchedule.validStepDurationRange` at `0x272283f40` loads the double
  `0.0001` from `0x27229b338` and returns upper bound `1.0` in d1.
* `SteppedSchedule.defaultStepDuration` at `0x272283f50` loads double `0.2` from
  `0x2722a1010`. This is a mode default, not proof of every runtime caller's step.
* Public enum case symbols at `0x27229b3ec` (stepped) and `0x27229b3f0`
  (continuous) contain uint32 values 0 and 1. `SchedulingPolicy.default` at
  `0x2722549cc` writes a zero payload and tag 1. Thus its default is continuous.
* Contrary to the earlier research table's associated-payload notation, the
  policy's continuous case has no associated schedule: its symbol is
  `...O10continuousyA2EmFWC`, while stepped has a Double payload
  (`...O7steppedyAESd_tcAEmFWC`). A scheduling policy and a resulting schedule
  are different types.
* Configuration validation at `0x2722548b4` bypasses range validation for tag 1;
  for stepped it performs `fcmp`/`fccmp` and accepts the inclusive range. NaN and
  infinities reach the error path.

`SchedulingPolicy` carries these exact defaults/constraints. Native tests cover
both endpoints, nextafter values just outside them, NaN/infinities and continuous
mode's absence of a duration. The stepped compiler consumes an explicitly chosen
policy; actual runtime policy selection and continuous-policy delivery remain
separate. There is no unconditional 200 ms sampler.

Recovered stepped constructor chain:
`0x27226725c -> 0x272282c80`; the latter builds per-side time calculators through
`0x27227965c`, collects/deduplicates transition landmarks through `0x272282f30`,
sorts through `0x272283124`, and assembles per-parameter records in `0x2722842b0`.
The converter now follows this chain through sampling at `0x27227b000` and
compaction at `0x272279d20`; see STEPPED_SCHEDULE_IMPLEMENTATION.md for the
implemented algorithm, original ARM fixtures and integration boundaries.
