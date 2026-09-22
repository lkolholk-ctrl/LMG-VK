# Tonality relationship recovered

`plannerTonalityRelationship` ports 27221eb58 and helper 27221ec70. It accepts
native tonic/mode enum bytes, not cloud strings. Missing tonality yields tag 3
as 272233fac does. Identical bytes yield tag 0 before other checks. Different
modes 0/1 match at identical table indices (tag 1); equal modes 0/1 match at
adjacent indices including 11/0 (tag 2); other cases yield 3.

Raw lookup pairs at 2884aa480 (mode 0) and 2884aa4b8 (mode 1):

- Mode 0 tonic order: 4,11,6,1,8,3,10,5,0,7,2,9.
- Mode 1 tonic order: 1,8,3,10,5,0,7,2,9,4,11,6.

`tools/planner_tonality_reference.py` executes both original functions against
these original data pages, patching only PAC prologue and stopping before the
return epilogue. All 1764 combinations of tonic 0–13 and mode 0–2 match. This
includes unknown enum combinations and the early identical-byte return.

Important correction to the older Kotlin contract: 272231dd0 calls outgoing
melodicness, and 272231dd4 branches to check incoming ONLY if outgoing returned
false. Therefore incompatible keys are accepted when EITHER melodicness is
insignificant, not only when both are. Helper 2722341d4 returns false for absent
melodicness, otherwise value <0.25 OR value >1. It does not clamp values to [0,1].
`plannerTonalitiesCompatible` preserves this short-circuit behavior.

The planner test covers threshold neighbors, missing values and each side of
the OR independently. Host ASan/UBSan passed with leak detection disabled.
Cloud-to-native tonic/mode normalization and selection of beginning/end/main
analysis components remain separate; this document does not claim those are
resolved or that the complete transition planner is ready.

## Cloud tonic conversion trace (partial; no planner binding assumed)

The MusicKit cloud converter 1d41222b8 calls 1d4257f74 for tonic conversion.
The latter compares 21 cloud enum values and returns ordinal 0..20; unknown or
missing tonic becomes 21. Actual cloud getter instructions at
21608e078..21608e120 embed these ASCII spellings in order:
Ab,A,A#,Bb,B,B#,Cb,C,C#,Db,D,D#,Eb,E,E#,Fb,F,F#,Gb,G,G#.
`planner/cloud_tonic_strings.json` preserves the getter addresses, literal
bytes and code hash; bounded disassembly and converter listings accompany it.

This proves MusicKit's intermediate representation has 21 tonic values, whereas
the planner relationship tables use 12. Passing the intermediate ordinal
straight to PlannerTonality would therefore be wrong. The intervening
normalization and beginning/ending/main selection are not yet traced. Do not
connect CloudKey directly using a guessed pitch-class mapping. Package import
stubs at 2743ddxxx are outside the extracted package Mach-O segments; a further
trace must use the matching shared-cache mapping or the provider witness table.
