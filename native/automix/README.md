# LMG AutoMix — evidence-based implementation in progress

User requirement: complete AutoMix from research/decompilation only. No invented
DSP, no JUCE/Oboe. The working lmg31 crossfade remains untouched.

The previous substitute effect engine, renderer and speculative style-time
resolver have been removed from every build target and public API. Their source
and former documentation remain in `excluded_prototype/` for audit only.
They are NOT an implementation of Apple DSP and must not be integrated.

Currently built:

- Automation curve evaluator, checked against `FUN_272275f84` disassembly.
- Recovered LP/HP coefficients, shared per-channel sample kernel and integer delay,
  reconstructed from
  newly extracted libEmbeddedSystemAUs. See `evidence/LOWPASS_IMPLEMENTATION.md`,
  `HIGHPASS_IMPLEMENTATION.md` and `DELAY_IMPLEMENTATION.md`.
- Recovered 16-line matrix reverb, with mono/stereo binary reference fixtures.
  See `evidence/REVERB_IMPLEMENTATION.md` for geometry, parameter-write semantics,
  exact kernel comparisons and remaining AudioUnit wrapper boundaries.
- Full five-section AUFilter, compared against original ARM coefficient and
  sample routines (`evidence/FILTER_IMPLEMENTATION.md`).
- Recovered GainBox smoothing and sample multiplication, including queued writes,
  silence hold and alignment-sensitive ramps. See `evidence/GAIN_CONTROL_IMPLEMENTATION.md`
  and `GAIN_SAMPLES_IMPLEMENTATION.md` for the control/sample boundary and tests.
- Connected per-track graph with dry/wet routing, separate channel histories,
  stereo reverb and prepared parameter updates (`evidence/GRAPH_IMPLEMENTATION.md`).
- Resolved automation lanes and gain-only mixing. Buffer validation, scheduling
  rejection rules and API ownership are host infrastructure, not claims of Apple
  internal behavior. TwoDeckMixer remains the separate gain-only renderer;
  TrackGraph supplies effect processing for the upcoming playback host.
- Strict resource-schema loader preserving raw style times/placements/values.
- Assembly of already-resolved schedules supplied by a caller. It does not invent
  missing initial values, instruction windows or song alignment.

Not implemented: spectral PCM time stretching, full planner, server-analysis
integration and complete JNI/Media3 playback integration. Graph scheduling at
explicit frame boundaries, serialized effect bypass, normalized style-time
resolution and stepped schedule preparation are implemented (details below).
The presence of parameter metadata or passing self-tests does not prove these.
See `FIDELITY_AUDIT.md` for concrete evidence and next work.

## Native verification

```sh
cmake -S native/automix -B /tmp/lmg-automix-build -DCMAKE_BUILD_TYPE=Debug -DLMG_AUTOMIX_SANITIZERS=ON
cmake --build /tmp/lmg-automix-build --parallel 2
ctest --test-dir /tmp/lmg-automix-build --output-on-failure
```

Optional schema verification against the local research resource:

```sh
/tmp/lmg-automix-build/lmg_automix_style_tests /srv/research/apple-music-ios26/appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json
```

No Android build/emulator. No network access during builds/tests. The private
JSON parser dependency is nlohmann/json v3.11.3 (MIT); see third_party/nlohmann.

Explicit stepped parameter delivery is now available through
`scheduled_graph.h`: `prepareGraphEvents` binds the graph's 23 live scalar IDs,
and `ScheduledTrackGraph` applies prepared snapshots at supplied frame boundaries
without callback allocation. Conversion of recovered media-time events to PCM
delivery remains separate. See [schedule evidence and limits](evidence/SCHEDULED_GRAPH_IMPLEMENTATION.md).

`PlaybackTimeMap` now converts song/transition/stretched time for the recovered
single-rate-ramp active domain. Its 332 ARM reference cases match bit-for-bit
under the same host libm. See [time mapping evidence](evidence/PLAYBACK_TIME_IMPLEMENTATION.md)
for source addresses and the remaining planner/TimePitch boundaries.

`continuous_schedule.h` connects supplied continuous automation points to the
playback-time map, preserving the source's complete ts_rate descriptor match and
first-ramp selection. [Evidence and remaining scope](evidence/CONTINUOUS_SCHEDULE_IMPLEMENTATION.md).

`ContinuousAutomationValues` evaluates prepared continuous points directly in
song seconds, using the recovered default/boundary/reverse-ramp selection rules.
Its curve math is shared with the frame evaluator; it does not assume normalized
style placement semantics or an event sampling cadence.

Style timing now follows the recovered nested placement/ramp resolver, including
clamping and midpoint collapse for reversed endpoints (126 ARM reference cases).
The automatically inserted bypa gate is also recovered. Serialized live bypass
delivery now preserves reverb tails and resets LP/HP/filter/delay on reactivation
according to their recovered AU behavior. See [timing evidence](evidence/STYLE_TIMING_IMPLEMENTATION.md)
and [bypass evidence](evidence/EFFECT_BYPASS_IMPLEMENTATION.md).

`compileContinuousStyle` now converts parsed styles to continuous automations
using explicit planner windows/rates/mapped values and the extracted 29-parameter
catalog. Both sides of all 14 research styles pass the compiler checks. See
[schedule compilation evidence](evidence/STYLE_SCHEDULE_IMPLEMENTATION.md).

`compileSteppedAutomations` produces point events and ramp intervals from the
recovered time grid and raw samples. `compileTimedEffectEvents` merges point
writes in source order onto the stretched-song media clock. `compileTimedVolumeRamps`
handles the separate first matching out_gain lane, including its initial hold.
`compileTimeStretchingSteps` preserves adjacent mapped intervals and their
average playback rates. These are preparation APIs, not a completed PCM host.
See [stepped sampling](evidence/STEPPED_SCHEDULE_IMPLEMENTATION.md),
[effect transport](evidence/EFFECT_EVENTS_IMPLEMENTATION.md),
[volume ramps](evidence/VOLUME_RAMPS_IMPLEMENTATION.md) and
[rate steps](evidence/TIME_STRETCHING_STEPS_IMPLEMENTATION.md).

Planner preparation now includes recovered tempo matching, scoring for styles
8/9/12 and candidate selection (`planner_scoring.h`), plus loudness-map creation,
closed-window means and trailing-region ratios (`planner_loudness.h`). These
consume explicitly resolved inputs; complete region generation is still needed.

`media_analysis.h` decodes the raw catalog/analysis response, matching the
requested song and embedded/included relationships. Missing values, raw units,
vocal strings and pivot arrays are preserved. It does not invent normalized
planner inputs or fetch credentials. The saved real response passes decoding.

The actual spectral backend is now linked to the aufc/nutp NewTimePitch factory.
Recovered FFT geometry, analysis window, latency, phase analysis and hop-clock
arithmetic are implemented in `time_pitch_setup.h` / `time_pitch_phase.h`.
FFT, coherence/transient processing, synthesis and overlap-add remain necessary
before this can process a complete PCM stream.

An independent [Kotlin/JNI track-effects bridge](android/README.md) now calls the
real scheduled graph and passes host JVM integration tests. Build it with
`LMG_AUTOMIX_JNI=ON` (default on for Android). It is not yet attached to the app:
track identity, PCM timeline and the complete AutoMix render path must be wired.
The existing working crossfade is unchanged.
