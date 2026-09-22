# Fidelity audit — 2026-09-16

Objective is full AutoMix, not a generic compatible mixer. Completion is unproven.
Research root: `/srv/research/apple-music-ios26/`.

| Requirement | Current evidence | Status / next action |
|---|---|---|
| Automation evaluator | `decompiled_package/automix/disassembly/272275f84__FUN_272275f84.asm` | Implemented curve families; geometric 0x81 follows assembly rather than obsolete prose. Host schedule validation is separate. |
| Style schema | Raw `TransitionStyles.json`, SHA256 fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120 | Parser preserves 14 styles, 55 instructions, 74 automations. Counts alone do not prove semantics. |
| Graph topology / parameters | Raw `DSPGraph.dspg`, SHA256 8326e890fd6905a0fba94176ed57ae200fe38c206dbac1c07d8355c278dafd2a | Topology recovered; metadata is not executable effect math. |
| Filter/delay/reverb internals | Recovered EmbeddedSystemAUs kernels and original ARM fixtures, per-effect evidence files | LP/HP, five-section filter, delay and 16-line reverb implemented. Earlier substitutes remain excluded. |
| Time stretching | MEMixerChannel spec→aufc/nutp registration; TIME_PITCH_SETUP/PHASE evidence | FFT geometry/window/latency and phase/hop arithmetic implemented. Complete spectral PCM processing still pending. |
| Placement/time conversion | Original style resolver, playback calculator and CoreMedia numeric conversion fixtures | Nested placement, song/transition/stretched mapping and media-time conversion implemented. Final PCM delivery remains. |
| Planner and analysis fields | Original ARM tempo/score/loudness fixtures; raw captured MediaAPI response | Tempo matching, scoring subset, loudness calculations and raw JSON decoder implemented. Full region generation, predicates and normalized cloud mapping remain. |
| bypa activation | Original style gate, AU property setters and reset paths | Serialized frame-scheduled activation implemented: reverb state preserved; filter/LP/HP/delay reset on reactivation. Concurrent setters outside contract. |
| Stepped schedules | Original grid/sampler, ramp selector, range zipper and MusicKit consumer | Effect events, output-volume intervals and average-rate intervals implemented. Not a PCM volume/TimePitch renderer. |
| App / Media3 | Independent Kotlin/JNI scheduled-track-effects bridge, real host JVM checks | JNI bridge exists; no complete app AutoMix connection yet. Working lmg31 crossfade preserved. |
| Vendor portability | No JUCE/Oboe in native CMake | Fulfilled restriction so far; no claim of full device verification. |

## Retractions

The sections below are chronological records. Their historical remaining-work
lists are superseded by the current table, README and individual evidence files.

Previous tests verified a custom DSP prototype, NOT parity with Apple. Successful
builds and full catalog parsing did not establish a complete AutoMix engine.
`excluded_prototype/` is retained only as a record of rejected assumptions.

## Available next evidence

`decompiled_package/targets/AudioToolbox` (5.4 MiB) and `AudioToolboxCore` (6.0 MiB)
exist, along with symbol lists. AppOS shared-cache parts also exist. Their presence
is not proof that every relevant text section is present. Inspect AU processor
factories/implementations and address coverage before claiming DSP is unrecoverable.
Final status reports alone are not a sufficient reason to stop research.

The latest audit report explicitly records unresolved timing, TimePitch delivery
steps and bypa activation. The older specification bundle and top-level report
must not override later raw evidence/red-team corrections.

## Verification after excluding substitutes

CMake archive members were inspected: automation.cpp.o, mixer.cpp.o, styles.cpp.o,
style_curves.cpp.o only. Rejected effects/renderer/timing objects are absent.
Both retained CTest suites passed with ASan/UBSan (25.94 seconds); the raw catalog
schema check also passed. These results cover only the currently built subset.

Memory snapshot before further binary work: 11967 MiB total, 5649 MiB available,
3493 MiB swap used. Build concurrency is limited to two; no emulator, Android build,
whole-cache disassembly or unrelated service termination was performed.

