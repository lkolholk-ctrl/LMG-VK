# Raw MediaAPI song-analysis ingestion

`decodeMediaApiSongAnalysis` implements bounded JSON ingestion of a requested
catalog song and its `audio-analysis` / `flexml-analysis` relationships. This is
the raw cloud transport representation, **not** normalized MusicKit analysis or
a pairwise transition recipe. It performs no HTTP request or authorization work.

## Evidence and corrections to the illustrative Kotlin contract

The local response `/tmp/lmg-apple-analysis-1488408568.json` provides the actual
wire schema. The response was inspected and used as an optional local test input;
its complete analysis was not copied into repository fixtures. The existing
research contract `kotlin_contracts/analysis/SongAnalysis.kt` describes later
internal models and contains assumptions that are not valid for this wire layer:

- `loudnessCurve` can contain `value` without `samplingFrequency`. The decoder
  retains absent frequency. It does not infer 2 Hz or derive a rate from duration.
- `vocalActivity[].strength` is a string, and `kind` can be absent. Neither field
  is mapped to a numerical category or synthesized from the other.
- Pivot `gainTimeInSeconds` and `gainValue` are arrays. `fadeToBlack` is numeric
  in the observed exit point. Time, gain arrays and tags are independently optional.
- Cloud BPM remains numeric; no integer truncation or deviation reinterpretation
  is performed here.
- Cloud beats retain `beatsInMilliseconds` and `barsInMilliseconds` with their
  original units. They are not asserted to be the planner's beat-grid input.
- `visualTempo.samplingFrequency` can be -1. Raw ingestion preserves that value;
  it does not enforce a guessed positive-frequency semantic rule.

The decoder also preserves composite key/acousticness/danceability/melodicness,
energy, valence and loudness statistics; fades, phrases, vocal ranges; flex
arousal/valence/visual tempo, video-event arrays, entry/exit points; song duration,
ISRC, support flag and genre names. Strings stay strings, including unfamiliar
keys, modes, vocal categories and pivot tags. No enum membership is assumed.

## Selection, relationships and validation

The caller supplies the requested song ID. Exactly its `type=songs` resource
must exist in top-level `data`; another song is never silently substituted.
Duplicate resource identities in `data` or `included` are rejected. An analysis
relationship accepts zero or one resource. Embedded attributes are decoded
directly; linkage can resolve through `included` using the exact `(type,id)` pair.
Conflicting embedded and included attributes are rejected. Unresolved linkage
retains the resource ID with an absent attributes payload.

Known optional fields distinguish absent values from explicit empty arrays.
Missing and JSON `null` are both represented as unavailable. Presence of an
attribute with a wrong type is rejected. A false supportsSmartTransitions value
remains present false, distinct from unknown. Analysis resource IDs are not
assumed equal to the song ID: the relationship itself establishes association.

The parser limits input to 4 MiB and nesting to 32, rejects duplicate JSON keys,
invalid syntax, nonfinite numeric values and type mismatches. Unknown catalog
fields are tolerated, while syntax/depth/duplicate-key checks cover the entire
document. These bounds and ambiguity rejections are LMG transport safeguards,
not claims about Apple's original decoder behavior.

Semantic normalization belongs in a later, separately evidenced layer. This
decoder deliberately does not reorder arrays, reconcile unequal parallel-array
lengths, clamp scores/times, convert units, synthesize defaults or decide planner
eligibility. Consumers must validate the invariants needed by their algorithms.

## Verification

On 2026-09-16 GCC 14.2 compiled `media_analysis.cpp` with
`media_analysis_test.cpp`, `-Wall -Wextra -Wpedantic` and ASan/UBSan. The test passed
with the actual local captured response and synthetic fixtures covering:

- inline/included/unresolved relationships and exact requested-ID selection;
- absent/null versus empty data, false support flags and fractional values;
- wire-specific vocal/pivot/frequency handling and unchanged units;
- duplicate keys/identities, conflicting resources, wrong types, numeric overflow,
  invalid relationship types, missing song, input size and nesting bounds.

No Android build, emulator, endpoint request or token exposure was involved.
