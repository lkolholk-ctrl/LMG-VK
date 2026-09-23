# Stage 3a — native analysis preparation and region inventory

Base: `6a26fcd4c6ec094fc29631e8da678b704a6e8631`, `feat/ios26-automix`.
This is the first implemented slice of Stage 3. **It is not the completed Stage 3
candidate selector, and it must not be used to execute a transition.**

## Scope and integration

The existing Stage 2 `AudioService` observation entry point and generic pipeline
are reused. No `AudioService`, `PlayerAudioChain`, player, renderer, sink, PCM,
volume, playback-speed, queue, seek, dependency-version or Gradle changes occur.
One ExoPlayer remains the only player. No Oboe/JUCE/TFLite dependency is introduced.

The worker now captures the entire response before Stage 1 parsing, hashes that
same owned copy, and retains it in `DecodedObservationSong`. Once both original
single-use tickets have supplied responses, the calculator validates the bundled
catalog, performs the existing metadata probe, and calls the new native entry:

```
AudioService's existing submitAutoMixObservationAnalysis
  -> existing generation/ticket lifecycle
  -> DecodedObservationSong (owned bytes, exact requested song ID, SHA-256)
  -> Stage 1 parser and verified catalog
  -> NativeObservationBridge.preparePair
  -> decodeMediaApiSongAnalysis (unchanged)
  -> preparePlannerAnalysisMaps (unchanged)
  -> inspectPlannerRegions (new, diagnostic only)
  -> bounded, versioned snapshot
  -> state.result.preparation
```

The raw source still uses the actual `flexml-analysis` relationship. No alternate
cloud schema, endpoint, token scheme, network requester or recording matcher is
invented. The existing response-provider integration is still required. Absence
of a provider still means `WAITING_FOR_ANALYSIS`.

`MetadataProbeReport` remains the public result type; its new `preparation` field
is optional for compatibility. The current pipeline never receives a Player in a
worker. The same mutex serializes decode/calculation; cancellation and generation
checks still discard stale outputs after seek, queue/repeat/shuffle changes,
pause, backend replacement and close. No new listener or native handle is created.

Copies, hashes, parsing and native preparation happen in the existing worker
section, not on the application looper. Bytes and full maps are not published in
StateFlow. The Kotlin input copies for the current pair remain bounded by two
4-MiB responses and are released with that pair's pipeline values on invalidation
or close; transient parsing/copying and DTO allocations require additional memory.
No unbounded response cache is added. C++ maps exist only for the native call.

## Reused evidence, not new musical heuristics

Authoritative current sources are the native implementation and its evidence:

- `native/automix/evidence/PLANNER_BEATS_IMPLEMENTATION.md`: Flex video-event
  hierarchy, section normalization, adjacent bars/segments/sections, stable runs.
- `native/automix/src/planner_analysis.cpp`: tonality, loudness, vocal and Flex maps.
- `native/automix/evidence/PLANNER_FLEX_IMPLEMENTATION.md` and
  `PLANNER_VOCALS_IMPLEMENTATION.md`: original enum/time conversion rules.
- `native/automix/evidence/PLANNER_SCORING_IMPLEMENTATION.md`: scoring consumes
  already established region predicates; it is not a raw-input candidate builder.

All these implementations are unchanged. The native structure path uses **Flex
videoEvents**, not a uniform grid inferred from cloud BPM or audio beat arrays.
Source order, duplicate times and recovered optional/default behavior survive.
Negative/reversed or out-of-track regions are diagnosed, not silently repaired.
No catalog-duration substitute is used for the playback duration. Millisecond
integer transport is converted to seconds in C++; Kotlin performs transport
consistency validation, not key, tempo, loudness, vocal or scoring calculations.

`planner_regions.cpp` inspects the existing bars, segments, sections and stability
map. It does **not** implement the original source's region-pair enumeration,
scale/truncate/shift operations, criteria, style-specific region extraction, vocal
relationship predicate or tie-break field mapping. Its first 16 usable stable
ranges are a **preview in native map order**, never a complete candidate set.

## States and meaning

Per track:

| Status | Meaning |
|---|---|
| `PREPARED` | Native maps were prepared and at least one stable range has a valid forward shape/tempo and lies inside the known playback duration. Not transition eligibility. |
| `INSUFFICIENT_ANALYSIS` | Preparation completed but that condition is not met; absent, unresolved and present-empty maps stay distinct. |
| `INVALID_ANALYSIS` | Native conversion or host-domain validation rejected the data. No partially prepared maps escape. |
| `RESOURCE_LIMIT` | The bounded observation workload was exceeded before source structure normalization. No partial maps escape. |

`supportsSmartTransitions` remains tri-state; `false` is not replaced by missing.
A `PREPARED` structural report does not override a false capability or any other
product eligibility gate. Both sides are prepared independently. One side's
normalization failure does not manufacture a usable result for that side or
silently discard the other side. Raw JSON/identity/ABI errors still reject the
whole native request through sanitized exceptions.

Every report has:

```
canExecute = false
selectedStyleId = null
selectionBlock = REGION_PAIR_RULES_UNVERIFIED
```

Stage 2's `REGIONS_REQUIRED` is intentionally retained: an inventory of original
stable ranges does not provide verified outgoing/incoming *transition pairs*.
`OBSERVED` means the lifecycle calculation finished; it is not a playback command.

## Bounded work

The new observation limits are LMG host safeguards, **not recovered Apple
constants**. They may reject exceptionally large analyses and must be reprofiled
before changing:

- 4 MiB per raw response (existing parser/adapter bound).
- 4,096 consumed Flex timestamps, checked before source section normalization.
- 16,384 loudness samples and 4,096 vocal entries.
- At most 16 diagnostic stable-range previews per side, preserving original order.
- At most 328 64-bit words in the result (2,624 bytes of primitive payload).
- Positive playback duration at most `2^53 - 1` integer milliseconds.

Excess Flex scores beyond the consumed time-array length keep the original
converter's behavior; they are not silently promoted into events. Preview
truncation is explicit; it never limits or selects a music candidate.

The source section normalization can perform quadratic work. Event bounds limit
that work before it begins. The coroutine timeout is still only a discard
deadline, not an interrupt mechanism for an already executing C++ function.

## Native ABI v1

`preparePair` takes standard UTF-8 byte arrays for two full responses and two
requested IDs, a two-slot `LongArray` of playback durations in milliseconds, and a
presence mask (bits 0/1). An absent duration slot must contain zero; zero is not a
valid present duration. Invalid lengths, masks, empty IDs and oversized byte
inputs are rejected. Pending JVM allocation/copy errors are preserved.

Return header (8 words):

`[1, totalWords, 2, 32, 8, 16, 0, -1]`

The final two header values assert non-executable/no-selected-style. Each track
has 32 words immediately followed by `previewCount * 8` preview words. Integers
are signed 64-bit JNI values; double fields use exact IEEE-754 bits, not numeric
float-to-integer conversion. All reserved/absent slots are zero.

| Track words | Meaning |
|---|---|
| 0 | Status: insufficient=0, prepared=1, invalid=2, resourceLimit=3 |
| 1 | Issue bits, defined by `PlannerPreparationIssue` in both headers/types |
| 2, 3 | Audio/Flex availability: absent=0, linkage=1, resolved=2 |
| 4 | Supports transitions: unknown=0, false=1, true=2 |
| 5 | Presence bits: duration=1, tonality=2, loudness=4, vocals=8, Flex=16, structure=32, beginning tonality=64, ending tonality=128 |
| 6 | Playback duration in milliseconds |
| 7, 8, 9 | Loudness, vocal and normalized Flex counts |
| 10, 11 | Structure event and downbeat counts |
| 12, 13, 14, 15 | Bar, segment, section and stability-map counts |
| 16, 17 | Forward/tempo-valid and playback-bounded stable-range counts |
| 18, 19 | Malformed and outside-track range counts across all inspected collections |
| 20, 21 | Duplicate and reversed adjacent event-time counts |
| 22..27 | Main/beginning/ending native tonic and mode tags; not MIDI pitch classes |
| 28, 29 | Preview count and explicit truncation flag |
| 30, 31 | Reserved zero |

Each preview is `[sourceMapIndex, startEvent, endEvent, startSecondsBits,
endSecondsBits, beatsPerBar, tempoBpmBits, trackBounds]`; bounds are
unknown=0, inside=1, outside=2. Indices reference this track's original structure.
Malformed/outside counts can include the same interval in different collections;
they are not unique song-region counts.

