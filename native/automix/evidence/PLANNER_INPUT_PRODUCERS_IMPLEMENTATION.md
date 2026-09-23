# MusicKit input producers and resolved-scope native observation

Date: 2026-09-23. Base: `a90f451ef0e380717eb479a93f70e70d6a6bfe18`.
This patch adds source-derived seed production before the existing candidate selector.
It does **not** establish the default strategy profile, raw-catalog decoder mapping,
complete Criteria/provider context, live JNI selection, or permission to process PCM.

## Delivered boundary

`observeProducedMusicKitCandidates` takes two native CloudSongAnalysis objects,
resolved playback durations, requested style IDs, internal catalog records, resolved
discovery/placement bounds and explicit BeatMatched strategy eligibility. It prepares
the two inputs with the existing Stage 3a converters, produces ordered region seeds,
selects MusicKit main tonal/melodic components, and calls the unchanged prepared selector.
No manually selected seeds or manually calculated tonal compatibility are inputs.

All native structures belong to the two preparations for this call. The diagnostic
16-region preview is not used. Result event indices describe those deterministic
preparations; they are not native handles and must not be applied to another input or
playback generation. No pointer outlives its owning preparation. `canExecute` is false.

This is the production **native composition for resolved inputs**, not an automatic
AudioService pipeline integration. Kotlin/JNI, the existing preparation transport and
the live `selectionBlock` are unchanged. Generation-bound JNI publication is a later
binding obligation, not something these native test results prove.

## Recovered sequence

| Operation | Source anchors | Implemented rule |
|---|---|---|
| Discovery driver | `27222f54c`, `272230274`, `272230380`, `2722304d8`, `2722305c8` | Resolve maximum optional style bar count (absent ->4); form outgoing/incoming endpoint lists, suffixes, stable suffixes, outgoing loudness filter. Not all stable ranges or their diagnostic preview. |
| Outgoing endpoints | `27222fa50`, `27221c9b8`, `27221c864` | First adjacent section pair with downbeat delta divisible by4, otherwise first section; first downbeat matching its phase ordinal. Keep later downbeats on the four-bar phase with time >= resolved minimum. Source order retained. |
| Incoming endpoints | `27222fd74` | Section-boundary events at or before resolved maximum. If the first retained downbeat ordinal is below the maximum bar count, remove **one** item, not every short region. |
| Initial suffix | `27221c740` | First downbeat ordinal >= end-minus-count; keep original end. Absence and explicit zero are different. No nearest-time snapping. |
| Stable suffix | `27223218c`, `27221abb4`, `2722190fc` | First forward stable-map entry whose CLOSED ordinal interval contains the requested end. Clip start, preserve end; a zero-duration first match fails without trying a later match. |
| Beat stability | `2722324d0` | Compare successive beat durations against the requested window's mean beat duration; reject absolute deviations >0.031. This is not the stable-map bar-duration tolerance. |
| Outgoing nonsilence/fade predicate | `272232790`, `272216414`, `272215c88`, `27221582c`, `272215af4` | Closed-window samples, source-order linear regression, require abs(slope)<1 if slope exists, then mean>-30. An absent loudness map passes; a present empty map does not. No fallback to all ranges after filtering. |
| Initial pair order | `27222b14c`, `2722307ec` | Outgoing-major/incoming-minor Cartesian traversal of the filtered pools, then scale and truncate each pair. No sorting, deduplication or provisional prefix. |
| Scale discovery | `2722315f8`, `272232a90`, `272232c24` | Mean of individual fully contained bars' unquantized BPM values. Existing binary-scale search determines half/one/two; its incompatible tag is not an early eligibility gate. This differs from the selector's quantized stable-map tempo accessor. |
| Incoming scale | `272233100`, `2722335b4`, `272231b68` | Half: double incoming bar extent, cap at zero ordinal and recheck stability. Two: halve the beat extent using the existing algebra. Then call the existing inverse-scaled beat-budget truncation. |
| Internal style lookup | `27223e92c`, `27223a31c`, `272231e44`, `27221f524` | Preserve requested-ID order and duplicates; fail the entire lookup if a requested record is absent. Maximum count uses only resolved requested records, not unrelated catalog entries. This is NOT a JSON decoder. |
| Main tonality consumer | `272233fac`, `272223f90` | The MusicKit composite stores main in the middle packed component, which the consumer extracts. Reuse the existing cloud normalization and pass main; no directional ending/beginning substitution. This is scoped to the traced MusicKit path. |
| Main melodicness consumer | `2722341d4`, `272224414`, `2722200cc` | The consumer reads main at offset16 in the scalar composite. MusicKit normalization requires valid main in [0,1]; invalid edges remain absent. Invalid raw >1 must not reach the generic selector's >1 melodicness fallback on this scoped provider path. |
| Duration helpers | `2722210e0`, `272221524`, `27222171c` | For positive duration d: preferred=(d>=60 ? max(0,d-30)*0.5 : min(d,2)); maxOutgoing=min(d,60); maxIncoming=min(preferred,60). These are helper outputs, NOT final placement/previous-state/spatial bounds. |