Additional candidate evidence: `targets/libAudioDSP.dylib` and its symbols exist.
Symbols include `DspLib::LexiPlate::Algorithm::processReverbTank` at 0x1dc922d04
and `processPreDelay` at 0x1dc922b90. These are search leads only: no link to the
SmartTransitions rvb2 component has been established, so they must not be substituted
merely because their names refer to reverb. AudioToolbox contains TimePitchUpstream
0x1b8f5f248 and TimePitchDownstream 0x1b8f5f5a4. Follow component registration and
actual call/data dependencies, not name similarity.

## Subsequent verified implementation progress

Newly recovered code, not the excluded prototype: LP/HP coefficient updates and
their shared sample kernel are compiled, followed by the actual AUDelay kernel.
Call chains, constants and bounded disassemblies are documented in
`evidence/LOWPASS_IMPLEMENTATION.md`, `HIGHPASS_IMPLEMENTATION.md`, and
`DELAY_IMPLEMENTATION.md`. Platform libm rounding limits remain explicitly stated.

Verification this pass: core/styles/LP/HP suites passed with ASan+UBSan (20.85 s).
After adding delay, affected styles/LP/HP/delay suites passed (0.15 s); unchanged
core was not rerun. Delay active processing performed zero heap allocations under
the test allocation watcher, including a two-wrap signal. Memory available after
verification: 5054 MiB; swap used 3555 MiB. No emulator or Android build.

Remaining full-product work is unchanged: faithful reverb, AUFilter, TimePitch,
parameter scheduling, planner and server-analysis mapping, JNI/Media3/app wiring,
and end-to-end verification. Passing component tests is not completion.

## Matrix reverb pass

Implemented the recovered 16-line matrix reverb in `src/reverb.cpp`, including
deterministic geometry RNG, prime delays, spectral decay, mono/stereo routing,
linear wet mixing and parameter-change reset behavior. The independent fixtures
execute the bounded original ARM functions, with host libm/allocation hooks;
all 20480 reference output floats matched bitwise. This does not claim Apple
libm or the complete AudioUnit host was executed. See `evidence/REVERB_IMPLEMENTATION.md`.

All six native CTest suites passed with ASan/UBSan in 24.06 seconds. Active reverb
processing and decay changes performed zero heap allocations. Available RAM was
4993 MiB, swap remained 3555 MiB. Only a 110 MiB cache part was additionally
extracted for the actual RNG; no Android build/emulator or services were started.
The reverb fixture tool ran in under one second and does not boot an OS.

Next effect trace is already established (not yet implemented): AUFilter factory
`0x234f3a43c` -> constructor `0x234f3a4e0`, class vtable `0x284adf598`,
allocator +0x240 `0x234f3921c`, kernel vtable `0x284adf808`, process +0x18
`0x234f39304`, coefficient update `0x234f393b0`. It processes FIVE existing
biquad kernels in order offsets +0x18,+0xa8,+0xf0,+0x138,+0x60. Raw graph maps
Fcf1/Fcg1/Fbw1 to AU parameter IDs 3/4/5; there are 15 AU parameters total.
Defaults for IDs 0..14 are 0,100,0,625,0,2,2500,0,2,5000,0,2,0,10000,0.
Do not mistake modes 0/12 = 0 for bypass: they use shelf coefficient wrappers
`0x234f896c4` (dispatcher kind 7) / `0x234f89710` (kind 8), while mode 1
selects resonant HP/LP. Band update `0x234f395e8` converts bandwidth using
constant .34657359027997264 and a branch at `0x236f45ca0` (resolve import),
then dispatches kind 11. Those dumps are retained in `/tmp/lmg-au-evidence/disassembly`.

Remaining: AUFilter, TimePitch, faithful graph/event scheduling and bypass/tail
wrappers, planner/timing/server mapping, JNI/Media3/application wiring and full
product verification. Working lmg31 crossfade and app sources remain untouched.

## Five-section filter and gain control pass

AUFilter's full five-section coefficient/update/sample path is now implemented.
All 125 coefficients and 4169 sample outputs match execution of the original ARM
routines bitwise in five independent fixtures with host libm hooks. This includes
shelves, resonant edge modes, peaks, clamping and a fractional double sample rate.
Details: `evidence/FILTER_IMPLEMENTATION.md`.

