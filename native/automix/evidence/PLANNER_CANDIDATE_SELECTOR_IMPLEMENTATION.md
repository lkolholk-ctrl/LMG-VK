# Explicit-seed BeatMatched candidate selector

Date: 2026-09-23. Baseline: `01a7a2fc25e04f1c2981edc0e58f0ad7b65755f9`.
This is a native observation component, not a complete AutoMix planner or a PCM renderer.

## Delivered boundary

`selectPlannerRegionCandidates` accepts caller-ordered, already source-scaled/truncated region seeds, internal style descriptors, resolved placement bounds, full native structures, and provider-resolved tonal components. It builds style-specific pairs, calculates predicates, calls the **unchanged** `beatMatchedStyleScore` and `bestPlannerCandidate`, and returns a concrete winning candidate when its score is positive.

`selectPreparedPlannerCandidates` composes this with the actual `PlannerSongPreparation` objects produced by Stage 3a. It borrows their full structure, vocal and loudness maps. It does not use the 16-item diagnostic preview, rebuild a beat grid from cloud BPM, or retain pointers after returning.

A synthetic prepared pair with equal 120 BPM Flex structure, empty-but-present vocal maps, explicitly resolved matching keys, outgoing events 32..64 and incoming events 0..32, and internal eight-bar descriptors selects style 9 at score 15.016. The same fixture with incoming Flex tempo 150 selects style 12 and shifts the incoming endpoints to 16..48. These are **fixture results**, not captured Apple full-planner decisions for arbitrary songs.

## Evidence provenance

Input dependency archive SHA-256:
`ffd81a8190f1f2081994046824d3303a733f2c2aec11b95fea653cc0a5a80347`.
All 1,594 payload files and 3,674,643 payload bytes were checked against the manifest.
The manifest reports no missing required/transitive disassembly and 20 unvisited nodes at its depth limit. File availability is not semantic closure.

`PLANNER_CANDIDATE_SOURCE_MANIFEST.json` records individual ASM hashes and archive provenance for 34 inspected functions. A few original entry-point bodies are retained in the earlier 64-file evidence archive rather than repeated in the dependency archive. No Apple instruction bodies are shipped in this patch. No ARM binary, Swift runtime, or iOS process was executed in this verification pass.

## Recovered composition

| Piece | Source anchor | Implemented behavior |
|---|---|---|
| Per-style construction | `272234380`, `272234b0c`, `272234e14` | Use existing source suffix/truncation algebra. Only IDs 8, 9, 12 belong to this builder. Preserve seed order, then descriptor order. |
| Candidate delta | `27222d51c`, with range accessors `27222b7e0`, `27222b858`, `27222bc88` | Enabled style calls pass flag 1. The field difference is outgoing **end** minus incoming **end**, after style-region transformation. |
| Bar ratio | `27223550c`, `27222be34`, `27222c8a0`, `27222f3d4` | Both endpoints must cast to downbeats. Incoming bar count scales half -> integer /2, one -> unchanged, two -> checked *2. Ratio is scaled incoming / outgoing when both are positive. No nearest-bar snapping. |
| Matched bars | `272235d08`, `272235fd8` | Present only when outgoing and scaled incoming counts agree. Missing differs from zero. Existing scoring requires count >7 for 9/12. |
| Tempo at region start | `272236498`, `272236920`, `27221e200`, `272236b2c`, `272218f90`, `272218e64` | Exact bar start when both endpoints are downbeats; otherwise reverse source-order search for the preceding downbeat. Use the first stable-map entry covering its downbeat ordinal in [start,end). Feed the existing tempo matcher; no cloud BPM projection. |
| Leading incoming vocal window | `272235198`, `272237058` | Find beat ordinal `incomingStart-4`, then use the closed window `[0, foundBeat.songTime]`. This is not a fixed four-second window and not the last four beats before the start. Missing event/map/window stays unavailable. |
| Vocal relationship | `272236188`, `272236f48`, `272236d10`, `272236bcc`, `27221f568`, `27223a230`, `27223a25c`, `27221f4e4` | Filter the closed beat-ordinal interval in source order. For scale two decimate outgoing; for half decimate incoming. Keep even **enumeration positions**, then form adjacent closed time windows and call existing vocal-strength extraction. Align vector suffixes. Skip sentinel-5 pairs; max of paired minima is incompatible unless absent or veryLow. A missing vocal map is incompatible, a present empty map is not. |
| Style-12 shift | `272233adc`, `272233c88`, `272233f00` | After suffix/truncation, shift incoming endpoints **forward** by 16 inverse-scaled beats (32/16/8 for half/one/two). Keep exact event matches, original outgoing range and scale. Missing endpoints remove that candidate. |
| Loudness and tonality | Existing `planner_loudness.cpp`, `planner_scoring.cpp`; tonal caller `272231d04`, provider accessor `272233fac` | Reuse existing loudness ratio and tonal compatibility. Do not manufacture main/beginning/ending provider selection. |

