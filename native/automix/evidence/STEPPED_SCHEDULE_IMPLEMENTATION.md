# Continuous automation to stepped points

`compileSteppedAutomations` implements the per-song stepped automation path:
transition-time grid, playback-time conversion, song-time curve evaluation, then
adjacent equal-value removal. It also builds ramps independently from the raw
samples; see VOLUME_RAMPS_IMPLEMENTATION.md. It accepts an explicitly selected stepped policy;
the recovered default planner policy remains continuous. No global 200 ms
sampling rule is imposed, and no PCM frame quantization occurs in this module.

## Source chain

Source image: extracted iOS 23A341 `_SonicKit_MusicKit_Packages`.
`0x27226725c` selects stepped conversion only for policy tag 0 and validates
the step duration. `0x272282c80` builds the two calculators and landmark arrays.
`0x2722842b0` filters out the complete `ts_rate` descriptor, retaining other
automations in original order, including duplicates and `out_gain`.
The Automation.points getter `0x272279ce4` calls `0x272279d20`, which calls
sampler `0x27227b000` and compacts its output.

SongSchedule playbackTransitionTimeRange getter `0x272277dbc` reads +0x10/+0x18;
referenceTransitionTime getter `0x272277e1c` reads +0x20. Thus the arrays built
at `0x272282d4c..d98` contain:

* Outgoing: outgoing playback start, incoming playback start, outgoing reference
  transition time, outgoing playback end.
* Incoming: incoming playback start, outgoing reference transition time, outgoing
  playback end, incoming playback end.

`0x272282f30` removes duplicates using a Set, `0x272283124` sorts the result.
`steppedTransitionLandmarks` ports this choice of fields. The incoming reference
time is not substituted for the outgoing reference. These are transition times,
not normalized style positions or song times.

## Grid and values

Grid builder `0x272252970` returns empty if the landmarks are empty or the total
span is shorter than one step. Otherwise it emits the first landmark, then for
each landmark:

1. While its distance from the last emitted time is at least twice the step,
   emit `last + step`, using repeated double addition.
2. Emit the last landmark unconditionally. Emit another landmark only if both
   its distance from the last emitted point and its distance from the final
   landmark are at least one step.

This does not emit a regular `start + i*step` sequence followed by every landmark.
Near-end landmarks may be skipped. A nonempty but short transition may produce
no points; the port does not insert a fallback endpoint.

Sampler `0x27227b000` maps each grid transition time to the full PlaybackTime
triple. Before the playback anchor, or without a selected rate ramp, it uses
the recovered unity fallback. Otherwise it calls `0x27224fc0c`. Parameter
evaluation uses the resulting **song** time, the first value before the first
point, the parameter default for an empty automation, and reverse-inclusive
ramp selection through `0x27227086c`/`0x272275f84`.

Compactor `0x272279d20` preserves the first sampled point, then the second point
of every adjacent pair with unequal values. Time differences do not cause a
write when the value is unchanged. There is no forced final point. Numeric
equality treats positive and negative zero as equal; the retained point keeps
its original bits. Parameters are not clamped to descriptor min/max.

The original ramp zipper also rejects nonterminal optional curve tags above
0xfb (`0x272270950..958`); these are reserved optional encodings. The C++
continuous point validator now rejects them too. The standalone arithmetic
curve evaluator's fallback-byte behavior is a separate API.

## Verification

`tools/stepped_schedule_reference.py` executes the original ARM functions:

* 372 grid cases: empty/single/short/equal-step spans, boundary rounding,
  irregular landmarks, offsets and all supported step-range endpoints.
* 96 compaction cases: empty/single/constant/changing points, signed zeros,
  one-ULP value differences and repeated values.
* 96 ramp-selection cases: original raw-point selection and pair closure,
  including constant plateaus before/after changes and signed zeros.
* 216 combined sampler cases: original grid, complete time calculator,
  adjacent-ramp construction, reverse selection, curve arithmetic and
  compaction executed together. Cases cover absent/unity/increasing/decreasing
  rate ramps, queries before the anchor, empty automation defaults, nine curve
  tags and same-time jumps.

Swift allocation, refcount, slice creation and array concatenation are supplied
by the harness; libm imports use host math. PAC entry/return instructions are
patched. The isolated compaction fixtures substitute supplied sampler output;
the combined cases execute the actual sampler. Code/binary/fixture digests and
hook addresses are in `tests/fixtures/stepped_schedule.json`. C++ tests compare
every retained value and all three times bit-for-bit. This is not an end-to-end
Apple playback run or a claim of Apple-versus-Android libm bit identity.

## Integration and remaining boundaries

The compiler consumes the output of `compileContinuousStyle` and the already
recovered `schedulePlaybackTimeMap`. Catalog tests exercise both sides of all
14 styles through this chain, with explicit test parent windows and rates.
These fixtures do not choose the planner's actual cues/rates/policy.

Prepared graph writes still use their explicit PCM frame timeline and reject
unimplemented geometry/out_gain/ts_rate processors. Duplicate parameter lanes
remain distinct here; EFFECT_EVENTS_IMPLEMENTATION.md now documents their
source-order merge into stretched-song CMTime events. Final PCM delivery and
continuous-policy delivery remain separate. Native
AutoMix still requires planner/TimePitch and Android playback integration.

Finite/order/progress validation and a one-million-point preparation ceiling
are explicit LMG host guards against malformed inputs/unbounded memory use, not
new DSP or recovered Apple constants. All schedule allocation stays off callback.
