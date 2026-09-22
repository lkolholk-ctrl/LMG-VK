# iOS 26 Apple Music AutoMix Android Port Specification

## Overview
This repository contains the complete reverse-engineering corpus and native C++ DSP implementation of **Apple Music's AutoMix (Smart Transitions)** extracted from the iOS 26 system image.

Your goal is to implement the production-ready Android integration in Kotlin and C++ for the **LMG-VK** music player using **AndroidX Media3 / ExoPlayer**.

---

## 1. Directory Structure and Evidence Base

1. **`research/ios26-automix/TransitionStyles.json`**:
   - Extracted directly from `_SonicKit_MusicKit_Packages.framework`.
   - Contains all **14 transition styles**, **55 instructions**, and **74 automation curves**.
2. **`research/ios26-automix/specs/10_final_closure/`**:
   - `TRANSITION_PLANNER_CLEANROOM_SPEC.md`: Exhaustive logic for tempo matching, bar alignment, transition window calculation, scoring, and style selection.
   - `TRANSITION_STYLES_IMPLEMENTATION_SPEC.md`: Specification of automation parameters (`out_gain`, `lpf_cutoff`, `hpf_cutoff`, `reverb_wet`, `delay_feedback`).
   - `DSP_RUNTIME_ARCHITECTURE.md`: Topology and execution graph matching `DSPGraph.dspg`.
   - `MEDIAAPI_TO_PLANNER_CALLCHAIN.md`: Exact mapping from Apple Music catalog API (`audio-analysis`, `flexml-analysis`, `fades`, `loudnessCurve`).
   - `transition_styles_normalized.json`: Normalized JSON representation.
3. **`research/ios26-automix/contracts/`**:
   - Clean-room Kotlin 2.x interface contracts:
     - `automix/TransitionPlannerSpec.kt`: Main planner contract.
     - `automix/TransitionStyles.kt`: Data classes for styles, instructions, and placements.
     - `automix/TransitionModels.kt`: Transition plan and candidate models.
     - `analysis/SongAnalysis.kt`: Audio and FlexML analysis data models.
     - `dsp/DspGraphSpec.kt` & `dsp/Automation.kt`: DSP node topology and parameter automation.
4. **`native/automix/`**:
   - Fully compiled, verified C++ engine matching ARM disassembly bit-for-bit:
     - 16-line matrix reverb (`src/reverb.cpp`)
     - 5-section AUFilter biquad (`src/filter.cpp`)
     - GainBox smoothing & ramps (`src/gain.cpp`)
     - Curve evaluators & time mapping (`src/playback_time.cpp`, `src/scheduled_graph.cpp`)
     - 39/39 passing unit tests (`ctest --test-dir /tmp/lmg-automix-build`)
5. **`app/src/main/kotlin/com/lmg/vk/playback/` & `engine/automix/`**:
   - `PlaybackService.kt`: Active `MediaLibraryService` managing dual `ExoPlayer` instances (`playerA` and `playerB`).
   - `CrossfadeController.kt`: Current volume transition controller.
   - `AutoMixCoordinator.kt`: Coroutine-based track lifecycle coordinator.

---

## 2. Deliverables & Production Requirements

### Component 1: `AppleSongAnalysisParser.kt`
- Parse the JSON response from `v1/catalog/{storefront}/songs/{id}?extend=supportsSmartTransitions&include=audio-analysis,flexml-analysis&extend%5Baudio-analysis%5D=fades,loudnessCurve`.
- Decode sections:
  - `supportsSmartTransitions` (boolean)
  - `audio-analysis` -> `fades` (intro/outro durations), `loudnessCurve` (LUFS time series), tempo, key, beats.
  - `flexml-analysis` -> structural segments (verses, choruses, drops, outro).
- Convert strictly into `research/ios26-automix/contracts/analysis/SongAnalysis.kt` models.

### Component 2: `ProductionTransitionPlanner.kt`
- Implement and complete `TransitionPlannerSpec.kt` without stubs or `TODO()`.
- Implement candidate scoring based on:
  - Tempo compatibility (BPM ratio, half/double tempo tolerance within $\pm 4\%$).
  - Energy & loudness curve alignment.
  - Structural cue detection (outro start of track A and intro end of track B).
- Select the best matching style from the 14 `TransitionStyles`:
  - `Gapless` (ID 0)
  - `constant-power cross-fade` (ID 1)
  - `constant-power long fade-out short fade-in` (ID 2)
  - `overlap` (ID 3)
  - `overlap + Reverb` (ID 4)
  - `Beat-matched long fade-out short fade-in` (ID 7)
  - `BM - Filter high to low` (ID 9)
  - etc.
- Compute final `TransitionPlan`:
  - `transitionStartMs` (timestamp in track A to begin the mix)
  - `crossfadeDurationMs` (duration of the transition window)
  - `entryOffsetMs` (cue-in position for track B)
  - `styleId` & compiled automation curves.

### Component 3: `Media3AutoMixBridge.kt` (ExoPlayer Orchestration)
- Coordinate `playerA` (active) and `playerB` (incoming) in `PlaybackService`:
  1. Pre-arm and buffer track B in `playerB` $N$ seconds ahead of `transitionStartMs`.
  2. At `transitionStartMs`, start playback of `playerB` at `entryOffsetMs`.
  3. Apply calculated volume ramps (Equal-power $\cos / \sin$ or GainBox curve) with high-resolution frame/tick cadence to prevent any 3 dB loudness dip or buffer underrun clicks.
  4. On transition completion, swap active player reference seamlessly (`activePlayer = playerB`) and reset `playerA`.

### Component 4: Graceful Local Fallback
- If a track does not have Apple Music analysis metadata (e.g. arbitrary VK tracks):
  - Fall back cleanly to algorithmic audio analysis (`SmartTransitionFinder.kt` / `EnergyAnalyzer.kt`).
  - Calculate outro fade / intro onset from local PCM and execute equal-power crossfade without crashing.

---

## 3. Verification & CI Feedback Loop
- **Native C++ Tests**:
  ```sh
  cmake -S native/automix -B build_automix -DCMAKE_BUILD_TYPE=Debug
  cmake --build build_automix --parallel 2
  ctest --test-dir build_automix --output-on-failure
  ```
- **Kotlin Unit Tests**:
  ```sh
  ./gradlew testDebugUnitTest
  ```
- **Compilation Check**:
  ```sh
  ./gradlew compileDebugKotlin
  ```
All tests are automatically run on push to `feat/ios26-automix` via `.github/workflows/automix-ci.yml`. Check GitHub Actions job logs for failure traces.