The source manifest records ASM and pseudocode provenance per address. The two user
archives were integrity-checked in full, but only named source paths were interpreted;
file coverage does not mean semantic closure. No original instruction bodies are shipped.

## Explicit test profile and concrete results

The native composition fixture explicitly supplies internal records 8/8 bars, 9/8 bars,
12/16 bars, requested in order8,9,12. These numbers are **fixture inputs**, not a proven
default profile or an implicit conversion from TransitionStyles.json.duration.
Both actual playback durations are 120 seconds. Discovery bounds are outgoingEnd>=104,
incomingEnd<=32; later placement bounds are supplied separately by the fixture.

With 60 bars at 120 BPM on each side, empty-but-present vocal maps and matching main keys,
the source producers make 12 ordered seeds. The unchanged selector chooses style 9,
outgoing[104,120]seconds, incoming[0,16], score15.104. With 75 incoming bars at 150 BPM
(the same 120-second extent), 15 seeds are produced and style 12 wins, outgoing[88,120],
incoming[6.4,32], score10.088. The style 12 shift is performed by the existing selector.

These are native CloudSongAnalysis-to-result tests, not raw JSON/JNI tests and not
captured full-Apple-planner decisions. Main/edge changes, absent vs empty vocals,
wrong cloud BPM distractors, style lookup failures, and source immutability are tested.

## Resource and failure contracts

Existing hard limits are retained: 4096 structure/beat/downbeat/stability entries,
4096 vocals, 16384 loudness points, 14 internal records/requests, 64 seed pairs and
16,000,000 charged work units. They are LMG control-thread safety limits, not musical
thresholds inferred from Apple. The complete Cartesian pair scope must fit the seed
budget before pairs are evaluated; a set with>64 raw pairs fails even if later filters
might have removed enough of them. This avoids calling an arbitrary prefix complete.

Producer work and existing selector work share ONE budget. Exhaustion clears seeds,
counters and any provisional winner; completeness is false. The boundary test derives
the unchanged selector's minimal budget independently, then verifies total-minus-one
fails and the exact combined amount succeeds. A mutation which omits subtraction is
detected. Allocation/internal failures propagate; invalid finite-domain inputs are not
silently clamped. Unknown or denied strategy eligibility short-circuits before work.

`completeForResolvedScope` means all candidate work within the provided internal
profile, bounds and established MusicKit eligibility is finished. It is NOT a claim
that the entire source planner, all strategies or all Apple source/provider mappings
were executed. Default style order and raw schema mapping are not supplied by this API.

## Verification and limits of evidence

- 81 targeted producer groups, including threshold neighbors, first-vs-last source
order, optional count semantics, half/double scaling, validity and resource failures.
- 39 native composition groups using existing cloud converters and the existing selector;
no test caller supplies seed event pairs or a tonal-compatibility boolean.
- 1,685 independent finite-domain **specification oracle** rows:212 duration,729 scalar
composite,618 loudness and126 uniform-grid ordered-seed cases. Expected data comes
from a separate Python implementation, not C++. This is not original ARM execution.
- Eleven isolated semantic mutations must fail assertions. Compilation errors, timeouts
and signals are not successful detections. The same original source is never edited.
- GCC checks, Clang ASan/UBSan with leak detection, and three new isolated CTest entries
pass. These use real necessary native sources, not stubs or a full repository build.
- Existing Stage 2, Stage 3a, region-algebra and selector host scripts and eight previous selector
mutations pass again. Full repository CTest, JSON/JNI, Android/NDK and device checks
are server acceptance tasks. Expected CTest total after this patch is 51, not a claimed
local 51/51 result. Existing 17 real-JNI and 20 lifecycle packaging requirements remain.

## Remaining exact production bindings

1. The registry at `2722461cc` calls the BM constructor `272243e0c`. Its body is genuinely
in the supplied dependency manifest's 20 depth-limit nodes, not among the included
bodies. The default ordered requested-style profile is therefore not manufactured.
2. Requested-ID lookup reaches an internal record. It does not establish the JSON
`duration` -> optional `maximumBarCount` decoder mapping. The earlier style spec marks
this mapping as STRONG_INFERENCE; a runtime default is not asserted here.
3. Discovery provider witnesses+0x18/+0x28 and later Criteria/previousPlaybackEndState,
genre/support and spatial guards need their final caller bindings. Duration helper
arithmetic alone is insufficient. The scoped API accepts explicitly resolved results.
4. After those producers are bound, add the stateless JSON/JNI request and publish the
result under the existing observation ticket/generation. Do not register another
ExoPlayer/listener, add DSP dependencies, or change selectionBlock merely because
this resolved-input native composition selects a style in fixtures.

The connected large research archive remains unreadable through the connector's
256 MiB raw-download limit, including the streamed-reference option (HTTP 413 in this
pass). This is not a claim that the remaining source does not exist on the user's server.
No additional bulk archive collector or archive request is added by this patch.
