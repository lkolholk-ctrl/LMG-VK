# Generation-bound stateless selection observation

Date: 2026-09-23. This is the binding of the **resolved-input** composition, not
closure of Apple's default profile, catalog decoder or final Criteria/provider.
Remote readback was a90f451ef0e380717eb479a93f70e70d6a6bfe18. Required source overlay:
`automix-stage3b-input-producers`, reported tested/committed by the user as e9a1f94.
That commit was not resolved by the connector in this pass; installation is pinned
by complete source hashes, not an invented full commit identifier.

## Delivered

`NativeObservationBridge.selectResolvedPair` receives two privately owned raw
responses, their requested song IDs, and a bounded signed64 request. Explicit
mode uses the existing `decodeMediaApiSongAnalysis` and
`observeProducedMusicKitCandidates`. It does not accept caller-selected seeds,
precomputed tonal relationships, scores, or native handles. The existing source
converters, producers, region algebra and scoring are unchanged.

The Kotlin wrapper requires the existing verified `TransitionStyleCatalog` and
checks requested/internal ID membership. It does not read raw `duration` as a
bar count. Internal record values and profile order remain explicitly resolved
inputs supplied by the provider. The low-level JNI method alone is not a
canonical-catalog authentication interface. Neither an enum, a boolean, a
checksum nor a passing test proves that the caller recovered an Apple binding.
The report always labels this domain `EXPLICIT_RESOLVED`, not `APPLE_DEFAULT`.

Without explicit scope, the native call returns `NEEDS_SOURCE_BINDINGS` with a
three-bit blocker mask:1 default ordered profile,2 raw-to-internal field mapping,
4 final provider/Criteria context. It does not decode raw JSON in this mode and
never claims to have done so. The production pipeline already decoded both
responses using Stage1 before reaching this calculation.

## Remaining evidence, not invented defaults

`PLANNER_INPUT_PRODUCERS_IMPLEMENTATION.md` documents the actual open bindings.
The registry at 2722461cc calls 272243e0c. The constructor body is a depth-limit
node in the supplied dependency manifest, not among its included function
bodies. The supplied specialized style specification marks correspondence of
raw keys and internal fields STRONG_INFERENCE, not an exact decoder trace.
The style loader at 27224e480 and its generated decoding chain remain relevant
source leads. Provider/Criteria witnesses, previousPlaybackEndState and higher
strategy/genre/spatial guards must be resolved at their actual call sites.
No default ID list, duration-to-bars conversion, position-derived placement,
or supportsSmartTransitions-only eligibility policy is introduced here.

A nondefault resolved scope can select a candidate, but it does not remove
these source gaps or assert completeness across other strategies/providers.
`canExecute` is a computed false property, never a writable flag or command.

## Lifecycle

The existing owner-thread ObservationPipeline holds the immutable scope outside
its playlist-identity snapshot. Binding requires an ORIGINAL ticket currently
owned by that pipeline. Accepted response tickets remain valid for binding; the
binding does not consume them or refetch either analysis. Exactly one scope may
be bound per generation: equal repeats are DUPLICATE; replacements are CONFLICT.
Use a new genuine playback generation for a new source context.

Binding increments a separate revision, cancels only calculation (not decoding
or retained responses), and recalculates when both decodes are ready. Workers
receive a captured snapshot with generation+revision+scope. Raw copies are never
published in StateFlow. Work remains serialized by the existing mutex.
Both successful publication AND caught error publication check the revision.
This matters when a cancelled, uninterruptible native call throws an ordinary
failure instead of CancellationException after a same-epoch rebind. An older
failure must not revoke the newer job. Seek/pause/backend change/close still
revoke the whole epoch and its scope. Owner update rejects a replayed bound
worker snapshot; it cannot bypass ticket binding.

