# AUFilter: full five-section kernel

libEmbeddedSystemAUs.dylib, iOS 23A341. Call chain and raw bounded instruction
dumps are saved in `filter/`:

- Factory 0x234f3a43c -> constructor 0x234f3a4e0.
- Class vtable 0x284adf598 +0x240 -> allocator 0x234f3921c.
- Kernel vtable 0x284adf808 +0x18 -> process 0x234f39304.
- Coefficient-generation update 0x234f393b0; peak-band update 0x234f395e8.
- Each section calls the previously recovered 0x234f8975c sample kernel.

Storage contains low edge, high edge, then three peaks. Processing order is low
edge -> peak 1 -> peak 2 -> peak 3 -> high edge, with float output between stages
and separate double histories. Coefficient updates preserve history. Zero gain
is not optimized into a bypass; the original still processes those sections.

The 15 AU constructor defaults are:
`0,100,0,625,0,2,2500,0,2,5000,0,2,0,10000,0`.
DSPGraph overrides IDs 3/4/5 through Fcf1/Fcg1/Fbw1 (2500/0/2 defaults).
Modes 0 and 12 select resonant HP/LP only when their converted integer is 1.
Otherwise the low/high edge uses the shelf wrappers 0x234f896c4/0x234f89710,
dispatcher kinds 7/8 with the exact stored Q 0.7071067811865475. Our typed bool
models these two supported modes; it is not an arbitrary-float AU setter.

AUFilter uses half of the **double** sample rate, without the standalone LP/HP
kernel's float-rate narrowing or 10 Hz floor. Nonpositive angle yields identity.
Peak bands first cap frequency to .99 of Nyquist, form the angle and cap that
again to .99*pi; nonpositive bandwidth also yields identity. Bandwidth-to-Q is:

```
t = (double(floatBandwidth) * .34657359027997264) * angle
s = sinh(t / sin(angle))
q = 1 / (s+s)
```

The actual Q round trip is retained before dispatcher kind 11 computes alpha.
The branch 0x236f45ca0 resolves to 0x296db9800, `_sinh`/`_sinhl`, confirmed in
libsystem_m's nlist. Peak and shelf amplitudes use exp10((gain*.5)*.05).
Normalization follows dispatcher stores 0x234fee4f4..51c; implicit FMA is disabled.
All formulas and operation order come from these routines, not an RBJ substitute.
Host validation rejects nonfinite/unbounded gain, nonfinite coefficient results
and unsupported sample rates. These rejection limits are not claims of AU clamps.

## Tests

`tools/filter_binary_reference.py` executes the original coefficient updater,
dispatcher and entire five-stage sample kernel in Unicorn. Parameter/rate getters
supply the fixture inputs, and math imports use host libm; PAC is removed from
the selected routines. No OS or Android emulator starts. It produces five cases
covering shelves, resonant edge modes, peaks, zero-bandwidth identity, nonpositive
frequency, Nyquist clamping, and a fractional sample rate.

All 125 coefficients and all 4169 output floats compare bitwise in `filter_test`.
The fixtures and code/output hashes are in `tests/fixtures/filter_reference.json`.
In-place processing and history-preserving updates also pass. This checks the
reconstructed algorithm with a common host libm, not Apple libm bit identity or
the full graph's scheduling/bypass host.
