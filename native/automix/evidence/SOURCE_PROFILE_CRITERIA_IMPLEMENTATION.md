# Source BM profile and finite-domain Criteria limits

Date: 2026-09-24. Applies after the generation-bound Observation Selection Binding package.
This closes the **ordered default BM request profile**, not the complete default planner.
AudioService, PlayerAudioChain, renderer/sink ownership, PCM and existing scoring are unchanged.

## Verified input and attribution

`production-closure-evidence.zip` SHA-256:
`d7ae5ff80326a873ce800c0c2a9689b5549cff1e08283525aa4f2c98051a8d4e`.
All 654 payload sizes/hashes matched. All 888 referenced texts resolve by hash against this
archive plus the two previously supplied corpora (290 new and 598 reused in this manifest).
The declared mapped image is UUID `83c387fd-3c68-344a-85e5-0c3c015811c1`, SHA-256
`e843a9a94838f49c9c1e9b11f57fca70c6eebde59d7a3fd8d43917ee65607b4d`.

The collector reports `textImageMatchProven=false`. This pass does not have the complete
image or root function raw bytes, so it does NOT claim independently verified binary
identity between every text instruction and the mapped image. Conclusions below combine
the supplied assembly with exact static-data slices; no original ARM/Swift execution
was performed. Per-file provenance is in `SOURCE_PROFILE_CRITERIA_PROVENANCE.json`.

## 1. Closed: source default requested-style order

Registry `2722461cc` calls constructor `272243e0c`. In that constructor the BM state
receives a reference to the static array at `2884aa5e8` through the first strategy
record. The captured array has count 3 at +0x10 and signed-64 elements at +0x20:

```
8, 9, 12
```

This is not an inference from the switch in `beatMatchedStyleScore`, a sort of IDs,
or the order of records in TransitionStyles.json. `plannerDefaultBeatMatchedStyleIds`
is the C++ source of these requests for this investigated image. It returns an immutable
array and never mutates an explicit request. It does NOT create internal style records,
assign maximumBarCount, or authorize the BM strategy.

Default observation now reports missing-binding mask **6** (catalog + complete context),
not 7. Its status remains NEEDS_SOURCE_BINDINGS, with no winner and canExecute=false.
The explicit resolved path and candidate ordering remain unchanged.

## 2. Closed subset: Criteria start-time ranges

`272213b68` calls `272258148` (outgoing) and `272255b88` (incoming). For finite inputs:

| Input | Resolved start-time range |
|---|---|
| Incoming after(t) | [t, duration], provided t is in [0,duration] |
| Incoming within(a,b) | [a,b], provided both endpoints are in [0,duration] |
| Incoming inSong | [+0.0, duration] |
| Outgoing early-after(t) | [t, duration], provided t is in [0,duration] |

Native functions preserve endpoint bits (including -0.0 copied from inputs), fractional
seconds and exact inclusivity. Equal endpoints and zero duration are valid normalization
inputs; they do not create a nonzero transition or permission to process audio. No epsilon,
clamping, milliseconds conversion, fixed eight-second window or catalog-duration fallback
is introduced.

**Host-domain rules:** nonfinite/negative inputs, reversed Within and missing duration are
explicit results without a range. Checking Within ordering is an LMG input-construction
invariant, not a newly attributed comparison inside 272255b88. Validating supplied payloads
before returning missing-duration is also host behavior. Types use std::variant instead
of accepting unknown raw Swift enum bytes.

Outgoing late-after/late-inSong return `latePlacementUnresolved`; the branch delegates to
`272259834`, whose body is absent. The early-after upper-bound rule is not applied to late
payloads as if it were confirmed there. These helpers do NOT form ResolvedPlannerScope:
provider discovery bounds, previous playback state and strategy gates are still separate.
They are compiled/tested native APIs, not newly activated automatic service constraints.

## 3. Wire v2 and stale-library protection

The request remains version1 (24..80 words). The response stays 48 words but is now
version2; its formerly reserved slots are:

- word44: profile count=3;
- words45..47: native known default request IDs 8,9,12.

They are present on default, explicit-success and explicit-failure reports. Kotlin validates
the exact finite protocol, copies the profile into an unmodifiable List, and exposes
`PlannerSelectionReport.knownBeatMatchedStyleIds`. This metadata is not substituted for
an explicit scope's order/duplicates/empty request list.

The internal JNI export is deliberately versioned `selectResolvedPairV2`. The old export
is not used as a fallback. A stale .so fails closed instead of accepting an old default
mask/protocol. `automix_verify_jni_export.py` now requires a defined dynamic V2 function
symbol; the tests also compile genuine V1 ELF32/ELF64 fixtures and reject them. New APK
assembly is mandatory, even when JVM test reports are fresh. Public AudioService methods,
generation and binding-revision checks are unchanged.

## 4. Exact remaining edges, not another unrestricted corpus request

The raw conformance record at 27229a8a8 is referenced by 27224ecd4 from the catalog decode
chain `27224e480 -> 27224e814`. Its signed relative field at **27229a8c0** resolves to
**27224eab0**. That witness body is absent from all supplied text and raw windows.
Consequently raw JSON duration -> maximumBarCount remains unproven. The observed 8/8/16
JSON numbers and prior explicit test descriptors do not close the decoder mapping.

The MusicKit provider chosen in 27222a4b4 has witness table **2884ace78**. Captured entries:

| Witness slot | Target | Consumer role established at caller |
|---|---|---|
| +0x18 | 2722246f8 | outgoing minimum start; reused by discovery |
| +0x20 | 272224708 | outgoing minimum end |
| +0x28 | 272224718 | incoming maximum end; reused by discovery |

All three getter bodies are absent. The constructor contains duration-envelope-derived
fields, but selecting which field belongs to which getter without their bodies would be
guessing. `2722666d0/2722666e4` only copy the 17-byte previous-state representation; their
presence alone does not establish its timing semantics. Late helper `272259834` is absent.

The mapped segments' file extent is 1,063,640 bytes, approximately 1 MiB. The exact extracted
Mach-O target, not the full firmware/archive, is the next useful input:
`/srv/research/apple-music-ios26/decompiled_package/targets/_SonicKit_MusicKit_Packages`.
Verify its full SHA-256 above. Further direct dependencies may exist inside that image;
this report does not promise that five leaf functions exhaust every remaining context gate.

## 5. Verification boundaries

New native tests: 5 profile groups and 20 Criteria groups, including an exhaustive finite
interval matrix and nextafter neighbors. Existing 24 binding groups keep their candidate
expectations. Twelve real C++ response snapshots are decoded in Kotlin; 261 malformed
variants are rejected. The unchanged 16 binding lifecycle scenarios and 20 Stage2 lifecycle
scenarios remain separate from source arithmetic tests.

New source mutations cover profile order, remaining blockers, endpoint inclusivity, Within
upper bound, missing duration, invented late ranges, nonfinite values and signed zero.
GCC and Clang ASan/UBSan execute actual needed native modules. No substitute DSP or provider
stub is linked into the composition tests. Source metadata transport is tested, not actual
Apple ARM instructions. Two new JUnit cases require real JNI on the server: ordered immutable
profile and preservation of an explicitly different request order.

Host regression scripts do not execute the new positive raw-JSON/JNI selection path or build
an Android APK. JSON/JNI files are object-compiled, while Gradle/JUnit, full CTest and device
checks are server gates. Expected acceptance after this patch: **55 CTest, 31 real JNI,
36 lifecycle tests**, all three target ABIs and unchanged canonical catalog checksum.