Kotlin rejects unknown versions/bits, inconsistent lengths/counts, dangling or
unordered map indices, nonfinite values, invalid tonality tags, changed reserved
fields and partial maps on failed preparation. Lists and sets from the decoder
are unmodifiable. Raw payloads, IDs, source URIs and full event arrays are not in
the snapshot. Ordinary diagnostics include only statuses, counts and issue codes.

## Tests in this patch

- `automix_planner_preparation`: 28 native groups over synthetic inputs, including
  independent expected counts/endpoints, source conversions, bounds and presence.
- `automix_planner_preparation_json`: real raw decoder -> complete preparation;
  compiled in the full CMake build, with exact IDs and `flexml-analysis` fixtures.
- `NativePlannerPreparationIntegrationTest`: eight JUnit tests requiring the full
  real host JNI, Stage 1 parser and actual verified style catalog.
- `PlannerPreparationTransportScenarios`: consumes seven snapshots produced by
  the C++ tests and rejects 34 corrupted variants. This binary transport test is
  not claimed to be an invocation of the raw JSON/JNI path.
- Required-report guards reject absent/skipped/failed/wrong-name/wrong-class
  Stage 3a reports. The verifier still requires all Stage 1 and 2 test reports.
- Read-only evidence collector: eight temporary-file tests.

The native test expectations added here are synthetic functional fixtures. They
are not new Apple ARM candidate-builder or end-to-end parity fixtures. Existing
ARM-backed converters are reused without changing their math.

## Server acceptance

Run in the repository after applying the patch, preserving local changes. Use a
new native build directory and rerun Gradle tasks to avoid stale JNI test reports.

```bash
set -euo pipefail
git diff --check
cmake -S native/automix -B build_native_stage3a \
  -DCMAKE_BUILD_TYPE=Debug \
  -DLMG_AUTOMIX_JNI=ON -DLMG_AUTOMIX_TESTS=ON -DLMG_AUTOMIX_SANITIZERS=OFF
cmake --build build_native_stage3a --parallel 2
ctest --test-dir build_native_stage3a --output-on-failure
bash scripts/automix_stage2_host_checks.sh
bash scripts/automix_stage3a_host_checks.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --stacktrace \
  -PautomixHostLibraryPath="$PWD/build_native_stage3a/android"
python3 scripts/automix_verify_observation_apk.py
```

Expected complete build: **43 CTest suites**, at least **17 real JNI tests**
(3 existing Stage 1 + 6 Stage 2 + 8 Stage 3a), and the **20 existing lifecycle
tests**. These are acceptance requirements, not claims about a completed server
run. Local targeted results and their narrower scope are included in the patch
package's `checks/` directory. Android compilation, all ABI linking/packaging,
real `preparePair` JNI execution and on-device verification remain required.

Observe a pair with supplied captured responses; confirm `result.preparation`
contains both summaries and every result remains non-executable. Repeat seek,
skip, duplicate IDs, shuffle, pause and backend changes; old results must not
publish. The existing audio behavior must remain unchanged.

## Remaining Stage 3 region selection

The repository's clean-room spec lists function addresses for region operations,
but assigns several semantics only from logs. Source arrays and a plausible
bar-count formula are not sufficient evidence for the complete pairing order,
placement constraints, style-specific windows or `tieBreakDelta`. This patch does
not guess them. Full candidate selection remains unfinished, not prohibited or
assumed impossible.

Use retained local research to resolve the exact region transformation chain,
add independent input/output fixtures, and then reuse the existing scoring. The
read-only helper can collect a small evidence package from an explicit root:

```bash
python3 scripts/automix_collect_stage3_region_evidence.py \
  --root /srv/research/apple-music-ios26 \
  --output /tmp/automix-stage3-region-evidence.zip
```

Use the actual research root if different; `--root` may be repeated. It collects
only specifically named reports and text files whose filenames contain known
region-function addresses, without following symlinks or unpacking archives. It
creates a new ZIP only, refuses overwrites, and includes a SHA-256 manifest plus
explicit rejected-file reasons. Review the manifest before sharing. No source
files are changed or uploaded. No-match and capacity failures are errors, not
claims that the underlying research does not exist.
