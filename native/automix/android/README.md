# Track-effects JNI bridge

`NativeTrackEffects` owns one `ScheduledTrackGraph`. It implements the recovered
per-track DSP effect graph, using explicit frame events prepared off the audio
thread. It does not implement a complete AutoMix player, time stretching,
`out_gain`, media-time/frame conversion, decoding or track selection.

The app build includes this bridge, but the Media3 playback path does not yet
invoke it. Final APK packaging remains unverified. It is independent of the old
`AutoMixNativeEngine` JUCE/Oboe singleton and does not load that engine.

## Ownership and PCM contract

Construct off the audio thread with sample rate, mono/stereo channel count,
maximum block size, first frame and ordered parameter writes. Core validation
rejects unsupported parameters, duplicate same-frame parameters, incompatible
settings and out-of-order events. A write at the first frame is allowed. No
implicit conversion of song time, nanoseconds or schedule policy is performed.

Use one render-thread owner. Kotlin synchronizes processing, position reads and
close on the same instance so a native pointer cannot be freed during a call.
Close is idempotent. There is no finalizer; the playback owner must close instances
on replacement, release and failed preparation. A seek requires a newly prepared
instance. Creating or closing on a competing thread can cause monitor contention;
this API does not promise a lock-free handoff.

PCM is direct, native-order, interleaved IEEE float32. Positions identify input
and output offsets; neither position nor limit is advanced. Output must be
writable. Exact in-place processing is allowed; partial aliasing is rejected.
Null input drains tails. Nonfinite input is rejected before graph state changes.
Successful processing allocates no native buffers; all graph scratch storage is
prepared during construction. JNI maps allocation failures to OutOfMemoryError
and validation failures to IllegalArgumentException; C++ exceptions do not escape
the boundary. `silenceFlags` passes explicit input/send/dry/wet Gain-box flags.

## Concrete app insertion point

`PlayerAudioChain.renderersFactory().buildAudioSink()` creates separate
`DefaultAudioSink` instances and processor chains for both Media3 audio renderers.
The fork's `DefaultRenderersFactory` calls this override at lines 461 and 698,
including its secondary renderer. A per-sink effects processor can therefore be
inserted through the app factory without editing the existing crossfade classes.

However, each instance needs the actual track identity and its resolved timeline.
Sink creation order does not identify the outgoing/incoming track: renderer slots
change roles. `AudioProcessor.queueInput()` itself does not carry timestamps or
media-item identity. An optional sink wrapper or renderer event binding must pass
stream identity, discontinuities and frame origin to a prepared schedule, with
flush/seek lifecycle handling. The existing sink uses integer PCM processors, so
integration also needs explicit PCM16/float conversion or a consistently float
processing chain; simply appending this JNI bridge is insufficient.

The graph remains per-track. AVFCore's concrete DSPGraph placement flag 1
establishes that it precedes TimePitch; see
[the recovered binding](../evidence/TIME_PITCH_CHANNEL_ORDER.md).
The precise application of track output volume is still being traced.
Existing lmg31 fade volumes are driven through Renderer.MSG_SET_VOLUME; enabling
a separate AutoMix volume envelope simultaneously would multiply envelopes.
Mode selection must preserve the existing ordinary-crossfade path and give the
complete AutoMix path sole ownership of its transition envelope.

## Build wiring

The app's `src/main/cpp/CMakeLists.txt` now adds this core with tests and
sanitizers off and JNI on. Its Gradle source set includes
`native/automix/android/src/main/kotlin` and its default ProGuard configuration
includes `consumer-rules.pro`. Host test Kotlin remains outside production.
The Android NDK 27 debug arm64-v8a target `lmg_automix_jni` builds successfully;
readelf verifies an AArch64 shared library. Other ABIs, final APK packaging and
on-device playback are not yet verified. Building the library does not enable
AutoMix in PlayerAudioChain.

Host verification on 2026-09-16: GCC 14.2 built the real core and JNI shared
library, Kotlin compiled the actual bridge, and OpenJDK 21 ran
`NativeTrackEffectsTest.kt` against that shared library. Covered stereo identity,
direct-buffer offsets, in-place processing, zero-input drain, zero-length calls,
scheduled gain, unknown/duplicate writes, invalid rate/timeline, capacity bounds,
heap/read-only buffers, partial overlap, NaN rejection, preserved timeline on
failure and repeated close. No Android build or emulator was used. ABI packaging
and Android device playback remain unverified.

## Streaming TimePitch bridge

