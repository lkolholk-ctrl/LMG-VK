# Stage 4b — Host-only PCM capture and verification bench

This package follows the accepted Stage4a transition-schedule compiler (user-reported
commit 92789a5). It is a validation tool, not a second player, an Android audio backend,
an automatic playback feature, or a claim of complete Apple rendering parity.

## Architecture and actual dependencies

The host-only CMake subdirectory `native/automix/offline` links the existing
`lmg_automix` static target. It creates no shared library and is excluded when ANDROID
is true. No Kotlin, JNI ABI, AudioService, PlayerAudioChain, Media3 dependency, DSP
formula, or application code is changed. The only existing modified file is the
native root CMakeLists.txt. Both the compiled plan and the capture retain
`canExecute=false`.

There is one output-frame timeline and two independent PCM lanes. Those are buffers
and per-track DSP states, not two ExoPlayer instances. Stage4c must continue to use the
user's existing single-player media3-lmg architecture.

The real processing dependencies are:

- Stage4a `compilePlannerTransitionSchedule` and its native JSON composition;
- `ProcessedTrackStream`, containing `ScheduledTrackGraph` BEFORE `TimePitchStream`;
- `prepareGraphEvents`, original graph coefficient/kernels and mapped-hop TimePitch;
- `PlaybackTimeMap` and `ContinuousAutomationValues`;
- separately applied scalar output gain and one float addition in this host bench.

None of these native kernels is replaced by a mock, a resampler, generic crossfade,
Python audio processing, or newly introduced DSP library in the shipped targets.
`out_gain` is not an effect parameter inside DSPGraph. The existing stream has no EOF
primitive and no implicit tail policy; passing null enqueues ordinary zero PCM.

## Deliberately explicit offline policies

This is a finite, cued-excerpt test. Each PCM input begins at its side's compiled cue.
Cold per-track graph/TimePitch states are constructed off any audio callback. Earlier
playback history, decoder preroll, renderer scheduling and the A -> B -> C state carry
are not reconstructed by this tool.

The continuous plan stays immutable. Effects are sampled on an explicit SOURCE-frame
grid, default 256 frames, plus every continuous point boundary. The grid does not depend
on enqueue/dequeue block sizes. Fractional boundaries apply to the first sample at or
after the point. Multiple records are evaluated in source record order; the final write
for a graph address at the sampled instant wins. Equal double values are compacted
before the existing AU-float-narrowing and snapshot compiler are called. Unsupported
live geometry setters are rejected, not silently ignored. This sampler is a BENCH
policy, not a newly claimed Apple/Media3 event cadence or recovered AudioUnit host.

TimePitch uses its scheduled/offline mode and a callback into the EXISTING native time
map. The callback returns advancing source-frame coordinates with the correct cue
origin. Explicit bench controls are rate=1, pitch=1, smoothness=8, coherence=false and
preserveTransients=false. Mapped hops determine the variable source advance. The fixed
rate control is NOT declared Apple's transient-policy default; dynamic transient/coherence
configuration is outside this capture profile. Invalid callback mapping quarantines the
call and makes the owner fail without returning a partial capture.

The first full out_gain descriptor is evaluated through the existing continuous-value
implementation at the output frame's inverse-mapped song time. It is narrowed to float
and multiplied exactly once AFTER graph and TimePitch. The sum of the two already-gained
lanes uses one float addition. There is no limiter, normalization, automatic master gain,
dither or extra fade. This is a transparent reference gain/mix consumer for testing, not
an implementation of the remaining AVAudioMix-to-MEMixerChannel slot binding.

The scheduled TimePitch reset already includes its native padding/skip behavior. The
bench does NOT remove another half-FFT block. It reports native declared scheduled
latency (zero), the number of accepted input frames at first delivery (lookahead, NOT
wall-clock latency), and actual impulse/clock tests separately. Buffers are poisoned
before dequeue and only returned frames are accepted as valid.

## Finite input, tails and accounting

Real-input frames, accepted zero-input frames and captured output frames are distinct
counters. Only the count returned by enqueue advances the source pointer. Only the
count returned by dequeue advances the output timestamp. Pending intermediate frames
remain owned by the stream. Fill-ahead tests deliberately apply backpressure, and zero
frame calls must not progress either stage.

A fixed output capture horizon and a separate zero-input budget are supplied explicitly.
No-progress or exhausted padding before the requested horizon is a failed run, not a
short successful WAV. Longer captures can contain residual echo/reverb; their diagnostic
raw and gained lanes extend beyond the requested mix, but the actual mix remains within
the Stage4a transition range. Capturing a tail never extends a playback range or grants
execution permission.

Three labels are possible:

`NOT_CAPTURED`: the transformed source EOF is beyond the captured output.
`RESIDUAL_AT_CAPTURE_LIMIT`: the final measurement window exceeds the configured threshold.
`FINAL_WINDOW_QUIET_NOT_PROOF_OF_DRAIN`: the final window is quiet; later sparse echoes,
frozen bypass state or unobserved buffered data have NOT been proved absent.

