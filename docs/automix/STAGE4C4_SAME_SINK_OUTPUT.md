# Stage 4c-4 — Existing-sink output port and separated gain

This is the output-write/gain slice, **not a complete playback-owner executor**.
There is no production `CuePlaybackLease`, renderer/period-clock override, DSP pump,
automatic live activation, or drain/handback implementation. `SameSinkPairOutput`
intentionally does not implement the lease; a constant-true adapter is forbidden.
No Player, AudioTrack, sink, renderer or audio device is constructed by these classes.
The existing delegate may still perform its own ordinary lazy initialization.

## Why the fork changes

The accepted `v1.5.1-lmg30` PlayerAudioFadeControl sends `fade * playerVolume` using
Renderer.MSG_SET_VOLUME; restoreFullGain sends playerVolume. Once multiplied, the
sink cannot reliably recover the factors (especially at zero). Ignoring all volume
messages would discard user volume and audio-focus ducking.

The patch changes just those two fade dispatch sites to a fork-private typed message,
LmgTransitionGainSink.MESSAGE_TYPE (0x4c4d4701). The renderer dispatches to the optional
sink extension, or applies the exact float product for an ordinary sink. Normal Player
volume messages use a separate typed path and still set the original direct value.
Finite [0,1] components are validated; -0.0 is preserved. This is not an upstream
message assignment and not a reconstruction of an Apple property.

Both original manual fade calculations and Media3 period scheduling remain unchanged.
The new dispatch alone neither authorizes a transition nor reserves MediaClock. Keep
all accepted boundary1 output-identity changes; publish a NEW same-base boundary2 (or
other unused suffix) AAR. Do not overwrite boundary1 and do not mix 1.11/1.5 libraries.

## Output resource and ownership phases

`Media3BoundaryAudioSink` constructs an object that delegates to the **same AudioSink
instance it already wrapped**. `RenderBoundaryEndpoint` stores a marker-typed reference
so the old standalone ledger does not gain an Android dependency.

`SameSinkPairOutput.reserve` requires the real controller receipt, exact epoch and
endpoint versions, two held codec buffers, matching output formats and two distinct
registered ports. Outgoing is the writer; incoming is a suppressed companion and must
have no already-pending legacy sink data. Roles derive from source identity, not
factory order. The output base timestamp is the exact outgoing held-buffer PTS,
queried from the cue gate, NOT an arbitrary timestamp supplied by a test/UI.

Reservation quarantines legacy writes but changes no device volume. Activation is a
separate method: a non-unity legacy fade cannot be removed while old PCM is pending.
Unity-gain queued legacy audio can remain on the same delegate without a gain jump.
An unknown clock/device readiness condition is not replaced with a positive answer.

The high-level pair writer additionally requires an original **already committed**
CueOwnerTicket; a staged copy or foreign receipt is insufficient. Its clock lease is
still the caller's separate obligation. Tests provide a clearly labeled test clock
lease, not a physical output grant. There is no production call site that arms this
path automatically or exposes it as an AudioService/UI toggle.

Before source claim and before any output attempt, rollback can restore the last
legacy gain value and release the output reservation. After source claim, even with
zero output bytes, high-level rollback is prohibited. After any sink write attempt
(including zero-byte acceptance) the low-level port also requires a true reset.
Changing generation, source, format or route does not authorize replay.

## Byte-level output contract

The output packet is already encoded in the existing sink's PCM format. No resampling,
channel conversion, normalizer, limiter or output-gain multiplication is performed here.
In particular, ingress reads float32: callers MUST NOT pass those bytes unconverted to
a PCM16 sink. The direct native-ingress-to-output tests are float32 tests. PCM16 byte
transport is tested separately, not represented as a DSP float-to-PCM16 conversion.

New output packets are direct, frame-aligned, bounded to 1 MiB and contiguous in a
single output-frame sequence. Packet ID, first frame, buffer object, PTS, limit and
expected position are retained until the same delegate returns completion. An empty
retry after `false` is allowed; a different object is not. PTS is the exact base plus
floor(absoluteOutputFrame * 1e6 / sampleRate), never rounded incrementally per packet.

Acceptance is ONLY the actual buffer.position delta. Partial non-frame byte acceptance
is recorded; the completed-frame counter advances only when a full frame is accounted.
A sink exception retains partial side effects and is rethrown as the original object.
Cancellation inside a write records any bytes accepted, then fails; they are not replayed.
A malformed sink result or backward clock sample requires coordinated reset.

`sinkPositionUs` is the existing AudioSink's reported media time, **not** an ExoPlayer
renderer/period clock, an output-frame cursor, or a claim of audible samples. There is
no wall-clock fallback. The existing wrapper getCurrentPositionUs is not remapped.

## Gain and invalidation

Idle/reserved split messages reproduce fade*master. Ordinary master messages preserve
the original direct setter semantics (equivalent last legacy fade factor 1). After
activation, split messages retain master/ducking and ignore only transition factor,
because a future DSP mixer must apply out_gain exactly once to its output samples.
No output-gain processing is installed by this package itself.

After revocation of an active writer, that separation stays in force until a successful
actual flush/reset/release; already queued owned PCM must not suddenly receive a second
fade. Pause/configure/offset/route/etc invalidate but do not count as a successful flush.
An exception from a real flush does not free the reservation. No automatic reset occurs.

All operations except ticket.revoke are on the established playback owner. Creation
and initial volume calls do not accidentally bind ownership to the factory/Main thread;
actual output context binds it. The implementation is not claimed lock-free or hard-RT.
The pair and snapshot APIs allocate control objects; profile them before live deployment.

## Local versus server evidence

Host tests execute the real new output Kotlin and the actual existing C++ ingress JNI.
The outputs record calls/bytes instead of playing them; the clock lease is a test double.
60 pure Kotlin cases, 18 ingress-JNI pair cases and 12 Android-API decorator cases are
separate suites. The latter require the rebuilt real Media3 AAR on the server. The
10 fork gain tests exercise the actual small protocol/dispatch helper, not a whole
MediaCodecAudioRenderer. The AAR guard separately inspects both real gain call sites,
message dispatch and all accepted boundary1 hooks in the built artifact.

No synthetic AAR, class, .so, PCM fixture or Apple binary is shipped as a runtime artifact.
Existing DSP, native CMake/JNI exports, offline fixes and Gradle base dependencies are
unchanged. New Java protocol requires a new ExoPlayer AAR; CI's existing URL/hash/version
variables must reference that new artifact. Local publication verifies its gain bytecode
before creating an unused Maven version. A mismatch fails rather than falling back.

Server gates: old 70 CTest, 93 named real-JNI, 44 observation lifecycle, 78 boundary and
78 cue JVM, 8 old fork tests; plus 90 new app JVM (including 18 additional actual-ingress
JNI cases) and 10 new fork protocol tests. These counts do not assert device playback.

## Remaining integration

ExoPlayerImplInternal's transition/period ownership, DefaultMediaClock arbitration,
preparing/pumping the accepted DSP, per-track processors, format conversion, incoming
read-ahead/start position, prefixes, starvation/EOF and handback are not implemented
by this patch. Do NOT turn canExecute/liveDspInstalled on or substitute a fake clock
lease merely because this write/gain layer has passed tests. One ExoPlayer is preserved;
it does not by itself prove there is exactly one physical AudioTrack in the old fork.
