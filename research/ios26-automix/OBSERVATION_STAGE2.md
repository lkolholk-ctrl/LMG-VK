# Stage 2 — observation pipeline and native metadata preflight

## Baseline and scope

Target: `lkolholk-ctrl/LMG-VK`, `feat/ios26-automix`, base commit
`e1f1e67f2bd872acf7b4c39f3c6cb340efa337b0`, after the server-verified Stage 1.
The branch's existing `1.5.1-lmg30` dependency is unchanged. Gradle, including the
server's `java.security.MessageDigest` import fix, is not changed by this patch.

The observer uses the **existing AudioService player and listener**. It never
creates a player/renderer/sink, installs an audio processor, or calls playback,
seek, gain, crossfade or speed setters. It does not add Oboe, JUCE, TFLite, local
ML analysis, HTTP endpoints or credentials. Existing unrelated legacy backends
are not removed by this patch; their session ownership suspends this observer.

This is a **metadata preflight**, not a completed region planner or an executable
transition. `OBSERVED` means a diagnostic native calculation finished. It does
not mean audio processing was scheduled or a style was selected.

## Control and worker paths

```
existing AudioService.Player.Listener.onEvents
  -> Media3ObservationPipeline: immutable occurrence-pair snapshot (Main)
  -> two single-use ObservationTickets (outgoing and incoming)
  -> existing response provider submits a matched catalog response + ticket
  -> AppleSongAnalysisParser -> existing C++ decoder (serialized worker)
  -> verified TransitionStyleCatalog (loaded once, worker)
  -> scalar projection + explicit presence/availability bits
  -> NativeObservationBridge.probePair
  -> existing plannerDurationConfidence / matchPlannerTempos (C++)
  -> checked MetadataProbeReport -> StateFlow + redacted diagnostics (Main)
```

The scalar projection is transport only. Unit conversion and mathematical
comparisons stay in C++. `planner_scoring.cpp` is unchanged. Missing, zero, false,
linkage-only and resolved values stay distinct.

## Playlist identity and lifecycle

A pair includes timeline window UID, occurrence index, media ID, source URI,
custom cache key, optional provider recording revision, duration, repeat and
shuffle settings. A ticket is an object-identity capability, not a reconstructible
media ID. Two entries with identical IDs and repeat-one use separate tickets.

The automatic successor comes from `Timeline.getNextWindowIndex` with the actual
repeat/shuffle modes, never `index + 1`. Pause, suppression, stopped/ended state,
player errors, ads and wrong session ownership suspend observation. Live/dynamic
windows and clipped/default-relative time mappings are refused rather than guessed.

Timeline/transition/discontinuity/repeat/shuffle/metadata/playback-parameter events
force a new generation. This is deliberately conservative: unrelated timeline
updates may also invalidate the observation, avoiding stale-source assumptions.
Backend changes are explicitly forwarded by AudioService. The observer closes
before service scopes and the existing player are released.

Workers never read Player. Decode and native work are serialized by one mutex.
Both coroutine cancellation and generation checks guard publication. JNI is not
forcibly interruptible: a ten-second timeout discards late results, not a promise
to terminate a running C++ call. Closing and re-opening a service creates new
tickets; an old ticket cannot authorize a new instance even if counters match.
A rejected generation revokes both tickets and waits for a relevant lifecycle
invalidation; there is no automatic network retry loop.

## Connecting the existing recipe/analysis provider

Two service APIs are supplied:

```kotlin
suspend fun deliverObservationResponse(
    service: AudioService,
    originalTicket: ObservationTicket,
    exactCatalogSongId: String,
    responseBody: ByteArray,
): ObservationSubmission = service.submitAutoMixObservationAnalysis(
    ticket = originalTicket,
    requestedSongId = exactCatalogSongId,
    response = responseBody,
)
```

Collect `service.autoMixObservationState` after `onCreate` and retain each exact
object from `state.requests` during asynchronous fetching. The snippet illustrates
the delivery boundary; `exactCatalogSongId`/`responseBody` are the existing provider's
outputs, not hardcoded new endpoint assumptions. Use the project's normal imports
for AudioService and the observation types. The actual
provider fetch/matching function is **not implemented or invoked by Stage 2**.
Until it submits both tickets, a valid pair remains `WAITING_FOR_ANALYSIS`.
Responses must use the cloud JSON shape already accepted by Stage 1; no new
unverified pair-recipe schema is silently treated as that shape.

