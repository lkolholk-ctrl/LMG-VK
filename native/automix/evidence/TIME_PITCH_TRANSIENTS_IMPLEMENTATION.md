# TimePitch spectral coherence and transient preservation

`time_pitch_transients.h/.cpp` ports both complete functions from the actual
NewTimePitch hop path: `0x234f44b58` and `0x234f44d54`. Original instruction
ranges are saved in `evidence/time_pitch`. This adds two required DSP kernels;
it does not yet provide FFT or a complete streaming processor.

`timePitchApplyCoherence` finds the first maximum-magnitude bin and valley
boundaries, detects peaks against their two neighbours on each side, selects
inclusive peak regions, then copies each region's peak phase correction and
applies its pitch-dependent bin shift. Important exact details include:

- relative threshold is double(maximum magnitude) times double 0.001,
  narrowed to float;
- the rolling valley starts at float 1e10;
- peak selection uses non-strict comparison for immediate neighbours and
  strict comparison for second neighbours;
- after a peak, iteration advances by three bins and retains its right second
  neighbour as the rolling minimum;
- no peaks produces one region whose anchor is bin 1;
- pitch shift rounds a float FMA with 0.5, truncates, then applies unsigned
  wrap/range rejection. Out-of-range destinations are bin zero.

`timePitchPreserveTransients` ports the original state machine, including
weighted wrapped phase differences per region, negative/positive offset
counts, rate-dependent quorum, transient hold/release, phase flattening to the
maximum bin, effective-hop replacement and repayment of accumulated frame
adjustment. Double versus float operations and explicit FMA sites match the
original. Constants are read from the binary, including 0.65f/0.4f/0.3f,
rate boundaries 0.2 and 1.0, and the signed 0.3*N threshold.

The state contains original fields +0x8e0 (position), +0x8e4 (active), +0x8ec
(frame debt), +0x900 (effective input hop), and +0x8f8 (previous input hop).
Coherence supplies +0x8e8 (maximum bin), region boundaries and count. The
caller must copy any modified effective hop back into its stream clock before
advancing input cursors; skipping this would lose transient preservation.

All arrays and scratch are preallocated by the eventual processor. Kernels are
noexcept and allocate nothing. These internal kernels assume validated FFT
sizes, finite spectral data, valid inclusive regions and representable bounded
stream state; they are not untrusted recipe-input validators.

Validation uses `tools/time_pitch_transients_reference.py`, executing the two
entire ARM functions without DSP, mathematical or allocation substitutions.
Fixtures contain 48 coherence cases and 960 transient cases, with silence,
flat spectra, ascending/descending magnitudes, random spectra, regularly spaced
peaks, edge/tied peaks, very large magnitudes; rates below/above unity; both
signed phase slopes and random phases; five active/debt initial states.
305 of the transient function's 308 instructions were visited by these cases.
All regions, mapping entries, corrected phases, scratch values and state fields
match C++ bit-for-bit. Standalone GCC ASan/UBSan, warnings-as-errors and
`-ffp-contract=off` passed.
