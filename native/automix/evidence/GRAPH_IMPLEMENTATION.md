# Connected per-track effect graph

`TrackGraph` now executes the topology in the recovered `DSPGraph.dspg`:

```
Input -> Gain1 -> AUFilter -> HP1 -> LP1
   -> Gain3 --------------------------------------------> Mixer input 0
   -> Gain2 -> Delay -> Reverb -> HP2 -> LP2 -> Gain4 ----> Mixer input 1
Mixer -> Output
```

The graph resource SHA256 is
`8326e890fd6905a0fba94176ed57ae200fe38c206dbac1c07d8355c278dafd2a`.
Each graph owns independent per-channel filter and delay instances, and one
mono/stereo reverb tank shared only within that track. Two tracks never share
wet state. The first AUFilter peak is wired to Fcf1/Fcg1/Fbw1; its other four
sections retain the recovered AU defaults. Four gain controllers advance once
per slice, then their control blocks are applied separately to every channel.
Dry is mixed first and wet second through the recovered fused multiply-add path
with unity Mixer input gains. `out_gain` and `ts_rate` remain outside this graph.

`PreparedGraph` validates settings and calculates coefficients away from the
audio callback. AU float parameter narrowing is explicit, including RVrr's
float-to-uint32 conversion and saturation. Applying a prepared snapshot updates
coefficients without clearing biquad history and queues gain writes through the
recovered smoothing behavior. Reverb decay changes clear its tank; the explicit
decay-write flag also handles a repeated write with an unchanged value.

All scratch buffers and kernels are owned at construction. A caller-supplied
maximum slice size bounds scratch storage; the current host API accepts mono
or stereo and capacities up to 16384 frames. Oversized calls and unsupported
snapshot geometry/rate changes are rejected before any state/output
mutation. Input may be null to supply zero-valued PCM and drain tails, or may
alias output exactly. Passing zero-valued PCM does not invent AU silence flags.

## Boundaries still requiring the runtime host

* Bypass remains the explicit prepared `bypa` value, default **true**, as in the
  resource. Serialized changes are supported: leaving bypass resets channel
  filters/delay and preserves the reverb tank. See EFFECT_BYPASS_IMPLEMENTATION.md.
* Reverb geometry changes require a separately prepared graph; the live geometry
  reconfiguration lifecycle is not implemented by `apply`.
* `GraphSilence` supplies the actual flags entering the four Gain boxes. AU flag
  propagation and tail/property wrappers are not guessed from sample amplitudes.
  Empty PCM and a buffer flagged silent are distinct operations.
* The caller supplies slice boundaries and prepared writes. This API does not
  invent an automation sampling frequency or normalized style-time resolver.
* `TrackGraph` is connected native DSP, not yet a complete application AutoMix:
  TimePitch, planner/server mapping, scheduling and playback integration remain.

## Verification

The integration test checks raw topology behavior: bypass still processes all
four gains, dry/wet sum, exact in-place processing, stereo wet coupling with
reverb, independent channel delays when reverb wet is zero, independent track
state, tails under zero input, identical stereo smoothing, atomic rejection and
zero-frame handling. Allocation watching covers active rendering and applying a
prepared snapshot plus rendering. These are routing/lifecycle assertions built
on independently checked kernels, not an end-to-end Apple graph recording.

Follow-up: the missing activation producer is now found in the style schedule
builder. It inserts bypa=0 on the parent transition interval and restores 1 at
its end. See STYLE_TIMING_IMPLEMENTATION.md. Binary bypass writes are now accepted
by the graph event bridge; conversion from continuous song time remains separate.