Providers should track ticket **identity**, start each request once, and cancel
old network work when its generation disappears. State changes within a generation
also remove accepted tickets; do not restart still-pending requests on every
emission. The observer owns/cancels decoding and native work, not an external
provider's sockets. `ACCEPTED` means queued for validation, not a valid plan.
`STALE`, `DUPLICATE`, size/ID errors and service shutdown are explicit results.
No source URI, title, token, catalog ID or response fragment appears in observer logs.

An occurrence ticket proves freshness/ownership, **not recording equivalence**.
Exact source-to-catalog matching remains the provider's responsibility. An optional
`MediaMetadata.extras["lmg.automix.recordingRevision"]` can distinguish provider
content revisions; it must not be derived from a title. The provider must not
mutate its ByteArray concurrently while the suspend call copies it.

## Native result semantics

The version-1 ABI transports ten optional scalars, trait bits and eight result
words. It checks array lengths, finite/canonical slots, flags, schema and tags.
Cloud/timeline duration comparisons use the existing native two-second boundary
without claiming all streams are automatically high-confidence recordings.
Two separately labelled tempo probes use the existing normal/expanded tolerances:

* `main` versus `main`;
* outgoing `ending` versus incoming `beginning`, only when both are present.

These are diagnostic probes, not a recovered cloud-to-region mapping. Edge BPM
does not fall back to main BPM. Unknown durations remain uncomputed. Unsupported
or missing data is reported explicitly. Tempo tags preserve native source
encoding; `252` means incompatible, not a chosen half/normal/double scale.

**Every report includes `REGIONS_REQUIRED`, and `selectedStyleId` is null.**
No regions, bar counts, vocal relationships, tonality enum mapping or gain curves
are fabricated. Calling `beatMatchedStyleScore` with invented inputs would turn
an observation into a misleading plan, so it is intentionally not done here.
The next planning stage must supply verified candidate-region predicates, invoke
the existing native scoring and bind the selected schedule to source coordinates.
Audio execution remains a later, independently gated step.

## Verification

Targeted dependency-free Linux host checks (Kotlin compiler + coroutines jar,
JDK JNI headers, C++17 compiler and Python are required):

```bash
bash scripts/automix_stage2_host_checks.sh
```

This compiles the unchanged scoring implementation, new probe and a **targeted**
host library exporting `probePair`. It does not replace your complete production
host JNI library and must not be passed to the full Stage 1/2 Gradle tests.
The script uses an isolated temporary directory and does not overwrite existing
build products. `AUTOMIX_STAGE2_KEEP=1` retains its test products.

Full project verification in the existing server sandbox:

```bash
cmake -S native/automix -B build_native \
  -DCMAKE_BUILD_TYPE=Debug \
  -DLMG_AUTOMIX_JNI=ON -DLMG_AUTOMIX_TESTS=ON \
  -DLMG_AUTOMIX_SANITIZERS=OFF
cmake --build build_native --parallel 2
ctest --test-dir build_native --output-on-failure
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace \
  -PautomixHostLibraryPath="$PWD/build_native/android"
python3 scripts/automix_verify_observation_apk.py
```

The patch adds CTest `automix_metadata_probe` (the 41st test relative to Stage 1).
It adds 20 JUnit lifecycle cases and six real production-JNI integration tests.
The latter include Stage 1 JSON parsing + real verified catalog + new native
calculation, missing analysis, and a deliberately corrupted catalog. The APK
verifier now requires the existing three real JNI cases, the six new JNI cases
and all 20 lifecycle cases, with no missing/skipped/failed cases or malformed XML.
Existing workflow commands already invoke this verifier; no workflow change is
required. Green targeted tests are **not** a claim that full CTest/Gradle/APK or
device checks have run.

### Device acceptance matrix (still required)

1. Two normal tracks; submit both matched responses and see one `OBSERVED` report.
2. Repeat-one; same media ID must receive a new generation and two distinct tickets.
3. Shuffle/repeat-all; incoming request follows Media3's automatic successor.
4. Seek/skip/queue replacement with delayed responses; old generation never publishes.
5. Pause/resume, audio-focus suppression, stop, source replacement and service restart.
6. Unknown duration becoming known; fresh probes without invented zero durations.
7. Missing/unresolved analysis, wrong catalog ID, altered catalog and unavailable JNI.
8. Verify unchanged playback, volume, speed, existing transitions and output routing.
9. With no response provider attached: only bounded waiting state, no network activity
   and no change to sound.
