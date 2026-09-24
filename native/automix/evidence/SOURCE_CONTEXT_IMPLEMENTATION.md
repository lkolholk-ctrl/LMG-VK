# Byte-backed catalog mapping and scoped MusicKit context

Date: 2026-09-24. Applies after `automix-stage3b-profile-criteria`.
This patch closes the raw-catalog count mapping, the finite late-placement helper,
and the nonspatial provider's three duration-envelope getter bindings. It connects
these operations to generation-bound observation. It does NOT claim that every
Apple provider policy, spatial/time mapping, previous playback state, strategy,
or executable transition schedule is implemented.

## Image attribution and method

The user supplied the extracted `_SonicKit_MusicKit_Packages` Mach-O, not an IPSW:

- 1,063,640 bytes.
- SHA-256 `e843a9a94838f49c9c1e9b11f57fca70c6eebde59d7a3fd8d43917ee65607b4d`.
- UUID `83c387fd-3c68-344a-85e5-0c3c015811c1`.

The whole-file identity and VM-to-file mappings were checked before inspection.
`SOURCE_CONTEXT_PROVENANCE.json` records 26 inspected function windows, their
file offsets, lengths, and SHA-256 values. LLVM 17 was used for disassembly;
duplicate outlined-function labels were resolved using raw B/BL immediates.
No original code, binary image, or long disassembly is redistributed here.

`automix_verify_source_image.py --image <path>` reproduces attribution and selected
metadata/literal/getter checks against the same full image. It does not execute
the Swift decoder or Apple planner, emulate the OS, or establish whole-engine
bit identity. Function hashes identify evidence, not semantic correctness by
themselves. Outside-image runtime branch islands were not guessed.

## Catalog field mapping

Catalog loading goes through 27224e480 / 27224e814. Relative conformance fields
27229a8c0 and 27229a3b0 resolve to wrappers 27224eab0 and 27224d8fc. The array
wrapper reaches 27224e8bc; the element wrapper reaches 27224d1f0.

The element type's metadata at 2884ae360 has field offsets 0,8,24,40,48. Its field
record at 272294c58 names id, startTime, maximumBarCount, outgoingSchedule and
incomingSchedule; maximumBarCount's mangled type is `SiSg` (optional signed Int).
The CodingKeys record at 272294c18 places maximumBarCount at tag 2. String getter
27224ce64 maps that tag to `duration`: the literal MOVZ/MOVK sequence at
27224ceac..27224ceb8 materializes its eight ASCII bytes. The element decoder sets
tag 2 at 27224d400/404, decodes an optional Int at 27224d414, and stores the payload
and absence flag at object offsets +0x18/+0x20 in 27224d5a4..27224d5b4.

Consequently `bindPlannerCatalogFields` widens the existing raw parser's `duration`
to the internal optional bar count. It does not multiply by four, divide by BPM,
interpret seconds, or default an absent count to zero/four. The existing downstream
source helper alone retains its absent-count -> four rule. A present zero stays
present. Canonical BM counts are 8,8,16; requested order comes from the previously
recovered source profile 8,9,12, not catalog storage order. The mapper preserves
all decoded record IDs and order; requested default records must all resolve.

**Host domain:** the existing raw parser exposes an optional C++ int, narrower
than a source Swift Int64. This patch does not claim the entire signed64 JSON
decoder domain. At most 14 records with unique nonnegative IDs/counts are accepted.
The Kotlin production caller uses the existing immutable SHA-verified catalog
bytes. The native low-level JSON API still accepts explicitly supplied bytes;
it is not an independent network/catalog trust service. No catalog file is changed.

## Late placement

272258148 delegates late constraints to 272259834. On the finite host domain,
late-inSong returns [+0,duration]; late-after(t) returns [t,duration] when
0 <= t <= duration. The after payload is not subtracted from the end of the song.
The source early/late enum tags remain distinct for other consumers.

The existing C++ Criteria API now implements this branch; no earlier enum values
are reordered. Exact endpoints, fractional seconds and copied -0.0 are retained.
Missing duration, NaN/Inf/negative input and reversed Within remain explicit host
failures. Zero duration can normalize to a zero-size range but cannot authorize a
transition. No epsilon, nearest beat, or fixed transition length is inserted.

## Provider getters and the two duration domains

MusicKit witness table 2884ace78 contains these direct getter targets:

| Slot | Getter | Metadata offset field | Role at consumer |
|---|---|---|---|
| +0x18 | 2722246f8 | +0x1c | minimum outgoing start; also outgoing END discovery |
| +0x20 | 272224708 | +0x20 | minimum outgoing end |
| +0x28 | 272224718 | +0x24 | maximum incoming end; also incoming END discovery |

Each getter loads a signed field offset from metadata and returns that stored
Double unchanged. Constructor 2722200cc writes the fields at 272220344, 272220350
and 272220360 after calling the existing duration helpers 2722210e0, 272221524 and
27222171c. For positive reference duration d seconds:

    preferred = d >= 60 ? max(0,d-30)*0.5 : min(d,2)
    minOutgoingStart = d-preferred
    minOutgoingEnd   = d-min(d,60)
    maxIncomingEnd   = min(preferred,60)

The new helper calls the existing duration-envelope implementation instead of
copying its formula into Kotlin. The discontinuity at 60 and distinct start/end
bounds are deliberate. At d=120 the three fields are 75,60,45.

