# Planner complexity eligibility

Implemented in `planner_scoring`: algorithm values Fallback=0, DeadAir=1,
Smart=2, BeatMatched=3; independent of style IDs 8/9/12.

Source evidence is P2_AUTOMIX_CLOSURE.md section 2 and disassembly functions
272224eac (duration confidence), 272225810 (spatial drift), 272225204 (spatial
reduced set), 27222a728 (maximum complexity and membership in both song sets).
The caller supplies the resolved not-downloaded flag, spatial drift optional
and close-match confidence; raw catalog fields are not guessed into these.

Duration confidence uses strict abs(expected-actual)<2, with an unconditional
high-confidence not-downloaded branch. The drift comparison is <=0.04 with no
absolute-value conversion. None/low/high duration tables retain source order
{0}/{0,1,2}/{0,1,2,3}; spatial low-close/high-drift allows {0,3}, both-high
allows all, otherwise only {0}. Empty per-song sets reject all algorithms.

The existing planner-scoring test now checks threshold boundaries, absent
analysis, source table contents, both-song membership and maximum complexity.
Host ASan/UBSan passed (leak detection disabled). This adds eligibility rules;
it does not implement the still missing complete strategy coordinator.

## Default catalog order recovered directly

The older Kotlin contract leaves order UNKNOWN. The extracted binary resolves
it: constructor 2722461cc allocates four 40-byte existential entries (array count
4, offsets 0x20, 0x48, 0x70, 0x98) in this order:

| Entry | Protocol table | +0x20 thunk | Strategy implementation |
|---|---|---|---|
| 0 | 2884ad878 | 272243f7c | 27224241c BeatMatched |
| 1 | 2884ada88 | 2722460a8 | 272245764 Smart |
| 2 | 2884ad928 | 2722450e0 | 272244aa4 DeadAir |
| 3 | 2884ad9d8 | 272245750 | 27224522c Fallback |

Raw table bytes/pointers are saved in `planner/catalog_witness_tables.json`.
Constructor, filtering and dispatch listings are saved alongside it.
27224670c walks the input entries in order and appends only those for which the
predicate succeeds. Predicate 2722474a8 dispatches the +0x20 protocol method.
2722469f0 then walks that filtered sequence, invokes +0x28 and stops at the first
nonempty styling result. Thus enum-value sorting would be incorrect. Eligibility
filtering precedes styling; this evidence does not justify a global score sort
across all four algorithms.

`kPlannerAlgorithmOrder` and `plannerEligibleAlgorithms` preserve this order
while applying the implemented complexity gate only. Other strategy predicates
and styling construction remain separate work. Tests cover all four, reduced
complexity and empty-set paths; they do not claim a full transition coordinator.

## Catalog candidate selection

`selectPlannerCatalogCandidate` connects the catalog order to the existing
per-strategy winner selection. Inputs are complete strategy eligibility results
and candidate scores indexed by algorithm complexity; these are explicit
resolved inputs, not substituted defaults for missing analysis. 27223e4f0
filters scores <=0, then retains the first maximum (strict replacement only).
2722469f0 stops on the first strategy returning a nonempty result. Therefore a
small positive BeatMatched result wins over even a much larger later Smart
score. Only failing/empty/nonpositive strategies permit the next strategy.

Tests exercise this distinction, equal-score stability, disabled strategies,
empty candidates, fallback and complete failure. This function selects a
candidate index; construction of all candidate regions/automations and final
transition rendering remain unfinished.
