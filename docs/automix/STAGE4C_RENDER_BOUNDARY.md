# Stage 4c-1 — Output-bound identity and passive PCM admission metadata

Date: 2026-09-24. Applies after accepted Stage 4b. This is the first integration
slice of Stage 4c, NOT a live transition executor or a completed single-player AutoMix.

## Delivered boundary

The existing service's one streaming ExoPlayer and its two internal audio renderers
are retained. A passive sink decorator sees metadata immediately before the real
AudioSink.handleBuffer call and accounts for only the bytes actually accepted by
that same delegate. It does not read or copy PCM, change buffer positions/limits,
change the return value, swallow delegate failures, register another Player or
listener, or own gain/rate/seek/queue operations. Existing audio processors stay in
exactly their existing order. A compiled schedule supplies source identities and
cue deadlines, not permission to execute it.

All plans, reports and reservations remain canExecute=false. A reservation is a
versioned metadata receipt only. It is NOT an execution lease or an atomic promise
about decoder readiness, downstream latency, AudioTrack state or future buffers.
The normal sink and the existing manual fade controller remain the only audio/gain
owners. This package does not implement rendering handoff or claim audible AutoMix.

## Verified source versions

GitHub's LMG-VK connector still resolves feat/ios26-automix to
`a90f451ef0e380717eb479a93f70e70d6a6bfe18`; accepted later artifact postimages are used
for exact app preconditions. No rollback to that old commit is required.

