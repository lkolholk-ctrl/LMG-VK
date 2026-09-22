# Planner loudness map and trailing ratio

`planner_loudness.h/.cpp` implements the numeric portion of the source loudness
map path. It does not decode MediaAPI JSON or guess measurement units.

## Map construction

`272223b6c` first checks audio-analysis/loudness-curve availability and nonempty
sample values. Getter `2743dd190` returns optional sampling frequency and
`2743dd1a0` returns the values. The new API accepts these already decoded values.
If frequency is present but <=0, the source returns no map: it does **not** try
song duration in this case. If frequency is absent, helper `272226584` receives
sample count and song duration; count must be >=1 and duration >0. It computes
count/duration and snaps to exactly 2.0 only if the nearest-integer-away result
is 2.0 (the positive interval [1.5,2.5)). Otherwise it retains the unrounded
ratio. This is a source-specific exception, not generic rate rounding.

`272223e74..ef8` takes reciprocal frequency once, then writes every original
sample unchanged alongside `time = reciprocalFrequency * double(index)`. Index
starts at zero. It is not repeated addition, duration/(count-1), interpolation,
or center-of-bin placement. Empty input returns no map on the source's initial
check. The wrapper represents missing source analysis with `std::nullopt`.

## Window mean

`272216414` selects entries with `start <= time <= end`, retaining array order.
Both endpoints are included. The samples need not be sorted for this function;
the implementation does not sort or short-circuit based on timestamps.
`272215af4` -> `272215bbc` extracts values, sums left-to-right from +0, and
returns sum/count. No sample means no value. There is no time weighting,
window integration, interpolation, or neighboring-sample extension.

## Trailing incoming ratio

`272235840` first requires a present vocal map and a present loudness map. The
vocal map is not inspected further in this routine; an empty but present map
satisfies the presence guard. Given region [start,end], calculate its mean L.
If absent or L>=0 return no value. The second window is
`[end, (end-start)+end]`, preserving that arithmetic order. Its mean T must also
exist and be negative. Return L/T with no clamp. A pivot sample at exactly end
contributes to **both** means; a zero-duration region with a negative pivot
sample gives ratio 1.

No inferred physical dB units, amplitudes, extra vocal-strength gate, or limits
on a positive ratio are introduced. Host validation rejects nonfinite inputs,
reversed windows, and arithmetic overflow; these are API domain protections,
not claims of equivalent source error behavior.

## Verification and scope

`tools/planner_loudness_reference.py` executes original instructions for:

* 50 timestamp maps, including supplied frequency and inferred-rate boundaries;
* 510 inclusive selections followed by ordered means, including exact endpoints,
  empty windows, unordered inputs, and cancellation-sensitive sums.

All timestamps, copied values, and means compare bit-for-bit under ASan/UBSan.
Only Swift array allocation/refcounts and PAC are substituted. The source's
actual map arithmetic, frequency inference, selection, and mean run unchanged.
The original full Swift analysis wrapper and full ratio caller are not executed
by this fixture; their guards/ratio are covered by focused C++ boundary tests
and saved raw assembly (`evidence/planner/272223b6c.asm`, `272235840.asm`).

Compile `src/planner_loudness.cpp` with `-ffp-contract=off`. Standalone test is
`tests/planner_loudness_test.cpp`, using `LMG_AUTOMIX_FIXTURES`. App/JNI/playback
and the existing crossfade are untouched. This provides inputs for planner
scoring; complete region selection and the other analysis maps remain separate.
