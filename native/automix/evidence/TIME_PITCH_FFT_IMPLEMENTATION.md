# Prepared real FFT for TimePitch

`TimePitchRealFft` now provides the complete prepared forward/inverse backend,
using `TimePitchComplexFft`'s recovered radix stages and the original real
pre/post arithmetic. All scratch and twiddles are allocated at construction;
processing allocates nothing. Supported real lengths are powers of two from
256 through 8192. This is native C++; no JUCE/Oboe dependency.

The real dispatch is `0x234ace2a8`. Its stride-one forward postpass dispatches
`0x234acfac8` to `0x234aca520`; the inverse prepass dispatches `0x234b62dbc`
to `0x234ad0cd8`. Inverse has separate input/output pointers, not the forward
five-register signature. The portable kernels preserve four-lane load/store
ordering at the overlapping centre bin and separate multiply/add operations.
The real twiddle constructor `0x234ac3068` divides float 2*pi by N before
multiplying float indices, then uses cosf and double sin narrowed to float.

Planes have N/2 floats. real[0] packs DC; imaginary[0] packs Nyquist.
Forward has original vDSP factor two; inverse(forward(x)) has factor 2*N.
The caller controls normalization; this adapter introduces none. Inverse
preserves both caller input planes. One instance is owned by one processing
thread; internal scratch makes concurrent calls on the same instance invalid.

Validation: 72 original split-complex cases and 36 complete original real
forward/inverse cases pass bit-for-bit under ASan/UBSan. The latter include
impulse, constant and random inputs at every supported size, and independent
inverse spectra (not only round trips). Reproduction uses
`tools/time_pitch_real_fft_reference.py` with cache and data-cache directories;
it calls the original complete wrapper with allocation and host-libm hooks.
Original radix and real arithmetic instructions run unchanged. Fixture hashes
and image hashes are recorded beside the binary fixtures. These are bounded
instruction-level references, not an Android emulator.

This completes the FFT backend, not streaming TimePitch or app integration.
Input pull/output drain, identity-path resume, channel coupling and lifecycle
still need to be connected with the existing source-derived kernels before
this backend constitutes a usable track processor.
