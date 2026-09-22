# Gain and mix sample operations

Followed GenericGainBox/MixBox imports through verified .06 branch islands and
.70 v5 authenticated pointer fixups, rather than substituting a generic fade.

* `0x1937345e0` -> `0x234b33380`, exported `_vDSP_vrampmul`.
* `0x193734640` -> `0x234bcf5b8` -> pointer `0x280df0958` -> resolver thunk
  `0x234bcf8a8` -> `0x234aec75c`, exported `_vDSP_vsmul`.
* `0x193734620` -> `0x234bcf594` -> pointer `0x280df0940` -> resolver thunk
  `0x234bcfce0` -> `0x234b7de20`, exported `_vDSP_vsma`.

The image Mach-O header is at 0x234ac2000. Export names were read from its nlist
in .72: symoff 90410464, 510 symbols, stroff 136209360. Function bounds came
from that image's LC_FUNCTION_STARTS, confirming vrampmul 0x234b33380..33680.
The earlier candidate dump is retained; the full bounded functions are adjacent.

## Ramp multiplication

The contiguous-float API reproduces the destination-alignment prefix, four-lane
FMA initialization with constant `[0,1,2,3]` at 0x234aeca00, eight groups per
32-sample loop, four-sample remainder and scalar tail. Strided overloads are not
exposed. The 32-sample step is `(16*increment)+(16*increment)`, and the intermediate
group values retain the original additions. This cannot be reduced to one FMA
per output sample while preserving rounding. The final accumulator is returned
through start, matching the imported routine; GainBox's control state separately
uses its projected block-end FMA and ignores that per-channel accumulator.

## Steady multiply and mix

The vsmul resolver selects 0x234acac84 or 0x234acb870 based on CPU flags; vsma
selects 0x234ae8d90 or 0x234ae6db0. The first versions forward counts below
20480 to the second. Their scalar and NEON paths use ordinary float multiply
and fused float multiply-add respectively. `multiplyGain` and `multiplyAddGain`
preserve those operations. Larger CPU-accelerated branches were not executed by
the fixtures; no claim of inspecting every specialized large-buffer path is made.

`applyGainBlock` now connects the recovered GainSmoother control to the ramp and
steady sample paths, including a constant suffix after a completed fade and
positive-zero filling on flagged silence. Callers must compute control once per
slice and reuse it for every channel; otherwise stereo would advance twice.
It does not yet wire the entire effect graph or its scheduler.

## Verification

`tools/gain_samples_reference.py` executes the original bounded instructions,
patching only PAC entry/return. There are no host libm or math-operation hooks.
The reference stores every float output and the ramp's final accumulator.

* 936 ramp cases: lengths 0..67, 95/96/97, 127/128/129, 255/256/257 and 1024;
  all four float destination offsets modulo 16; positive/negative/zero steps.
* 312 additional steady/mix cases, with ordinary and fused arithmetic references.
* Native checks compare all outputs bitwise, verify in-place buffers and guards,
  then apply one control block to two independent channels and test silence.

Fixtures/hashes are `tests/fixtures/gain_{ramp,steady}.{bin,json}`. There is no
Android emulator, OS boot, whole-cache mapping or service restart in this tool.
