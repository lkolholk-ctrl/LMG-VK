# Planner vocal activity preparation and leading significance

Implemented `planner_vocals.h/.cpp`: source-tag map builder, closed-window
strength aggregation, leading-incoming significance, and cloud normalization.

## Proven enum mapping

The planner builder `27222343c` compares imported MusicKitInternal enum cases
and writes its own kind/strength tags into 24-byte interval records. Source GOT
slots in `.70.dylddata` (v5 target = low34 bits + 0x180000000), resolved against
MusicKitInternal exported case symbols:

| Planner tag | GOT slot | MusicKitInternal case symbol address | Case |
|---:|---|---|---|
| strength 0 | 27808af68 | 1d44c18b0 | veryLow |
| strength 1 | 27808af50 | 1d44c18b4 | low |
| strength 2 | 27808af60 | 1d44c18b8 | medium |
| strength 3 | 27808af58 | 1d44c18bc | high |
| strength 4 | 27808af70 | 1d44c18c0 | veryHigh |
| kind 0 | 27808af48 | 1d44c18d0 | singing |
| kind 1 | 27808af38 | 1d44c18d4 | speech |
| kind 2 | 27808af40 | 1d44c18d8 | rapping |

The builder drops end<start (`27222385c..87c`) before enum conversion, retains
zero-duration intervals, and preserves input order. Nonfinite host times and
invalid source tags are rejected. Optional-result strength tag 5 is never a
valid map-entry strength.

## Cloud normalization is not a guessed enum conversion

MusicKitInternal `1d41253ac` reads optional signed-Int start/end milliseconds.
Either absent => invalid entry. Both present => signed integer to double, then
/1000.0. It calls `1d4124d0c` and `1d4125094` for strength and kind.
**Missing OR unrecognized strength defaults to medium (2); missing OR
unrecognized kind defaults to singing (0).** This is established by instructions
`1d412560c..5618` and `1d4125658..5680`, not a fallback invented by the port.
The converted intervals then pass through the planner's reversed-range filter.

Cloud raw string spellings were independently resolved through `.26` branch
islands to `.40` MusicKit getter constants and executed with Unicorn:

| Import island | Getter | Exact raw string |
|---|---|---|
| 1d4a28780 | 21600f4cc | very-low |
| 1d4a28750 | 21600f4e4 | low |
| 1d4a28770 | 21600f4f4 | medium |
| 1d4a28760 | 21600f508 | high |
| 1d4a28790 | 21600f518 | very-high |
| 1d4a28a60 | 21600f2bc | singing |
| 1d4a28a40 | 21600f2d4 | speech |
| 1d4a28a50 | 21600f2e8 | rapping |

`planner_vocal_cloud_reference.py` saves raw getter instructions, string bytes,
and a code hash. CamelCase `veryHigh` is not a recognized cloud constant.

`normalizeCloudVocalActivities` keeps missing-array versus present-empty-array
semantics. It rejects fractional/nonfinite/out-of-signed64 millisecond doubles,
since the source expects Int and the raw decoder stores doubles. It does not
silently truncate. Negative times within this domain remain allowed. This port
cannot recover integer precision already lost by a double-valued JSON decoder.

## Predicates

`27221f218` filters intervals satisfying `entry.start <= window.end` AND
`entry.end >= window.start`, including endpoint-only contact. It extracts
strength tags and calls `27221f4e4`, which returns their maximum or sentinel 5
for an empty result. Duration and kind do not weight this aggregation.

`272235198` returns false for missing vocal map, missing leading window, or
missing aggregate strength; otherwise tags 3/4 are significant. The leading
window is an explicit optional input, ready for source region/beat selection.
It is never replaced by a fixed duration or the whole incoming transition.

Source window builder `272237058` asks a region witness for an index, subtracts
4, finds the corresponding beat, and emits [0, beat time]. The identity of that
region witness and complete beat hierarchy selection are outside this module.
The full pair relationship `272236188` additionally constructs/resamples beat
sequences; it is not substituted by the leading-vocal predicate. Its reducer
`27221f568` requires separate slice/alignment validation before porting.

## Verification

Original ARM fixtures cover **256 interval/strength cases** and **768 complete
leading predicate cases**, including absent maps/windows. They execute original
filter/max/predicate instructions. Hooks replace Swift arrays/refcounts, disable
logging, and supply the already-resolved optional leading window only.
All C++ results match under ASan/UBSan. Eight original MusicKit raw-string getters
also execute unchanged and decode to the constants above. Cloud normalization
has targeted all-case/default/range/unit tests; the full Swift cloud conversion
runtime has not been emulated.

CMake integration requires `src/planner_vocals.cpp`, a
`tests/planner_vocals_test.cpp` target, and `LMG_AUTOMIX_FIXTURES`. Header depends
on the existing raw `media_analysis.h` models. No app, JNI, fork, or crossfade
code changed.
