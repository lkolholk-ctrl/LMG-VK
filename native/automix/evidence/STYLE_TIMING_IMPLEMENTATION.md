# Recovered style timing and automatic bypass gate

The normalized time mapping previously marked PARTIAL is now located in
`FUN_272210660`, a schedule builder in `_SonicKit_MusicKit_Packages`.
Raw instructions are saved in `scheduling/style_schedule_builder.asm`.

## Nested timing

Given an explicit parent window [a,b], each normalized endpoint (r,offset) is:

```
x = a + (offset + (b-a)*r)
x = clamp(x, a, b)
```

The arithmetic is multiply, inner add, outer add, with no fused operation. Both
placement endpoints are converted independently at `0x2722106fc..0x27221073c`.
If the resulting end precedes the start, `0x272210740..0x272210758` replaces BOTH
with `start + (end-start)*0.5`; it does not swap them. The resulting placement
becomes the parent of each automation start/end time, which undergoes the same
calculation/clamp/collapse (`0x2722108bc..0x27221091c` for ts_rate and the ordinary
value branch immediately after `0x27221097c`).

`resolveStyleWindow` and `resolveStyleWindows` implement these rules. The latter
returns one window for every resource ramp, preserving instruction/ramp order.
It accepts the parent from the planner. No assumption is made that catalog
`duration` is seconds or about which cue pair supplies that parent. The loader's
absent seconds offset resolves to zero. Finite-input/range/overflow rejection is
an explicit LMG host guard; valid source arithmetic order is preserved.

`tools/style_timing_reference.py` executes the two original ARM arithmetic blocks
with no math or computation hooks. All 126 two-level cases match the C++ windows
bit-for-bit: nonzero/fractional/zero-width parents, ordinary/reversed placements,
positive/negative offsets, clamping, nested ramps and reversed ramp times.
Fixture/code hashes are in `tests/fixtures/style_timing.json`. These are actual
arithmetic-block references, not execution of the whole Swift planner.

## Automatic bypa activation found

At the end of `FUN_272210660`, the builder scans generated automations for the
full descriptor at `0x280c9aa98`. Its initializer `0x27226e534` stores **bypa**,
not out_gain: inline string value 0x61707962, range [0,1], default 1 and auxiliary
string bypa. If absent and parent a<b, `0x272211104` constructs four points:

```
(value=1,time=a,linear)
(value=0,time=a,linear)
(value=0,time=b,linear)
(value=1,time=b,linear)
```

The new automation is prepended to the existing list. `withStyleBypass` ports
this rule; existing full-descriptor matches suppress insertion even with empty
points. `ContinuousAutomationValues`' already recovered reverse interval lookup
makes bypa=1 before a, 0 on [a,b), and 1 at/after b. Unit tests exercise these
boundaries, descriptor identity and zero-width parents under ASan/UBSan.

This resolves WHY the style JSON contains no bypa automation although the graph
resource defaults to bypassed. It supersedes the earlier 'activation callsite
not found' boundary in the graph evidence. It does not yet implement live AU
bypass fades or property delivery: TrackGraph.apply still rejects runtime bypass
changes, and prepareGraphEvents still rejects bypa. Those restrictions must be
replaced with the recovered host behavior before application playback is ready.

Still required: value mapping/parameter catalog and continuous schedule assembly,
cue-based parent selection, schedule sampling/delivery, AU bypass lifecycle,
TimePitch and application integration. This is not a full AutoMix completion.
