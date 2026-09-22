# Timed DSP effect events

`compileTimedEffectEvents` ports the MusicKit effect-event producer, grouping
stepped writes by numeric media time and FourCC, then sorting times. It does not
yet choose Android PCM callback boundaries or implement out_gain/TimePitch.

## Recovered source chain

The producer starts from the default provider's dictionary, not an empty one.
`0x2721b5a04..18` loads `0x280c813d0`; the once path at `0x2721b5fb8`
calls `0x2721af158` / initializer `0x2721af178`. That initializer walks allCases,
skips output volume and IDs without FourCC, requests CMTime zero with scale 1e9
at `0x2721af3d4..3e0`, reads defaultValue at `0x2721af3f4`, narrows to Float
and inserts into the time-zero inner dictionary at `0x2721af4f4..508`.
The empty-automation path `0x2721b5f74..7c` returns that seeded dictionary.
Consequently even an empty schedule has one event with all 27 FourCC defaults,
including the three reverb geometry parameters and bypa=1. A source point at
numeric zero overrides its own address, retaining the other defaults and the
original exact zero key. Negative-time source events precede the default event.

This initialization was missing from the first C++ transport revision and is
now fixed, with empty/zero-collision/negative-time regression checks.
`initializeGraphSettings` binds the numeric-zero event before fresh graph
construction, including all three reverb geometry values. It rejects nonzero
times, unknown IDs, nonbinary bypass and nonintegral/out-of-range RVrr values.
Later live events still use the scalar binder; live geometry updates remain
unsupported and are rejected. Fresh initialization is not a seek operation.

`_SonicKit_MusicKit` producer `0x2721b5788` walks automations and their points in
array order. The original .67 import islands resolve as follows:

| Island | Target | Meaning |
| --- | --- | --- |
| `0x2743daa10` | `0x27227b744` | AutomationPoint.time |
| `0x2743da9b0` | `0x27227e0b4` | PlaybackTime.stretchedSongTime (+8) |
| `0x2743dacd0` | `0x27226a110` | StretchedSongTime.rawValue |
| `0x2743daa20` | `0x27227b73c` | AutomationPoint.value |
| `0x2743da990` | `0x272279c90` | Automation.parameter |
| `0x2743dab90` | `0x27226e4d4` | outputMixerVolume descriptor |
| `0x2743dabb0` | `0x27226be54` | parameter ID |
| `0x2743dbe40` | `0x2680a6b28` | SonicFoundation String.fourChars |
| `0x2743dc320` | `0x22c50a5a4` | Swift CMTime seconds constructor |

The event clock is therefore **stretched song time**, not song/transition time.
`out_gain` is skipped by full descriptor equality and uses a separate volume
path. An ID without a FourCC is skipped; `ts_rate` has no FourCC and is already
excluded by the stepped automation builder. SonicFoundation `0x2680a6b28`
requires four characters, traps on negative signed character bytes, and packs
the first four bytes big-endian at `0x2680a6c48..c64`. The port accepts ASCII
graph IDs and rejects non-ASCII input explicitly.

## Numeric media time

`0x2721b5ce0..cec` requests timescale 1,000,000,000. Swift constructor
`0x22c50a5a4` calls .47 island `0x2300aa860` -> CoreMedia `0x196bf782c`:

* Multiply seconds by scale, truncate absolute magnitude to int64, apply sign.
  This is **not** nearest-nanosecond rounding.
* Halve the scale until magnitude fits. Exactly double(2^63) maps to INT64_MAX
  before applying sign. Epoch is zero.
* Set hasBeenRounded if `double(value)/double(scale) - seconds != 0`.

The LMG API rejects nonfinite or unrepresentable numeric times rather than
forwarding CoreMedia's infinity/invalid-time results to DSP.

Dictionary equality at `0x27219736c` calls .67 island `0x2743dc300` ->
`0x22c50a558`; sorting at `0x2721b649c` calls `0x2743dc2f0` -> `0x22c50a6ec`.
They test CoreMedia comparison == 0 / < 0, respectively, through .47 island
`0x2300aa820` -> `0x196bf8574`. Numeric comparison orders epoch, then exact
rational value; rounded flags do not distinguish keys. Different scales use
integer product comparison at `0x196bf90dc`.

The C++ implementation uses exact quotient/remainder comparison to avoid
overflow and compiler-specific 128-bit types (the app also targets armeabi-v7a
and x86). Android builds for this change have not been run.

## Collisions and values

The producer stores `[CMTime: [UInt32: Float]]`. Repeated equal time/address
updates the existing Float slot (`0x2721b5e90` onward), so the last write in
source automation/point order wins, including nanosecond-quantization collisions.
The first key representation is retained. `0x2721b5284` sorts times for
`0x2721b5368`; inner parameter dictionary order has no recovered contract.

Values narrow once from double to float (`fcvt s0,d8`, `0x2721b5e90`). Unknown
FourCCs remain in the transport; DSP preparation validates supported IDs.
Finite Float checks and a one-million-write limit are LMG host guards. All
grouping/allocation occurs off callback.

## Verification and limits

`tools/media_time_reference.py` executes original CoreMedia instructions with
only PAC entry/return patches and **no math/allocator hooks**: 570 conversions
and 424 comparisons cover signs, sub-nanoseconds, neighboring doubles, ordinary
song times, int64 limits, scale reduction, equivalent fractions and epochs.
Native tests compare all value/scale/flag/epoch fields and comparison results.
They also verify merge order, time-base selection, Float narrowing, exclusions,
unknown IDs and continuous gate -> stepped points -> timed-effect integration.

This does not execute the entire Swift dictionary producer or Android playback.
Source inspection supports the producer rules. Digests are in
`tests/fixtures/media_time.json`; disassembly is in `scheduling/`.

Remaining: delivery to the correct PCM domain, output-volume/rate processors,
continuous-policy delivery and playback integration. No guessed frame rounding
or placement relative to TimePitch has been inserted.