In the traced nonspatial factory path (27222a140, 27222a4b4, 272220ae4), the
constructor's reference duration comes from MusicKitAnalysis.duration. The actual
Song.duration/optional-presence result is a separate playback/confidence input.
27222f54c consumes +0x18 for outgoing endpoint discovery. Later constraints in
27222919c and 272229758 use the start/end roles independently; the new composition
preserves that distinction.

## Supported scope: not the complete source default

`MusicKitSourceContext` accepts caller Criteria and independently established
provider facts. It accepts no internal descriptors, bar counts, seeds, tonality
predicates, scores, or resolved discovery bounds. Native code resolves the catalog
and ranges, then calls the unchanged produced-observation and selector APIs.

The supported branch requires:

- The upstream policy verdict is ALLOWED and maximum complexity permits BeatMatched.
- Both sources are explicitly confirmed nonspatial; absence is not inferred from
  the reduced Cloud DTO or a URL extension.
- Both sources explicitly have no previous source playback end state.
- Reference and actual playback durations are present and EXACTLY equal in the
  supported integral-millisecond domain (positive and <=2^53-1).

**Exact duration equality is an LMG integration restriction, NOT Apple's <2-second
confidence rule.** It avoids silently using different coordinates in existing
preparation. A one-millisecond discrepancy returns DURATION_MAPPING_REQUIRED,
not rescaling or clamping. Matching durations is not evidence that two recordings
are identical: the existing trusted source/catalog matching remains mandatory.
An ALLOWED verdict must not be hardcoded from supportsSmartTransitions, genre text,
BPM, or file extension. The source/provider policy is still an explicit input.

Unknown facts, spatial input, previous state, missing/mismatched duration, invalid
Criteria and unavailable catalog records produce separate non-executable results.
No fallback strategy or intermediate winner is manufactured. Existing seed/event/
work limits remain unchanged. Their exhaustion clears the provisional result.

Unbound ordinary observation now retains only mask 4 (complete provider/context
policy unresolved), rather than mask 6; catalog field mapping is no longer claimed
unknown. A bound source report is labelled SOURCE_MUSICKIT_SUBSET, never APPLE_DEFAULT.
The historical Criteria blocker string refers to the still-unbound complete policy,
not to the now-implemented finite late range helper. completeForResolvedScope covers
this scoped BeatMatched search, not all original strategies. canExecute stays false.

## JNI and generation lifecycle

The existing explicit path and `selectResolvedPairV2` remain available. New export:
`NativeObservationBridge.selectMusicKitSourcePairV1`.

The source request has exactly 24 signed64 words: magic/version/length; generation
and revision; actual-duration presence/payloads; work budget; eligibility and four
source-knowledge tags; outgoing/incoming Criteria tags and Double-bit payloads;
maximum complexity; zero reserved slots. No pointer or native handle is returned.

A response is 12..140 words. Its header echoes generation/revision and reports a
source-resolution status plus known profile IDs. A scope rejection is only the
12-word header with no partial data. Successful resolution appends the native-built
existing resolved request and its 48-word V2 selection report. That report can
still represent a resource/structure/selection failure: scope resolution is not a
promise of a candidate.

Kotlin checks versions, shapes, masks, exact echoes, source mode, catalog identity
set, descriptor presence, finite bounds, raw caller Criteria endpoints, and the
existing strict candidate transport. It does not recalculate provider math or
scores. The source context is immutable; init validates constructors and copies.

The existing pipeline uses the same tickets, worker mutex, generations and binding
revisions for both explicit and source contexts. Cross-kind rebinds conflict within
an epoch. Same context is a duplicate; a changed context requires a new epoch.
No refetch is required when binding after replies. A late old error cannot revoke
a new binding. Seek/pause/backend replacement/close revoke the whole context.

AudioService receives only an additional overload delegating a MusicKitSourceContext
to its existing observer; the original resolved overload is retained. No new Player,
listener, sink, renderer, PCM ownership or gain/rate/seek call is introduced. This
is a snapshot decision, not an executable promise for a later playback position.
Stage 4 must add schedule compilation and deadline/position revalidation separately.

## Verification interpretation

Local checks use real required native implementation files in a partial checkout:
54 native groups (10 catalog,10 provider,34 scope), 19 native snapshots decoded in
Kotlin, 893 rejected malformed transport variants, 8 source-context lifecycle
scenarios, and 10 isolated native semantic mutations. The current source path
selects style9 with score15.104 for a 120s/120BPM synthetic pair; a 96s pair at
120/150BPM selects style12 with score10.064. No test caller supplies seed pairs or
bar descriptors to the source request. A wider 120s/120-150BPM case exercises the
existing resource guard without preserving a winner.

These fixtures are not recordings of full Apple planner decisions. No original
ARM/Swift routines are executed. The image verifier proves byte attribution and
selected metadata relationships; C++/Kotlin tests check the implemented finite scope.

The JSON adapter, its real-parser test and JNI translation unit are object-compiled
locally. The new positive raw-JSON -> JNI -> source selector JUnit path and complete
Android/NDK build are SERVER acceptance gates, not local results. Twelve new real-JNI
JUnit tests and eight new lifecycle JUnit tests are mandatory in the APK report guard.
Both V2 and source-context V1 defined ELF exports are required in every ABI; stale
V2-only libraries fail even when XML test reports are fresh.

Expected full-server totals: 59 CTest, 43 real-JNI, 44 lifecycle cases, three ABIs,
and unchanged canonical catalog SHA. Expected is not a claim of local full execution.