No report says "full drain complete". Raw post-DSP lanes and raw-tail metrics remain
available even when output gain hides that residual in the gained lane. Canonical BM
bypass gates remain in force. Separate active-graph impulse probes exercise delay and
reverb tails without silently removing those gates from the actual transition.

## Actual fixture composition

`fixtureSchedule` constructs explicit SYNTHETIC analysis responses and an allowed scoped
source context, then uses the actual JSON decoder, catalog loader, source-context
resolver, input producers, selector and Stage4a compiler. It supplies no manual seeds,
bar descriptors, compatibility booleans, scores, windows or rates. It separately checks
that the typed plan equals the encoded plan from the existing Stage4a raw-JSON API. This
additional search is test verification, not another search added to production.

The canonical catalog must be present and retains SHA-256
`fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120`.
Root CMake and the host acceptance script check that resource. The CLI itself reads the
explicitly supplied catalog and is not a replacement signature/trust service.

Unity uses the existing 120-second 120/120 BPM fixture selecting style9. Stretch uses
the 96-second 120/150 BPM fixture selecting style12. PCM stimuli are independently
created tones, deterministic low-level noise and impulses. They are not Apple song
recordings or captured full-Apple-renderer reference outputs. Optional caller WAVs must
already be cued float32 excerpts; their analysis remains the SYNTHETIC fixture and is
labelled as such. Do not infer real recording identity from that mode.

## Test contracts

Five new CTest entries are added with label `offline_pcm`:

1. `automix_pcm_utils`: float-WAV read/write, create-only publication, finite values,
   allocation limits, measurements, signed zero and malformed headers.
2. `automix_pcm_transport`: mono/stereo 8/44.1/48 kHz, accounting, zero calls, input
   ownership/backpressure, variable I/O partitions, silence and gain-once checks.
3. `automix_pcm_latency_tails`: impulse positions/amplitudes/stereo isolation; independent
   0.75x/1.5x source-advance and 311 Hz pitch-preservation probes; active delay/reverb
   tails, larger captures and unchanged prefixes.
4. `automix_pcm_schedule`: actual canonical JSON/native selection -> immutable Stage4a
   plan -> real kernels -> one aligned mix, plus fractional map origins and explicit
   zero-budget failures.
5. `automix_pcm_report_guard`: independent Python float-WAV/report parsing with malformed
   and false-completion negatives. This test alone is NOT DSP execution.

I/O partition comparisons use absolute tolerance 2e-5, not fitted gain, waveform shifting,
normalization or post-hoc resampling. This explicit cross-block tolerance is not an Apple
parity threshold. Effects retain their existing block-boundary cleanup/FMA behavior.
The frequency probe is a coarse smoke check (311 +/-3 Hz), not a perceptual quality score.
The source-advance allowance is bounded by FFT lookahead and two maximum input blocks.

Four optional server mutation cases must fail the expected test assertion: double gain,
hidden attenuation, disconnected native time map, and shifted incoming mix. The script
uses temporary source/build files linked to the original prebuilt archive; it never
edits repository sources. Baseline must pass first. Compilation failures, signals,
timeouts and unrelated failures do not count as successful mutation detection.

## Capture artifacts and independent verifier

The CLI publishes a NEW directory with seven IEEE-float32 WAVs and `report.json` written
last. A partial write does not publish a completion report. Existing paths are not
overwritten; failed directories are left for inspection. Float samples outside unity
are reported, never clipped. All sample rates/channels are explicit; no resampling occurs.

The Python verifier rechecks RIFF extents/format/fact counts, finite samples, frame
accounting, measured RMS/peak/mean, tail status, output-gain ownership metadata and the
exact float32 one-timeline mix equation. It emits SHA-256 for the report and seven WAVs.
It validates self-consistency, not the independent truth of every DSP parameter. The
block-partition claim comes from the two real C++ renders; the verifier does not rerun
them or treat a JSON field as a fidelity certificate.

## Verification performed while authoring this package

Executed locally: 42 standalone PCM/WAV support assertions, the same executable under
Clang ASan/UBSan with leak detection, Python report-validator tests, Python syntax and
Bash syntax checks, and temporary-repository installer tests (see actual logs).

GCC and Clang object-compiled the new bench, fixture, integration-test and CLI translation
units against a local declaration-only mirror of the retrieved native interfaces. This
checks source-level interface usage; it is NOT a link, ABI/device verification or a run
of the existing DSP. Those mirrored headers are NOT included in the delivery.

The environment did not contain a complete native checkout or linkable DSP archive.
Therefore actual new DSP CTests, canonical positive JSON-to-PCM execution, four native
mutations, capture WAV generation and full host JNI/Android regression were NOT executed
locally. No synthetic DSP stubs were run to make those results appear passed. Server
acceptance must compile and execute the unchanged actual kernels; the scripts enforce
that baseline and report failures rather than relaxing assertions.

Expected full server totals with host tests and PCM bench ON: 68 CTest (63+5). Existing
53 real-JNI, 44 lifecycle tests, three APK ABIs, three defined JNI exports and catalog
hash remain unchanged. Expected is not a claimed local full-suite result.