Before connecting the graph, recovered an important additional behavior:
GenericGainBox uses 20 ms smoothing (2 ms minimum), queues gain writes during a
running ramp, and holds ramp advancement for input flagged silent. Added the
actual control state as `GainSmoother`, tested initial/queued writes, mute,
silence, truncation and reset. This is control only; sample multiplication must
still reproduce its imported vector path. It is not replaced by direct curve
evaluation. Details: `evidence/GAIN_CONTROL_IMPLEMENTATION.md`.

Affected styles/LP/HP/delay/reverb/filter/gain suites passed under ASan/UBSan in
0.39 seconds. Unchanged long core suite was not rerun (last pass 23.74 seconds
in the previous turn). Available memory after checks: 5057 MiB, swap 3557 MiB.
No Android build/emulator or unrelated service changes.

Next actionable sample paths: verified .06 branch-island UUID and extracted
only 409600 bytes. `0x1937345e0` -> `0x234b33380`, a concrete ramp multiply
routine in the already present .48 text. Bounded dump saved in
`evidence/gain/ramp_multiply_candidate.asm` through next routine 0x234b33680.
It has alignment-sensitive SIMD, 32-/4-sample groups and scalar tails; do not
collapse it into a single scalar formula and claim bit identity.
`0x193734640` -> `0x234bcf5b8` -> authenticated pointer at `0x280df0958`;
`0x193734620` -> `0x234bcf594` -> pointer at `0x280df0940`. Resolve those data
fixups/CPU dispatch before implementing the steady gain and mix sample paths.
Remaining product work includes graph/events/bypass, TimePitch, planner/time
resolution, server mapping, JNI/Media3/app integration and end-to-end verification.

## Gain sample processing pass

Implemented the actual contiguous `_vDSP_vrampmul` sample ordering, including
destination alignment, 32-/4-frame groups, scalar tails and returned accumulator.
Resolved the steady and mix import chains to `_vDSP_vsmul`/`_vDSP_vsma` using the
image's nlist and its actual function-start table. Their reached ordinary kernels
use float multiply / fused float multiply-add. Added both sample functions and
`applyGainBlock`, connecting recovered control to PCM processing once per channel
without advancing smoothing twice. No heap allocations exist in these methods.

Independent execution of original ARM sample instructions (only PAC patched, no
math hooks) generated 936 ramp and 312 steady/mix fixtures. All sample outputs
and ramp accumulators matched bitwise, including in-place and all four float
destination offsets modulo 16. Original long-buffer CPU-accelerated paths were
not executed; see `evidence/GAIN_SAMPLES_IMPLEMENTATION.md` for that boundary.

Affected eight native suites passed ASan/UBSan in 0.46 seconds. The unchanged
long core suite was not repeated. Available memory: 5074 MiB, swap: 3557 MiB.
No Android build/emulator, unrelated service changes or app/crossfade edits.

The filter/gain/delay/reverb building blocks now exist with recovered sample
processing, but a connected graph is still absent. Next work is graph ownership,
per-channel/per-track state and scheduling/parameter writes, then the remaining
TimePitch/planner/server/app end-to-end path. Do not report component completion
as a finished AutoMix product or replace the unresolved runtime bypass decisions
with an invented always-active setting.

## Connected graph pass

Added `PreparedGraph` and `TrackGraph`, executing the recovered full per-track
dry/wet routing with the verified kernels. Prepared snapshots update coefficients,
gain controllers and effect parameters without callback allocation. The graph
owns per-channel filters/delays and one shared stereo tank per track. Graph
defaults retain `bypa=true`; unsupported live geometry/bypass changes are rejected
atomically instead of receiving invented lifecycle behavior.

The new graph integration test passed ASan/UBSan (0.07 s), including stereo wet
coupling, independent track state, delay channel isolation, zero-input tails,
in-place processing, identical channel smoothing and allocation watching for
both processing and applying prepared updates. See `evidence/GRAPH_IMPLEMENTATION.md`
for API contracts and the important remaining wrapper/silence/scheduling limits.

The connected DSP graph is now present. Remaining end-to-end work is still
substantial: AU host lifecycle/silence semantics, schedule delivery and timing,
TimePitch, planner/server analysis, JNI/Media3/app integration and product tests.
The active goal is not complete. Existing lmg31 crossfade and app files untouched.

## Explicit stepped graph event transport

