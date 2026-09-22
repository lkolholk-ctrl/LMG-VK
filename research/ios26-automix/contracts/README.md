# kotlin_contracts — clean-room Kotlin contract sources for iOS 26 Apple Music features

**Package scope:** pure Kotlin (JVM) contract + skeleton sources. No Android/Compose/Media3 imports
are required. This directory is the deliverable of the "Kotlin Contract Author" role, derived
exclusively from the research results located under
`/srv/research/apple-music-ios26/deepseek_analysis/`.

**Archive:** `kotlin_contracts_ios26_apple_music_v1.tar.gz` (created from the parent directory
`/srv/research/apple-music-ios26/`, outside `/root/LMG-VK`).

**Kotlin version note:** sources are written against Kotlin 2.x and compile with `kotlinc-jvm 2.1.0`
(JRE 21). Only `kotlin.math` from the standard library is used; no third-party dependencies.

---

## 1. Disclaimer (read first)

- This is a **clean-room contract derived from observed behavior** (disassembly, pseudocode, raw
  resources, symbols, live JSON). **No Apple source code was copied**; no Apple binary is distributed.
- Values are re-expressed as Kotlin constants/functions because they were proven by raw artifacts.
  The value-level provenance is carried in KDoc as `report path + status`.
- **No invented values.** Where the research marks a value as unknown, this code contains
  `TODO("STATUS: UNKNOWN — needs runtime/device: ...")` instead of a plausible number.
- This code is a **skeleton/spec**: functions for unresolved behavior throw `TODO()` at runtime.
  It is intended as the compilation reference for a port, not as a runnable player.
- `/root/LMG-VK` was not modified, no git operations were performed, and no code was copied from it.

## 2. Status legend (as used in every file's KDoc)

| Status | Meaning |
|---|---|
| `[EXACT]` | directly confirmed by a raw artifact (address / bytes / symbol / resource / live JSON). |
| `[STRONG_INFERENCE]` | follows from several independent confirmed artifacts. |
| `[PARTIAL]` | part of the chain confirmed, part not; must not be presented as fully proven. |
| `[UNKNOWN]` | no data. In code: `TODO("STATUS: UNKNOWN — needs runtime/device: ...")`. |
| `[UNUSED BY PLANNER]` | verified consumer-negative (field exists in MediaAPI but the planner never reads it). |
| `[PORT DESIGN]` | a port-side decision; Apple equivalent absent or unproven. |

## 3. Source reports (provenance of every Kotlin file)

Paths are relative to `/srv/research/apple-music-ios26/deepseek_analysis/` (reports) and
`/srv/research/apple-music-ios26/appos/` (raw resources) unless absolute.