`NativeTimePitch` now exposes the recovered mono/stereo streaming processor in
this same independent JNI library. Input/output are native-order interleaved
float direct buffers; native scratch converts to the core's planar API without
allocating during enqueue/dequeue. Calls return actual accepted/delivered frame
counts and preserve ByteBuffer positions/limits. Timestamps and time-map segment
coordinates are PCM frames, explicitly supplied by the host.

Controls, source-map segments, reset and destruction serialize per Kotlin
instance. Map replacement prepares and validates a new immutable native map
before changing the live view; null detaches it and an empty list attaches the
source identity mapping. Configuration/map preparation occur off rendering.
EOF and trimming are deliberately not synthesized by the bridge: the actual
source tail policy and per-track lifecycle still must be bound in the host.

Host OpenJDK 21 verification now also covers the real NativeTimePitch Kotlin
class against liblmg_automix_jni: interleaved stereo levels, nonzero buffer byte
offsets, unchanged positions, variable-rate map processing, reset, invalid
controls/PCM/maps/buffers, zero frames and repeated close/use-after-close. The
existing NativeTrackEffects JNI test also passes with the expanded library.
This does not prove Android ABI packaging, Media3 attachment or device playback;
these production integration steps remain open.

## Combined graph and TimePitch bridge

`NativeProcessedTrack` owns the C++ `ProcessedTrackStream` and its immutable
time map. It exposes the verified DSPGraph -> TimePitch order through one JNI
context. Initial graph parameters override the recovered 27 time-zero defaults;
this includes reverb geometry prepared before rendering. Ordered frame writes
use the same initial settings when preparing later snapshots.

Input can be accepted into a bounded intermediate buffer before TimePitch has
consumed it. The caller advances only by the returned accepted frame count;
the graph is never reapplied when downstream processing stalls. `pendingFrames`
reports staged frames, not TimePitch's entire retained history, and is **not**
an EOS indicator. Null input supplies explicit zero PCM, not implicit EOS.
Source and output origins, event frames and map coordinates are explicit host
inputs. A seek requires replacement with a freshly prepared instance.

`NativeProcessedTrackTest.kt` passes on OpenJDK 21 against the actual JNI/core:
stereo scheduled gain, bounded-buffer backpressure, input frame accounting,
nonzero buffer offsets, unchanged positions, native-owned tempo mapping,
invalid PCM/parameters/buffers and closed-instance rejection. Android NDK 27
also compiled and linked the combined bridge for the app's debug arm64-v8a
target. No emulator or full APK build was run for this change.
This bridge still requires the Media3 adapter, out_gain, resolved event clocks
and source-derived EOF behavior before it can provide complete AutoMix playback.


Media3 1.11 stream identity update: `AudioSink.AudioSinkConfig` includes both
`timeline` and `mediaPeriodId`; MediaCodecAudioRenderer supplies them at
configuration. Its output-stream change marks a pending format/configuration
notification, so an optional ForwardingAudioSink can resolve the real period
and MediaItem rather than relying on sink creation order. `setOutputStreamOffsetUs`
provides the offset added to media timestamps before `handleBuffer`; subtract
it to recover media time. The wrapper still needs flush/seek lifecycle and
correct processing-time versus presentation-time handling before activation.

The app now has `AutoMixStreamIdentity.from(AudioSinkConfig)`, compiled against
its actual local lmg31 AARs. It resolves MediaItem.mediaId, period UID, window
sequence number and period position from that sink's own configuration. Ads,
missing metadata and unresolved period UIDs remain unavailable instead of using
the player's globally active item. Timestamp conversion subtracts the renderer
stream offset and adds period position, retaining integer microseconds without
choosing an unverified PCM rounding policy. This resolver is not yet attached
to a processing sink; pending configuration, partial writes and EOS lifecycle
still need the final adapter.

### Decoded PCM transport

`NativeTrackPcmQueue` owns a prepared `NativeProcessedTrack` and preallocates its
input conversion and output buffers. It accepts native-order PCM16 or float PCM
from decoder buffers (including heap/read-only buffers with arbitrary byte
offsets). Only accepted input bytes advance. Its float output buffer remains
unchanged until the downstream consumer has consumed it completely. This layer
is transport code, not an additional DSP algorithm. Source/output frame origins
and native graph configuration must already be resolved by the caller.

`NativeTrackPcmQueueTest` runs against the actual host JNI library and compares
PCM16 and float input paths bit-for-bit with direct native processing across
varying input block lengths. It also checks partial downstream consumption,
unchanged input limits/byte-order metadata, rejection without consuming invalid
input, and closed lifecycle. This transport is not yet installed in
`PlayerAudioChain`: EOF/tail handling, output gain and the transition coordinator
remain required before activation. A native input stall is not EOF.
