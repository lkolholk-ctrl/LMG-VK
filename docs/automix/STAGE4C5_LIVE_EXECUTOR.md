# Stage 4c-5 — explicit single-pair live executor

Date: 2026-09-25. Applies after accepted Stage 4c-4 Output Port and the same-base
`1.5.1-lmg30-boundary2` fork. This is the first **installed, explicitly requested
DEBUG live path**, not a complete production AutoMix or a device certification.
No Player, Renderer, AudioSink, AudioTrack, external DSP dependency or extra playback
thread is constructed. The two existing audio renderers feed the existing selected
outgoing sink; its AudioTrack remains the output for A->B and the remainder of B.

## Delivered path

A validated Stage 4a source-context schedule is copied into an immutable execution
payload on observation publication. The debug service request is accepted only for
that exact generation/revision. The real `ExoPlayerImplInternal` acquires the actual
prepared A and B MediaPeriodHolders and SampleStreams, prepares B at a bounded preroll,
and mints a package-private `LmgLivePlaybackLease`. Application code cannot construct
this grant through its public API. The lease rechecks the owner thread, revocation,
actual queue links, timeline, stream identity and player state. It pins the real
DefaultMediaClock override before the accepted ingress transaction claims PCM.

Both cue buffers are held by the existing gate. `LivePcmPump` commits the accepted
native ingress, uses the accepted SameSinkPairOutput, emits the complete A prefix,
feeds `NativeLivePcmExecutor`, and writes one monotonic sequence in the selected
sink's actual PCM format. No newly allocated replacement audio device is used.

The native executor composes the existing ProcessedTrackStream (DSPGraph then
TimePitch), PlaybackTimeMap and ContinuousAutomationValues. It maintains a separate
ready cache per side. A ready A cache survives B starvation; no missing input is
relabelled EOF or padded as if it were a completed source. Output gain is applied
exactly once after each track's processing. PCM16 conversion uses explicit nearest
integer rounding and saturation, counted in native statistics. Float32 output is
finite but not normalized, limited or silently attenuated.

## Scoped first live behavior

The actual fork acquisition permits only one A->B with B the final prepared period;
no third period, repeat or shuffle. Sources must also pass the existing source-
context policy (nonspatial, no previous state, established recording identity,
matching supported reference/playback durations). Unsupported sources are not made
supported by replacing UNKNOWN with ABSENT or granting policy ALLOWED artificially.
Only same-format PCM16/float32 mono/stereo and the existing finite, unclipped,
single-period boundary domain are accepted. No resampler is added.

The first live entry additionally requires normalization OFF and DjStreamFx idle.
It never silently disables an existing processor or applies A's frozen normalization
gain to a mixed A+B signal. Existing Bass/DJ/normalization instances and their order
stay installed; keep these settings stable during a debug run. The check is not an
atomic global transaction with arbitrary third-party processor setting mutations.

The request is inactive by default. Release builds cannot arm it via AudioService.
Observation schedules retain `canExecute=false`: a value report is not a device grant.
The separate live runtime report becomes LIVE with nativeDspInstalled and actual-
PlaybackLease only after the real fork lease, cue claim, output activation and native
executor creation succeed. REQUESTED means accepted intent, not completed takeover.

The native path remains installed for all of B up to its real decoded EOF. The app
publishes B's media item only when the existing output clock reaches the transition
end. This is an explicit LMG handoff policy, not an attribution of renderer-switch
authority to Apple's referenceTransitionTime. The writer/clock are NOT switched to
the intentionally silent incoming AudioSink. B is drained on the same selected sink;
then renderers are disabled, retained A is released, gain control is reset and the
player reaches ENDED. Mid-B handback and A->B->C chaining are **not** implemented here.

## Clock, period and gain ownership

Normal reading/playing advancement and the old manual fade driver are held while
the actual lease is present. Source EOS is explicitly forwarded even though normal
reading advancement is held. The existing playback loop does bounded work before
and after renderer callbacks, scheduled by its existing Handler. No busy loop,
new timer thread or Main-thread native teardown is introduced.

Owned DefaultMediaClock never falls back to elapsed-time StandaloneMediaClock while
waiting for PCM. It queries the same output sink through the pump and converts the
actual sink media position through the native inverse time map. It caps reported
frame progress by complete frames actually accepted by the sink. Accepted frames
are not by themselves declared audible. Before the first owned packet, only the
real existing A renderer clock may describe still-playing queued legacy PCM. Missing
subsequent clock observations freeze the last position instead of inventing time.