Added prepared frame events, all 23 live DSPGraph scalar bindings, float value
narrowing, same-frame coalescing and explicit repeated-decay reset delivery.
Graph compatibility is validated before playback; callbacks split at supplied
events without allocations. Scheduled/manual identical-slice output and failure
boundaries pass ASan/UBSan. This is an LMG transport policy, not a reconstruction
of Apple's event sampling cadence or song/playback-time conversion. Full host,
TimePitch, planner and app integration remain open; see
`evidence/SCHEDULED_GRAPH_IMPLEMENTATION.md`.

## Recovered playback time calculator

Implemented active-domain song <-> transition mapping from `0x27224fb08` /
`0x27224fc0c` and their rate integrals. Verified log/exp imports through `.67`
and libsystem_m symbols. Preserved the near-constant slope branch and ARM
arithmetic grouping. 256 original-instruction reference cases (768 doubles)
match the C++ port bit-for-bit with host libm; ASan/UBSan tests pass. Planner
anchor extraction, pre-anchor/absent-ramp dispatch, event sampling and actual
TimePitch audio processing remain open. No synthetic time-stretching substitute
was introduced. Details: `evidence/PLAYBACK_TIME_IMPLEMENTATION.md`.

## Playback-time wrapper follow-up

Recovered and implemented pre-anchor/no-ramp unity dispatch from `0x27225157c`
and signed stretched intervals from `0x27220f754`. Added an actual optional ramp;
no synthetic ramp substitutes for absence. 64 new original-ARM wrapper cases
and the previous 256 active cases pass bit-for-bit under host libm + ASan/UBSan.
The earlier active-domain-only limitation is removed. Planner field extraction,
event sampling, TimePitch and application integration remain open.

## Previous-transition anchor carry

Added `playbackAnchor` from `0x27227965c`: playback transition start and start
song time plus the previous stretched-minus-song offset. Getter/Codable field
names and ARM arithmetic were checked. Twelve original-constructor fixtures
with automation lookup explicitly hooked absent pass; total playback mapping
reference coverage is 332 cases. Rate-ramp selection is not covered by that
hook and remains pending, as do planner/scheduler/TimePitch/app completion.

## Continuous automation to rate-map selection

Added adjacent continuous-point ramp conversion and full-descriptor ts_rate
lookup; connected first selected ramp and previous-state anchor to PlaybackTimeMap.
Preserved first-match/first-ramp and empty/single-point behavior from the source.
Behavior tests and all 332 original time fixtures pass ASan/UBSan. This closes
the typed-record-to-time-map bridge, not planner generation or application
AutoMix. See `evidence/CONTINUOUS_SCHEDULE_IMPLEMENTATION.md`.

## Continuous parameter evaluation

Ported source lookup `0x272271704`: empty/default, pre-first value, post-last
value and reverse inclusive ramp selection. Added immutable preparation and
seconds-domain queries, sharing recovered curve math with frame ramps. ASan/UBSan
core regression and continuous/playback tests pass (21.74 s total), including all
332 existing ARM time fixtures. Normalized style placement remains unresolved;
no prose-derived replacement was introduced. See continuous schedule evidence.

## Normalized timing and bypass activation resolved

Located actual style builder `0x272210660`. Added two-level placement/ramp time
mapping with clamping and reversed-window midpoint collapse; 126 original ARM
arithmetic-block fixtures pass bit-for-bit. Also located the automatically
prepended four-point bypa gate (`0x272211104`) and ported its insertion rule.
Continuous schedule boundary tests pass ASan/UBSan. This supersedes prior
normalized-mapping/activation-not-found notes, but parent cue selection and live
AU bypass property behavior remain open. See `evidence/STYLE_TIMING_IMPLEMENTATION.md`.

## Full descriptor catalog and continuous style compilation

Executed all 29 original parameter initializers without hooks; preserved raw
ranges/defaults and both ID domains. Resolved +0x28 string as styleParameterID
and verified source lookup at `0x27226bd5c`. Added resource-to-continuous compiler
with mapped values, planner ts_rate overrides, recovered timing and bypa gate.
Both sides of all 14 styles/74 automations compile in ASan/UBSan tests; existing
332 playback references remain green. Planner input production, event delivery,
AU host, TimePitch and app work remain. See STYLE_SCHEDULE_IMPLEMENTATION.md.
