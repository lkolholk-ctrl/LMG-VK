# TimePitch synthesis, pitch remapping and overlap-add

`time_pitch_synthesis.h/.cpp` ports the default four-lane path of synthesis
`0x234f45224`, pitch-bin mapping `0x234f4608c`, remapping `0x234f45464` and
ring overlap-add `0x234f45614`. It uses the already-recovered analysis window,
phase corrections and FFT split-complex layout. FFT remains a separate module.

Synthesis normalization is exactly
`float(((outputHop * double(1.3333)) * double(inverseN)) * double(inverseN))`.
The constant at `0x234ffcde0` is **1.3333**, not mathematical 4/3. DC and Nyquist
are stored separately before complex rotation and restored afterwards.
The recovered polynomial sine/cosine approximation operates on phase cycles;
its FMA sites are preserved. Paired channels reuse rotations: the second
channel supplies `updateRotation=false` and does not change phase history.

Complex rotation is the original vDSP implementation reached from
`0x236f46040 -> 0x234ad14a8 -> 0x234ad1570`, with an initial float product
and FMA/subtract for each real/imaginary output. Oracle execution includes
that original implementation, not a complex arithmetic hook.

Pitch mapping uses float FMA with 0.5 followed by truncation. The original
mapping update runs only when pitch changes; coherence may subsequently
replace entries. The caller must preserve that ordering.

Remapping preserves an unusual but observable SIMD detail: four destination
values are gathered **before** any of the four additions are stored. If two
source bins in a group map to the same destination, the later store overwrites
the earlier contribution in that group. A scalar sequential scatter-add is
not equivalent. Both same-destination and randomly colliding mappings are in
the original-code fixtures. DC/Nyquist are then restored again.

Overlap-add wraps by splitting into at most two contiguous calls. Import
`0x236f45f00` resolves through `0x234bcf540` and the CPU resolver
`0x234b7dcb8` to `0x234ade520` or `0x234adecf0`; for our <=8192 frame calls,
the former unconditionally forwards to the latter. In this vDSP implementation,
32-element groups use separate float multiply and add, while alignment prefix
and remaining samples use FMA. The C++ port preserves destination alignment
and each wrap segment's independent grouping. Thus replacing this code with
one FMA loop would change output samples.

Validation: 24 synthesis, 20 mapping, 20 remap and 144 overlap-add cases execute
original functions and original vDSP math. Hooks only route resolved imports
and clear memory; PAC entry/return instructions are removed. Tests cover all
ring alignments modulo 16, wrap/nonwrap, four FFT sizes including 8192, arbitrary
initial ring contents and collisions. All outputs match C++ bit-for-bit.
Standalone GCC ASan/UBSan, warnings-as-errors and `-ffp-contract=off` passed.

These are internal noexcept kernels with caller-validated FFT sizes, pitch,
array bounds and nonoverlap contracts. No allocation occurs in their callbacks.
