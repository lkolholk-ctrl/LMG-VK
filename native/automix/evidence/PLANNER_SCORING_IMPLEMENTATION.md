# Planner tempo matching and scoring

Implemented `planner_scoring.h/.cpp`, independently of analysis parsing, region
selection, and app playback. Source: `_SonicKit_MusicKit_Packages`, iOS 23A341.
All original instruction fixtures include hashes in `tests/fixtures/planner_scoring.json`.

## Tempo matching

`matchPlannerTempos(reference, candidate, tolerance)` implements the arithmetic
and source result classification of `272219ff0`, with binary-scale search
`27221a300`, scaling `27221a60c`, and equality `27221a65c`.

Two corrections to the older cleanroom report are essential:

* Input tag bit 0 denotes **tempo representation**, not optional presence.
  Tag 0 contains BPM; tag 1 contains seconds per beat. Missing tempo handling is
  in upstream callers and is not fabricated by this API.
* Binary-scale selection minimizes **absolute natural-log distance** between
  the BPM representations. The import at `2743ddb20` is `log`. This is not
  absolute BPM difference, nor a linear BPM-ratio tolerance.

Candidate binary scales are visited half, one, two. BPM values are multiplied
by 0.5/1/2; seconds-per-beat values are divided by the same factors. The selected
scale is the first with the minimum log distance (strict comparison). Equality
compares original values when the representations agree; otherwise only the
seconds-per-beat value is converted to BPM. Exact equality produces source tags
0/1/2. Otherwise log distance <= tolerance produces 0x80/0x81/0x82; failure is
0xfc. Normal/expanded caller constants are 0.16 and 0.287 respectively.

Host-domain validation requires positive finite tempos, representable positive
converted values, and nonnegative finite tolerance. This does not claim source
NaN/overflow behavior. `PlannerTempoMatch.scale` retains the search result even
on failure for diagnostics; original 0xfc carries no scale payload.

## Candidate scoring and selection

`plannerCandidateScore` reproduces `27222d644`: left-to-right binary64
multiplication starting at base; only a positive product receives the separately
multiplied `tieBreakDelta * 0.001` and separate addition. There is no clamp to
base or to 0. The unknown physical meaning of this field difference remains
explicit in the API. Compile this translation unit with `-ffp-contract=off`.

`bestPlannerCandidate` implements the finite-value portion of `27222e800`:
reject score <= 0, choose the highest score, retain first candidate on ties.
Nonfinite input and nonfinite score arithmetic are explicit host errors.

`beatMatchedStyleScore` accepts **already recovered/computed** region predicate
results, not raw Apple JSON. It mirrors the weights and order in `272234380`:

| ID | Base | Weight order |
|---|---:|---|
| 8 | 10 | normal tempo pass, leading vocal penalty, bar ratio (missing=0), trailing loudness ratio (missing=1) |
| 9 | 15 | normal tempo pass, tonality pass, matching bars >=8, NOT vocal-relationship-incompatible, leading vocal penalty, trailing ratio |
| 12 | 10 | normal tempo FAIL AND expanded tempo PASS, matching bars >=8, leading vocal penalty, trailing ratio |

Leading significant incoming vocals yield 0.75, otherwise 1. Unknown matching
bar count fails. Other style IDs return no candidate, consistent with this
specific BM builder; this says nothing about their non-BM strategy consumers.

**Correction:** style 12 requires normal tempo **incompatibility**, proven by
`272234934..948`: expanded-compatible weight is selected only when normal source
tag is above 0xfb. This reverses the misleading shorthand in the older report.

## Verification

`tools/planner_scoring_reference.py` executes original ARM64 instructions for
3240 tempo cases and 306 score cases. Tempo cases cover both representations,
half/unity/double scaling, exact and inexact matches, normal/expanded tolerances,
nextafter neighbors at thresholds, and deterministic random tempos. Scores
cover zero and negative products, negative final scores after adjustment,
missing weights, signed zero, and random ordered factors. All C++ results match
source tags or score bits exactly under ASan/UBSan.

Hooks replace Swift array allocation/refcounts and `log` with host libm.
Original arithmetic and comparisons execute unmodified. PAC instructions alone
are patched. No Android emulator or full OS is run. The fixture does not prove
Apple libm bit identity against host libm; it proves the surrounding tempo
algorithm with the same math implementation on each side.

Style-specific weight assembly and winner behavior have targeted boundary tests
and raw disassembly evidence; unlike tempo/scalar score they do not yet have
full original Swift candidate-builder fixtures.

## Still required for a complete planner

Region generation, analysis-to-internal tempo/beat/vocal mappings, predicate
calculation, complete non-BM strategy selection, and final schedule construction
remain separate tasks. This module must not be presented as a complete planner
or a working app AutoMix integration. No crossfade, Kotlin, JNI, app, or fork
code was changed.
