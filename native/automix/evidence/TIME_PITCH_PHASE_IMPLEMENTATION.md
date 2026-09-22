# TimePitch phase analysis and hop clock

New files: `time_pitch_phase.h/.cpp`, `time_pitch_phase_test.cpp`, and
`tools/time_pitch_phase_reference.py`. This is a further recovered part of the
actual NewTimePitch PCM pipeline. It is not the complete time stretcher.

## Exact source path

AU Render `0x234f9d9cc` calls main spectral-hop processor `0x234f46400`.
That processor uses:

1. `0x234f44794`: copy a frame from per-channel input ring.
2. `0x236f45f30`: imported window multiplication.
3. `0x234f44844`: split-complex packing and real forward FFT.
4. `0x234f448a0`: magnitude and phase analysis.
5. `0x234f44b58`: coherence-dependent correction.
6. `0x234f44d54`: optional transient processing.
7. `0x234f45224`: phase synthesis/complex rotation.
8. `0x234f45464`: spectral pitch remapping.
9. `0x234f455b8`: inverse FFT and unpacking.
10. `0x234f45614`: overlap-add to output ring.
11. `0x234f46208`: advance stream state and timing history.

Assembly for these functions is saved in `evidence/time_pitch`.

## Implemented phase branch

`timePitchAnalyzePhase` implements the four-lane branch of `0x234f448a0`,
selected by DSP state byte `+0x9a6 == 1`. Constructor `0x234f43e6c..43e74`
initializes that byte to one. It consumes split-complex FFT bins and previous
analysis/synthesis phases, updates analysis phase, and produces phase
corrections. Magnitudes, coherence and synthesis are separate remaining work.

This branch does **not** use atan2. Its phase-in-cycles approximation uses
float constants with bits `0x3e2c8649`, `0xbd321922`, FMA, and explicit
quadrant/sign selection. The phase residual is wrapped with
`residual - floor(residual + 0.5f)`. Pitch/output-hop normalization and the
propagated phase use the source float operation order including FMA. Both
signed zero axes and all four quadrants are covered by fixtures.

The function allocates nothing and is noexcept. The eventual processor must
supply validated buffer bounds and the recovered finite domain. It does not
invent a zero-input-hop fallback: such a state needs the complete DSP control
path before being exposed through an application API.

## Implemented hop clock

`timePitchAdvanceHop` implements `0x234f46498..464fc`; mapped equivalent
implements `0x234f46440..46494`, with the separate mapper result passed in.
The output hop is `floor(double(N)/double(float(smoothness)) + 0.5)`.
The ordinary input hop rounds `fma(outputHop, rate, 0.5)` down; effective input
hop retains the source's difference from the previous rounded hop. The mapped
branch uses mapped-next-input minus current-input and stores that exact
fractional difference as both effective and previous hop.

The API commits the returned next input/output times to its compact time-clock
state. These are the fields copied from `+0x8c0/+0x8c8` into `+0x8d0/+0x8d8`
by `0x234f46278..4627c`. Ring cursors and the 64-entry timing history are not
part of this compact clock and are not claimed implemented here.

## Validation

Unicorn executes original SIMD polynomial and both hop instruction spans with
**no hooks or replacement mathematical functions**. Fixtures cover 144 phase
cases (105,984 bins, comparing updated phase plus correction) and 320 hop
cases across four FFT sizes, smoothness/rate extremes, mapped/unmapped modes
and a nonzero fractional initial state. C++ matches every result bit-for-bit.

Standalone GCC ASan/UBSan, warnings-as-errors and `-ffp-contract=off` passed.
The source/test can be added to the existing native CMake targets; CMake and
app integration were deliberately left to the parent task.