The generic scalar score API remains unchanged: weights multiply left-to-right, the positive product receives a separate `delta * .001` addition, and the first highest positive score wins. New diagnostics do not replace those computations. All affected compilation must preserve `-ffp-contract=off`; no fast-math claim is made.

## Explicit inputs that are NOT automatically resolved here

1. **Seed discovery and order.** The earlier driver (`27222f54c` / `2722307ec`) and helpers select, filter, scale and truncate initial region pairs. This patch does not replace that with all stable ranges, a Cartesian product, the first 16 previews, or the last N seconds.
2. **Internal style descriptors.** `suffixBars` is the optional source internal field at +0x18/tag +0x20. It is not guessed from `TransitionStyles.json.duration`. Absence uses the recovered four-bar default; explicit zero remains zero.
3. **Provider tonal components.** `PlannerCandidateTonalBinding` contains actual native tonality/melodicness values and a resolved flag. The source provider's main/beginning/ending projection is not asserted here. If style 9 is requested and either binding is unresolved, no provisional style-8 winner is returned.
4. **Placement and global eligibility.** Criteria/context-derived bounds are inputs. Download/spatial confidence, genre/support gates, other strategy generators, and priority across BeatMatched/Smart/DeadAir/Fallback remain above this component.
5. **Schedule and execution.** The candidate is not a transition schedule. No player calls, time stretching, output gain, PCM processing, JNI ABI changes, or audio-backend changes are included.

These are remaining implementation/binding obligations, not a claim that the supplied research lacks every relevant function. Another bulk archive is not requested by this package.

## Host contract and failure behavior

Native normalized inputs are finite, use nonnegative event ordinals, and preserve source list order. Unknown enums, dangling references, invalid intervals and checked arithmetic overflow are rejected explicitly. This is an LMG host domain; it does not emulate original Swift traps or unsupported nonfinite inputs.

Limits: 4,096 structure/beat/downbeat/stability entries; 4,096 vocal intervals; 16,384 loudness points; 64 seed pairs; 14 style descriptors; 16,000,000 conservatively charged scan units. They are **LMG safety limits**, not Apple musical thresholds. Exceeding a budget returns `resourceLimit`, clears all partial counters/reasons/winners, and leaves `completeForProvidedSeeds=false`. Allocation or internal failures propagate; they are not converted into successful observations.

The prepared adapter requires known, valid playback durations and checks supplied seed endpoints against their own track. It does not substitute catalog duration or silently clip a seed. Incoming maximum end must already be at or before the resolved incoming duration; shifted style-12 candidates are checked against it. Invalid/reversed preparation is rejected without sorting. Unused outside-track inventory alone is not fatal when the supplied seed is inside the track.

`completeForProvidedSeeds=true` means all **provided** seeds/styles were processed. It does not prove they are the complete source-generated candidate set. The result contains seed/style indices, transformed event indices, song times, score, native predicate outputs and LMG rejection flags. The flags describe rejected attempts even when a different attempt wins; they are not Apple's `FailureReason` values. `canExecute` is an immutable false constant.

## Verification meaning

- 61 targeted C++ groups cover selection, styles, source ordering, half/double behavior, absent-vs-empty input, boundaries, three-beat decimation parity, reasons and resource failures.
- 18 groups use existing cloud DTO converters, Flex structure construction and Stage 3a preparation before the selector. The fixture explicitly resolves its known tonal component; it is not a production provider policy.
- 3,065 independent finite-domain **specification** oracle cases cover bar arithmetic, endpoint delta and vocal suffix alignment. The Python generator does not call C++; it is not original ARM execution or a full source-candidate oracle.
- Eight independently compiled behavioral mutations must be rejected. Compiler failures do not count as detections.
- Targeted GCC and Clang ASan/UBSan/leak checks pass. Three CTest entries are also run in an isolated project using the actual source files and the new declarations copied from the patched CMake.
- Existing Stage 2, 3a and region-algebra host scripts are rerun. Full repository CTest, raw-JSON/full-JNI integration, Android/NDK packaging, CI and device playback remain server checks.

## Next production boundary

Connect the source seed/descriptor/provider/context producers to this API under the existing observation generation. Resolve and verify their ordering and component projection before publishing the candidate through `AudioService`. Then add a real JNI selector contract and positive/negative lifecycle tests. Do not switch the current `selectionBlock` or schedule PCM merely because the explicit-input native tests pass.