| Kotlin file | Provenance (report paths) |
|---|---|
| `analysis/SongAnalysis.kt` | `10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md`; `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` §1.1; `10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md`; `10_final_closure/FINAL_BLOCKED_QUESTIONS.md` §3–§4; `09_appos/APPOS_MASTER_SUMMARY.md` §5 |
| `automix/TransitionPlannerSpec.kt` | `10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md`; `10_final_closure/FINAL_BLOCKED_QUESTIONS.md` §1; `10_final_closure/FINAL_RED_TEAM_AUDIT.md` §10; `06_android_port/ANDROID_AUTOMIX.md` |
| `automix/TransitionModels.kt` | `10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md` §2/§3/§9/§12/§14; `10_final_closure/FINAL_BLOCKED_QUESTIONS.md` §1; `10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` §2.1 |
| `automix/TransitionStyles.kt` | `10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md`; `10_final_closure/transition_styles_normalized.json`; `10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md` §11; `09_appos/APPOS_TRANSITION_STYLES.md` |
| `dsp/DspGraphSpec.kt` | `09_appos/APPOS_DSPGRAPH.md`; `10_final_closure/DSP_RUNTIME_ARCHITECTURE.md` §A–§D; `10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` §8; `10_final_closure/FINAL_RED_TEAM_AUDIT.md` §1 |
| `dsp/Automation.kt` | `06_android_port/ANDROID_DSP_ENGINE.md` §4; `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` §1.5; `10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` §7/§8; `09_appos/APPOS_MASTER_SUMMARY.md` §11 #22 |
| `lyrics/LyricsModel.kt` | `10_final_closure/TTML_PARSER_CLOSURE.md` §1–§7; `10_final_closure/LYRICS_RENDERER_IMPLEMENTATION_SPEC.md` §1; `09_appos/APPOS_LYRICSX.md`; `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` §1.8 |
| `lyrics/TTMLParseSpec.kt` | `10_final_closure/TTML_PARSER_CLOSURE.md`; `10_final_closure/FINAL_RED_TEAM_AUDIT.md` §9 (corrections applied); `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` §1.8 |
| `lyrics/LyricsRendererSpec.kt` | `10_final_closure/LYRICS_RENDERER_IMPLEMENTATION_SPEC.md` §2–§4; `09_appos/APPOS_ANIMATIONS.md` §A.5; `10_final_closure/FINAL_RED_TEAM_AUDIT.md` §6; `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` §1.9 |
| `glass/GlassSpecs.kt` | `10_final_closure/LIQUID_GLASS_IMPLEMENTATION_SPEC.md`; `09_appos/APPOS_GLASS_RECIPES.md`; `09_appos/APPOS_MASTER_SUMMARY.md` §8; raw IR: `decompiled_package/glass/air_ir/*.ll`; recipe: `appos/extracted/System/Library/PrivateFrameworks/CoreMaterial.framework/platformContentGlass.materialrecipe` |
| `animation/AnimationSpecs.kt` | `10_final_closure/NOW_PLAYING_ANIMATION_SPEC.md`; `09_appos/APPOS_ANIMATIONS.md` §A; `09_appos/APPOS_MASTER_SUMMARY.md` §12.4; `10_final_closure/FINAL_RED_TEAM_AUDIT.md` §7 |
| `pipeline/CleanRoomPipeline.kt` | `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` §2; `10_final_closure/DSP_RUNTIME_ARCHITECTURE.md` §B–§D; `06_android_port/MEDIA3_INTEGRATION.md`; `06_android_port/ANDROID_ARCHITECTURE.md` |

Raw evidence roots (read-only, not modified): `appos/extracted/**` (OS volume), `appos/sys_dsc/**`
(shared cache subcaches), `decompiled_package/**` (targets/pseudocode/disassembly), plus
`10_final_closure/transition_styles_normalized.json` and
`10_final_closure/itunes_music_genre_tree_id_name.json`.

**Resource hashes used by the contracts:**
- `TransitionStyles.json` — sha256 `fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120`, 55905 B.
- `DSPGraph.dspg` — sha256 `8326e890fd6905a0fba94176ed57ae200fe38c206dbac1c07d8355c278dafd2a`, 3001 B.
- `platformContentGlass.materialrecipe` — sha256 `589fc5c5795c149a78a8339a93738e13a88d6919f124d9b790f920e9dd897858`, 1849 B.

## 4. Safe to implement (proven data / behavior)

These carry `[EXACT]` or `[STRONG_INFERENCE]` and may be implemented directly:

- **Planner constants/gates:** tempo tolerances 0.16/0.287; `60/bpm` conversion; 0xfc incompatible tag;
  vocal penalty 0.75; bases 10.0/15.0/2.0/1.0/3.0; tie scale 0.001; min matching bars 8; drift 0.04;
  complexity 4 cases 0..3 + algorithm mapping; reduced sets; musicality bands 0.85/0.3/0.25 with the
  audited polarity; winner rule (`score > 0`, strict `>`); fallback 2.0 s / base 1.0; style dispatch 8/9/12.
- **Tie-breaker semantics:** `param_1 = T_end(outgoing) - T_end(incoming)` (closed in
  `FINAL_BLOCKED_QUESTIONS.md` §1); units seconds are `[STRONG_INFERENCE]`.
- **Transition catalog:** ids/names/offsets/durations/counts of all 14 styles; dispatch/reachability;
  interpolation formulas (curve engine); resource schema/keys. Full automation values live in the
  resource (`transition_styles_normalized.json` mirrors it).