Two alternative media3-lmg source profiles were inspected (apply ONE, matching
the app's existing dependency base):

| Profile | Source | Codec renderer Git blob | Audio renderer Git blob |
|---|---|---|---|
| lmg30 | v1.5.1-lmg30 | 64887aecc34c3e95872244f497e5ffae7a8c3419 | 4397670365a03cea58f43f7f144e6fcbca14e53c |
| lmg31 | 877d68b9850f637b55b10b2a11ec5f3479273475, 1.11.0-lmg31 | cd731fd2d0f0b3c68ab82b8ce248fdd006cc7496 | ec4d209aaad5d19c556507e664cd248ad7bdd109 |

These are source identity checks, not proof that either complete fork was built
locally in this pass. The installer rejects other contents rather than guessing a
patch offset. No base-version migration, old release overwrite, or remote write is
performed. The fresh artifact uses a separate BASE-boundaryN coordinate.

## Why an output-queue hook is required

MediaCodecRenderer may replace its input stream while buffers from the prior
stream are still draining. A Player's current media item, the newest sink instance,
or a current input MediaPeriodId therefore cannot label those pending buffers.
Even the newer AudioSinkConfig's timeline/id must not be used as a substitute for
the identity of the concrete output queue entry being drained.

The small fork patch captures Timeline and MediaPeriodId in each OutputStreamInfo
at all three queue-entry construction paths. Getters expose that entry's opaque
identity token, captured timeline/id and a copy of its renderer offset. The original
queue timing fields, scheduling and ownership are unchanged. In lmg30, a reset
which discards pending stream transitions invalidates the old captured identity;
it does not relabel that entry as the newly read input. Missing identity is explicit.

The optional LmgPcmBoundaryListener is invoked immediately before the unchanged
AudioSink.handleBuffer. The callback has no PCM argument and no takeover result.
It carries decoded output format, mapping/tunnelling information, renderer position
and output identity. An ordinary diagnostic exception disables observation rather
than substituting an audio result. No AudioSink interface method is made mandatory.

## App binding and acceptance accounting

One RenderBoundaryController belongs to the existing ExoPlayer. Sink registration
order does not assign outgoing/incoming roles. A role requires the full source key:
window UID, mediaId, URI, custom cache key and optional recording revision. These
values are redacted in diagnostics. Actual period UID and window sequence number
are retained in output identity, but a matched key is not a complete playback lease.
Repeat-one ambiguity and multiple renderer matches fail closed.

The initial passive domain is unclipped, finite, non-ad, nonspatial-style PCM from
a single period in the window, with zero period offset and zero encoder trim,
mono/stereo, 16-bit or float32. Channel remapping, encoded/offload/tunnelling,
non-unity playback parameters and silence skipping make the metadata gate unusable;
the original sink's playback behaviour is still forwarded unchanged. Other sources
continue through the original sink, not through an invented replacement decoder.

The ledger distinguishes attempted bytes from accepted bytes using the delegate's
actual ByteBuffer position delta. A partial retry must retain the same buffer,
PTS, limit and expected next position. Only remaining-length frame alignment is
required; a valid nonzero absolute buffer position is not rejected unnecessarily.
An inconsistent full/partial result, timestamp discontinuity or fractional accepted
frame invalidates the diagnostic gate, not the normal buffer consumption result.

PTS/renderer offset/period offset arithmetic uses checked integer operations.
Acceptance endpoints carry a conservative one-microsecond timestamp uncertainty.
Cue conversion for this diagnostic gate floors seconds to microseconds. These are
LMG admission policies, NOT DSP quantization or recovered Apple rounding rules.
Physical accepted-end watermarks survive a new observation epoch for the same
output token. A callback finishing after invalidation still accounts for physical
consumption but cannot overwrite the new epoch's published state.

Both renderer position past cue and already-forwarded PCM beyond cue reject a plan.
The second condition matters because current playback position can lag data already
sent to an AudioSink. This reports that a future takeover is too late; it does not
recall already queued data or stop normal playback. No PCM ownership is acquired.

## Publication and lifecycle

ObservationPipeline invokes the new publication callback synchronously on its
existing owner thread BEFORE publishing its StateFlow value. Any non-compiled,
missing, rejected, closed, wrong-generation or wrong-revision schedule revokes the
passive plan. The endpoint mailbox uses epoch identity/CAS so an old callback cannot
publish into a replacement epoch. Consumption/version changes invalidate receipts.

Flush, stream replacement, pause, route changes, unsupported playback settings,
wrong-thread access and delegate failure revoke the current metadata gate. Terminal
faults are sticky for the current epoch. Closing is permanent. There is no extra
player listener or background loop. The playback-thread ledger is not an audio
callback implementation and no zero-allocation or realtime performance claim is made.

AudioService only exposes autoMixRenderBoundaryState, owns the passive controller,
passes it to the existing factory/observer and closes it before shutdown. No new
Player is created. The existing manual crossfade remains authoritative.

## Packaging and stale-fork protection

The app adds a strict optional ExoPlayer artifact override, leaving all other Media3
coordinates on the existing base. Local publication verifies a newly built AAR and
copies it to a NEW local Maven version, preserving the base POM dependencies. It
never overwrites the base release or silently crosses between 1.5.1 and 1.11.

The bytecode verifier inspects compiled classes via javap. It checks captured output
identity fields/constructors/getters and the actual notification-before-handleBuffer
call site, not merely presence of an interface name. Its success is source/bytecode
wiring verification, not a decoder drain run or device-audio validation.

CI needs an explicitly published same-base AAR URL, SHA-256 and new version. Until
those are configured it fails clearly instead of silently testing the stale release.
Native CMake, JNI exports, offline testbench files and user-supplied Stage4b numerical
fixes are not modified or pinned by this package.

## Actual local verification / required server gates

Locally executed against real pure-Kotlin production code and real coroutines:
62 buffer/controller/forwarding scenarios, 8 synchronous-publication scenarios,
and the prior 20+16+8 lifecycle scenarios. Six isolated semantic mutations fail
assertions. No Android MediaCodec/AudioTrack or Media3 runtime was executed locally.

The package tool tests use synthetic javap/report fixtures and synthetic Git source
carriers. They prove parser/install rejection behaviour, not whole-fork compilation,
actual AAR contents or complete real AudioService patch application. Full source
hashes and exact bounded replacements are checked when the installer reads the
user's real checkout. The full current source files were not locally available.

Server acceptance must build the chosen actual fork and its 8 new constructor
integration tests, verify the produced AAR, then compile the app and run 78 new JVM
tests (62 core,8 publication,8 sink-delegation API tests). Keep the prior 68 CTest,
53 real-JNI and 44 lifecycle gates; no extra native/JNI gate is claimed. The 8 sink
API tests are not device tests. Hardware playback regression remains a separate gate.

## Remaining Stage4c work

An actual prepared executor must own bounded buffers before the cue, share a valid
output frame clock, coordinate both renderer streams atomically, avoid duplicate
manual and native transition gain, and preserve master volume/audio focus. It needs
safe cancellation/late-plan recovery, decoder preroll/trim/EOS/route handling and
state handoff after the transition. This package deliberately does not implement
those steps or promote BOTH_STREAMS_BOUND into execution permission.
