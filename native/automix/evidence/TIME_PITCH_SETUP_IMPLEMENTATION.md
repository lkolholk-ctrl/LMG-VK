# NewTimePitch backend and initialization

Scope: recovered backend selection and initial DSP geometry/window/latency. This
is **not yet a streaming pitch-preserving PCM processor**. No application,
Media3 fork, crossfade, or CMake changes are included here.

## Backend selection is now traced to a concrete constructor

All addresses refer to iOS 23A341.

1. AudioToolbox `MEMixerChannel::FindProcessor`, `0x1b8f676a0`, selects channel
   slot `+0x568` for processor ID `tmpt`. Its description constant at
   `0x1b916bf28` is `{ 'aufc', 'iptm' }`, followed by manufacturer `appl`.
2. `MEMixerChannel::EnableProcessor`, `0x1b8f678fc`, dispatches algorithm at
   channel `+0x598`. At `0x1b8f67abc..67ac8`, `spec` selects
   `0x1b8f67ca8`, which replaces the component subtype with `nutp`.
   The component type remains **aufc**, not aufx.
3. EmbeddedSystemAUs registration `0x234f10f14`, at
   `0x234f1179c..117cc`, loads descriptor constant `0x234ffdb10`:
   `{ 'aufc', 'nutp', 'appl', 0, 0 }`, and registers factory `0x234f3ebd0`.
   The different registration for `{ 'aufx', 'nutp' }` at `0x234f11498`
   has a different factory and must not be substituted.
4. Actual aufc factory `0x234f3ebd0` installs constructor `0x234f3ec74`.
   Exported factory `0x234f9e984` installs the same constructor; the matching
   symbol name is NewTimePitchFactory. Constructor vtable is `0x284ad80f8`.
5. Vtable `+0x38` Initialize is `0x234f9e544`, `+0xd0` Render is
   `0x234f9d9cc`, `+0x158` latency getter is `0x234f9d518`.
   Initialize creates the DSP state (0xa90 bytes) using `0x234f43c7c`.

This closes the backend choice after AudioToolbox receives `spec`. The earlier
research supplies Music's Spectral preference. The complete AVFoundation/Fig
transport from its string preference into this channel field has not been
re-executed here.

## Implemented arithmetic

`time_pitch_setup.h/.cpp` provides preparation primitives to be reused by the
actual spectral processor:

- Initialize `0x234f9e5e0..0x234f9e644`: FFT sizes 512, 1024, 2048, 4096,
  8192, with strict sample-rate boundaries 8192, 16384, 32768, 65536 Hz.
  Integer AU quality below 33 halves the size. The AU constructor sets quality
  128; this is not inferred from a generic phase-vocoder implementation.
- DSP constructor `0x234f43d48..0x234f43e2c`: FFT geometry, reciprocal FFT
  size, rings sized to the power-of-two ceiling of `2*N + maximumFrames` and
  `N + maximumFrames`, respectively.
- DSP constructor `0x234f43fa4..0x234f44130`: analysis window. The angular
  constant is **6.283185482025146484375**, double representation of float
  2*pi, read at `0x234ffcdc0` (bits `0x401921fb60000000`). For each index,
  multiply index by constant/N, call double cosine, evaluate
  `fma(-0.5, cosine, 0.5)`, then narrow to float. Using a conventional full
  precision pi constant is observably different.
- Latency getter `0x234f9d54c..0x234f9d56c`: exactly
  `((1 / double(rate)) + 1) * double(N/2) / sampleRate`. It returns zero when
  DSP state byte `+0xa81` has bit zero set. The constructor derives this flag
  from offline component mode or a nonempty external scheduling state; API
  callers must pass the recovered condition, not assume all playback is in
  this mode.

The host supports the already-used 8–192 kHz and 1–16384 frame domain;
validation of these limits is host policy, not claimed as Apple's AU limits.
The helper accepts the already-clamped finite float playback-rate domain
1/32–32. AU parameter update `0x234f9d584` narrows/clamps into this domain.
Geometry and the window are prepared outside the callback. No runtime audio
allocation or substitute DSP was added.

## Validation

`tools/time_pitch_setup_reference.py` executes the original instructions in
Unicorn, loading only the selected image's TEXT range. Fixtures cover:

- 375 geometry cases: rate boundaries, quality boundary 32/33, five buffer sizes;
- all 16,128 float window coefficients for six FFT sizes;
- 540 latency cases, including nonexact decimal float rates.

C++ matches all fixtures bit-for-bit. Only imported cosine is supplied by host
libm; the ARM window arithmetic and rounding are executed unchanged. This
therefore establishes instruction/arithmetic fidelity conditional on the host
cosine, not bit equality with Apple's libm for every input.

Standalone `time_pitch_setup_test.cpp` passed GCC ASan/UBSan, warnings-as-errors,
`-ffp-contract=off`. Add the source/test to CMake when integrating this portion.
No Android build or emulator was launched.

## Next concrete DSP work

The PCM path is now locatable. AU Render `0x234f9d9cc` calls DSP processing at
`0x234f45ce4`, with time mapping lookup `0x234f45984` and further spectral
processing/state updates. Full FFT calls, spectral coherence, transient
preservation, overlap-add, ring transport and event ingestion still require
recovery and implementation. The setup module must not be exposed as a
finished time-stretch processor.