- **DSP graph:** `SmartTransitions` topology (14 wires), 27 graph parameters with defaults/targets,
  all 29 `AutomationEffectParameter` ids/ranges/defaults, AU parameter indices, `bypa` polarity
  (1 = bypassed), `ts_rate`/`out_gain` out-of-graph delivery, fourCC schedule-key behavior.
- **Automation engine:** ramp formula and boundary shortcuts; curve formulas for 0x00/0x01/0x40/0x41/0x80;
  stepped default 0.2 s and valid range 0.0001..1.0.
- **Lyrics:** TTML parser rules (time format, element/attribute chart, x-bg handling, translations map,
  sorting), model inventory, renderer constants (2 / 0.5 / 0.05 / 0.1 / 0.25 / animators / springs /
  feather 30.0 / instrumental formulas / seek 0.25+curve 3).
- **Glass algorithms:** continuous-corner polynomial, expansion factor 1.528665, AA 1e-4, meniscus,
  variable-blur LOD and 4-tap rule, mip cap 7, static downsample weights, Gaussian threshold 0.002,
  filter order lists, `platformContentGlass` recipe (blurRadius 45 + 4x5 colorMatrix), plusL/plusD.
- **Animations:** sheet 0.4 s / ζ1.0 / response 0.3441442326 (k/c derived), high-speed ζ0.8,
  marquee 3.0/30.0/0.016, backdrop crossfade 0.8, artwork-morph easing/inherited duration,
  AutoMix artwork 3.0/4.8/0.6/1.3, highlight/pill springs, PPT fallback 10.0.

## 5. Do not hardcode (runtime/device or unproven)

Never turn these into universal constants; keep them behind runtime reads or feature flags:

1. `bypa = 0` runtime write / actual property-21 value (no static call-site; DSPR §A.4).
2. Literal `TransitionPlanner.transition(...)` call-site / call-level field binding (MC §3, #9 PARTIAL).
3. Automations -> `TimeStretchingStep` function; exact `setSpeedRamp:` instruction (DSPR NOT FOUND #2–3).
4. Alignment/pivot formula in stretched time (`SynchronizedPlaybackTimeRange` math) (DSPR #4).
5. DSPGraph teardown/reset and AVFoundation identifier dedup (DSPR #5–6).
6. Tie-breaker field name/units beyond the closed formula (PLC §12.2).
7. Planner sources for `beatEvents/downbeatEvents/bars/beatStabilityMap` (PLC §1.4/§5.4).
8. Genre `ID -> name` local table (absent; runtime capture needed) (MAA #11).
9. `bpm.percentDeviation` semantics and `loudness.peak` units (BLOCKED: #13/#15).
10. Apple TTML parser call-site in MEE and instrumental-marker producer (TTML §12).
11. Glass runtime tuning: tile sizes chosen by Metal, per-frame mip levels, per-radius Gaussian weights,
    layer `MTLPixelFormat`, effective `edge_*`/`shadow_*`/`sdr_*` uniforms, CASDF runtime values.
12. CASDF per-pixel highlight/displacement shader (absent from `.air`) — defaults are data only.
13. Reflection 12-segment geometry (config EXACT, path construction NOT FOUND).
14. Style `duration` unit (bars vs seconds) and normalized->absolute time mapping (`entryOffsetUs`).
15. Fallback -> style 4 binding; `beat_length` resolver; non-BM style -> algorithm binding.
16. `LoudnessMap` construction window/interpolation; `VocalActivityStrength` tags/significance threshold.
17. Full `Transition`/`Transition.Strategy`/`PlaybackState`/`SpatialTimingInformation` layouts.
18. Result of `SpatialTimingInformation` and `MusicalCompatibility.SongIssues` bit semantics.
19. User preferences: `SheetDampingRatio` / `SheetResponse` / `SheetHighSpeedDampingRatio`,
    `PPTContentOffsetScrollIncrement` — device values override defaults.

## 6. Forbidden list (disputed constants — excluded from active code)

The constants below were reported once but **not confirmed by the final audit** (or belong to other
platforms). They are **not active values anywhere in this directory** and must not be implemented
until re-verified against raw artifacts:

| Disputed value | Where it came from | Why it is forbidden | Audit reference |
|---|---|---|---|
| `100.0` (line-change spring stiffness) | `Specs.init` `0x100460b1c`; Music.app `vpfi` | no direct double in MEE found (float only); m/k/c order unconfirmed | `LYRICS_RENDERER_IMPLEMENTATION_SPEC.md` §4; `APPOS_RED_TEAM_AUDIT.md` row 58 |
| `0.95` (`touchDownTransform` scale) | MEE `Specs.init` (`0x3fee666666666666`); Music.app `vpfi` | audit did not confirm the double in MEE | LYR §4; REC (`0.95`) |
| `39.0` (`paragraphSpacing`) | MEE `Specs.init` (`0x4043800000000000`) | audit did not confirm the double in MEE | LYR §4; `APPOS_RED_TEAM_AUDIT.md` row 59 |
| `1.12` (emphasis scale) | Android prior art | absent from the iOS corpus; real range upper bound is 1.14 | `LYRICS_RENDERER_IMPLEMENTATION_SPEC.md` §4 |
| `28%` / `38%` (viewport anchors) | old specs / Android patch | absent in iOS; `0.28` is an animator duration; `28%` is an Android `ACTIVE_LINE_BIAS` | LYR §2.3/§4 |
| `4000` ms (user-scroll pause) | Android patch `USER_SCROLL_PAUSE_MS` | not found in iOS | LYR §4 |
| `7000` ms (instrumental threshold) | Android plan §48 | not found in iOS; interlude comes from the model, parser has no instrumental marker | LYR §4; TTML §9 |
| sqrt-pair "constant-power" derivation for styles 1/2 | inference over `ease-in-0.5`/`ease-out-0.5` | the derivation is `INFERRED` through a STRONG string->curveByte link; `cos/sin(π/2·t)` rejected entirely | `TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` §4 |

Notes:
- The resource style **names** "constant-power cross-fade" / "constant-power long fade-out short
  fade-in" are exact resource strings and appear in `automix/TransitionStyles.kt` as names only.
- The `sqrt(p)` / `1 - sqrt(1 - p)` curve formulas in `dsp/Automation.kt` are the audited curve-engine
  formulas (bytes 0x40/0x00), not the disputed crossfade derivation.
- The percent upper bound of `fx_delay_dry_wet` / `fx_reverb_dry_wet` (exact range 0..100 percent,
  `APPOS_DSPGRAPH.md` §6) is written as `1.0e2` so that the decimal spelling of `100.0` stays out of
  active code; it is an exact, unrelated parameter range.
- Hex provenance strings (e.g. `0x4000000000000000`) are raw artifact bytes required for traceability;
  they are not the forbidden millisecond values.

## 7. File inventory and TODO/UNKNOWN count

```
kotlin_contracts/
├── README.md
├── analysis/SongAnalysis.kt
├── animation/AnimationSpecs.kt
├── automix/TransitionModels.kt
├── automix/TransitionPlannerSpec.kt
├── automix/TransitionStyles.kt
├── dsp/Automation.kt
├── dsp/DspGraphSpec.kt
├── glass/GlassSpecs.kt
├── lyrics/LyricsModel.kt
├── lyrics/LyricsRendererSpec.kt
├── lyrics/TTMLParseSpec.kt
└── pipeline/CleanRoomPipeline.kt
```

Count markers with:
`grep -rn 'TODO(' kotlin_contracts --include='*.kt' | wc -l` and
`grep -rn 'STATUS: UNKNOWN' kotlin_contracts --include='*.kt' | wc -l`.

## 8. Build check

```
kotlinc $(find kotlin_contracts -name '*.kt' | sort) -d out
```
Compiles with `kotlinc-jvm 2.1.0` / JRE 21 with no errors. `TODO()` bodies compile and throw only
when an unproven path is executed at runtime (by design).