The service adds only `bindAutoMixObservationScope` delegating to its existing
observer. No listener, player, renderer, audio processor, gain/rate mutation,
seek, queue mutation, alternate audio backend or ML dependency is introduced.
Old scalar `MetadataProbeIssue.REGIONS_REQUIRED` describes only that probe.
`result.selection` is the separate scoped result; preparation remains inventory.

## Wire contract v1

All words are signed64 Java Long/C++ int64. Double payloads are IEEE bit copies,
not numeric conversion. Native state lasts one call. Request max80 words;
response always48 words. No stream pointer or deferred native ownership exists.

Request fixed24 words:
0 magic 0x4c4d47535131,1 version,2 length,3 generation,4 binding revision,
5 mode(0 blocked default/1 explicit),6 duration-presence bits,7/8 actual duration
milliseconds,9 work budget,10 eligibility(0 unknown/1 allowed/2 denied),11 requested
ID count,12 internal record count. Words13..21 hold nine bounds, in the order
of ResolvedPlannerBounds. Words22/23 reserved0. Tail: ordered requested IDs,
then internal records `{id,presence,maximumBars}`. Missing payload must be0;
explicit0 differs from absent. Requested duplicates stay ordered; record IDs
must be unique. Default mode cannot smuggle resolved fields or a revision.

Response:
0 magic0x4c4d47535231,1 version,2 length48,3/4 correlation,5 mode,
6 status(0 blocked; otherwise1+PlannerProducedStatus),7 blocker mask,
8 completeForResolvedScope,9 executable0,10 hasWinner,11 seedCount,
12..19 bounded production counters,20..25 selection counters,26 rejection mask,
27 candidateStatus(-1 unavailable/0selected/1noPositive),28 styleID,
29 seedIndex,30 styleIndex,31..34 event references,35 incoming scale,
36..39 song-local time bounds,40 score,41/42 tempo tags,43 producer work used,
44..47 reserved0. No-winner fields have a single canonical empty representation.
Kotlin checks header/correlation, status-completeness, counter relationships,
request/style agreement, event ranges, finite times/positive score and duration
containment. Event indices refer only to these owned inputs and this epoch.

Limits are existing LMG control-thread bounds, not musical constants:4MiB per
response,4096-byte UTF8 ID,14 IDs/records,64 seeds,16M charged work units. On
resource failure the existing composition and this encoder expose no provisional
winner or partial counters. No timing or sample playback permission is implied.

## Verification in this authoring environment

- 24 native model-to-wire groups using actual existing producers/converters/
  selector, including explicit 120/120 BPM style9 and120/150 BPM style12 fixtures.
- 12 C++-generated snapshots read by Kotlin;225 malformed snapshots rejected.
  Kotlin request encoding is compared byte-for-word to the native test request.
- 16 new coroutine lifecycle scenarios, including three late-error variants;
  six isolated semantic mutations detected by assertions, not build errors,
  signals or timeout failures.
- Clang ASan/UBSan with leak detection passed for the native binding/model chain.
- Production raw JSON entry, its native test and JNI entry compiled to objects
  against real declarations/JDK headers. Full raw parser linkage/execution is
  NOT claimed here. No replacement parser, fake JNI calculation or Apple ARM
  execution is presented as that verification.
- Prior Stage2, Stage3a, algebra, selector and producer targeted host scripts
  passed. Full repository CTest/Gradle/Android/device tests remain server tasks.
- 12 new required real-JNI JUnit tests and16 required binding lifecycle JUnit
  tests are added. The APK verifier rejects their absence/skip/wrong identity.
  A bounded ELF dynsym check requires the defined exported new JNI entry for
  all three Android ABIs, so an old APK cannot pass on fresh JVM reports alone.
  Local ELF-guard tests use a tiny compiled symbol fixture, not an Android APK.

Expected SERVER acceptance:53 CTest suites,29 real-JNI cases,36 lifecycle cases,
three freshly assembled JNI ABIs and the unchanged canonical catalog checksum.
Those totals are requirements, not results obtained in this environment.
