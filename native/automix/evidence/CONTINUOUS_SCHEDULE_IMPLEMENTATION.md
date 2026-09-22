# Continuous points to playback-time calculator

`continuous_schedule.h/.cpp` now connects explicit continuous automation records
to the already verified `PlaybackTimeMap` and previous-state anchor carry.

## Source behavior

`ContinuousSchedule.Automation.ramps` (`0x2722706d0`) pairs its point array with
the same array after dropping the first point (`0x272270750`), then calls
`0x27227086c`. The converter stores for each pair: first value, second value,
first song time, second song time, first curve. Point getters at `0x2722754ac`,
`0x2722754b4`, `0x2722754c0` establish the fields. Equal times are retained;
reversed times and absent nonterminal curves trap in the original. The port
raises invalid_argument instead and additionally rejects nonfinite pair fields
as a host guard. No sorting or curve substitution is performed.

The calculator constructor `0x27227965c` obtains its rate automation through
`0x272278dfc`. Lookup compares the complete descriptor, not merely the ID:

* ID: `ts_rate` (Swift inline string written at `0x27226e410`).
* Minimum/maximum: `0.03125`, `32.0` (two doubles at `0x27229e1b0`).
* Default: `1.0` (`0x27226ed2c` stores it at descriptor +0x20).
* Additional string at +0x28: `ts_rate` (same helper stores the inline string).

The semantic name of the last string is not yet resolved; the C++ representation
calls it `auxiliaryId`, and does not infer its use outside exact equality.
The lookup returns the first complete match. The constructor materializes all
adjacent ramps and takes the first if any exist (`0x272279730`). A matching
empty or single-point automation produces no rate ramp; it does not continue
searching later duplicate descriptors. Multiple ramps are not integrated into
a different timing model. The curve is preserved in generic ContinuousRamp,
but the recovered time integrals do not dispatch on it.

`schedulePlaybackTimeMap` composes this selection with `playbackAnchor`, using
the explicit playback transition start, start song time, and previous end state.
It is ready to consume the continuous records once planner production is ported.
It does not claim to produce those records from raw style placements yet.

## Verification

ASan/UBSan tests cover adjacent curve ownership, equal/reversed times, missing
curves, all five descriptor comparisons, first-match empty/single-point behavior,
first-ramp selection, rejection of a malformed later ramp, and resulting time
mapping with previous stretch carry. The existing 332 original-ARM time/anchor
fixtures continue to pass. New selection tests are behavior tests derived from
the disassembly, not a claim that Swift collection/runtime execution was replayed.
Raw converter and lookup disassembly are in `scheduling/adjacent_ramps.asm` and
`scheduling/rate_automation_lookup.asm`.

Still open: continuous record creation from selected styles/cues, full schedule
sampling/delivery, TimePitch audio processing, and application integration.

## Continuous value lookup

`ContinuousAutomationValues` ports `0x272271704`, reached by the public
`ContinuousSchedule.Automation.value(at:)` getter at `0x2722716d0`:

* Empty points return descriptor.defaultValue.
* Before the first point, return its value.
* Otherwise start with the last point value, construct adjacent ramps, and
  search backwards for the first inclusive interval containing the query.
* Evaluate that ramp with `0x272275f84`; if none matches, keep the last value.
* Ramp evaluation tests the end boundary before the start boundary. Consecutive
  equal-time points are retained, and reverse search resolves shared boundaries.

The port validates/prepares the immutable ramp list once rather than allocating
Swift arrays on every query. Nonfinite values/queries and nonpositive logarithmic
endpoints are rejected as host guards. Times remain doubles in seconds and are
not rounded to output frames. The curve math is now a shared `curveValue` helper
also used by the pre-existing frame-based Ramp evaluator; frame-lane sorting
and gap policies do not leak into this continuous evaluator.

New tests check empty/single-point schedules, fractional seconds, before/after
range behavior, runs of simultaneous points, terminal zero-length ramps, linear,
squared and logarithmic interpolation. These are behavior tests against the
inspected source, not new full-Swift execution fixtures. Raw lookup instructions
are saved in `scheduling/continuous_value_lookup.asm`.

The normalized placement resolver is still unproven. The research specification
explicitly marks its runtime mapping PARTIAL. No placement scaling rule was
added based on its prose alone; this evaluator supplies the confirmed value
lookup needed by subsequent schedule generation/sampling.
