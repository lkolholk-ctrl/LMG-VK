# Player DSP integration

The unchanged `vendor/lmg_dsp` tree comes from the user-supplied
`LMG_DSP_Cpp17_v1.0.0.zip` (SHA-256
`44b89c06bb78e0ea8cdca56a509814bcb6b7522e5db84dcc88771ebc4f64e7cd`).
The adapter and JNI target are separate from the existing AutoMix native library.
This DSP target has no AudioFX, JUCE, Oboe or Android audio device dependency.
Android DSP targets use `-O2` even in debug APKs, retaining strict floating-point
flags. Client APKs are distributed for arm64-v8a only, as requested by the user.

## Audio contract

`PlayerAudioChain` installs one `DspAudioProcessor` per Media3 sink after
metering, per-source DJ effects and normalization. Native AutoMix output also
enters that sink's PCM chain. Instances share only parameter snapshots, never
filter histories. Encoded passthrough and offload are disabled for this chain.
Media3's float-output shortcut skips custom processors, so it is disabled;
processing internally uses float32, with the current sink output using PCM16.
This is not a claim of end-to-end float/high-resolution output.

The core supports mono/stereo at 44.1–192 kHz. Other formats bypass this effect.
The adapter processes at most 4096 frames per call, consumes only whole frames,
and preserves the frame count and presentation timestamps. Scratch memory and
output capacity are prepared before processing. Missing JNI falls back to dry
audio; the settings screen reports a missing library.

Lookahead and limiter attack are deliberately zero. This avoids priming silence,
buffered samples or added scheduling latency in the existing two-sink pipeline.
The limiter has release smoothing and a sample ceiling; it is not a true-peak
limiter. A future lookahead mode must implement latency accounting and EOS drain.
Normal drained boundaries with unchanged format preserve filter state; actual
seek/flush/discontinuity clears it. This does not add a new gapless scheduler:
track handoff remains owned by the existing player/AutoMix implementation.

## Thread ownership

The playback thread owns each native handle and serializes processing, reset,
configuration and destruction. Native processing/reset never take the registry
mutex. `DspController` is the sole control producer. It compiles and submits
snapshots on a worker coroutine, persists sanitized settings, and retries the
latest update if a paused sink's bounded queue is full. Creation, submission and
destruction share the registry mutex so the producer cannot access freed memory.
JNI handles must not be used by arbitrary callers after destruction.

`onConfigure` only records pending format: Media3 can still drain the old format
before `onFlush`. Native state is replaced only when that pending format becomes
active. Runtime master bypass preserves PCM16 sample values exactly.

## Validation

Run `bash scripts/dsp_host_checks.sh /absolute/build/directory` for the original
core/C API tests, PCM conversion/filter/limiter/lifecycle tests, and a JVM test of
the actual JNI bridge (direct-buffer bounds, queue retry and concurrent control
updates during instance recreation). `DspSettingsTest` covers persistence,
normalization and the Kotlin/native parameter layout.

`DspAudioProcessorTest` covers empty EOS, bounded partial input with offsets,
deferred format activation and unsupported formats. The host script can run it
against the real Media3 processor and JNI library when
`LMG_DSP_MEDIA3_TEST_CLASSPATH` contains the dependencies listed in that script.
Ordinary Gradle unit tests without a host JNI library exercise its dry fallback.

Android compilation and APK inspection complement these checks. Actual output,
route changes, HLS seeks and AutoMix handoffs still require a physical-device
listening test. No device test is implied by passing host tests.
