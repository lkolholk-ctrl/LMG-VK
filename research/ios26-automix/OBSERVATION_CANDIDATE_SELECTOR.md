# Stage 3b continuation: native candidate selection

Baseline: `01a7a2fc25e04f1c2981edc0e58f0ad7b65755f9`.

## What changed

The new C++ APIs are `selectPlannerRegionCandidates` and `selectPreparedPlannerCandidates`. They connect explicit, ordered source seed pairs to style-specific region transformations, native predicates, existing scoring, and first-on-tie winner selection. IDs 8, 9 and 12 are supported by this BeatMatched builder.

The preparation adapter reads the full maps in `PlannerSongPreparation`, not `stablePreview`. It retains no handles and produces a non-executable value result. `planner_scoring.cpp`, `AudioService`, Kotlin, JNI, Gradle, Media3 and the existing PCM path are unchanged.

## Calling contract

The control-thread caller supplies:

- The two immutable preparation objects associated with the current exact recording pair.
- Ordered seed event references after the upstream source scaling/truncation driver.
- Source internal style descriptors with optional suffix bar count; these are not invented from JSON duration.
- Resolved tonal provider values, not compatibility booleans.
- Resolved placement bounds and playback durations, not catalog-duration fallbacks.

The API computes compatibility, matching bars, vocal predicates, loudness ratio, endpoint delta and score in C++. It returns a winner or an explicit incomplete/unavailable/nonpositive outcome. `completeForProvidedSeeds` is deliberately scope-limited; `canExecute` is always false.

`native/automix/tests/planner_candidate_prepared_test.cpp` is a runnable synthetic example through the actual native preparation path. It must not be copied as an automatic production seed-selection or main-key binding policy.

## Current runtime remains observation/preparation only

This patch does not add a JNI entry point or attach the selector to `AudioService`. The current preparation transport continues to report no executable selection. The native selector is ready to be called once the upstream source producers and their ownership/generation binding are integrated. No extra archive collection is required by this patch.

## Checks

```bash
bash scripts/automix_candidate_selector_host_checks.sh
python3 scripts/automix_candidate_mutation_checks.py

# Separate sanitizer build; no JVM loading of sanitized libraries is attempted.
CXX=clang++ AUTOMIX_CANDIDATE_SANITIZERS=1 \
  ASAN_OPTIONS=detect_leaks=1:halt_on_error=1 \
  UBSAN_OPTIONS=halt_on_error=1 \
  bash scripts/automix_candidate_selector_host_checks.sh
```

Full server verification adds three CTests to the previous 45: `automix_candidate_selector`, `automix_candidate_predicate_reference`, `automix_candidate_prepared`. Existing 17 real-JNI and 20 lifecycle tests remain mandatory but are not replaced or renumbered by a native-only change.

See `native/automix/evidence/PLANNER_CANDIDATE_SELECTOR_IMPLEMENTATION.md` and its source manifest for recovered behavior, remaining boundaries, hashes and host-domain restrictions.
