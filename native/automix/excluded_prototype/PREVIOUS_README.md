# LMG AutoMix — C++ core

Standalone C++17 library. It is not connected to Android, JNI, Media3 or lmg31 yet.

Implemented:

- Automation evaluator for every byte family observed in `FUN_272275f84`, including
  geometric interpolation for `0x81` and linear behavior for `0x82..0xff`.
- Immutable, validated automation lanes: integer frame positions, explicit steps,
  held values between ramps, deterministic seeking and block boundaries.
- Two independent gain plans and interleaved float PCM mixing, including the four
  gain boxes of `DSPGraph.dspg` with all effect AUs bypassed. External `out_gain`
  is applied separately. Initial output gain must be supplied explicitly.
- Rendering without heap allocations, locks, network access or wall-clock timing.
- `TransitionRenderer`: validates resolved per-deck parameter schedules, prepares
  two independent effect graphs and sums their outputs using bounded scratch PCM.
- `TrackEffects`: a prepared, stateful per-track graph with center EQ, high/low
  pass filters, delay, prototype reverb and dry/wet gains. Each channel has its
  own filter and tail state. Immutable automation plans drive 23 effect/gain
  parameters plus external output gain on the absolute output-frame timeline.

This is **not a complete AutoMix engine**. Pitch-preserving time stretching, a transition planner, network loading and JNI
are not implemented. The mixer cannot execute complete transition styles.
It expects both input blocks to have already been aligned by the playback layer.
No Apple-specific filter/reverb internals or spectral algorithm are assumed.

## Prepared effect graph

`TrackEffects(sampleRate, channels, settings, outputGain)` executes:

```text
input -> inputGain -> center EQ -> HP1 -> LP1
    dry -> dryGain -------------------------------------+
    wet -> sendGain -> delay -> reverb -> HP2 -> LP2 -> wetGain
                                                        + -> outputGain
```

`bypass=true` (the recovered default `bypa=1`) bypasses all seven effect nodes,
retaining all gains and both branches. `false` enables effects. Output gain is
explicit and external to the recovered graph. Construct one instance per deck.
The existing `TwoDeckMixer` remains a separate gain-only renderer; there is no
automatic connection that would apply graph gains twice.

Process continuous PCM in any block sizes; null input drains tails. Call `reset(frame)`
on seeks/new tracks to clear history and establish the next absolute output frame. Construction/destruction and configuration
replacement belong off the callback; process/reset require exclusive ownership.
Automation lanes are installed at construction and retain effect history while
changing values. Replacing plans still requires a new instance and loses history;
concurrent publication and click-free graph replacement are not implemented yet.

LMG algorithm choices (not claims of Apple equivalence):

