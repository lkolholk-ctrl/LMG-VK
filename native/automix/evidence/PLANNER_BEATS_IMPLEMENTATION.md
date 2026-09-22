# MusicKit SongStructure: authoritative beat source and complete grid

The previously unresolved MusicKit planner beat source is **Flex video events**,
not `audio-analysis.beats`. `planner_beats.cpp` now implements the complete
recovered branch: hierarchical events, section normalization, event reclassification,
adjacent bar/segment/section regions and the beat-stability map.

## Source chain

1. `2722200cc` calls `2722231b4` to construct optional SongStructure.
2. `2722231b4` reads MusicKitAnalysis.flexAnalysis through metadata field offset
   `+0x1c`; missing Flex analysis produces absent SongStructure.
3. Its import `2743dd140` resolves through actual dyld branch-island bytes to
   `1d3e81a84`, **FlexAnalysis.events**. This is the same internal event array
   produced from cloud videoEvents by the verified `planner_flex` adapter.
4. `272225ef4` maps these events to SongStructureEvent records. Imports
   `2743dd100/120` resolve to Event.time (`1d3e83244`) and Event.timeScale
   (`1d3e83248`). No cloud audio-analysis.beats/BPM getter is needed.
5. `27221bd50` constructs the initial SongStructure; `27221cb94` selects normalized
   section boundaries; `27221cca4` reclassifies/reindexes events and calls
   `27221bd50` again to construct the final structure.

These are direct call/data-flow observations, not an inference from unrelated
beat getter names. Imported enum constants were dereferenced through cached
slide pointers to original values 0/1/2/3 in MusicKitInternal. The exact import
targets and source hashes are recorded in `planner_beats/reference.json`.

Swift field metadata independently identifies the models:

- `272293e90`: SongStructureEvent cases `beat`, `downbeat`, `segmentBoundary`,
  `sectionBoundary`, in tag order.
- `272293df8/3e44/3ed0/3f1c`: event fields `songTime`, `beatIndex`,
  `downbeatIndex`, `segmentIndex`, `sectionIndex`.
- `272294238`: SongStructure fields `events`, `beatEvents`, `downbeatEvents`,
  `segmentBoundaryEvents`, `sectionBoundaryEvents`, `bars`, `segments`, `sections`,
  `beatStabilityMap`.

## Hierarchy and section normalization

Flex short/medium/long/extra-long become beat/downbeat/segment/section events.
Every event advances the beat index from zero. A downbeat or higher advances the
downbeat index; a segment or higher advances the segment index; sections advance
the section index. Before its first corresponding boundary each higher index is
absent. Time is copied; Flex amplitude is unused by this source path.

`27221cb94` normalizes section placement in this exact sequence:

1. Pair adjacent original sections and find the first whose **downbeat-index**
   difference is divisible by four. Use its starting section as the reference;
   otherwise use the first section (`27221c9b8`, `27221b928`).
2. Use reference.downbeatIndex modulo four as phase, or zero without a section.
   Select downbeats with nonnegative `(downbeatIndex - phase)` divisible by four
   (`27221c864`, `27221d4b0`). This is a four-bar section alignment, not a claim
   that each bar has four beats.
3. For each original section, choose the last candidate at/before its time and
   first candidate at/after it. If both exist, choose the later one when
   `(laterTime - sectionTime) - 1e-9 <= sectionTime - earlierTime`
   (`27221dac0`). These are first/last matches in existing order, not a new sort.
4. Retain the original section if no candidate exists or the chosen downbeat
   already belongs to an original section (`27221d5f4`). Keep only strictly
   increasing downbeat-index selections (`27221d858`).
5. Rebuild the hierarchy (`27221cca4`). Selected downbeats become section
   boundaries. Unselected original sections become ordinary downbeats. Original
   segment boundaries remain segments unless promoted. Beat/downbeat identities
   remain; segment and section indices are recomputed.

All event views include higher-level conforming events: every event is a beat;
segments and sections are also downbeats; sections are also segment boundaries.
Bars, segments and sections pair adjacent respective boundary events. The native
model stores event indices instead of Swift existential copies; references point
to the same recovered event fields. No leading/trailing synthetic regions are added.

## Beat stability and tempo

`272218400` consumes adjacent bars. A run continues when its beats-per-bar count
is unchanged and the next bar duration differs from the current running mean by
at most **0.040000001 seconds** (raw double `0x1.47ae150451a6fp-5`). This compares
bar durations, not vocal timing or individual beat error. The running mean is the
ordered sum of bar durations divided by run size (`2722191e0`). Only runs with at
least **five bars** become stability regions. Region endpoints are the first
bar's start and last bar's end.

The tempo calculation at `2722196e0` is performed in this operation order:
`beatDuration = meanBarDuration / beatsPerBar`; `bpm = 60 / beatDuration`;
`FRINTX(bpm + bpm) * 0.5`. FRINTX follows the current floating-point rounding mode
(default nearest-even). C++ uses nearbyint, preserves this order and disables
floating-point contraction. This replaces an unsupported assumption that the
other research tolerance `0.031` directly specified this map.

## Verification and bounds

The reference harness executes original ARM instructions for the complete
hierarchy, section selector/rebuild and stability builder. It replaces Swift
metadata, retain/release, object copying/allocation, array storage and field
getters with bounded test representations, and removes pointer authentication.
It does not replace the selection, index arithmetic, distance comparisons,
averaging, thresholds or rounding decisions. The final rebuild's structure
allocation is intercepted to capture its raw event array; the stability builder
is independently executed from synthetic original-layout bars.

ASan/UBSan C++ comparisons passed:

- 71 initial hierarchy cases, 2,214 original events;
- 172 complete section-normalization/rebuild cases, 5,671 original events,
  including duplicate and non-monotonic times;
- 134 original stability-map cases, comparing endpoints, meter and tempo bitwise.

Fixtures contain synthetic data, not copied song analysis. Original raw padding
bytes are ignored in comparisons. Reproduction:
`python3 native/automix/tools/planner_beats_reference.py --output <directory>`.
The harness requires the already extracted local Packages Mach-O, Unicorn and
Capstone. It performs no network requests and launches no Android emulator.

Host guards reject nonfinite event times, invalid scale tags and unrepresentable
event counts. No amplitude threshold, interpolated beat, guessed tempo, audio
cloud beat substitution, resampling or PCM scheduling is introduced. The caller
retains optional Flex/SongStructure availability; a present empty Flex array
produces a present empty structure.
