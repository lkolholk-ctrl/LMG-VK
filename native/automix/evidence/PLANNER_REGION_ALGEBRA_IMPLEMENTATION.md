# Stage 3b: recovered region operations (not a complete selector)

Base: `c47d129a87dc2f9810c60b92b568ce0e09bfe1ea`.
Input: `automix-stage3-region-evidence.zip`, SHA-256
`b94c277d51d910401d57e8da37e2a7cf9e21a210c0218bd87a4389f36c184aee`.
All 64 manifest-listed text files (510832 bytes) matched their supplied hashes.
This establishes package consistency, not independent authenticity of an Apple build.

Implementation: `planner_region_algebra.h/.cpp`, namespace
`lmg::automix::region_algebra`. No new JNI entry point or player integration.

## Recovered operations and their boundaries

| Source | Ported behavior | Boundary not reproduced here |
|---|---|---|
| `272234e14` | First matching downbeat at `max(0, oldFirst, last-requestedBars)`; original end retained | Swift generic conversion `27222be34` |
| `272234380:2722343dc..2722343ec` | Missing optional bar count uses 4; explicit zero is not missing | Binding that internal field to the JSON style schema |
| `272233888` | Keep incoming end; locate beat ordinal `last-min(sourceCount,budget)` | Swift beat conversion/count getter |
| `2722335b4` | Incoming suffix with integer-halved source beat count | Same projection/getter boundary |
| `272233804` | Count uses inverse tempo scaling: half -> x2, one -> unchanged, two -> integer /2 | Choosing the scale from regions |
| `272231b68` | Keep outgoing, truncate incoming using its computed count budget, preserve scale tag | Generation/eligibility of the supplied seed pair |
| `272234b0c` | Take outgoing style suffix, then constrain incoming with inverse-scaled beat budget | Generic conversions and upstream seed selection |
| `27223c3ac` | Clip outgoing by song end, require at least 2 seconds, rebuild incoming end from incoming start | Strategy-specific choice of the initial time pair |
| `27222919c`, `272229758` | Inclusive minimum outgoing start/end and maximum incoming end checks | Resolving bounds from placement criteria and previous playback state |
| `27223c184` | Later of two present incoming starts; non-silent start wins equality; `duration <= maxEnd-start` | Downbeat/non-silent start getters `27223d324`/`27223d6cc` |

The implementation is newly written C++; it does not paste decompiler output.
The pseudocode's pointer-valued temporaries are not accepted as semantic types.
The scalar rules above were cross-checked with the supplied disassembly.

## Host adaptation, explicitly not a source-fidelity claim

`PlannerRegionRef` binds endpoints to one immutable caller-owned `SongStructure`.
It holds no ownership and must never outlive, move with, or be used concurrently
with mutation of its owner. Rvalue construction is deleted. Functions revalidate
references, ordinal domains, collection membership, event kinds and finite times.
Each operation is bounded to 4096 events/collection entries (LMG work limit).

Beat and downbeat ordinals use the existing normalized `StructureEvent` fields.
Ordinal count is their end-minus-start difference. The primitive scalar tests
start AFTER source getters: they do not prove the Swift getter implementation or
all cases of its generic region conversion. A bar view can project to a beat view
only through identical endpoint references already present in `beatEvents`.
There is no automatic beat-to-bar conversion, nearest event, snapping, tempo-derived
grid, inferred phase, interpolation, sorting, or deduplication.

Invalid/nonfinite/reversed host input throws; missing exact lookup returns no
region. Negative host ordinals/counts are rejected. Integer overflow never wraps.
These guards are not claims about Apple's trapping/nonfinite domains. Zero-length
results permitted by the source arithmetic are retained, NOT called eligible.

The first matching entry is retained even with duplicate ordinals. A later duplicate
is not preferred for prettier timing. Output carries the original end-event identity.
The diagnostic 16-region cap from Stage 3a is never used as a candidate search cap.

The time-pair functions intentionally differ: `plannerTruncateStylingTimePair`
preserves incoming START, whereas beat suffix truncation preserves incoming END.
The 2-second minimum belongs only to the former. `plannerIncomingStylingTimeRange`
uses subtraction before comparison; rewriting it as `start+duration <= maxEnd`
changes behavior at binary64 rounding boundaries. Equal incoming start choices
retain the non-silent start's signed-zero bit. Equal clipped outgoing end retains
its original bits. No extra cap is derived from the old incoming time-range end.

## Verification

`tests/planner_region_algebra_test.cpp` contains 43 scenario groups, including:
existing cloud Flex normalization -> existing SongStructure -> new region operations;
owner/end-reference preservation; inverse scaling; odd counts; explicit/missing zero;
non-four-beat meter; exact lookup failure; duplicate and reordered collections;
invalid kinds/refs/domains; limits and signed64 overflow; time/placement boundaries;
819 seed/count/scale combinations inside the final sweep group.

`tests/fixtures/planner_region_instruction_slices.json` retains 30 scalar instruction
lines, original addresses, source paths and SHA-256s for four disassembly files.
`tools/planner_region_reference.py` replays these TEXT instructions and flags with
explicit getter inputs and skipped object-management boundaries. Its 1657 frozen
rows are compared with C++ in the second CTest. It does NOT execute the original
ARM binary, Swift runtime, protocol witnesses, whole source functions, or an iOS OS.
The optional `--evidence-zip` additionally checks the slices against the supplied ZIP.

Targeted checks passed with GCC/O2 and Clang/ASan+UBSan (including leak detection).
A temporary targeted CMake project ran both new CTests using the actual three
implementation files (algebra, beats, flex), not mock functions. Six deliberate
behavior mutations were detected by the scenario tests. These results do not
constitute a full 45-suite CTest, Android/NDK build, JNI rerun or device verification.

## Remaining source dependencies: why selection is still disabled

The original collector selected 29 named functions plus six reports, but did not
follow the bodies they call. The package therefore contains callers for important
steps without those callee implementations. In particular:

- Seed enumeration/order: `27222f54c`, `27222fa50`, `27222fd74` and filtering helpers.
- Stable suffix and tempo extraction: `272218ff0`, `2722324d0`, `272232a90`, `272232c24`.
- Beat/bar/protocol conversion and source count getters.
- Region-based predicates: `27223550c`, `272235d08`, `272236188`, `272236498` and leading-window selection.
- Shifted region closures: `2722378a0`, `27223775c`; style 12's caller shifts by 16,
  which must not be replaced by the ordinary unshifted pair operation.
- `27222d51c` and its conversion chain: a report describes a difference of two
  Double fields; this is NOT permission to replace the missing mapping with zero,
  track position, candidate order, or an assumed end-time difference.

Some related disassemblies also exist in the repository's `evidence/planner` tree.
The dependency request describes a coherent corpus to collect, NOT 52 claims of
global inaccessibility. `research/ios26-automix/stage3b/required-functions.json`
records requested entries and observed call sites in the supplied archive.
The new collector follows bounded direct calls and named callbacks, includes all
matching versions rather than choosing one silently, and reports missing bodies
and its depth frontier. File availability alone still does not prove semantics.

No strategy is marked eligible, no score is fabricated, no style is selected,
and `planner_scoring.cpp` is unchanged. `REGION_PAIR_RULES_UNVERIFIED` remains the
production Stage 3a result until generation, predicates, placement and selection
are implemented and independently checked. This patch is PART of Stage 3b.
