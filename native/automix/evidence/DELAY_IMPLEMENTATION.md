# Delay recovered from 23A341

Chain: AUDelayFactory 0x234fdf30c -> constructor 0x234f40748 -> vtable
0x284ad3420 slot +0x240 -> allocator 0x234fde9b8 -> kernel vtable
0x284ad0710 slot +0x18 -> process 0x234fdeabc. Three bounded disassemblies are
retained under evidence/delay. This code replaces no missing portion by inference.

## Allocation and time

Allocator takes trunc(fma(sampleRate,2,10)), rounds up to power-of-two length,
clears storage, sets write index to length-1 and filter accumulator to zero.
The process reads all four float parameters once per call. Delay seconds clamps
to float 0.0001..2; seconds*double(sampleRate) truncates to integer, then clamps
to 1..bufferLength-1. Read index is (write+length-delay)%length at each call.
There is no fractional-delay interpolation or implicit time smoothing.

## Signal operations

Cutoff clamps to float 10..24000 Hz. Normalized frequency is float(2*double(cutoff)/
sampleRate), capped at float 1. The coefficient is float(1-exp(-pi*double(normalized))).
Branch island 0x236f45790 jumps to libsystem_m 0x296db0b00, named _exp in nlist.
Feedback ratio is float(double(percent)*0.01), clamped by double comparisons
against +/-0.999 to the corresponding float +/-0.999 constants.

For each sample, using FLOAT operations with contraction disabled except explicit
FMA: filtered=previousAccumulator*coefficient; accumulator=(ringRead+accumulator)
-filtered; ringWrite=fma(filtered,feedback,input). The output uses the PREVIOUS
accumulator result: wet= float(double(wetPercent)*0.01), wetGain=sqrt(float(wet)),
dryGain=float(sqrt(1-double(wet))), output=fma(dryGain,input,wetGain*filtered).
Thus first fully-wet impulse output is at integer delay+1, not integer delay.
No end-of-block accumulator cleanup was found in this kernel.

Silence flag is cleared only if float absolute output peak, promoted to double,
exceeds 1e-6. It is not set true when samples become quiet. The C++ optional flag
preserves this behavior; a null flag simply omits status reporting.

Constants checked in __TEXT: .01 at 0x234ffcec8; .0001 at 0x234ffce38,
float lower delay at 0x234ffd840; 24000f at 0x234ffd8c8; -.999/.999 at
0x234ffd610/0x234ffcf78 and float counterparts 0x234ffd8cc/0x234ffd8d0;
-pi at 0x234ffcf28; silence threshold at 0x234ffd618.

## Host boundary and verification limits

Host preparation accepts PCM rates 8..192 kHz to bound allocations; this is not
claimed to be an Apple AU format limit. It rejects malformed nonfinite parameters,
wet percentages outside 0..100, missing buffers and unrepresentable frame counts.
DSP math/parameter clamps above are from code, while invalid-input rejection is
host infrastructure. Parameters are per-call; automation scheduling is not yet wired.
Platform exp/sqrt rounding is not proven bit-identical to Apple's math library.

Tests cover impulse onset/envelope, noninteger delay truncation, square-root wet/dry,
signed feedback onset, ring wrap, fixed-parameter block partition equality, in-place
processing, silence flag semantics and zero allocation in active processing.
No emulators used. No audio capture comparison or full AutoMix completion claimed.
