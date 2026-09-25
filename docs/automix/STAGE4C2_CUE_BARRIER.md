# Stage 4c-2 — reversible codec-buffer cue barrier

## Delivered scope

An app-only transport step after accepted Stage4c-1 Output Boundary Binding.
Uses the existing `LmgPcmBoundaryListener` in the same `1.5.1-lmg30-boundary1` AAR.
No new Player, renderer, AudioTrack, native library, JNI export or DSP dependency.
The original sink still owns playback, output clock and transition volume.
This does NOT implement the live AutoMix executor or an atomic DSP/gain handoff.

The new `PcmCueGate` shares the existing boundary controller across both audio sink
wrappers. Unrequested operation forwards the SAME original ByteBuffer, original
PTS/access-unit count, Boolean result and exception. It does not read samples.

A manual DEBUG-only AudioService request targets exactly one current generation
and binding revision. The attempt is tied to the immutable publication token;
one attempt per generation/revision prevents repeated requests extending a hold.
The timeout is a monotonic 1..500 ms deadline (default 100 ms), checked on callbacks
and snapshot reads. It does not create a timer or block the playback thread.
Timeout is a release condition, not a guarantee that callbacks keep arriving or
that a stalled device becomes audible within this time.

## The barrier

For each matching output occurrence, the first WHOLE not-yet-offered codec buffer
whose source-frame interval contains the cue is withheld with handleBuffer=false.
Any pre-cue prefix in that buffer is withheld too. No position, limit, PCM byte,
PTS, or AudioSink return convention is fabricated. A buffer ending exactly at the
cue may pass normally; the next buffer beginning at the cue is withheld.

At most one buffer per side is gated. The probe accepts at most 1 MiB per offered
buffer. A partially offered buffer is never taken back from the original sink,
even if that sink accepted zero bytes. This is intentionally more conservative
than the Stage4c-1 accepted-byte ledger. Offered input may have escaped to the
sink and cannot be treated as exclusively owned by the future DSP adapter.

Buffer identity, cursor, limit and PTS remain fixed over a held retry. Changed
output token, occurrence, format or unsupported runtime context invalidates the
attempt. Both sides must refer to the expected distinct playlist occurrences and
have equal supported PCM formats. Renderer creation order does not assign roles.
The gate supports the existing finite, single-period, nonclipped PCM16/float32
mono/stereo domain without remap, trim, tunnelling or offload.

## Sample seam policy (LMG transport, not an Apple DSP claim)

The compiled double-precision source cues are retained in RenderBoundaryPlan;
they are not reconstructed from the old conservative microsecond-floor fields.
Cue frames use nearest-frame rounding, ties toward positive infinity.

A codec PTS is converted after removing its renderer offset and applying its
period position. A unique zero-origin source frame must be within ONE integer
microsecond tick of the PTS. Checked integer arithmetic implements this test;
no subframe epsilon or heuristic drift repair is introduced. Supported rates
(8..192 kHz) make this interval narrower than one frame. A nonmatching grid,
noncontiguous buffer sequence or already-missed cue releases to ordinary playback.
This does not claim support for arbitrary fractional origins, encoder trimming,
resampling or a source/time mapping not already established by earlier stages.

The passive Stage4c-1 accepted-end estimate deliberately includes an extra PTS
uncertainty tick. Its CUE_ALREADY_FORWARDED diagnostic can therefore disagree
with a frame-aligned gate at an exact exclusive boundary. It remains a passive
conservative estimate, NOT the gate's sample ownership authority. The gate uses
its own offered-buffer interval, exact retained buffer and frame seam, and still
never authorizes execution. Physical acceptance and device playback are distinct.

## Release, cancellation and lifecycle

Cancel, timeout or invalidated publication releases the attempt. The next normal
renderer callback forwards the exact retained original to its original delegate.
No replay clone is generated, so partial sink retries cannot duplicate a prefix.
The codec itself continues to own the buffer until handleBuffer finally returns
true or its normal flush/stream replacement discards that output.

Pause/configure/route changes revoke the gate without pretending to flush the
sink. True flush/reset/release additionally clear the gate's stream accounting;
no old buffer is replayed into a new period. Decode failure and delegate exceptions
are not replaced by synthetic successes. Readiness and EOS methods retain normal
AudioSink delegation. A reference to an inactive held buffer is cleaned on the
owner's next callback/lifecycle event; there is no private queued PCM backlog.

The new internal copyHeldCuePair operation is diagnostic only. It copies both held
buffers into caller-preallocated direct writable buffers after full preflight,
without consuming the codec buffers. Destinations must be independently allocated,
disjoint from each other and from the source (different view objects do not prove
non-aliasing). Epoch, format and playback positions are
checked again. On invalidation during a copy, null is returned and destination
bytes must be discarded; a copy is NEVER a license to enqueue live audio. The
native executor has accepted zero frames. Release would play these bytes normally.
No buffer reference, source URI, credentials or raw PCM is exposed in reports.

## APIs and states

AudioService:
- autoMixCueProbeState — metadata snapshot.
- requestAutoMixCueProbe(generation, revision, maxWaitMs=100) — manual debug probe.
- releaseAutoMixCueProbe() — cancellation command; no sink method called from Main.

The service returns DISABLED in non-debug builds. Nothing arms automatically when
a schedule arrives. Ordinary builds remain pass-through. Calling this diagnostic
on a device MAY cause temporary buffering or an audible gap; silence-free or
realtime behavior is NOT established by JVM tests.

Phases: IDLE, ARMED, ONE_BUFFER_HELD, PAIR_HELD, RELEASED.
All reports: canExecute=false, ownsTransitionGain=false, pcmAcceptedByExecutor=false.
PAIR_HELD proves only a currently validated pair of retained original buffers.
It does not prove decoder preroll, sample-accurate clock alignment, AudioTrack
readiness, output takeover, end-of-transition handback or the full A->B->C chain.

## Validation and remaining integration

70 pure-Kotlin scenarios run the actual gate and accepted ledger, including
randomized partial-write release with byte-for-byte comparison; 62 predecessor
ledger scenarios run unchanged. Eight mutation checks require assertion failure
from a verified loaded mutant jar, not a compiler failure/crash/timeout. The same
original test bytecode is reused across mutants with explicit classpath checking.

Eight new Android/JVM tests use the built fork's real AudioSink API with a recording
Proxy delegate (not AudioTrack). They require Gradle execution on the user's setup.
A strict XML guard requires these eight tests plus all 70 named pure scenarios;
missing/skipped/failed/duplicate reports fail. XML validation is not device proof.

No full fork build, Android APK, hardware decoder, AudioTrack, realtime allocation
or click-free transition is claimed locally. Existing 68 native CTest, 53 JNI,
44 lifecycle, 78 previous boundary JVM and 8 fork cases remain separate gates.

NEXT: playback-owner executor transaction taking these cue buffers, pre-cue prefix
handling, bounded native queues, shared output frame-clock and single gain owner,
plus drain/handback. No method in this package commits that transaction or makes
canExecute true. Do not enable live DSP merely because PAIR_HELD appears.