- Biquads use the [W3C/RBJ Audio EQ Cookbook](https://www.w3.org/TR/audio-eq-cookbook/).
  Center bandwidth is interpreted in octaves. Resonance maps to
  `Q=clamp(10^(dB/20)/sqrt(2), 0.1, 20)`. Frequencies clamp to `0.49*sampleRate`.
- Fixed delay rounds seconds to the nearest whole sample (at least one), with a one-pole
  lowpass on the delayed signal before wet output and signed feedback. Percent
  parameters divide by 100; mixing is linear. Automated delay uses fractional sample
  positions with linear interpolation. Moving the read head changes pitch; this
  is not pitch-preserving delay-time automation, and steps can click.
- Reverb is a **prototype four-parallel-comb network**, not Apple's `rvb2`.
  Deterministic seeded delays occupy four strata between min/max delay. Each
  feedback path splits at 2.5 kHz into low/high decay components, with feedback
  `0.001^(delaySeconds/decaySeconds)`. Returns are averaged; reverb dB gain applies
  to the wet return. No diffusion allpasses, spatial crossfeed or modulation yet;
  listening/tuning is still required before product integration.
- Recursive state below `1e-30` flushes to zero. Double precision internal state,
  float PCM output, no limiter. Channels stay independent; equal mono inputs
  remain equal rather than receiving artificial stereo width.
- Accepted formats: 8–192 kHz, 1–8 channels. Parameter validation is a port policy;
  all frequency fields accept 10–22050 Hz, preserving the graph's 22000 Hz LP
  default despite its inconsistent published 21829.5 upper bound. Resonance fields
  accept -20–40.01 dB. Reverb seed accepts integer 1–1000. Other settings use the
  recovered ranges; max reverb delay must be at least min delay.

Delay capacity covers the settings and all automation endpoints, allocated during
preparation; no buffers grow during processing. Reverb geometry stays fixed. Resource bounds and validation
are explicit LMG policies, not undocumented AudioUnit behavior.

## Effect automation

```cpp
EffectSettings settings;
settings.bypass = false;
TrackEffects deck(48000, 2, settings,
    AutomationLane(0, {{0, 48000, 0, 1, 0x80}}), // external output gain
    {{&EffectSettings::lowpassHz,
      AutomationLane(22000, {{0, 48000, 22000, 800, 0x81}})}});
// deck.process(input, output, frames, firstFrame);
```

All double settings except `reverbMinSeconds` and `reverbMaxSeconds` support lanes.
Bypass and reverb seed are also preparation-only. Unsupported/null/duplicate
bindings and any out-of-range initial value or ramp endpoint are rejected before
processing. Lane values override settings from frame zero, including initial values.

Values are evaluated every frame, independently of block boundaries. Changed
filter coefficients retain recursive state; delay and reverb retain their tails.
Coefficient design uses transcendental functions on the audio thread when needed,
so real-device CPU profiling remains necessary. Abrupt steps are honored without
implicit smoothing; extreme filter modulation and audible transition quality need
further testing. This is an execution contract, not a finished sonic tuning pass.

The three-argument `process` advances an internal frame cursor starting at zero.
The four-argument form additionally checks the caller's absolute frame position;
noncontiguous calls fail without changing state or output. Use `reset(firstFrame)`
for a seek, discarding history rather than attempting to reconstruct prior tails.
Negative reset positions fail without modifying state. Reset restores deterministic
rendering for the same subsequent input/timeline. Timeline overflow is rejected;
`INT64_MAX` is an end cursor, not a renderable frame.

## Time and execution contract

All ramp times and `firstFrame` are on one absolute output-frame timeline. One
frame contains `channelCount` samples. Conversion from seconds rounds to the nearest
frame, with half frames rounded upwards. This conversion is an LMG policy; mapping
Apple style `relative` times / beat lengths / stretched song times is still separate.

Build plans outside the audio callback. Keep the mixer alive and immutable while
rendering; replace/destroy it only when the playback layer has stopped using it.
The library does not implement concurrent plan publication or PCM buffering.

Ramps may touch but cannot overlap or share a start. At a zero-duration step, the
end value takes effect. Before the first ramp the initial value is held; gaps hold
the preceding endpoint. Non-finite values, reversed windows and nonpositive
logarithmic endpoints are rejected during preparation. These are explicit port
validation rules, not claims about undocumented Apple error handling.

PCM buffers are interleaved, identically formatted and contain finite normalized
samples. Null input means silence for that deck. Exact in-place output is supported;
partial overlap is not. Output retains float headroom, without clipping or a limiter.
Caller-provided buffers must contain at least `frames * channels` samples.

## Evidence and corrections

Research root: `/srv/research/apple-music-ios26/`.

- `decompiled_package/automix/disassembly/272275f84__FUN_272275f84.asm`:
  the evaluator's branch and boundary behavior was read directly.
- `deepseek_analysis/08_closure/P2_MEDIADSP_CLOSURE.md`, §5:
  resolves the external functions as `log2`, `exp2`, `sqrt`, `pow`.
  Its sentence after the geometric formula incorrectly suggests another linear
  blend. The assembly at `0x27227606c..0x272276120` instead interpolates the logged
  endpoint values and tail-calls `exp2` directly. This implementation follows that.
- `appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/DSPGraph.dspg`:
  SHA256 `8326e890fd6905a0fba94176ed57ae200fe38c206dbac1c07d8355c278dafd2a`.
  Bypassed path: `input * Gain1 * (Gain3 + Gain2 * Gain4)`.
- `deepseek_analysis/10_final_closure/DSP_RUNTIME_ARCHITECTURE.md`, §C–D:
  separate per-track state and external output gain.

No changes are made to the research files or the working lmg31 controller. Native
floating-point results are tested numerically; bit-identical Apple output is not claimed.

## Standalone tests

```sh
cmake -S native/automix -B /tmp/lmg-automix-build -DCMAKE_BUILD_TYPE=Debug -DLMG_AUTOMIX_SANITIZERS=ON
cmake --build /tmp/lmg-automix-build --parallel 2
ctest --test-dir /tmp/lmg-automix-build --output-on-failure
```

No Android build, emulator, network dependency or third-party test framework is needed.
Tests cover all 256 curve bytes, logarithmic frequency interpolation, invalid input,
seek/step/gap boundaries, allocation-free rendering, stereo independence, in-place
mixing, and complete 18-second transitions at 44.1/48 kHz split into varying blocks.
Effect tests cover measured EQ/filter response, delay onset and signed feedback,
reverb tails/decay, channel/deck isolation, reset reproducibility, block partition
equality at 44.1/48 kHz, invalid settings and allocation-free active processing.

Automation tests cover gain values, timeline/overflow rejection, invalid bindings,
fractional delay against two integer-delay references, animated filter/delay/reverb
block equality, allocation-free processing and reset/seek reproducibility.

## Resolved transition execution

`transition.h` bridges the research parameter IDs to the native engine. It accepts
`DeckSchedule` objects containing settings and `ParameterSchedule {parameterId,
AutomationLane}` entries. All times must already be absolute output frames.
For example, a test transition with caller-selected duration:

```cpp
DeckSchedule outgoing{EffectSettings{}, {
    {"out_gain", AutomationLane(1, {{0, 864000, 1, 0, styleCurveByte("linear")}})}};
DeckSchedule incoming{EffectSettings{}, {
    {"out_gain", AutomationLane(0, {{0, 864000, 0, 1, styleCurveByte("linear")}})}};
TransitionRenderer renderer(48000, 2, outgoing, incoming);
// renderer.render(outgoingPcm, incomingPcm, outputPcm, frames, firstFrame);
```

This is an LMG execution example, not a reconstruction of Apple's style 1/2.
Parameter spellings come from `kotlin_contracts/dsp/DspGraphSpec.kt`, including
`lp_cutoff_freq`, `fx_delay_lp_cutoff_frequency` and `out_gain`. Unknown/duplicate
IDs, missing output gain and invalid values fail preparation. Reverb geometry,
seed and `bypa` accept only constant schedules; all scheduled endpoints must equal
the initial value. `ts_rate` accepts only constant 1 until a time-stretch stage
exists. Nothing silently substitutes an unfiltered/non-stretched transition.

The renderer processes both decks into separate preallocated float buffers before
writing the sum, preserving exact in-place operation. Scratch size (1–8192 frames,
default 256) does not limit caller block size or change automation timing. Each
track's gains are applied once inside its graph. Float output preserves headroom;
there is no extra gain normalization or limiter. Independent graph outputs round
to float before summing. Null inputs feed silence and drain tails. Reset/continuity
and single-thread ownership follow the effect graph contract.

This renderer layer does **not** select an algorithm/style,
resolve placement windows, `relative` times, `beat_length`, or unknown duration
units, align song PCM, or time-stretch it. Those decisions must be supplied by a
future planner/adapter. Style interpolation names map to the audited curve bytes;
unknown names fail instead of defaulting to linear.

Transition tests cover linear mixing against expected samples, parameter mapping
against an independent effect graph, different scratch/caller block sizes, exact
in-place output, seek/reset, unsupported commands and allocation-free rendering.

## Style JSON loading and schedule compilation

`styles.h` parses the recovered `TransitionStyles.json` schema on a control thread.
It preserves style IDs/names, raw duration metadata, optional offsets, instruction
placements, relative ramp times, numeric defaults and mapped parameter names.
It uses the vendored MIT-licensed nlohmann/json v3.11.3 single header privately;
no dependency downloads occur during builds. Input is limited to 1 MiB and nesting
32, duplicate JSON keys/style IDs and unknown schema fields/interpolations fail.
Loading a catalog does not imply every style is playable or planner-reachable.

`compileStyleDeck` groups commands by parameter into validated automation lanes.
The caller MUST supply an absolute frame window for each automation in source
instruction/ramp order, plus explicit initial values for scheduled parameters.
Placement/offset/style duration are intentionally not interpreted by this function:
the resolver must account for them before calling it. No generic 8-bars/8-seconds
assumption or transition-relative versus instruction-relative guess is made.
Missing/extra windows, overlaps and invalid ramps fail. Mapped numeric values use
an explicitly supplied binding, then their resource default, otherwise fail.
`beat_length` therefore requires a caller value when no default is present.

The result feeds `TransitionRenderer`, which performs parameter/range/capability
validation. Catalog loading and compilation alone do not certify playability;
non-unity time stretching and dynamic reverb geometry still fail preparation.
The loader does not select styles, interpret server analysis, or contact Apple.
No recovered catalog is bundled into the app. The local full research catalog can
be checked without copying it into the repository:

```sh
/tmp/lmg-automix-build/lmg_automix_style_tests /srv/research/apple-music-ios26/appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json
```

Synthetic tests cover JSON -> compiled gain schedule -> rendered PCM, numeric
bindings/defaults, missing mappings, exact timing supplied by the caller,
overlapping ramps, invalid JSON, duplicate keys, depth/size limits and schema errors.
The optional research check verifies 14 styles, 55 instructions, 74 automations and
2 mapped value occurrences. Research files remain unchanged.

## Explicit style timing policy

`resolveStyleTiming(style, transitionFrames, sampleRate, basis)` resolves both
instruction placements and flattened automation windows. Its required `basis`
argument selects an LMG policy: `InstructionRelative` maps each ramp's relative
0..1 into its instruction placement; `TransitionRelative` maps it into the whole
transition. The recovered nested resolver is not established, so neither option
is advertised as exact Apple behavior. Both reject ramps outside their placement
and placements outside the supplied transition, before frame rounding.

The caller supplies the actual transition window. Raw `style.duration` is ignored,
so 8/16 is never guessed to mean seconds or bars. A style offset is returned as an
`offsetAnchor` only: it does not silently shift tracks or their PCM alignment.
The caller must decide how that anchor applies to playback. It may lie outside the
transition but must remain on the nonnegative representable frame timeline.

Offsets in seconds are added in extended precision. Nested windows stay unrounded
until final frame conversion; half frames round upward. Integer timeline origins
are added after rounding local offsets, preserving short ramps beyond 2^53 frames.
Zero-length windows become explicit steps. Invalid sample rates, reversed windows,
nonfinite values, boundary violations and frame overflow fail preparation.

Pass `timing.outgoing.ramps` / `timing.incoming.ramps` into `compileStyleDeck`.
Tests cover the last/first 0.3-second placement windows at 44.1/48 kHz, explicit
basis disagreement, zero durations, half-frame rounding, large timeline origins,
limits, invalid windows and the recovered styles 8/9/12 with caller-selected timing.
Beat alignment, tempo selection, song offsets and time stretching remain separate.
