# TimePitch magnitude primitive

`time_pitch_magnitude.h/.cpp` implements original vDSP complex magnitude
`0x236f46020 -> 0x234acfba4 -> 0x234acfbc0`. This is the magnitude producer
called by phase analysis before coherence/transient detection.

The original uses ARM reciprocal-square-root estimate followed by two
reciprocal-square-root refinement steps. It is not an interchangeable call to
hypot or sqrt. A 512-entry table in `time_pitch_rsqrt_table.inc` records the
original ARM estimate instruction over both normalized exponent parities and
the high eight mantissa bits. Normalization and exact powers-of-two scaling
reproduce the estimate for other normal/subnormal positive float inputs.
The source zero mask and float refinement rounding are preserved; each
FRSQRTS step is evaluated as an explicit fused operation.

The original also changes the sum-of-squares rounding across paths. Full
16-bin blocks and the first remaining 4-bin block use imag*imag fused with
real*real; subsequent 4-bin blocks use two rounded squares plus an add. The
scalar tail uses a reversed FMA after its first element. Alignment prefixes
have their own original order. The implementation follows these boundaries.

`tools/time_pitch_magnitude_reference.py` executes the complete original vDSP
routine without hooks. It also generates the estimate table and checks 9,216
bucket-boundary/exponent combinations. 504 magnitude fixtures cover lengths
0..33 and up to 4096, all destination alignments modulo 16, signed zero,
ordinary spectra and logarithmically distributed magnitudes spanning squared
subnormal values. Every output matches bit-for-bit under GCC ASan/UBSan and
`-ffp-contract=off`. No substitute mathematical function is used in the oracle.

The runtime routine allocates nothing. Finite ordinary spectral data is the
validated domain; overflowing/nonfinite spectral inputs propagate floating
nonfinite values, whose cross-platform NaN payload is not part of the contract.
