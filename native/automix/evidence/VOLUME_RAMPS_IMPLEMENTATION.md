# Stepped ramps and output-volume transport

The C++ stepped compiler now produces both point events and ramp intervals
from the same raw samples. `steppedRamps` must not receive compacted points:
that would remove the end of a plateau before a slope. Existing effect-point
compaction remains unchanged. `compileTimedVolumeRamps` prepares the separate
output-volume schedule, not GainBox parameters or PCM samples.

## Source and selection

iOS 23A341 `_SonicKit_MusicKit_Packages`:

* `0x27227a020`: Automation.ramps getter, invokes `0x27227a05c`.
* `0x27227a05c`: calls raw sampler `0x27227b000`, selects points, then zips
  adjacent selected points through `0x27227b3c8` / `0x27227b34c`.
* `0x27227e888`: forms song, stretched-song and transition ranges; traps if
  any selected pair reverses one of these clocks.

Selection starts with the first raw point. For each unequal adjacent pair,
append the previous point if its transition time is greater than the last
selected time, then append the current point. Equal-value pairs are skipped.
At the end, append the final raw point only when the first transition time
minus the last selected transition time equals zero and the first time is less
than the final raw time (`fcmp` / conditional `fccmp` at `0x27227a2c8`).
Thus a constant sequence spanning time becomes one constant ramp. A trailing
plateau after a changing sequence does not receive an extra final ramp.
Each adjacent selected pair carries two values and all six time endpoints.

## MusicKit consumer

`_SonicKit_MusicKit` function `0x2721b2a04` creates audio-mix input parameters.
Its automation search `0x2721b2ec4..2fa4` takes the **first** complete out_gain
descriptor match. The matched path exits to effects at `0x2721b3614`; it does
not return to the automation search. An empty first match therefore suppresses
later matches too. Full descriptor equality includes the style parameter ID.

Resolved branch islands in cache .67:

| Import | Target | Meaning |
|---|---|---|
| `2743da970` | `27227a020` | ramps |
| `2743da9d0` | `27227c004` | startValue |
| `2743da9e0` | `27227c00c` | endValue |
| `2743da9f0` | `27227c014` | timeRange |
| `2743daa50` | `27227e8e0` | stretchedSongTimeRange |
| `2743daba0` | `27226be94` | parameter equality |
| `2743dc2a0` | `22c4dd28c` | CMTimeRange constructor |

For a nonempty ramp array, if the first stretched-song start is nonzero, the
consumer first submits a constant ramp from time zero to that start. Both
values are the first ramp's startValue (`0x2721b3174`, `0x2721b317c`). It then
iterates all ramps, reading startValue and endValue separately at
`0x2721b3380` / `3388`. Values narrow to float before submission. Each time
endpoint is converted separately to CMTime with requested scale 1e9, using the
already verified conversion in MEDIA_TIME / EFFECT_EVENTS evidence. There is
no metadata clamp or GainBox smoothing here. ObjC submissions at `0x2721b328c`
and `0x2721b359c` use `setVolumeRampFromStartVolume:toEndVolume:timeRange:`.

`TimedVolumeRamp` retains the two converted endpoints; duration arithmetic is
not replaced with conversion of a double difference. It preserves source ramp
order, overlaps and intervals whose endpoints quantize to the same time.
Negative initial holds, nonfinite/float-overflow values, reversed intervals
and excessive preparation size are rejected as explicit LMG host guards.

## Verification and boundary

`tools/stepped_schedule_reference.py` executes original selection and pair
construction for 96 sequences. The sampler is replaced by supplied points for
these isolated cases, Swift allocation/slicing/refcounts are supplied, and the
generic zip is replaced by invoking the original pair closure separately.
Every emitted value and all six endpoints match C++ bit-for-bit. The existing
216 combined sampler fixtures still verify raw sampling through compaction;
they do not claim to execute the complete MusicKit/ObjC volume consumer.

Native tests additionally cover plateau/slope/hold behavior through continuous
automation, independent clocks, first-match/empty-match selection, float
narrowing, no metadata clamping, sub-nanosecond intervals and invalid input.
The catalog test invokes volume preparation for both sides of all 14 styles.

This closes volume schedule preparation only. AVFoundation's per-sample volume
renderer, the spectral time-stretcher, final PCM event delivery and Android/JNI
integration are not implemented by this module. The existing lmg31 playback
and crossfade code was not modified.
