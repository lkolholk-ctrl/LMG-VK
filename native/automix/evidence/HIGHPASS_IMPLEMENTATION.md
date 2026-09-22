# HP filter trace and implementation

23A341 chain: AUHipassFactory 0x234fc5c94 -> constructor 0x234f41a2c ->
vtable 0x284ad2b58 slot +0x240 -> allocator 0x234fc55d0 -> kernel vtable
0x284ad06e0 slot +0x18 -> process 0x234fc5654 -> update 0x234fc56cc.
Pointers decoded as verified slide-info v5 authenticated 34-bit runtime offsets.

Update duplicates LP cutoff normalization and resonance transformation, but passes
kind 2 into 0x234fee538. Dispatcher 0x234fee3d4..0x234fee414 uses
numerator (cos(angle)+1)*0.5, middle numerator * -2, with the same denominator
and coefficient storage as LP. Runtime process tail-branches to the SAME
0x234f8975c section kernel. This evidence permits sharing BiquadSection, including
its FMA order, full-precision state and block-end cleanup. No topology inferred
from standard EQ recipes. See LOWPASS_IMPLEMENTATION.md for the shared math imports.

Tests: analytic quarter-rate coefficients at resonance 0/+20 dB, DC rejection,
Nyquist response at 44.1/48 kHz, impulse/in-place behavior and state independence.
Shared-kernel tests also exercise all main/scalar splits for lengths 1..24.