Fresh gain proof is important after flush. The fork sends actual typed split-gain
messages to both renderers after startup, from its real playerVolume (including
focus ducking). It refuses a non-unity outgoing fade and does not enter manual
crossfade state. The application does not manufacture a gain grant or replace the
master level by 1.0. After activation the accepted output port ignores only the
legacy transition factor; user volume and ducking still reach the real same sink.

User pause/seek/playlist/track-selection/repeat/shuffle/speed/crossfade-policy changes
revoke and clean up the real lease before ordinary control mutation. Existing sink
route/format changes also revoke. After claim, uncertainty causes coordinated actual
renderer reset/seek, never replay of admitted codec bytes through legacy forwarding.
The reset anchor is the latest real source position, not queued output duration or
wall time. A retained A removed from the forward queue at handoff is released only
after the real renderers have been disabled. A true sink drain precedes successful
completion; indefinite effects or arbitrary third-party pipelines are not certified.

## Explicit host policies, not recovered universal Apple defaults

- Request acquisition starts at most 2 s before outgoing cue; B preroll is 250 ms.
- Pair preparation/hold budget is 5 s; output/input stall watchdog is 3 s without
  actual source/write/clock progress. These clocks control failure, not media time.
- DSP control writes use a 256-source-frame grid plus automation boundaries.
- TimePitch scheduled/offline controls: rate 1, pitch 1, smoothness 8, coherence false,
  preserveTransients false; the confirmed schedule supplies the mapped source hops.
- Cue quantization is the existing nearest sample-frame policy. The accepted
  near-integer mapping stabilization removes <1e-6-frame floating arithmetic noise.
- True EOF alone permits bounded zero lookahead (FFT size * 64). No starvation zeros.
  B output ends at its mapped actual EOF; infinite/full effect-tail drainage is not
  asserted. Canonical supported BM styles 8/9/12 are the intended schedule scope.
- Initial allocation, coefficient preparation and JNI ownership are on the playback
  owner. No hard real-time or allocation-free startup claim; profile on devices.

These bounds do not alter the catalog or the existing DSP/scoring formulas. The
server's Stage 4b offline stabilization/tolerance files are not overwritten.

## Test and provenance interpretation

The new native test ran against the real prior project's DSP from GitHub Actions
run 35836465219, artifact 10738968416 (SHA-256 of ZIP
1963f0ef8bc5fe8f54381e2bebc24db2be06683bddc9116051289408315e3928).
To coexist with the new JNI wrapper in host tests, only the downloaded test copy's
SONAME bytes were renamed; native instruction bytes were not rewritten. Exact
provenance and changed offsets are in the package checks/test-provenance.json.
Two unused timing implementation files were compiled from their verified original
source; required declaration layouts were checked against CI library DWARF.
No prebuilt CI library, substitute runtime, Apple binary or local API fixture is
included in this release package. Server scripts link the complete current tree.

Local tests actually executed the new native engine with real prior DSP at 8/44.1/48
kHz, mono/stereo, active filtering, varying source/output blocks, starvation, changing
rates, true EOF, malformed input and PCM16 saturation. Thirteen Kotlin scenarios
executed real ingress JNI + real DSP JNI + production pump + recording output ports.
Their lease and output backend are explicit test fixtures, not physical Media3/AudioTrack.
Five isolated C++ mutations failed assertions. ASan/UBSan instrumented new code and
restored timing sources, **not the downloaded prebuilt DSP core**. A full sanitized
rebuild on the server is a separate required gate.

Eight tests executed the actual Java lease class with fixture period callbacks.
The four actual DefaultMediaClock JUnit tests and three real fade-control reservation
tests require the rebuilt Android fork. Driver/wrapper source was checked against
compile-only API shapes, not a real AAR. Bytecode/report verifier tests use clearly
synthetic dumps. Installer tests use genuine ordinary predecessor files but a
synthetic full service; the release installer authenticates the actual full service
and fork blobs in the user's checkout before any write.

**Not locally run:** full fork Gradle/JUnit, complete Android app compilation and APK,
full native 71-CTest tree, physical output, decoder preroll fidelity or device latency.
A bytecode guard cannot prove callback ordering or click-free sound on a device.
The implemented live path must pass those server/device gates before production use.
